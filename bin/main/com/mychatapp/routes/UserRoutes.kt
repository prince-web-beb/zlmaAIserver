package com.mychatapp.routes

import com.mychatapp.plugins.FirebaseUser
import com.mychatapp.services.UserService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val avatarUrl: String? = null
)

fun Route.userRoutes() {
    authenticate("firebase") {
        route("/api/user") {
            // Get current user profile
            get("/profile") {
                val user = call.principal<FirebaseUser>()!!
                val profile = UserService.getUserProfile(user.uid)
                call.respond(HttpStatusCode.OK, profile ?: mapOf("error" to "Profile not found"))
            }
            
            // Update user profile
            put("/profile") {
                val user = call.principal<FirebaseUser>()!!
                val request = call.receive<UpdateProfileRequest>()
                
                UserService.updateUserProfile(
                    uid = user.uid,
                    displayName = request.displayName,
                    avatarUrl = request.avatarUrl
                )
                
                call.respond(HttpStatusCode.OK, mapOf("updated" to true))
            }
            
            // Get usage stats
            get("/usage") {
                val user = call.principal<FirebaseUser>()!!
                val usage = UserService.getUserUsage(user.uid)
                call.respond(HttpStatusCode.OK, usage)
            }
            
            // Delete account
            delete("/account") {
                val user = call.principal<FirebaseUser>()!!
                UserService.deleteUser(user.uid)
                call.respond(HttpStatusCode.OK, mapOf("deleted" to true))
            }
        }
    }
}
