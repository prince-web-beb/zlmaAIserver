package com.mychatapp.services

import com.mychatapp.models.UserProfile
import com.mychatapp.models.UserTier
import com.mychatapp.models.UserUsage
import com.mychatapp.plugins.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

object UserService {
    private val usersCollection by lazy { Firebase.firestore.collection("users") }

    suspend fun createUserProfile(uid: String, email: String, displayName: String): UserProfile = withContext(Dispatchers.IO) {
        val profile = UserProfile(
            uid = uid,
            email = email,
            displayName = displayName,
            tier = "free",
            createdAt = System.currentTimeMillis(),
            lastActiveAt = System.currentTimeMillis()
        )
        
        usersCollection.document(uid).set(mapOf(
            "uid" to profile.uid,
            "email" to profile.email,
            "displayName" to profile.displayName,
            "tier" to profile.tier,
            "messagesUsedToday" to 0,
            "totalMessages" to 0,
            "isBanned" to false,
            "createdAt" to profile.createdAt,
            "lastActiveAt" to profile.lastActiveAt,
            "lastResetDate" to LocalDate.now().toString()
        )).get()
        
        profile
    }
    
    suspend fun getUserProfile(uid: String): UserProfile? = withContext(Dispatchers.IO) {
        val doc = usersCollection.document(uid).get().get()
        if (!doc.exists()) return@withContext null
        
        UserProfile(
            uid = doc.getString("uid") ?: uid,
            email = doc.getString("email") ?: "",
            displayName = doc.getString("displayName") ?: "User",
            avatarUrl = doc.getString("avatarUrl"),
            tier = doc.getString("tier") ?: "free",
            messagesUsedToday = doc.getLong("messagesUsedToday")?.toInt() ?: 0,
            totalMessages = doc.getLong("totalMessages")?.toInt() ?: 0,
            isBanned = doc.getBoolean("isBanned") ?: false,
            banReason = doc.getString("banReason"),
            createdAt = doc.getLong("createdAt") ?: 0,
            lastActiveAt = doc.getLong("lastActiveAt") ?: 0
        )
    }
    
    suspend fun updateUserProfile(uid: String, displayName: String?, avatarUrl: String?) = withContext(Dispatchers.IO) {
        val updates = mutableMapOf<String, Any>(
            "lastActiveAt" to System.currentTimeMillis()
        )
        displayName?.let { updates["displayName"] = it }
        avatarUrl?.let { updates["avatarUrl"] = it }
        
        usersCollection.document(uid).update(updates).get()
    }
    
    suspend fun canUserChat(uid: String): Boolean = withContext(Dispatchers.IO) {
        val doc = usersCollection.document(uid).get().get()
        if (!doc.exists()) return@withContext false
        
        // Check if banned
        if (doc.getBoolean("isBanned") == true) return@withContext false
        
        // Check daily reset
        val lastResetDate = doc.getString("lastResetDate")
        val today = LocalDate.now().toString()
        
        if (lastResetDate != today) {
            // Reset daily counter
            usersCollection.document(uid).update(mapOf(
                "messagesUsedToday" to 0,
                "lastResetDate" to today
            )).get()
            return@withContext true
        }
        
        val tier = doc.getString("tier") ?: "free"
        val messagesUsed = doc.getLong("messagesUsedToday")?.toInt() ?: 0
        val limit = when (tier.uppercase()) {
            "FREE" -> UserTier.FREE.dailyLimit
            "PRO" -> UserTier.PRO.dailyLimit
            "ENTERPRISE" -> UserTier.ENTERPRISE.dailyLimit
            else -> UserTier.FREE.dailyLimit
        }
        
        messagesUsed < limit
    }
    
    suspend fun incrementMessageCount(uid: String) = withContext(Dispatchers.IO) {
        val doc = usersCollection.document(uid).get().get()
        val currentDaily = doc.getLong("messagesUsedToday")?.toInt() ?: 0
        val currentTotal = doc.getLong("totalMessages")?.toInt() ?: 0
        
        usersCollection.document(uid).update(mapOf(
            "messagesUsedToday" to currentDaily + 1,
            "totalMessages" to currentTotal + 1,
            "lastActiveAt" to System.currentTimeMillis()
        )).get()
    }
    
    suspend fun getUserUsage(uid: String): UserUsage = withContext(Dispatchers.IO) {
        val doc = usersCollection.document(uid).get().get()
        val tier = doc.getString("tier") ?: "free"
        val dailyLimit = when (tier.uppercase()) {
            "FREE" -> UserTier.FREE.dailyLimit
            "PRO" -> UserTier.PRO.dailyLimit
            "ENTERPRISE" -> UserTier.ENTERPRISE.dailyLimit
            else -> UserTier.FREE.dailyLimit
        }
        
        // Calculate reset time (midnight UTC)
        val tomorrow = LocalDate.now().plusDays(1)
        val resetTime = tomorrow.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
        
        UserUsage(
            messagesUsedToday = doc.getLong("messagesUsedToday")?.toInt() ?: 0,
            dailyLimit = dailyLimit,
            totalMessages = doc.getLong("totalMessages")?.toInt() ?: 0,
            tier = tier,
            resetTime = resetTime
        )
    }
    
    suspend fun updateUserTier(uid: String, tier: String) = withContext(Dispatchers.IO) {
        usersCollection.document(uid).update("tier", tier).get()
    }
    
    suspend fun deleteUser(uid: String) = withContext(Dispatchers.IO) {
        // Delete user data from Firestore
        usersCollection.document(uid).delete().get()
        
        // Delete conversations
        val conversations = Firebase.firestore.collection("conversations")
            .whereEqualTo("userId", uid)
            .get().get()
        
        for (doc in conversations.documents) {
            doc.reference.delete().get()
        }
        
        // Delete from Firebase Auth
        Firebase.auth.deleteUser(uid)
    }
}
