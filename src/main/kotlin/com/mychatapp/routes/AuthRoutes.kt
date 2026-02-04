package com.mychatapp.routes

import com.mychatapp.plugins.Firebase
import com.mychatapp.services.UserService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val idToken: String,
    val displayName: String? = null
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val userId: String,
    val message: String
)

fun Route.authRoutes() {
    route("/api/auth") {
        // Register new user (creates Firestore profile after Firebase Auth)
        post("/register") {
            val request = call.receive<RegisterRequest>()
            
            // Verify the Firebase ID token
            val decodedToken = Firebase.auth.verifyIdToken(request.idToken)
            val uid = decodedToken.uid
            val email = decodedToken.email ?: ""
            
            // Create user profile in Firestore
            UserService.createUserProfile(
                uid = uid,
                email = email,
                displayName = request.displayName ?: decodedToken.name ?: "User"
            )
            
            call.respond(
                HttpStatusCode.Created,
                AuthResponse(
                    success = true,
                    userId = uid,
                    message = "User registered successfully"
                )
            )
        }
        
        // Verify token (for mobile app to check if token is valid)
        post("/verify") {
            val request = call.receive<RegisterRequest>()
            
            val decodedToken = Firebase.auth.verifyIdToken(request.idToken)
            
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "valid" to true,
                    "uid" to decodedToken.uid,
                    "email" to decodedToken.email
                )
            )
        }
    }
}
