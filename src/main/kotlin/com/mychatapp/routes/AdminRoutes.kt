package com.mychatapp.routes

import com.mychatapp.plugins.FirebaseUser
import com.mychatapp.plugins.ForbiddenException
import com.mychatapp.services.AdminService
import com.mychatapp.services.UserService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class SetAdminRequest(
    val userId: String,
    val isAdmin: Boolean
)

@Serializable
data class UpdateUserTierRequest(
    val userId: String,
    val tier: String // "free", "pro", "enterprise"
)

@Serializable
data class BanUserRequest(
    val userId: String,
    val banned: Boolean,
    val reason: String? = null
)

fun Route.adminRoutes() {
    authenticate("firebase") {
        route("/api/admin") {
            // Dashboard stats
            get("/stats") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }
                val stats = AdminService.getDashboardStats()
                call.respond(HttpStatusCode.OK, stats)
            }
            
            // Get all users (paginated)
            get("/users") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }
                val page = call.parameters["page"]?.toIntOrNull() ?: 1
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
                val users = AdminService.getAllUsers(page, limit)
                call.respond(HttpStatusCode.OK, users)
            }
            
            // Get specific user details
            get("/users/{userId}") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }
                val userId = call.parameters["userId"]!!
                val userDetails = AdminService.getUserDetails(userId)
                call.respond(HttpStatusCode.OK, userDetails)
            }
            
            // Set admin status
            post("/users/set-admin") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }
                val request = call.receive<SetAdminRequest>()
                AdminService.setAdminStatus(request.userId, request.isAdmin)
                call.respond(HttpStatusCode.OK, mapOf("success" to true))
            }
            
            // Update user tier
            post("/users/set-tier") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }
                val request = call.receive<UpdateUserTierRequest>()
                UserService.updateUserTier(request.userId, request.tier)
                call.respond(HttpStatusCode.OK, mapOf("success" to true))
            }
            
            // Ban/unban user
            post("/users/ban") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }
                val request = call.receive<BanUserRequest>()
                AdminService.setBanStatus(request.userId, request.banned, request.reason)
                call.respond(HttpStatusCode.OK, mapOf("success" to true))
            }
            
            // Get usage analytics
            get("/analytics") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }
                val period = call.parameters["period"] ?: "7d" // 7d, 30d, 90d
                val analytics = AdminService.getAnalytics(period)
                call.respond(HttpStatusCode.OK, analytics)
            }
            
            // Get API usage logs
            get("/logs") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }
                val page = call.parameters["page"]?.toIntOrNull() ?: 1
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 50
                val logs = AdminService.getApiLogs(page, limit)
                call.respond(HttpStatusCode.OK, logs)
            }
            
            // System settings
            get("/settings") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }
                val settings = AdminService.getSystemSettings()
                call.respond(HttpStatusCode.OK, settings)
            }
            
            put("/settings") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }
                val settings = call.receive<Map<String, String>>()
                AdminService.updateSystemSettings(settings)
                call.respond(HttpStatusCode.OK, mapOf("updated" to true))
            }

            // Revenue stats
            get("/revenue") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }
                val revenue = AdminService.getRevenueStats()
                call.respond(HttpStatusCode.OK, revenue)
            }

            // All subscriptions (for admin view)
            get("/subscriptions/all") {
                val user = call.principal<FirebaseUser>()
                if (user == null || !user.isAdmin) {
                    throw ForbiddenException("Admin access required")
                }
                val subscriptions = AdminService.getAllSubscriptions()
                call.respond(HttpStatusCode.OK, subscriptions)
            }
        }
    }
}
