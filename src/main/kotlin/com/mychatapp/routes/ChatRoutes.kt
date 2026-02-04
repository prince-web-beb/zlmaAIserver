package com.mychatapp.routes

import com.mychatapp.models.*
import com.mychatapp.plugins.FirebaseUser
import com.mychatapp.plugins.ForbiddenException
import com.mychatapp.services.ChatService
import com.mychatapp.services.UserService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val messages: List<ChatMessage>,
    val model: String = "openai/gpt-4o",
    val conversationId: String? = null
)

@Serializable
data class ChatMessage(
    val role: String, // "user" or "assistant"
    val content: String
)

@Serializable
data class ChatResponse(
    val id: String,
    val message: ChatMessage,
    val model: String,
    val usage: TokenUsage? = null
)

@Serializable
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

fun Route.chatRoutes() {
    authenticate("firebase") {
        route("/api/chat") {
            // Send message and get AI response
            post {
                val user = call.principal<FirebaseUser>()!!
                val request = call.receive<ChatRequest>()
                
                // Check user's quota/limits
                val userProfile = UserService.getUserProfile(user.uid)
                if (userProfile == null) {
                    throw ForbiddenException("User profile not found. Please register first.")
                }
                
                if (!UserService.canUserChat(user.uid)) {
                    throw ForbiddenException("Daily message limit reached. Upgrade your plan for more messages.")
                }
                
                // Call OpenRouter API
                val response = ChatService.sendMessage(
                    userId = user.uid,
                    messages = request.messages,
                    model = request.model,
                    conversationId = request.conversationId
                )
                
                // Track usage
                UserService.incrementMessageCount(user.uid)
                
                call.respond(HttpStatusCode.OK, response)
            }
            
            // Get available models
            get("/models") {
                val models = ChatService.getAvailableModels()
                call.respond(HttpStatusCode.OK, models)
            }
            
            // Get conversation history
            get("/conversations") {
                val user = call.principal<FirebaseUser>()!!
                val conversations = ChatService.getUserConversations(user.uid)
                call.respond(HttpStatusCode.OK, conversations)
            }
            
            // Get specific conversation
            get("/conversations/{id}") {
                val user = call.principal<FirebaseUser>()!!
                val conversationId = call.parameters["id"]!!
                val conversation = ChatService.getConversation(user.uid, conversationId)
                call.respond(HttpStatusCode.OK, conversation)
            }
            
            // Delete conversation
            delete("/conversations/{id}") {
                val user = call.principal<FirebaseUser>()!!
                val conversationId = call.parameters["id"]!!
                ChatService.deleteConversation(user.uid, conversationId)
                call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
            }
        }
    }
}
