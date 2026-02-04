package com.mychatapp.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val uid: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val tier: String = "free", // free, pro, enterprise
    val messagesUsedToday: Int = 0,
    val totalMessages: Int = 0,
    val isBanned: Boolean = false,
    val banReason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
)

@Serializable
data class UserUsage(
    val messagesUsedToday: Int,
    val dailyLimit: Int,
    val totalMessages: Int,
    val tier: String,
    val resetTime: Long // When daily limit resets
)

@Serializable
enum class UserTier(val dailyLimit: Int) {
    FREE(10),        // 10 messages/day for free users
    PRO(100),        // 100 messages/day for pro users
    ENTERPRISE(1000) // 1000 messages/day for enterprise
}
