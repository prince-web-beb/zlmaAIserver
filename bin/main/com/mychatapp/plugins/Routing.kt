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
        
        // Admin routes (require admin role)
        adminRoutes()
    }
}
