package com.mychatapp.models

import kotlinx.serialization.Serializable

@Serializable
data class DashboardStats(
    val totalUsers: Int,
    val activeUsersToday: Int,
    val totalMessages: Long,
    val messagesToday: Long,
    val newUsersToday: Int,
    val newUsersThisWeek: Int,
    val tierBreakdown: TierBreakdown
)

@Serializable
data class TierBreakdown(
    val free: Int,
    val pro: Int,
    val enterprise: Int
)

@Serializable
data class AnalyticsData(
    val period: String,
    val dailyMessages: List<DailyCount>,
    val dailyUsers: List<DailyCount>,
    val topModels: List<ModelUsage>,
    val peakHours: List<HourlyCount>
)

@Serializable
data class DailyCount(
    val date: String,
    val count: Long
)

@Serializable
data class HourlyCount(
    val hour: Int,
    val count: Long
)

@Serializable
data class ModelUsage(
    val model: String,
    val count: Long,
    val percentage: Float
)

@Serializable
data class ApiLog(
    val id: String,
    val userId: String,
    val userEmail: String?,
    val endpoint: String,
    val method: String,
    val statusCode: Int,
    val responseTime: Long,
    val timestamp: Long
)

@Serializable
data class SystemSettings(
    val maintenanceMode: Boolean = false,
    val registrationEnabled: Boolean = true,
    val defaultTier: String = "free",
    val enabledModels: List<String> = listOf(
        "openai/gpt-4o",
        "openai/gpt-4o-mini",
        "anthropic/claude-3.5-sonnet"
    ),
    val rateLimits: RateLimits = RateLimits()
)

@Serializable
data class RateLimits(
    val freePerMinute: Int = 10,
    val proPerMinute: Int = 30,
    val enterprisePerMinute: Int = 100
)

@Serializable
data class PaginatedUsers(
    val users: List<AdminUserInfo>,
    val page: Int,
    val limit: Int,
    val total: Int,
    val totalPages: Int
)

@Serializable
data class AdminUserInfo(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val tier: String? = "free",
    val totalMessages: Long? = 0,
    val isBanned: Boolean? = false,
    val createdAt: Long? = null,
    val lastActiveAt: Long? = null
)

@Serializable
data class UserDetailsResponse(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val tier: String? = "free",
    val isAdmin: Boolean = false,
    val isBanned: Boolean = false,
    val banReason: String? = null,
    val totalMessages: Long = 0,
    val conversationsCount: Int = 0,
    val totalTokensUsed: Int = 0,
    val createdAt: Long? = null,
    val lastActiveAt: Long? = null
)
