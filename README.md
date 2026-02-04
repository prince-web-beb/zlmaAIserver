# Zlma AI Server

Kotlin/Ktor backend server for Zlma AI with Firebase integration and Paystack payments.

## Features

- 🔐 Firebase Authentication
- 💬 OpenRouter API proxy for AI chat (users don't need their own API key)
- 👥 User management with tiers (Free/Pro/Enterprise)
- 💳 Paystack payment integration for subscriptions
- 📊 Usage tracking and rate limiting
- 🛡️ Admin API for dashboard
- 📱 Mobile API for Android/iOS apps
- 🐳 Docker support for Google Cloud Run

## How It Works (Like ChatGPT)

1. **Free Users**: Can chat with AI, but cannot upload images or files
2. **Subscribed Users**: Unlock image uploads, file uploads, and more messages per day
3. **Server handles AI**: Users don't need their own OpenRouter API key

## Prerequisites

- JDK 21+
- Firebase project with Firestore enabled
- OpenRouter API key
- Paystack account (for payments)

## Setup

1. **Create Firebase Project**
   - Go to [Firebase Console](https://console.firebase.google.com/)
   - Create a new project
   - Enable Firestore Database
   - Enable Authentication (Email/Password + Google)
   - Download service account JSON

2. **Configure Paystack**
   - Sign up at [Paystack](https://paystack.com/)
   - Get your API keys from Dashboard > Settings > Developers
   - Add webhook URL: `https://your-server.com/api/webhooks/paystack`

3. **Configure Environment**
   ```bash
   cp .env.example .env
   ```
   Edit `.env` with your values:
   - `OPENROUTER_API_KEY`: Your OpenRouter API key
   - `FIREBASE_PROJECT_ID`: Your Firebase project ID
   - `PAYSTACK_SECRET_KEY`: Your Paystack secret key
   - `PAYSTACK_PUBLIC_KEY`: Your Paystack public key
   - Place `firebase-service-account.json` in project root

4. **Run Locally**
   ```bash
   ./gradlew run
   ```
   Server starts at http://localhost:8080

## API Endpoints

### Public
- `GET /health` - Health check
- `GET /api/mobile/plans` - Get subscription plans
- `GET /api/mobile/paystack-key` - Get Paystack public key

### Mobile API (requires Firebase token)
- `POST /api/mobile/auth/register` - Register/get user profile
- `GET /api/mobile/profile` - Get user profile
- `GET /api/mobile/subscription` - Get subscription status
- `POST /api/mobile/chat` - Send message to AI
- `GET /api/mobile/conversations` - Get conversations
- `POST /api/mobile/subscribe` - Initialize payment
- `POST /api/mobile/verify-payment` - Verify payment

### Admin (requires admin role)
- `GET /api/admin/stats` - Dashboard statistics
- `GET /api/admin/users` - List all users
- `POST /api/admin/users/set-tier` - Update user tier
- `POST /api/admin/users/ban` - Ban/unban user
- `GET /api/admin/analytics` - Usage analytics
- `GET /api/admin/revenue` - Revenue statistics
- `GET /api/admin/subscriptions/all` - All subscriptions
- `POST /api/admin/subscriptions/plans` - Create/update plan

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
