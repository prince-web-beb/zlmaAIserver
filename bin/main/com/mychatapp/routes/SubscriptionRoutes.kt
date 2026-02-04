package com.mychatapp.routes

import com.mychatapp.plugins.FirebaseUser
import com.mychatapp.plugins.ForbiddenException
import com.mychatapp.services.PaystackService
import com.mychatapp.services.SubscriptionPlan
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class InitPaymentRequest(
    val planId: String,
    val callbackUrl: String
)

@Serializable
data class VerifyPaymentRequest(
    val reference: String
)

@Serializable
data class CreatePlanRequest(
    val id: String? = null,
    val name: String,
    val tier: String,
    val price: Long,
    val currency: String = "NGN",
    val interval: String = "monthly",
    val features: List<String>,
    val messagesPerDay: Int,
    val canUploadImages: Boolean = false,
    val canUploadFiles: Boolean = false,
    val isActive: Boolean = true
)

fun Route.subscriptionRoutes() {
    // Public routes - get plans
    route("/api/subscriptions") {
        // Get all available plans (public)
        get("/plans") {
            val plans = PaystackService.getActivePlans()
            call.respond(HttpStatusCode.OK, plans)
        }

        // Get Paystack public key
        get("/paystack-key") {
            call.respond(HttpStatusCode.OK, mapOf(
                "publicKey" to PaystackService.getPublicKey()
            ))
        }
    }

    // Authenticated routes
    authenticate("firebase") {
        route("/api/subscriptions") {
            // Get current user's subscription
            get("/my-subscription") {
                val user = call.principal<FirebaseUser>()!!
                val subscription = PaystackService.getUserSubscription(user.uid)

                if (subscription != null) {
                    call.respond(HttpStatusCode.OK, subscription)
                } else {
                    call.respond(HttpStatusCode.OK, mapOf(
                        "status" to "none",
                        "tier" to "free"
                    ))
                }
            }

            // Initialize payment
            post("/init-payment") {
                val user = call.principal<FirebaseUser>()!!
                val request = call.receive<InitPaymentRequest>()

                val result = PaystackService.initializePayment(
                    userId = user.uid,
                    email = user.email,
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
            }

            // Verify payment and activate subscription
            post("/verify-payment") {
                val user = call.principal<FirebaseUser>()!!
                val request = call.receive<VerifyPaymentRequest>()

                try {
                    val subscription = PaystackService.processSuccessfulPayment(request.reference)

                    // Verify it belongs to this user
                    if (subscription.userId != user.uid) {
                        throw ForbiddenException("Subscription does not belong to this user")
                    }

                    call.respond(HttpStatusCode.OK, mapOf(
                        "success" to true,
                        "subscription" to subscription
                    ))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, mapOf(
                        "success" to false,
                        "error" to e.message
                    ))
                }
            }

            // Cancel subscription
            post("/cancel") {
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
                    "message" to "Subscription will remain active until ${subscription.endDate}"
                ))
            }
        }
    }

    // Admin routes for managing plans
    authenticate("firebase") {
        route("/api/admin/subscriptions") {
            // Get all plans (including inactive)
            get("/plans") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }
                val plans = PaystackService.getActivePlans() // TODO: Include inactive
                call.respond(HttpStatusCode.OK, plans)
            }

            // Create or update plan
            post("/plans") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }

                val request = call.receive<CreatePlanRequest>()
                val plan = SubscriptionPlan(
                    id = request.id ?: "",
                    name = request.name,
                    tier = request.tier,
                    price = request.price,
                    currency = request.currency,
                    interval = request.interval,
                    features = request.features,
                    messagesPerDay = request.messagesPerDay,
                    canUploadImages = request.canUploadImages,
                    canUploadFiles = request.canUploadFiles,
                    isActive = request.isActive
                )

                val savedPlan = PaystackService.savePlan(plan)
                call.respond(HttpStatusCode.OK, savedPlan)
            }

            // Delete plan
            delete("/plans/{planId}") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }

                val planId = call.parameters["planId"]!!
                PaystackService.deletePlan(planId)
                call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
            }

            // Get all subscriptions
            get("/all") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }
                // TODO: Implement admin view of all subscriptions
                call.respond(HttpStatusCode.OK, emptyList<Any>())
            }
        }
    }

    // Paystack webhook
    post("/api/webhooks/paystack") {
        // Verify webhook signature (TODO: implement proper verification)
        val body = call.receiveText()
        // Process webhook events
        // For now, just acknowledge
        call.respond(HttpStatusCode.OK, mapOf("received" to true))
    }
}
