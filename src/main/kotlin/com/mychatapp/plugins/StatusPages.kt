package com.mychatapp.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String,
    val code: Int,
    val message: String? = null
)

open class ApiException(
    val statusCode: HttpStatusCode,
    override val message: String
) : Exception(message)

class UnauthorizedException(message: String = "Unauthorized") : ApiException(HttpStatusCode.Unauthorized, message)
class ForbiddenException(message: String = "Forbidden") : ApiException(HttpStatusCode.Forbidden, message)
class NotFoundException(message: String = "Not found") : ApiException(HttpStatusCode.NotFound, message)
class BadRequestException(message: String = "Bad request") : ApiException(HttpStatusCode.BadRequest, message)
class RateLimitException(message: String = "Too many requests") : ApiException(HttpStatusCode.TooManyRequests, message)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(
                cause.statusCode,
                ErrorResponse(
                    error = cause.statusCode.description,
                    code = cause.statusCode.value,
                    message = cause.message
                )
            )
        }
        
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    error = "Internal Server Error",
                    code = 500,
                    message = if (call.application.developmentMode) cause.message else null
                )
            )
        }
        
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(
                status,
                ErrorResponse(
                    error = "Rate Limit Exceeded",
                    code = status.value,
                    message = "Please slow down and try again later"
                )
            )
        }
    }
}

val Application.developmentMode: Boolean
    get() = environment.config.propertyOrNull("ktor.deployment.environment")?.getString() == "development"
