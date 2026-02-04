package com.mychatapp.services

import com.mychatapp.plugins.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class UserSubscriptionStatus(
    val tier: String,
    val isSubscribed: Boolean,
    val canChat: Boolean,
    val canUploadImages: Boolean,
    val canUploadFiles: Boolean,
    val messagesPerDay: Int,
    val messagesUsedToday: Int,
    val subscriptionEndDate: Long? = null,
    val planName: String? = null
)

object SubscriptionService {

    private val usersCollection by lazy { Firebase.firestore.collection("users") }
    private val subscriptionsCollection by lazy { Firebase.firestore.collection("subscriptions") }
    private val plansCollection by lazy { Firebase.firestore.collection("subscription_plans") }

    /**
     * Get user's current subscription status and permissions
     */
    suspend fun getUserSubscriptionStatus(userId: String): UserSubscriptionStatus = withContext(Dispatchers.IO) {
        val userDoc = usersCollection.document(userId).get().get()

        if (!userDoc.exists()) {
            // Create default user if not exists
            return@withContext UserSubscriptionStatus(
                tier = "free",
                isSubscribed = false,
                canChat = true,
                canUploadImages = false,
                canUploadFiles = false,
                messagesPerDay = getFreeTierLimit(),
                messagesUsedToday = 0
            )
        }

        val tier = userDoc.getString("tier") ?: "free"
        val messagesUsedToday = userDoc.getLong("messagesUsedToday")?.toInt() ?: 0
        val isBanned = userDoc.getBoolean("isBanned") ?: false

        // Check for active subscription
        val activeSubscription = getActiveSubscription(userId)

        if (activeSubscription != null) {
            val plan = getPlanById(activeSubscription.planId)
            return@withContext UserSubscriptionStatus(
                tier = activeSubscription.tier,
                isSubscribed = true,
                canChat = !isBanned && messagesUsedToday < (plan?.messagesPerDay ?: 100),
                canUploadImages = plan?.canUploadImages ?: false,
                canUploadFiles = plan?.canUploadFiles ?: false,
                messagesPerDay = plan?.messagesPerDay ?: 100,
                messagesUsedToday = messagesUsedToday,
                subscriptionEndDate = activeSubscription.endDate,
                planName = activeSubscription.planName
            )
        }

        // Free tier
        val freeLimit = getFreeTierLimit()
        UserSubscriptionStatus(
            tier = "free",
            isSubscribed = false,
            canChat = !isBanned && messagesUsedToday < freeLimit,
            canUploadImages = false,
            canUploadFiles = false,
            messagesPerDay = freeLimit,
            messagesUsedToday = messagesUsedToday
        )
    }

    /**
     * Check if user can upload files/images
     */
    suspend fun canUserUpload(userId: String): Boolean = withContext(Dispatchers.IO) {
        val status = getUserSubscriptionStatus(userId)
        status.canUploadImages || status.canUploadFiles
    }

    /**
     * Check if user can upload images specifically
     */
    suspend fun canUserUploadImages(userId: String): Boolean = withContext(Dispatchers.IO) {
        val status = getUserSubscriptionStatus(userId)
        status.canUploadImages
    }

    /**
     * Check if user can upload files specifically
     */
    suspend fun canUserUploadFiles(userId: String): Boolean = withContext(Dispatchers.IO) {
        val status = getUserSubscriptionStatus(userId)
        status.canUploadFiles
    }

    private suspend fun getActiveSubscription(userId: String): Subscription? = withContext(Dispatchers.IO) {
        val query = subscriptionsCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", "active")
            .get().get()

        val doc = query.documents.firstOrNull() ?: return@withContext null

        val subscription = Subscription(
            id = doc.getString("id") ?: "",
            userId = doc.getString("userId") ?: "",
            planId = doc.getString("planId") ?: "",
            planName = doc.getString("planName") ?: "",
            tier = doc.getString("tier") ?: "free",
            status = doc.getString("status") ?: "",
            paystackRef = doc.getString("paystackRef"),
            amount = doc.getLong("amount") ?: 0,
            currency = doc.getString("currency") ?: "NGN",
            startDate = doc.getLong("startDate") ?: 0,
            endDate = doc.getLong("endDate") ?: 0,
            autoRenew = doc.getBoolean("autoRenew") ?: true
        )

        // Check if expired
        if (subscription.endDate < System.currentTimeMillis()) {
            subscriptionsCollection.document(subscription.id).update("status", "expired").get()
            usersCollection.document(userId).update(mapOf(
                "tier" to "free",
                "canUploadImages" to false,
                "canUploadFiles" to false
            )).get()
            return@withContext null
        }

        subscription
    }

    private suspend fun getPlanById(planId: String): SubscriptionPlan? = withContext(Dispatchers.IO) {
        val doc = plansCollection.document(planId).get().get()
        if (!doc.exists()) return@withContext null

        SubscriptionPlan(
            id = doc.getString("id") ?: planId,
            name = doc.getString("name") ?: "",
            tier = doc.getString("tier") ?: "free",
            price = doc.getLong("price") ?: 0,
            currency = doc.getString("currency") ?: "NGN",
            interval = doc.getString("interval") ?: "monthly",
            features = (doc.get("features") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            messagesPerDay = doc.getLong("messagesPerDay")?.toInt() ?: 20,
            canUploadImages = doc.getBoolean("canUploadImages") ?: false,
            canUploadFiles = doc.getBoolean("canUploadFiles") ?: false,
            isActive = doc.getBoolean("isActive") ?: true
        )
    }

    private suspend fun getFreeTierLimit(): Int = withContext(Dispatchers.IO) {
        val settingsDoc = Firebase.firestore.collection("settings").document("system").get().get()
        settingsDoc.getLong("freeTierDailyLimit")?.toInt() ?: 10 // Default 10 messages/day for free users
    }

    /**
     * Get all active subscription plans for display to users
     */
    suspend fun getAvailablePlans(): List<SubscriptionPlan> = withContext(Dispatchers.IO) {
        val query = plansCollection.whereEqualTo("isActive", true).get().get()

        query.documents.map { doc ->
            SubscriptionPlan(
                id = doc.getString("id") ?: doc.id,
                name = doc.getString("name") ?: "",
                tier = doc.getString("tier") ?: "free",
                price = doc.getLong("price") ?: 0,
                currency = doc.getString("currency") ?: "NGN",
                interval = doc.getString("interval") ?: "monthly",
                features = (doc.get("features") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                messagesPerDay = doc.getLong("messagesPerDay")?.toInt() ?: 20,
                canUploadImages = doc.getBoolean("canUploadImages") ?: false,
                canUploadFiles = doc.getBoolean("canUploadFiles") ?: false,
                isActive = doc.getBoolean("isActive") ?: true
            )
        }.sortedBy { it.price }
    }
}
