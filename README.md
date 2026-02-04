# MyChatApp Server

Kotlin/Ktor backend server for MyChatApp with Firebase integration.

## Features

- 🔐 Firebase Authentication
- 💬 OpenRouter API proxy for AI chat
- 👥 User management with tiers (Free/Pro/Enterprise)
- 📊 Usage tracking and rate limiting
- 🛡️ Admin API for dashboard
- 🐳 Docker support for Google Cloud Run

## Prerequisites

- JDK 21+
- Firebase project with Firestore enabled
- OpenRouter API key

## Setup

1. **Create Firebase Project**
   - Go to [Firebase Console](https://console.firebase.google.com/)
   - Create a new project
   - Enable Firestore Database
   - Enable Authentication (Email/Password)
   - Download service account JSON

2. **Configure Environment**
   ```bash
   cp .env.example .env
   ```
   Edit `.env` with your values:
   - `OPENROUTER_API_KEY`: Your OpenRouter API key
   - `FIREBASE_PROJECT_ID`: Your Firebase project ID
   - Place `firebase-service-account.json` in project root

3. **Run Locally**
   ```bash
   ./gradlew run
   ```
   Server starts at http://localhost:8080

## API Endpoints

### Public
- `GET /health` - Health check
- `POST /api/auth/register` - Register user
- `POST /api/auth/verify` - Verify token

### Protected (requires Firebase token)
- `POST /api/chat` - Send message to AI
- `GET /api/chat/models` - List available models
- `GET /api/chat/conversations` - Get user's conversations
- `GET /api/user/profile` - Get user profile
- `GET /api/user/usage` - Get usage stats

### Admin (requires admin role)
- `GET /api/admin/stats` - Dashboard statistics
- `GET /api/admin/users` - List all users
- `POST /api/admin/users/set-tier` - Update user tier
- `POST /api/admin/users/ban` - Ban/unban user
- `GET /api/admin/analytics` - Usage analytics

## Deploy to Google Cloud Run

1. **Build Docker Image**
   ```bash
   docker build -t gcr.io/YOUR_PROJECT/mychatapp-server .
   ```

2. **Push to Container Registry**
   ```bash
   docker push gcr.io/YOUR_PROJECT/mychatapp-server
   ```

3. **Deploy to Cloud Run**
   ```bash
   gcloud run deploy mychatapp-server \
     --image gcr.io/YOUR_PROJECT/mychatapp-server \
     --platform managed \
     --region us-central1 \
     --allow-unauthenticated \
     --set-env-vars "OPENROUTER_API_KEY=xxx,FIREBASE_PROJECT_ID=xxx"
   ```

## Make User Admin

Use Firebase Admin SDK to set custom claims:
```kotlin
Firebase.auth.setCustomUserClaims(userId, mapOf("admin" to true))
```

Or use the Firebase CLI:
```bash
firebase functions:shell
> admin.auth().setCustomUserClaims('USER_UID', {admin: true})
```
