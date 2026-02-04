package com.mychatapp.services

import com.google.firebase.auth.UserRecord
import com.mychatapp.models.*
import com.mychatapp.plugins.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object AdminService {
    private val usersCollection = Firebase.firestore.collection("users")
    private val conversationsCollection = Firebase.firestore.collection("conversations")
    private val usageLogsCollection = Firebase.firestore.collection("usage_logs")
    private val settingsCollection = Firebase.firestore.collection("settings")
    
    suspend fun getDashboardStats(): DashboardStats = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val todayStart = LocalDate.now().atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC) * 1000
        val weekAgo = now - (7 * 24 * 60 * 60 * 1000)
        
        // Get all users
        val allUsers = usersCollection.get().get()
        val totalUsers = allUsers.size()
        
        var freeCount = 0
        var proCount = 0
        var enterpriseCount = 0
        var activeToday = 0
        var newToday = 0
        var newThisWeek = 0
        
        for (doc in allUsers.documents) {
            val tier = doc.getString("tier") ?: "free"
            when (tier.lowercase()) {
                "free" -> freeCount++
                "pro" -> proCount++
                "enterprise" -> enterpriseCount++
            }
            
            val lastActive = doc.getLong("lastActiveAt") ?: 0
            if (lastActive >= todayStart) activeToday++
            
            val createdAt = doc.getLong("createdAt") ?: 0
            if (createdAt >= todayStart) newToday++
            if (createdAt >= weekAgo) newThisWeek++
        }
        
        // Get message counts
        val allLogs = usageLogsCollection.get().get()
        val totalMessages = allLogs.size().toLong()
        
        val messagesToday = usageLogsCollection
            .whereGreaterThanOrEqualTo("timestamp", todayStart)
            .get().get()
            .size().toLong()
        
        DashboardStats(
            totalUsers = totalUsers,
            activeUsersToday = activeToday,
            totalMessages = totalMessages,
            messagesToday = messagesToday,
            newUsersToday = newToday,
            newUsersThisWeek = newThisWeek,
            tierBreakdown = TierBreakdown(
                free = freeCount,
                pro = proCount,
                enterprise = enterpriseCount
            )
        )
    }
    
    suspend fun getAllUsers(page: Int, limit: Int): Map<String, Any> = withContext(Dispatchers.IO) {
        val offset = (page - 1) * limit
        
        val query = usersCollection
            .orderBy("createdAt", com.google.cloud.firestore.Query.Direction.DESCENDING)
            .offset(offset)
            .limit(limit)
            .get().get()
        
        val users = query.documents.map { doc ->
            mapOf(
                "uid" to (doc.getString("uid") ?: doc.id),
                "email" to doc.getString("email"),
                "displayName" to doc.getString("displayName"),
                "tier" to doc.getString("tier"),
                "totalMessages" to doc.getLong("totalMessages"),
                "isBanned" to doc.getBoolean("isBanned"),
                "createdAt" to doc.getLong("createdAt"),
                "lastActiveAt" to doc.getLong("lastActiveAt")
            )
        }
        
        val total = usersCollection.get().get().size()
        
        mapOf(
            "users" to users,
            "page" to page,
            "limit" to limit,
            "total" to total,
            "totalPages" to ((total + limit - 1) / limit)
        )
    }
    
    suspend fun getUserDetails(userId: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        val userDoc = usersCollection.document(userId).get().get()
        
        // Get user's conversations count
        val conversationsCount = conversationsCollection
            .whereEqualTo("userId", userId)
            .get().get()
            .size()
        
        // Get recent usage
        val recentUsage = usageLogsCollection
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", com.google.cloud.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .get().get()
        
        val totalTokens = recentUsage.documents.sumOf { 
            it.getLong("totalTokens")?.toInt() ?: 0 
        }
        
        mapOf(
            "uid" to userId,
            "email" to userDoc.getString("email"),
            "displayName" to userDoc.getString("displayName"),
            "avatarUrl" to userDoc.getString("avatarUrl"),
            "tier" to userDoc.getString("tier"),
            "messagesUsedToday" to userDoc.getLong("messagesUsedToday"),
            "totalMessages" to userDoc.getLong("totalMessages"),
            "isBanned" to userDoc.getBoolean("isBanned"),
            "banReason" to userDoc.getString("banReason"),
            "createdAt" to userDoc.getLong("createdAt"),
            "lastActiveAt" to userDoc.getLong("lastActiveAt"),
            "conversationsCount" to conversationsCount,
            "recentTokenUsage" to totalTokens
        )
    }
    
    suspend fun setAdminStatus(userId: String, isAdmin: Boolean) = withContext(Dispatchers.IO) {
        // Set custom claim in Firebase Auth
        val claims = mapOf("admin" to isAdmin)
        Firebase.auth.setCustomUserClaims(userId, claims)
    }
    
    suspend fun setBanStatus(userId: String, banned: Boolean, reason: String?) = withContext(Dispatchers.IO) {
        val updates = mutableMapOf<String, Any?>(
            "isBanned" to banned
        )
        if (banned && reason != null) {
            updates["banReason"] = reason
        } else if (!banned) {
            updates["banReason"] = null
        }
        
        usersCollection.document(userId).update(updates).get()
        
        // Also disable in Firebase Auth if banned
        val userRecord = Firebase.auth.getUser(userId)
        val updateRequest = UserRecord.UpdateRequest(userId).setDisabled(banned)
        Firebase.auth.updateUser(updateRequest)
    }
    
    suspend fun getAnalytics(period: String): AnalyticsData = withContext(Dispatchers.IO) {
        val days = when (period) {
            "7d" -> 7
            "30d" -> 30
            "90d" -> 90
            else -> 7
        }
        
        val now = System.currentTimeMillis()
        val startTime = now - (days * 24 * 60 * 60 * 1000L)
        
        val logs = usageLogsCollection
            .whereGreaterThanOrEqualTo("timestamp", startTime)
            .get().get()
        
        // Group by date
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val messagesByDate = mutableMapOf<String, Long>()
        val usersByDate = mutableMapOf<String, MutableSet<String>>()
        val modelCounts = mutableMapOf<String, Long>()
        val hourCounts = mutableMapOf<Int, Long>()
        
        for (doc in logs.documents) {
            val timestamp = doc.getLong("timestamp") ?: continue
            val userId = doc.getString("userId") ?: continue
            val model = doc.getString("model") ?: "unknown"
            
            val date = java.time.Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .format(formatter)
            
            val hour = java.time.Instant.ofEpochMilli(timestamp)
                .atZone(java.time.ZoneOffset.UTC)
                .hour
            
            messagesByDate[date] = (messagesByDate[date] ?: 0) + 1
            usersByDate.getOrPut(date) { mutableSetOf() }.add(userId)
            modelCounts[model] = (modelCounts[model] ?: 0) + 1
            hourCounts[hour] = (hourCounts[hour] ?: 0) + 1
        }
        
        val totalMessages = logs.size().toLong().coerceAtLeast(1)
        
        AnalyticsData(
            period = period,
            dailyMessages = messagesByDate.map { DailyCount(it.key, it.value) }
                .sortedBy { it.date },
            dailyUsers = usersByDate.map { DailyCount(it.key, it.value.size.toLong()) }
                .sortedBy { it.date },
            topModels = modelCounts.map { 
                ModelUsage(it.key, it.value, (it.value * 100f / totalMessages))
            }.sortedByDescending { it.count }.take(5),
            peakHours = hourCounts.map { HourlyCount(it.key, it.value) }
                .sortedBy { it.hour }
        )
    }
    
    suspend fun getApiLogs(page: Int, limit: Int): Map<String, Any> = withContext(Dispatchers.IO) {
        val offset = (page - 1) * limit
        
        val logsQuery = usageLogsCollection
            .orderBy("timestamp", com.google.cloud.firestore.Query.Direction.DESCENDING)
            .offset(offset)
            .limit(limit)
            .get().get()
        
        val logs = logsQuery.documents.map { doc ->
            mapOf(
                "id" to doc.id,
                "userId" to doc.getString("userId"),
                "model" to doc.getString("model"),
                "totalTokens" to doc.getLong("totalTokens"),
                "timestamp" to doc.getLong("timestamp")
            )
        }
        
        mapOf(
            "logs" to logs,
            "page" to page,
            "limit" to limit
        )
    }
    
    suspend fun getSystemSettings(): SystemSettings = withContext(Dispatchers.IO) {
        val doc = settingsCollection.document("system").get().get()
        
        if (!doc.exists()) {
            return@withContext SystemSettings()
        }
        
        SystemSettings(
            maintenanceMode = doc.getBoolean("maintenanceMode") ?: false,
            registrationEnabled = doc.getBoolean("registrationEnabled") ?: true,
            defaultTier = doc.getString("defaultTier") ?: "free",
            enabledModels = (doc.get("enabledModels") as? List<*>)?.mapNotNull { it as? String }
                ?: SystemSettings().enabledModels,
            rateLimits = RateLimits(
                freePerMinute = doc.getLong("rateLimitFree")?.toInt() ?: 10,
                proPerMinute = doc.getLong("rateLimitPro")?.toInt() ?: 30,
                enterprisePerMinute = doc.getLong("rateLimitEnterprise")?.toInt() ?: 100
            )
        )
    }
    
    suspend fun updateSystemSettings(settings: Map<String, String>) = withContext(Dispatchers.IO) {
        settingsCollection.document("system").set(settings, com.google.cloud.firestore.SetOptions.merge()).get()
    }
}
