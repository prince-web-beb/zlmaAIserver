package com.mychatapp.services

import com.mychatapp.config.ZlmaAIPersona
import com.mychatapp.plugins.Firebase
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.*

/**
 * Mobile API Service - Handles chat requests from mobile app
 * Users don't need their own API key - server handles everything
 */
object MobileApiService {
    private val dotenv = dotenv { ignoreIfMissing = true }
    private val openRouterApiKey = dotenv["OPENROUTER_API_KEY"] ?: System.getenv("OPENROUTER_API_KEY") ?: ""

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val conversationsCollection = Firebase.firestore.collection("conversations")
    private val usersCollection = Firebase.firestore.collection("users")

    @Serializable
    data class MobileChatRequest(
        val messages: List<MobileMessage>,
        val conversationId: String? = null,
        val hasImage: Boolean = false,
        val hasFile: Boolean = false
    )

    @Serializable
    data class MobileMessage(
        val role: String,
        val content: String,
        val imageUrl: String? = null
    )

    @Serializable
    data class MobileChatResponse(
        val id: String,
        val message: MobileMessage,
        val conversationId: String,
        val usage: MobileUsage? = null
    )

    @Serializable
    data class MobileUsage(
        val messagesUsedToday: Int,
        val messagesPerDay: Int,
        val canUploadImages: Boolean,
        val canUploadFiles: Boolean
    )

    @Serializable
    private data class OpenRouterRequest(
        val model: String,
        val messages: List<OpenRouterMsg>
    )

    @Serializable
    private data class OpenRouterMsg(
        val role: String,
        val content: String
    )

    @Serializable
    private data class OpenRouterResponse(
        val id: String,
        val choices: List<OpenRouterChoice>,
        val usage: OpenRouterUsage? = null
    )

    @Serializable
    private data class OpenRouterChoice(
        val message: OpenRouterMsg
    )

    @Serializable
    private data class OpenRouterUsage(
        @SerialName("prompt_tokens") val promptTokens: Int,
        @SerialName("completion_tokens") val completionTokens: Int,
        @SerialName("total_tokens") val totalTokens: Int
    )

    /**
     * Send a chat message - main endpoint for mobile app
     */
    suspend fun sendMessage(
        userId: String,
        request: MobileChatRequest
    ): MobileChatResponse = withContext(Dispatchers.IO) {
        // Check subscription status
        val subscriptionStatus = SubscriptionService.getUserSubscriptionStatus(userId)

        // Check if user can chat
        if (!subscriptionStatus.canChat) {
            throw Exception("Daily message limit reached. Upgrade to Premium for more messages.")
        }

        // Check if user can upload images/files
        if (request.hasImage && !subscriptionStatus.canUploadImages) {
            throw Exception("Image uploads require a Premium subscription. Upgrade to unlock this feature.")
        }

        if (request.hasFile && !subscriptionStatus.canUploadFiles) {
            throw Exception("File uploads require a Premium subscription. Upgrade to unlock this feature.")
        }

        // Prepare messages with system prompt
        val systemPrompt = ZlmaAIPersona.getSystemPrompt()
        val messagesWithPersona = listOf(OpenRouterMsg("system", systemPrompt)) +
            request.messages.map { OpenRouterMsg(it.role, it.content) }

        // Select model based on tier (premium users get better models)
        val model = when (subscriptionStatus.tier) {
            "enterprise" -> "openai/gpt-4o"
            "pro" -> "openai/gpt-4o-mini"
            else -> "openai/gpt-4o-mini" // Free tier
        }

        // Call OpenRouter API
        val response = client.post("https://openrouter.ai/api/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $openRouterApiKey")
            header("HTTP-Referer", "https://zlmaai.com")
            header("X-Title", "Zlma AI")
            setBody(OpenRouterRequest(
                model = model,
                messages = messagesWithPersona
            ))
        }

        val openRouterResponse = response.body<OpenRouterResponse>()
        val assistantMessage = openRouterResponse.choices.firstOrNull()?.message
            ?: throw Exception("No response from AI")

        // Sanitize response
        val sanitizedContent = ZlmaAIPersona.sanitizeResponse(assistantMessage.content)

        // Create or get conversation ID
        val conversationId = request.conversationId ?: UUID.randomUUID().toString()

        // Save conversation
        saveConversation(userId, conversationId, request.messages, sanitizedContent)

        // Increment message count
        incrementUserMessageCount(userId)

        // Log usage
        logUsage(userId, model, openRouterResponse.usage)

        // Get updated usage
        val updatedStatus = SubscriptionService.getUserSubscriptionStatus(userId)

