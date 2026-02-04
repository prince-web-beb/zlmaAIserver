package com.mychatapp

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import java.io.FileInputStream

fun main() {
    // Initialize Firebase
    val serviceAccount = FileInputStream("firebase-service-account.json")
    val options = FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
        .build()
    
    if (FirebaseApp.getApps().isEmpty()) {
        FirebaseApp.initializeApp(options)
    }
    
    val uid = "YIF2ZXYsZLhmEkqJQI1vDHTbMMc2"
    
    // Set admin claim
    val claims = mapOf("admin" to true)
    FirebaseAuth.getInstance().setCustomUserClaims(uid, claims)
    
    println("✅ Successfully set admin claim for user: $uid")
    
    // Verify
    val user = FirebaseAuth.getInstance().getUser(uid)
    println("User email: ${user.email}")
    println("Custom claims: ${user.customClaims}")
}
