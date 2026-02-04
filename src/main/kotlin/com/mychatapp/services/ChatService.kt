package com.mychatapp.services

import com.mychatapp.config.ZlmaAIPersona
import com.mychatapp.models.Conversation
import com.mychatapp.models.ConversationSummary
import com.mychatapp.models.Message
import com.mychatapp.plugins.Firebase
import com.mychatapp.routes.ChatMessage
import com.mychatapp.routes.ChatResponse
import com.mychatapp.routes.TokenUsage
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

@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>
)

@Serializable
data class OpenRouterMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenRouterResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val message: OpenRouterMessage
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)

@Serializable
data class AvailableModel(
    val id: String,
    val name: String,
    val description: String,
    val contextLength: Int
)

object ChatService {
    private val dotenv = dotenv { ignoreIfMissing = true }
    private val apiKey = dotenv["OPENROUTER_API_KEY"] ?: System.getenv("OPENROUTER_API_KEY") ?: ""
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    private val conversationsCollection by lazy { Firebase.firestore.collection("conversations") }

    // Map Zlma model IDs to actual OpenRouter model IDs (internal only)
    private val modelMapping = mapOf(
        "zlma-pro" to "openai/gpt-4o",
        "zlma-fast" to "openai/gpt-4o-mini",
        "zlma-creative" to "anthropic/claude-3.5-sonnet",
        "zlma-research" to "google/gemini-pro-1.5",
        "zlma-open" to "meta-llama/llama-3.1-70b-instruct",
        // Also accept the real model IDs for backward compatibility
        "openai/gpt-4o" to "openai/gpt-4o",
        "openai/gpt-4o-mini" to "openai/gpt-4o-mini"
    )

    private fun resolveModel(zlmaModelId: String): String {
        return modelMapping[zlmaModelId] ?: "openai/gpt-4o" // Default to pro model
    }

    suspend fun sendMessage(
        userId: String,
        messages: List<ChatMessage>,
        model: String,
        conversationId: String?
    ): ChatResponse = withContext(Dispatchers.IO) {
        // Resolve the actual model from Zlma model ID
        val actualModel = resolveModel(model)

        // Inject Zlma AI system prompt at the beginning
        val messagesWithPersona = listOf(
            OpenRouterMessage("system", ZlmaAIPersona.getSystemPrompt())
        ) + messages.map { OpenRouterMessage(it.role, it.content) }
        
        // Call OpenRouter API
        val response = client.post("https://openrouter.ai/api/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            header("HTTP-Referer", "https://zlmaai.com")
            header("X-Title", "Zlma AI")
            setBody(OpenRouterRequest(
                model = actualModel,
                messages = messagesWithPersona
            ))
        }
        
        val openRouterResponse = response.body<OpenRouterResponse>()
        val assistantMessage = openRouterResponse.choices.firstOrNull()?.message
            ?: throw Exception("No response from AI")
        
        // Sanitize response to remove any leaked identity info
        val sanitizedContent = ZlmaAIPersona.sanitizeResponse(assistantMessage.content)
        
        // Save to conversation (with sanitized content)
        val convoId = conversationId ?: UUID.randomUUID().toString()
        saveToConversation(userId, convoId, messages, OpenRouterMessage(assistantMessage.role, sanitizedContent), model)
        
        // Log for analytics
        logApiUsage(userId, model, openRouterResponse.usage)
        
        ChatResponse(
            id = openRouterResponse.id,
            message = ChatMessage(
                role = assistantMessage.role,
                content = sanitizedContent  // Return sanitized content
            ),
            model = "zlma-ai-v1",  // Hide real model from client
            usage = openRouterResponse.usage?.let {
                TokenUsage(it.promptTokens, it.completionTokens, it.totalTokens)
            }
        )
    }
    
    private suspend fun saveToConversation(
        userId: String,
        conversationId: String,
        userMessages: List<ChatMessage>,
        assistantMessage: OpenRouterMessage,
        model: String
    ) {
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
                "role" to assistantMessage.role,
                "content" to assistantMessage.content,
                "timestamp" to now
            )
            
