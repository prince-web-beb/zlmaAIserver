package com.mychatapp.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val timestamp: Long
)

fun Route.healthRoutes() {
    get("/health") {
        call.respond(
            HttpStatusCode.OK,
            HealthResponse(
                status = "healthy",
                version = "1.0.0",
                timestamp = System.currentTimeMillis()
            )
        )
    }
    
    get("/") {
        call.respond(
            HttpStatusCode.OK,
            mapOf(
                "name" to "MyChatApp API",
                "version" to "1.0.0",
                "docs" to "/docs"
            )
        )
    }
}
