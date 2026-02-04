package com.mychatapp.plugins

import com.google.firebase.auth.FirebaseAuthException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*

data class FirebaseUser(
    val uid: String,
    val email: String?,
    val isAdmin: Boolean = false
) : Principal

fun Application.configureAuthentication() {
    install(Authentication) {
        bearer("firebase") {
            authenticate { tokenCredential ->
                try {
                    val decodedToken = Firebase.auth.verifyIdToken(tokenCredential.token)
                    val uid = decodedToken.uid
                    val email = decodedToken.email
                    
                    // Check if user is admin from custom claims
                    val claims = decodedToken.claims
                    val isAdmin = claims["admin"] as? Boolean ?: false
                    
                    FirebaseUser(uid, email, isAdmin)
                } catch (e: FirebaseAuthException) {
                    null
                }
            }
        }
    }
}
