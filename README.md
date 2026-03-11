#  Rakshak AI — Behavioral Fraud Intelligence System

An offline Android application that detects SMS-based fraud using behavioral pattern analysis and a community fraud reporting database.

---

##  Project Structure

```
com.rakshak/
├── RakshakApp.kt                    # Application class (init notification channels)
│
├── ui/
│   ├── MainActivity.kt             # Navigation Drawer host
│   ├── dashboard/
│   │   └── DashboardFragment.kt    # Live stats + SMS tester
│   ├── register/
│   │   └── RegisterFraudFragment.kt # Manual fraud report form
│   ├── search/
│   │   └── SearchFragment.kt       # Search fraud history
│   ├── complaint/
│   │   └── ComplaintFragment.kt    # Govt helpline access
│   └── adapters/
│       └── FraudReportAdapter.kt   # RecyclerView adapter
│
├── viewmodel/
│   ├── DashboardViewModel.kt       # Stats + test SMS
│   ├── RegisterFraudViewModel.kt   # Submit fraud reports
│   └── SearchViewModel.kt         # Search logic
│
├── repository/
│   └── FraudRepository.kt         # Single data source
│
├── database/
│   ├── RakshakDatabase.kt         # Room DB setup
│   ├── entities/
│   │   ├── FraudReport.kt         # Fraud record entity
│   │   └── ScanStats.kt           # Scan statistics entity
│   └── dao/
│       ├── FraudReportDao.kt      # Fraud report queries
│       └── ScanStatsDao.kt        # Stats queries
│
├── receiver/
│   └── SmsReceiver.kt             # SMS BroadcastReceiver
│
├── analyzer/
│   └── BehavioralFraudAnalyzer.kt # Core fraud detection engine
│
├── notifications/
│   └── RakshakNotificationManager.kt # Alert notifications
│
└── utils/
    └── DateUtils.kt               # Date formatting
```

---

##  Fraud Detection Logic

```
Incoming SMS
    ↓
SmsReceiver (BroadcastReceiver)
    ↓
BehavioralFraudAnalyzer.analyze(message, sender)
    ↓
Pattern Matching:
  • Fear indicators       (+20)  → "account blocked", "suspended"
  • Urgency indicators    (+20)  → "urgent", "act now", "immediately"
  • Authority spoof       (+15)  → "RBI", "SBI", "income tax"
  • OTP scam patterns     (+15)  → "OTP", "verification code"
  • Suspicious URL        (+25)  → bit.ly, tinyurl, .xyz domains
  • Unknown sender        (+5)
    ↓
Risk Score (0–100):
  0–30   =  SAFE
  31–70  =  SUSPICIOUS
  71–100 =  HIGH RISK
    ↓
Update ScanStats in DB (always)
    ↓
Show Notification (if Suspicious or High Risk)
```

---

##  Database Schema

### fraud_reports
| Column           | Type    | Description                         |
|------------------|---------|-------------------------------------|
| id               | Long PK | Auto-increment                      |
| sourceIdentifier | String  | Phone number or email               |
| messagePattern   | String  | Fraud message content (user-entered)|
| category         | String  | Phishing / OTP Scam / etc.          |
| reportCount      | Int     | Times this source was reported      |
| timestamp        | Long    | Unix timestamp                      |
| riskScore        | Int     | Computed 0–100 score                |

### scan_stats (single row, id=1)
| Column         | Type | Description               |
|----------------|------|---------------------------|
| id             | Int  | Always 1                  |
| totalScanned   | Int  | Total SMS analyzed        |
| highRiskCount  | Int  | High-risk count           |
| suspiciousCount| Int  | Suspicious count          |
| safeCount      | Int  | Safe count                |

---

##  Setup Instructions

### 1. Open in Android Studio
- Open Android Studio → File → Open → select `RakshakAI/` folder

### 2. Sync Gradle
- Click **Sync Now** when prompted
- Min SDK: 24 (Android 7.0+), Target SDK: 34

### 3. Build & Run
- Connect device or start emulator
- Click **Run ▶**

### 4. Grant Permissions
When the app launches, grant:
- **Receive SMS** — required for real-time monitoring
- **Read SMS** — required for SMS access
- **Notifications** — required for fraud alerts (Android 13+)

---

##  Test Messages

Paste these in the Dashboard → "Test a Message" box:

**High Risk (score ~75+):**
```
URGENT: Your SBI account has been BLOCKED! Click immediately: bit.ly/unblock-now 
Share your OTP to verify. Do NOT delay.
```

**Suspicious (score ~40-60):**
```
Dear customer, your KYC update is pending. 
Please visit our bank branch immediately to avoid account suspension.
```

**Safe (score 0-30):**
```
Your OTP is 482910. Valid for 10 minutes. 
Do not share with anyone. - HDFC Bank
```
*(Note: Legitimate OTP from bank → low score since no fear/urgency/URL combo)*

---

##  Privacy Guarantees

- ✅ **No SMS stored automatically** — messages are analyzed in memory only
- ✅ **No external servers** — all analysis is 100% on-device
- ✅ **User consent required** — messages only stored when user manually reports
- ✅ **No personal data collection** — only source identifiers in user-submitted reports

---

##  Features

| Feature               | Status |
|-----------------------|--------|
| SMS BroadcastReceiver | ✅     |
| Behavioral Analyzer   | ✅     |
| Risk Score Engine     | ✅     |
| Dashboard with Stats  | ✅     |
| Manual Fraud Reporter | ✅     |
| Search Fraud History  | ✅     |
| Govt Helpline Access  | ✅     |
| Push Notifications    | ✅     |
| Room Database         | ✅     |
| MVVM Architecture     | ✅     |
| Material Design 3     | ✅     |
| Fully Offline         | ✅     |

---

##  Helplines (built into app)

- **1930** — National Cyber Crime Helpline (24×7)  
- **cybercrime.gov.in** — National Cybercrime Portal  
- **ncrp.in** — National Cyber Crime Reporting Portal  