        MobileChatResponse(
            id = openRouterResponse.id,
            message = MobileMessage(
                role = "assistant",
                content = sanitizedContent
            ),
            conversationId = conversationId,
            usage = MobileUsage(
                messagesUsedToday = updatedStatus.messagesUsedToday,
                messagesPerDay = updatedStatus.messagesPerDay,
                canUploadImages = updatedStatus.canUploadImages,
                canUploadFiles = updatedStatus.canUploadFiles
            )
        )
    }

    private suspend fun saveConversation(
        userId: String,
        conversationId: String,
        userMessages: List<MobileMessage>,
        assistantContent: String
    ) = withContext(Dispatchers.IO) {
        val docRef = conversationsCollection.document(conversationId)
        val doc = docRef.get().get()
        val now = System.currentTimeMillis()

        if (!doc.exists()) {
            // Create new conversation
            val title = userMessages.lastOrNull()?.content?.take(50) ?: "New Chat"
            val messages = userMessages.map {
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "role" to it.role,
                    "content" to it.content,
                    "timestamp" to now
                )
            } + mapOf(
                "id" to UUID.randomUUID().toString(),
                "role" to "assistant",
                "content" to assistantContent,
                "timestamp" to now
            )

            docRef.set(mapOf(
                "id" to conversationId,
                "userId" to userId,
                "title" to title,
                "messages" to messages,
                "createdAt" to now,
                "updatedAt" to now
            )).get()
        } else {
            // Append to existing
            val existingMessages = doc.get("messages") as? List<*> ?: emptyList<Any>()
            val newMessages = existingMessages.toMutableList()

            // Add user's latest message
            val lastUserMsg = userMessages.lastOrNull()
            if (lastUserMsg != null) {
                newMessages.add(mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "role" to lastUserMsg.role,
                    "content" to lastUserMsg.content,
                    "timestamp" to now
                ))
            }

            // Add assistant response
            newMessages.add(mapOf(
                "id" to UUID.randomUUID().toString(),
                "role" to "assistant",
                "content" to assistantContent,
                "timestamp" to now
            ))

            docRef.update(mapOf(
                "messages" to newMessages,
                "updatedAt" to now
            )).get()
        }
    }

    private suspend fun incrementUserMessageCount(userId: String) = withContext(Dispatchers.IO) {
        val doc = usersCollection.document(userId).get().get()
        val currentDaily = doc.getLong("messagesUsedToday")?.toInt() ?: 0
        val currentTotal = doc.getLong("totalMessages")?.toInt() ?: 0

        // Check if we need to reset daily count
        val lastResetDate = doc.getString("lastResetDate")
        val today = java.time.LocalDate.now().toString()

        if (lastResetDate != today) {
            usersCollection.document(userId).update(mapOf(
                "messagesUsedToday" to 1,
                "totalMessages" to currentTotal + 1,
                "lastActiveAt" to System.currentTimeMillis(),
                "lastResetDate" to today
            )).get()
        } else {
            usersCollection.document(userId).update(mapOf(
                "messagesUsedToday" to currentDaily + 1,
                "totalMessages" to currentTotal + 1,
                "lastActiveAt" to System.currentTimeMillis()
            )).get()
        }
    }

    private suspend fun logUsage(userId: String, model: String, usage: OpenRouterUsage?) {
        Firebase.firestore.collection("usage_logs").add(mapOf(
            "userId" to userId,
            "model" to model,
            "promptTokens" to (usage?.promptTokens ?: 0),
            "completionTokens" to (usage?.completionTokens ?: 0),
            "totalTokens" to (usage?.totalTokens ?: 0),
            "timestamp" to System.currentTimeMillis()
        )).get()
    }

    /**
     * Get user's conversations
     */
    suspend fun getUserConversations(userId: String) = withContext(Dispatchers.IO) {
        val docs = conversationsCollection
            .whereEqualTo("userId", userId)
            .orderBy("updatedAt", com.google.cloud.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .get().get()

        docs.documents.map { doc ->
            mapOf(
                "id" to (doc.getString("id") ?: doc.id),
                "title" to (doc.getString("title") ?: "Chat"),
                "updatedAt" to (doc.getLong("updatedAt") ?: 0)
            )
        }
    }

    /**
     * Get a specific conversation
     */
    suspend fun getConversation(userId: String, conversationId: String) = withContext(Dispatchers.IO) {
        val doc = conversationsCollection.document(conversationId).get().get()

        if (!doc.exists()) throw Exception("Conversation not found")
        if (doc.getString("userId") != userId) throw Exception("Access denied")

        mapOf(
            "id" to (doc.getString("id") ?: conversationId),
            "title" to (doc.getString("title") ?: "Chat"),
            "messages" to (doc.get("messages") ?: emptyList<Any>()),
            "createdAt" to (doc.getLong("createdAt") ?: 0),
            "updatedAt" to (doc.getLong("updatedAt") ?: 0)
        )
    }

    /**
     * Delete a conversation
     */
    suspend fun deleteConversation(userId: String, conversationId: String) = withContext(Dispatchers.IO) {
        val doc = conversationsCollection.document(conversationId).get().get()

        if (!doc.exists()) throw Exception("Conversation not found")
        if (doc.getString("userId") != userId) throw Exception("Access denied")

        conversationsCollection.document(conversationId).delete().get()
    }
}
