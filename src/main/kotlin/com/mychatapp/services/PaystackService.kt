package com.mychatapp.services

import com.mychatapp.plugins.Firebase
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.*

@Serializable
data class PaystackInitResponse(
    val status: Boolean,
    val message: String,
    val data: PaystackInitData? = null
)

@Serializable
data class PaystackInitData(
    @SerialName("authorization_url") val authorizationUrl: String,
    @SerialName("access_code") val accessCode: String,
    val reference: String
)

@Serializable
data class PaystackVerifyResponse(
    val status: Boolean,
    val message: String,
    val data: PaystackVerifyData? = null
)

@Serializable
data class PaystackVerifyData(
    val status: String,
    val reference: String,
    val amount: Long,
    val currency: String,
    val customer: PaystackCustomer,
    @SerialName("paid_at") val paidAt: String? = null
)

@Serializable
data class PaystackCustomer(
    val email: String,
    @SerialName("customer_code") val customerCode: String? = null
)

@Serializable
data class SubscriptionPlan(
    val id: String,
    val name: String,
    val tier: String,
    val price: Long, // in kobo/cents
    val currency: String = "NGN",
    val interval: String = "monthly", // monthly, yearly
    val features: List<String>,
    val messagesPerDay: Int,
    val canUploadImages: Boolean = false,
    val canUploadFiles: Boolean = false,
    val isActive: Boolean = true
)

@Serializable
data class Subscription(
    val id: String,
    val userId: String,
    val planId: String,
    val planName: String,
    val tier: String,
    val status: String, // active, cancelled, expired
    val paystackRef: String? = null,
    val amount: Long,
    val currency: String,
    val startDate: Long,
    val endDate: Long,
    val autoRenew: Boolean = true
)

object PaystackService {
    private val dotenv = dotenv { ignoreIfMissing = true }
    private val secretKey = dotenv["PAYSTACK_SECRET_KEY"] ?: System.getenv("PAYSTACK_SECRET_KEY") ?: ""
    private val publicKey = dotenv["PAYSTACK_PUBLIC_KEY"] ?: System.getenv("PAYSTACK_PUBLIC_KEY") ?: ""

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val subscriptionsCollection by lazy { Firebase.firestore.collection("subscriptions") }
    private val plansCollection by lazy { Firebase.firestore.collection("subscription_plans") }
    private val transactionsCollection by lazy { Firebase.firestore.collection("transactions") }

    /**
     * Initialize a payment transaction
     */
    suspend fun initializePayment(
        userId: String,
        email: String,
        planId: String,
        callbackUrl: String
    ): PaystackInitResponse = withContext(Dispatchers.IO) {
        val plan = getPlan(planId) ?: throw Exception("Plan not found")

        val reference = "zlma_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        val response = client.post("https://api.paystack.co/transaction/initialize") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $secretKey")
            setBody(mapOf(
                "email" to email,
                "amount" to plan.price.toString(),
                "currency" to plan.currency,
                "reference" to reference,
                "callback_url" to callbackUrl,
                "metadata" to mapOf(
                    "user_id" to userId,
                    "plan_id" to planId,
                    "plan_name" to plan.name
                )
            ))
        }

        val result = response.body<PaystackInitResponse>()

        // Save pending transaction
        if (result.status && result.data != null) {
            transactionsCollection.document(reference).set(mapOf(
                "reference" to reference,
                "userId" to userId,
                "planId" to planId,
                "amount" to plan.price,
                "currency" to plan.currency,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )).get()
        }

