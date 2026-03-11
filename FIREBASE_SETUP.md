# 🔥 Firebase Setup Guide for Rakshak AI

Follow these steps **exactly once** before building the app.

---

## Step 1 — Create Firebase Project

1. Go to https://console.firebase.google.com
2. Click **Add project**
3. Name it: `RakshakAI`
4. Disable Google Analytics (optional) → **Create project**

---

## Step 2 — Register Android App

1. In Firebase console, click the **Android icon** (</> Add app)
2. Enter package name: `com.rakshak`
3. App nickname: `Rakshak AI`
4. Click **Register app**

---

## Step 3 — Download google-services.json

1. Click **Download google-services.json**
2. **Replace** the placeholder file at:
   ```
   RakshakAI/app/google-services.json
   ```
   with the downloaded file.

---

## Step 4 — Enable Firestore

1. In Firebase console → **Build → Firestore Database**
2. Click **Create database**
3. Choose **Start in test mode** (for development)
4. Select your region → **Enable**

---

## Step 5 — Set Firestore Security Rules (for production)

In Firestore → **Rules** tab, paste:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /fraud_reports/{doc} {
      allow read: if true;           // Anyone can read fraud history
      allow write: if true;          // Anyone can report fraud
    }
  }
}
```

Click **Publish**.

---

## Step 6 — Sync and Build

1. In Android Studio: **File → Sync Project with Gradle Files**
2. Build → Run

---

## Firestore Collection Structure

Collection: `fraud_reports`

Each document:
```
sourceIdentifier  : "9876543210"         (string)
email             : ""                   (string)
messagePattern    : "Your account is..." (string)
category          : "OTP Scam"           (string)
reportCount       : 3                    (number)
timestamp         : 1709123456789        (number)
riskScore         : 75                   (number)
```

---

## How Community Scoring Works

When an SMS arrives from a sender:

| Community Reports | Extra Score |
|---|---|
| 1+ reports  | +5  |
| 5+ reports  | +10 |
| 10+ reports | +15 |
| 20+ reports | +20 |
| 50+ reports | +25 |

If a sender has 20+ reports, notification shows:
> ⚠️ Known Fraud Number — Reported by X users. Avoid interaction.
