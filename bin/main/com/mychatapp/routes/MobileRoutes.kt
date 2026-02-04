package com.mychatapp.routes

import com.mychatapp.plugins.FirebaseUser
import com.mychatapp.services.MobileApiService
import com.mychatapp.services.PaystackService
import com.mychatapp.services.SubscriptionService
import com.mychatapp.services.UserService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

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
data class MobileUserProfile(
    val uid: String,
    val email: String,
    val displayName: String,
    val tier: String,
    val isSubscribed: Boolean,
    val canUploadImages: Boolean,
    val canUploadFiles: Boolean,
    val messagesUsedToday: Int,
    val messagesPerDay: Int,
    val subscriptionEndDate: Long? = null,
    val planName: String? = null
)

fun Route.mobileRoutes() {
    // Public endpoints
    route("/api/mobile") {
        // Get available subscription plans (public)
        get("/plans") {
            val plans = SubscriptionService.getAvailablePlans()
            call.respond(HttpStatusCode.OK, plans)
        }

        // Get Paystack public key
        get("/paystack-key") {
            call.respond(HttpStatusCode.OK, mapOf(
                "publicKey" to PaystackService.getPublicKey()
            ))
        }
    }

    // Authenticated endpoints
    authenticate("firebase") {
        route("/api/mobile") {
            // Get or create user profile
            post("/auth/register") {
                val user = call.principal<FirebaseUser>()!!

                // Check if user exists
                var profile = UserService.getUserProfile(user.uid)

                if (profile == null) {
                    // Create new user
                    profile = UserService.createUserProfile(
                        uid = user.uid,
                        email = user.email ?: "",
                        displayName = user.email?.substringBefore("@") ?: "User"
                    )
                }

                val subscriptionStatus = SubscriptionService.getUserSubscriptionStatus(user.uid)

                call.respond(HttpStatusCode.OK, MobileUserProfile(
                    uid = profile.uid,
                    email = profile.email,
                    displayName = profile.displayName,
                    tier = subscriptionStatus.tier,
                    isSubscribed = subscriptionStatus.isSubscribed,
                    canUploadImages = subscriptionStatus.canUploadImages,
                    canUploadFiles = subscriptionStatus.canUploadFiles,
                    messagesUsedToday = subscriptionStatus.messagesUsedToday,
                    messagesPerDay = subscriptionStatus.messagesPerDay,
                    subscriptionEndDate = subscriptionStatus.subscriptionEndDate,
                    planName = subscriptionStatus.planName
                ))
            }

            // Get current user profile
            get("/profile") {
                val user = call.principal<FirebaseUser>()!!
                val profile = UserService.getUserProfile(user.uid)

                if (profile == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Profile not found"))
                    return@get
                }

                val subscriptionStatus = SubscriptionService.getUserSubscriptionStatus(user.uid)

                call.respond(HttpStatusCode.OK, MobileUserProfile(
                    uid = profile.uid,
                    email = profile.email,
                    displayName = profile.displayName,
                    tier = subscriptionStatus.tier,
                    isSubscribed = subscriptionStatus.isSubscribed,
                    canUploadImages = subscriptionStatus.canUploadImages,
                    canUploadFiles = subscriptionStatus.canUploadFiles,
                    messagesUsedToday = subscriptionStatus.messagesUsedToday,
                    messagesPerDay = subscriptionStatus.messagesPerDay,
                    subscriptionEndDate = subscriptionStatus.subscriptionEndDate,
                    planName = subscriptionStatus.planName
                ))
            }

            // Get subscription status
            get("/subscription") {
                val user = call.principal<FirebaseUser>()!!
                val status = SubscriptionService.getUserSubscriptionStatus(user.uid)
                call.respond(HttpStatusCode.OK, status)
            }

            // Chat endpoint - MAIN ENDPOINT
            post("/chat") {
                val user = call.principal<FirebaseUser>()!!
                val request = call.receive<MobileChatRequest>()

                try {
                    val response = MobileApiService.sendMessage(
                        userId = user.uid,
                        request = MobileApiService.MobileChatRequest(
                            messages = request.messages.map {
                                MobileApiService.MobileMessage(it.role, it.content, it.imageUrl)
                            },
                            conversationId = request.conversationId,
                            hasImage = request.hasImage,
                            hasFile = request.hasFile
                        )
                    )

                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to (e.message ?: "Failed to send message")
                    ))
                }
            }

            // Get conversations
            get("/conversations") {
                val user = call.principal<FirebaseUser>()!!
                val conversations = MobileApiService.getUserConversations(user.uid)
                call.respond(HttpStatusCode.OK, conversations)
            }

            // Get specific conversation
            get("/conversations/{id}") {
                val user = call.principal<FirebaseUser>()!!
                val conversationId = call.parameters["id"]!!

                try {
                    val conversation = MobileApiService.getConversation(user.uid, conversationId)
                    call.respond(HttpStatusCode.OK, conversation)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
                }
            }

            // Delete conversation
            delete("/conversations/{id}") {
                val user = call.principal<FirebaseUser>()!!
                val conversationId = call.parameters["id"]!!

                try {
                    MobileApiService.deleteConversation(user.uid, conversationId)
                    call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                }
            }

            // Initialize payment for subscription
            post("/subscribe") {
                val user = call.principal<FirebaseUser>()!!

                @Serializable
                data class SubscribeRequest(
                    val planId: String,
                    val callbackUrl: String
                )

                val request = call.receive<SubscribeRequest>()

                try {
                    val result = PaystackService.initializePayment(
                        userId = user.uid,
                        email = user.email ?: throw IllegalStateException("Email required for payment"),
                        planId = request.planId,
                        callbackUrl = request.callbackUrl
                    )

                    if (result.status && result.data != null) {
                        call.respond(HttpStatusCode.OK, mapOf(
                            "authorizationUrl" to result.data.authorizationUrl,
                            "reference" to result.data.reference
                        ))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, mapOf(
                            "error" to result.message
                        ))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "error" to (e.message ?: "Failed to initialize payment")
                    ))
                }
            }

            // Verify payment after Paystack redirect
            post("/verify-payment") {
                val user = call.principal<FirebaseUser>()!!

                @Serializable
                data class VerifyRequest(val reference: String)

                val request = call.receive<VerifyRequest>()

                try {
                    val subscription = PaystackService.processSuccessfulPayment(request.reference)

                    if (subscription.userId != user.uid) {
                        call.respond(HttpStatusCode.Forbidden, mapOf(
                            "error" to "Subscription does not belong to this user"
                        ))
                        return@post
                    }

                    // Get updated status
                    val status = SubscriptionService.getUserSubscriptionStatus(user.uid)

                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to true,
                        "subscription" to subscription,
                        "status" to status
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "success" to false,
                        "error" to (e.message ?: "Payment verification failed")
                    ))
                }
            }

            // Cancel subscription
            post("/cancel-subscription") {
                val user = call.principal<FirebaseUser>()!!

                val subscription = PaystackService.getUserSubscription(user.uid)

                if (subscription == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf(
                        "error" to "No active subscription found"
                    ))
                    return@post
                }

                PaystackService.cancelSubscription(subscription.id)

                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "message" to "Subscription will remain active until ${java.time.Instant.ofEpochMilli(subscription.endDate)}"
                ))
            }
        }
    }
}
