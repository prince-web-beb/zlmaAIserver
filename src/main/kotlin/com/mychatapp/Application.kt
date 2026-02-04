package com.mychatapp

import com.mychatapp.plugins.*
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    val dotenv = dotenv {
        ignoreIfMissing = true
    }
    
    val port = dotenv["PORT"]?.toIntOrNull() ?: 8080
    
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    configureFirebase()
    configureSerialization()
    configureCORS()
    configureAuthentication()
    configureRateLimiting()
    configureStatusPages()
    configureRouting()
}
