package com.mychatapp.models

import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: String,
    val userId: String,
    val title: String,
    val messages: List<Message> = emptyList(),
    val model: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class Message(
    val id: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokenCount: Int? = null
)

@Serializable
data class ConversationSummary(
    val id: String,
    val title: String,
    val lastMessage: String?,
    val messageCount: Int,
    val model: String,
    val updatedAt: Long
)
