package com.mychatapp.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCORS() {
    install(CORS) {
        // Allow admin panel from Netlify
        allowHost("localhost:3000") // Local dev
        allowHost("localhost:5173") // Vite dev
        allowHost("*.netlify.app", schemes = listOf("https"))
        
        // Add your custom domain when ready
        // allowHost("admin.yourdomain.com", schemes = listOf("https"))
        
        // Allow mobile app
        anyHost() // For Android app - you can restrict this later
        
        // Allowed methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        
        // Allowed headers
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        
        // Allow credentials
        allowCredentials = true
        
        // Max age for preflight cache
        maxAgeInSeconds = 3600
    }
}