        result
    }

    /**
     * Verify a payment transaction
     */
    suspend fun verifyPayment(reference: String): PaystackVerifyResponse = withContext(Dispatchers.IO) {
        val response = client.get("https://api.paystack.co/transaction/verify/$reference") {
            header("Authorization", "Bearer $secretKey")
        }

        response.body<PaystackVerifyResponse>()
    }

    /**
     * Process successful payment - create subscription
     */
    suspend fun processSuccessfulPayment(reference: String): Subscription = withContext(Dispatchers.IO) {
        val verification = verifyPayment(reference)

        if (!verification.status || verification.data?.status != "success") {
            throw Exception("Payment verification failed: ${verification.message}")
        }

        // Get transaction details
        val transactionDoc = transactionsCollection.document(reference).get().get()
        if (!transactionDoc.exists()) {
            throw Exception("Transaction not found")
        }

        val userId = transactionDoc.getString("userId") ?: throw Exception("User ID not found")
        val planId = transactionDoc.getString("planId") ?: throw Exception("Plan ID not found")
        val plan = getPlan(planId) ?: throw Exception("Plan not found")

        // Update transaction status
        transactionsCollection.document(reference).update(mapOf(
            "status" to "success",
            "verifiedAt" to System.currentTimeMillis()
        )).get()

        // Create or update subscription
        val now = System.currentTimeMillis()
        val duration = when (plan.interval) {
            "yearly" -> 365L * 24 * 60 * 60 * 1000
            else -> 30L * 24 * 60 * 60 * 1000 // monthly
        }

        val subscriptionId = UUID.randomUUID().toString()
        val subscription = Subscription(
            id = subscriptionId,
            userId = userId,
            planId = planId,
            planName = plan.name,
            tier = plan.tier,
            status = "active",
            paystackRef = reference,
            amount = plan.price,
            currency = plan.currency,
            startDate = now,
            endDate = now + duration,
            autoRenew = true
        )

        // Save subscription
        subscriptionsCollection.document(subscriptionId).set(mapOf(
            "id" to subscription.id,
            "userId" to subscription.userId,
            "planId" to subscription.planId,
            "planName" to subscription.planName,
            "tier" to subscription.tier,
            "status" to subscription.status,
            "paystackRef" to subscription.paystackRef,
            "amount" to subscription.amount,
            "currency" to subscription.currency,
            "startDate" to subscription.startDate,
            "endDate" to subscription.endDate,
            "autoRenew" to subscription.autoRenew
        )).get()

        // Update user tier
        UserService.updateUserTier(userId, plan.tier)

        // Update user with subscription features
        Firebase.firestore.collection("users").document(userId).update(mapOf(
            "subscriptionId" to subscriptionId,
            "canUploadImages" to plan.canUploadImages,
            "canUploadFiles" to plan.canUploadFiles,
            "messagesPerDay" to plan.messagesPerDay
        )).get()

        subscription
    }

    /**
     * Get user's active subscription
     */
    suspend fun getUserSubscription(userId: String): Subscription? = withContext(Dispatchers.IO) {
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
            UserService.updateUserTier(userId, "free")
            return@withContext null
        }

        subscription
    }

    /**
     * Get subscription plan
     */
    suspend fun getPlan(planId: String): SubscriptionPlan? = withContext(Dispatchers.IO) {
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

    /**
     * Get all active subscription plans
     */
    suspend fun getActivePlans(): List<SubscriptionPlan> = withContext(Dispatchers.IO) {
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

    /**
     * Create or update a subscription plan (admin only)
     */
    suspend fun savePlan(plan: SubscriptionPlan): SubscriptionPlan = withContext(Dispatchers.IO) {
        val planId = if (plan.id.isBlank()) UUID.randomUUID().toString() else plan.id

        plansCollection.document(planId).set(mapOf(
            "id" to planId,
            "name" to plan.name,
            "tier" to plan.tier,
            "price" to plan.price,
            "currency" to plan.currency,
            "interval" to plan.interval,
            "features" to plan.features,
            "messagesPerDay" to plan.messagesPerDay,
            "canUploadImages" to plan.canUploadImages,
            "canUploadFiles" to plan.canUploadFiles,
            "isActive" to plan.isActive,
            "updatedAt" to System.currentTimeMillis()
        )).get()

        plan.copy(id = planId)
    }

    /**
     * Delete a subscription plan (admin only)
     */
    suspend fun deletePlan(planId: String) = withContext(Dispatchers.IO) {
        plansCollection.document(planId).delete().get()
    }

    /**
     * Cancel subscription
     */
    suspend fun cancelSubscription(subscriptionId: String) = withContext(Dispatchers.IO) {
        val doc = subscriptionsCollection.document(subscriptionId).get().get()
        if (!doc.exists()) throw Exception("Subscription not found")

        subscriptionsCollection.document(subscriptionId).update(mapOf(
            "status" to "cancelled",
            "autoRenew" to false,
            "cancelledAt" to System.currentTimeMillis()
        )).get()
    }

    /**
     * Get Paystack public key for frontend
     */
    fun getPublicKey(): String = publicKey
}
