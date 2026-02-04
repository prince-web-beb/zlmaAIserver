package com.mychatapp.plugins

import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes

fun Application.configureRateLimiting() {
    val dotenv = dotenv { ignoreIfMissing = true }
    val requestsPerMinute = dotenv["RATE_LIMIT_REQUESTS_PER_MINUTE"]?.toIntOrNull() ?: 30
    
    install(RateLimit) {
        // Default rate limit for all endpoints
        global {
            rateLimiter(limit = requestsPerMinute, refillPeriod = 1.minutes)
        }
        
        // Higher limit for authenticated users
        register(RateLimitName("authenticated")) {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
        }
        
        // Admin endpoints - more generous
        register(RateLimitName("admin")) {
            rateLimiter(limit = 200, refillPeriod = 1.minutes)
        }
    }
}
