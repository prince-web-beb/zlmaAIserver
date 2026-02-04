package com.mychatapp.plugins

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*
import java.io.File
import java.io.FileInputStream

fun Application.configureFirebase() {
    val dotenv = dotenv { ignoreIfMissing = true }
    
    try {
        if (FirebaseApp.getApps().isEmpty()) {
            val projectId = dotenv["FIREBASE_PROJECT_ID"]
                ?: System.getenv("FIREBASE_PROJECT_ID")
                ?: "vibenation-b1aa3"
            
            val credentialsPath = dotenv["GOOGLE_APPLICATION_CREDENTIALS"] 
                ?: System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
            
            val credentials = if (credentialsPath != null && File(credentialsPath).exists()) {
                // Use service account file if available (local development)
                log.info("Using Firebase credentials from file: $credentialsPath")
                GoogleCredentials.fromStream(FileInputStream(credentialsPath))
            } else {
                // Use Application Default Credentials (Cloud Run)
                log.info("Using Application Default Credentials")
                GoogleCredentials.getApplicationDefault()
            }
            
            val options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build()
            
            FirebaseApp.initializeApp(options)
            log.info("Firebase initialized successfully with project: $projectId")
        }
    } catch (e: Exception) {
        log.error("Failed to initialize Firebase: ${e.message}", e)
        // Continue without Firebase - health endpoints will still work
    }
}

object Firebase {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore by lazy { FirestoreClient.getFirestore() }
}
