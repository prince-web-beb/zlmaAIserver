package com.mychatapp.plugins

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*
import java.io.FileInputStream

fun Application.configureFirebase() {
    val dotenv = dotenv { ignoreIfMissing = true }
    
    if (FirebaseApp.getApps().isEmpty()) {
        val credentialsPath = dotenv["GOOGLE_APPLICATION_CREDENTIALS"] 
            ?: System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
            ?: "./firebase-service-account.json"
        
        val projectId = dotenv["FIREBASE_PROJECT_ID"]
            ?: System.getenv("FIREBASE_PROJECT_ID")
        
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(FileInputStream(credentialsPath)))
            .setProjectId(projectId)
            .build()
        
        FirebaseApp.initializeApp(options)
        log.info("Firebase initialized successfully")
    }
}

object Firebase {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore by lazy { FirestoreClient.getFirestore() }
}