            docRef.set(mapOf(
                "id" to conversationId,
                "userId" to userId,
                "title" to title,
                "messages" to messages,
                "model" to model,
                "createdAt" to now,
                "updatedAt" to now
            )).get()
        } else {
            // Append to existing
            val existingMessages = doc.get("messages") as? List<*> ?: emptyList<Any>()
            val newMessages = existingMessages.toMutableList()
            
            // Add user's latest message if not already there
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
                "role" to assistantMessage.role,
                "content" to assistantMessage.content,
                "timestamp" to now
            ))
            
            docRef.update(mapOf(
                "messages" to newMessages,
                "updatedAt" to now
            )).get()
        }
    }
    
    private suspend fun logApiUsage(userId: String, model: String, usage: Usage?) {
        Firebase.firestore.collection("usage_logs").add(mapOf(
            "userId" to userId,
            "model" to model,
            "promptTokens" to (usage?.promptTokens ?: 0),
            "completionTokens" to (usage?.completionTokens ?: 0),
            "totalTokens" to (usage?.totalTokens ?: 0),
            "timestamp" to System.currentTimeMillis()
        )).get()
    }
    
    fun getAvailableModels(): List<AvailableModel> {
        return listOf(
            AvailableModel(
                id = "zlma-pro",
                name = "Zlma Pro",
                description = "Our most capable model",
                contextLength = 128000
            ),
            AvailableModel(
                id = "zlma-fast",
                name = "Zlma Fast",
                description = "Fast and efficient",
                contextLength = 128000
            ),
            AvailableModel(
                id = "zlma-creative",
                name = "Zlma Creative",
                description = "Best for creative tasks",
                contextLength = 200000
            ),
            AvailableModel(
                id = "zlma-research",
                name = "Zlma Research",
                description = "Extended context for research",
                contextLength = 1000000
            ),
            AvailableModel(
                id = "zlma-open",
                name = "Zlma Open",
                description = "Open and versatile",
                contextLength = 131072
            )
        )
    }
    
    suspend fun getUserConversations(userId: String): List<ConversationSummary> = withContext(Dispatchers.IO) {
        val docs = conversationsCollection
            .whereEqualTo("userId", userId)
            .orderBy("updatedAt", com.google.cloud.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .get().get()
        
        docs.documents.map { doc ->
            val messages = doc.get("messages") as? List<Map<String, Any>> ?: emptyList()
            val lastMessage = messages.lastOrNull()
            
            ConversationSummary(
                id = doc.getString("id") ?: doc.id,
                title = doc.getString("title") ?: "Chat",
                lastMessage = lastMessage?.get("content") as? String,
                messageCount = messages.size,
                model = doc.getString("model") ?: "unknown",
                updatedAt = doc.getLong("updatedAt") ?: 0
            )
        }
    }
    
    suspend fun getConversation(userId: String, conversationId: String): Conversation = withContext(Dispatchers.IO) {
        val doc = conversationsCollection.document(conversationId).get().get()
        
        if (!doc.exists() || doc.getString("userId") != userId) {
            throw Exception("Conversation not found")
        }
        
        val messages = (doc.get("messages") as? List<Map<String, Any>> ?: emptyList()).map {
            Message(
                id = it["id"] as? String ?: "",
                role = it["role"] as? String ?: "",
                content = it["content"] as? String ?: "",
                timestamp = it["timestamp"] as? Long ?: 0
            )
        }
        
        Conversation(
            id = doc.getString("id") ?: doc.id,
            userId = userId,
            title = doc.getString("title") ?: "Chat",
            messages = messages,
            model = doc.getString("model") ?: "unknown",
            createdAt = doc.getLong("createdAt") ?: 0,
            updatedAt = doc.getLong("updatedAt") ?: 0
        )
    }
    
    suspend fun deleteConversation(userId: String, conversationId: String) = withContext(Dispatchers.IO) {
        val doc = conversationsCollection.document(conversationId).get().get()
        
        if (doc.exists() && doc.getString("userId") == userId) {
            conversationsCollection.document(conversationId).delete().get()
        }
    }
}
