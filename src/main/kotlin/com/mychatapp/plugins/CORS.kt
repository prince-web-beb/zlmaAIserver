package com.mychatapp.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCORS() {
    install(CORS) {
        // Allow admin panel from Netlify - be explicit with the domain
        allowHost("localhost:3000", schemes = listOf("http", "https")) // Local dev
        allowHost("localhost:3001", schemes = listOf("http", "https")) // Vite dev alt port
        allowHost("localhost:5173", schemes = listOf("http", "https")) // Vite dev
        allowHost("adminzlma.netlify.app", schemes = listOf("https")) // Your Netlify domain
        allowHost("*.netlify.app", schemes = listOf("https")) // Any Netlify subdomain

        // Add your custom domain when ready
        // allowHost("admin.yourdomain.com", schemes = listOf("https"))
        
        // Allow mobile app and any origin for now
        anyHost() // For Android app - you can restrict this later
        
        // Allowed methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Patch)

        // Allowed headers
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.Origin)
        allowHeader(HttpHeaders.AccessControlRequestHeaders)
        allowHeader(HttpHeaders.AccessControlRequestMethod)
        allowHeader("X-Requested-With")

        // Expose headers to client
        exposeHeader(HttpHeaders.Authorization)

        // Allow credentials
        allowCredentials = true
        
        // Allow non-simple content types
        allowNonSimpleContentTypes = true

        // Max age for preflight cache
        maxAgeInSeconds = 3600
    }
}
