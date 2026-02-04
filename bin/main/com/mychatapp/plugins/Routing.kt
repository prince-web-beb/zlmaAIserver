package com.mychatapp.plugins

import com.mychatapp.routes.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        // Health check
        healthRoutes()
        
        // Public routes
        authRoutes()
        
        // Protected routes (require Firebase auth)
        chatRoutes()
        userRoutes()
        
        // Subscription & Payment routes
        subscriptionRoutes()

        // Mobile API routes (for Android/iOS apps)
        mobileRoutes()

        // Admin routes (require admin role)
        adminRoutes()
    }
}
