package com.rakshak.analyzer

/**
 * AnalysisResult — full result of one SMS analysis pass.
 */
data class AnalysisResult(
    val riskScore: Int,                      // 0–100 final score
    val behavioralScore: Int,                // score from pattern matching only
    val communityScore: Int,                 // score from Firestore report count
    val communityReportCount: Int,           // raw count from Firestore
    val sequenceBonus: Int,                  // bonus from behavioral sequence detection
    val sequencePatternName: String?,        // e.g. "Call → OTP Scam"
    val riskLevel: RiskLevel,
    val detectedPatterns: List<String>,
    val hasUrl: Boolean,
    val senderTrust: SenderTrust,
    val summary: String
)

enum class RiskLevel(val label: String) {
    SAFE("Safe"),
    SUSPICIOUS("Suspicious"),
    HIGH_RISK("High Risk")
}

enum class SenderTrust {
    UNKNOWN, NUMERIC_SHORT, ALPHANUMERIC
}

/**
 * BehavioralFraudAnalyzer
 *
 * 10-rule fraud intelligence engine. Detects:
 *
 *  Rule 1  — Fear / threat language             +20
 *  Rule 2  — Urgency / time pressure            +20
 *  Rule 3  — Authority impersonation            +15
 *  Rule 4  — OTP / credential harvesting        +15
 *  Rule 5  — Suspicious shortened URL           +25
 *  Rule 6  — Regular URL present                +15
 *  Rule 7  — KYC / document scam                +20  ← NEW
 *  Rule 8  — Prize / lottery / reward scam      +20  ← NEW
 *  Rule 9  — Job / investment scam              +15  ← NEW
 *  Rule 10 — Impersonation of delivery/courier  +15  ← NEW
 *  Rule 11 — Foreign script detection           +20  ← NEW
 *  Sender trust penalty                          +5
 *  Community reports (Firestore)           +5 to +25
 *  Sequence bonus (cross-signal)          +15 to +30  ← NEW
 *
 * All existing rules and scoring are PRESERVED unchanged.
 * New rules only add on top.
 */
object BehavioralFraudAnalyzer {

    // ══════════════════════════════════════════════════════════════
    // Rule 1 — Fear / Threat  (+20)
    // ══════════════════════════════════════════════════════════════
    private val FEAR_PATTERNS = listOf(
        "account blocked", "account suspended", "account restricted",
        "account deactivated", "service blocked", "access denied",
        "blocked immediately", "suspended account", "your account will be",
        "legal action", "case filed", "arrest warrant", "fir registered",
        "fraud detected on your account", "unauthorized access detected"
    )

    // ══════════════════════════════════════════════════════════════
    // Rule 2 — Urgency / Time Pressure  (+20)
    // ══════════════════════════════════════════════════════════════
    private val URGENCY_PATTERNS = listOf(
        "urgent", "immediately", "act now", "last chance",
        "expires today", "within 24 hours", "limited time",
        "do not delay", "respond now", "time sensitive",
        "deadline", "asap", "right now", "don't wait",
        "within 2 hours", "before midnight", "expiring soon",
        "last opportunity", "final notice", "today only"
    )

    // ══════════════════════════════════════════════════════════════
    // Rule 3 — Authority Impersonation  (+15)
    // ══════════════════════════════════════════════════════════════
    private val AUTHORITY_PATTERNS = listOf(
        "rbi", "reserve bank", "income tax", "sbi", "hdfc", "icici",
        "axis bank", "bank of india", "government of india",
        "ministry", "irdai", "sebi", "trai", "cbi", "police",
        "aadhaar", "pan card", "epfo", "nsdl", "utiitsl",
        "cyber cell", "eci", "it department", "gst department",
        "paytm", "phonepe", "gpay", "amazon pay", "flipkart"
    )

    // ══════════════════════════════════════════════════════════════
    // Rule 4 — OTP / Credential Harvesting  (+15)
    // ══════════════════════════════════════════════════════════════
    private val OTP_PATTERNS = listOf(
        "otp", "one time password", "verification code",
        "do not share", "do not disclose", "never share",
        "confirm your identity", "authenticate", "passcode",
        "your code is", "enter this code", "security code",
        "2fa code", "two factor", "login code", "auth code"
    )

    // ══════════════════════════════════════════════════════════════
    // Rule 7 — KYC / Document Scam  (+20)  ← NEW
    // ══════════════════════════════════════════════════════════════
    private val KYC_PATTERNS = listOf(
        "kyc", "know your customer", "kyc pending", "kyc update",
        "kyc verification", "kyc expired", "complete your kyc",
        "kyc not done", "re-kyc", "e-kyc", "video kyc",
        "submit documents", "upload aadhaar", "upload pan",
        "aadhaar verification", "pan verification",
        "bank kyc", "wallet kyc", "update your details",
        "account will be closed", "submit your kyc within"
    )

    // ══════════════════════════════════════════════════════════════
    // Rule 8 — Prize / Lottery / Reward  (+20)  ← NEW
    // ══════════════════════════════════════════════════════════════
    private val PRIZE_PATTERNS = listOf(
        "you have won", "you are selected", "congratulations",
        "lucky winner", "prize money", "claim your reward",
        "reward points", "cashback of rs", "won rs",
        "lottery result", "lucky draw", "bumper prize",
        "scratch and win", "spin and win", "gift voucher worth",
        "free iphone", "free laptop", "amazon gift card",
        "you have been selected", "claim within 24",

        // ADD THESE
        "unclaimed cash",
        "claim cash",
        "claim your cash",
        "cash reward",
        "reward waiting"
    )
    // ══════════════════════════════════════════════════════════════
    // Rule 9 — Job / Investment Scam  (+15)  ← NEW
    // ══════════════════════════════════════════════════════════════
    private val JOB_INVESTMENT_PATTERNS = listOf(
        "work from home", "earn daily", "earn rs",
        "part time job", "daily income", "guaranteed return",
        "double your money", "investment opportunity",
        "earn from mobile", "refer and earn", "mlm",
        "trading profit", "crypto profit", "fixed return",
        "minimum investment", "high returns guaranteed",
        "task based earning", "like and earn", "youtube task"
    )

    // ══════════════════════════════════════════════════════════════
    // Rule 10 — Delivery / Courier Impersonation  (+15)  ← NEW
    // ══════════════════════════════════════════════════════════════
    private val DELIVERY_PATTERNS = listOf(
        "parcel held", "package detained", "customs clearance",
        "delivery pending", "failed delivery attempt",
        "pay customs fee", "release your package",
        "fedex", "dhl", "bluedart", "ecom express",
        "your shipment requires", "courier held",
        "delivery charge pending", "reschedule delivery"
    )

    // ══════════════════════════════════════════════════════════════
    // URLs
    // ══════════════════════════════════════════════════════════════
    private val URL_PATTERN = Regex(
        "(https?://[^\\s]+|www\\.[^\\s]+|bit\\.ly/[^\\s]+|tinyurl\\.com/[^\\s]+|[a-zA-Z0-9.-]+\\.(xyz|tk|ml|ga|cf)(/[^\\s]*)?)",
        RegexOption.IGNORE_CASE
    )

    private val SUSPICIOUS_DOMAINS = listOf(
        "bit.ly", "tinyurl", "tiny.cc", "ow.ly",
        ".xyz", ".tk", ".ml", ".ga", ".cf",
        "fakebank", "secure-login", "verify-now",
        "update-kyc", "claim-prize", "account-verify"
    )
    private val PHISHING_KEYWORDS = listOf(
        "login", "verify", "secure", "account", "update", "kyc",
        "bank", "wallet", "payment", "otp", "reward", "claim"
    )

    // ══════════════════════════════════════════════════════════════
    // Community score (unchanged)
    // ══════════════════════════════════════════════════════════════
    fun communityScore(reportCount: Int): Int = when {
        reportCount >= 50 -> 25
        reportCount >= 20 -> 20
        reportCount >= 10 -> 15
        reportCount >= 5  -> 10
        reportCount >= 1  -> 5
        else              -> 0
    }

    // ══════════════════════════════════════════════════════════════
    // Main analyze function
    //
    // sequenceBonus — pass in the bonus from FraudSequenceStore
    //                 if a Call→SMS or SMS→Call pattern was detected.
    //                 Defaults to 0 when called without sequence context.
    // ══════════════════════════════════════════════════════════════
    fun analyze(
        message: String,
        sender: String,
        communityReportCount: Int = 0,
        sequenceBonus: Int = 0,
        sequencePatternName: String? = null
    ): AnalysisResult {
        val text = message.lowercase().trim()
        var score = 0
        val patterns = mutableListOf<String>()

        // ── Rule 1: Fear (+20) ────────────────────────────────────
        val fearFound = FEAR_PATTERNS.filter { text.contains(it) }
        if (fearFound.isNotEmpty()) {
            score += 20
            patterns.add("Fear/Threat: \"${fearFound.first()}\"")
        }

        // ── Rule 2: Urgency (+20) ─────────────────────────────────
        val urgencyFound = URGENCY_PATTERNS.filter { text.contains(it) }
        if (urgencyFound.isNotEmpty()) {
            score += 20
            patterns.add("Urgency: \"${urgencyFound.first()}\"")
        }

        // ── Rule 3: Authority (+15) ───────────────────────────────
        val authorityFound = AUTHORITY_PATTERNS.filter { text.contains(it) }
        if (authorityFound.isNotEmpty()) {
            score += 15
            patterns.add("Authority Impersonation: ${authorityFound.first().uppercase()}")
        }

        // ── Rule 4: OTP (+15) ─────────────────────────────────────
        val otpFound = OTP_PATTERNS.filter { text.contains(it) }
        if (otpFound.isNotEmpty()) {
            score += 15
            patterns.add("OTP/Credential Scam detected")
        }

        // Rule 5 & 6: URL + phishing detection
        val urlMatch = URL_PATTERN.find(text)
        val hasUrl = urlMatch != null

        if (hasUrl) {

            val url = urlMatch!!.value

            val suspiciousDomain = SUSPICIOUS_DOMAINS.any { url.contains(it) }

            val phishingKeyword = PHISHING_KEYWORDS.any { url.contains(it) }

            when {
                suspiciousDomain -> {
                    score += 25
                    patterns.add("Suspicious Domain URL")
                }

                phishingKeyword -> {
                    score += 20
                    patterns.add("Possible Phishing URL")
                }

                else -> {
                    score += 15
                    patterns.add("URL Present")
                }
            }
        }
        if (hasUrl && authorityFound.isNotEmpty()) {
            score += 20
            patterns.add("Authority + Link Phishing Attempt")
        }
        // ── Rule 7: KYC Scam (+20) ────────────────────────────────
        val kycFound = KYC_PATTERNS.filter { text.contains(it) }
        if (kycFound.isNotEmpty()) {
            score += 20
            patterns.add("KYC Verification Scam: \"${kycFound.first()}\"")
        }

        // ── Rule 8: Prize/Lottery (+20) ───────────────────────────
        val prizeFound = PRIZE_PATTERNS.filter { text.contains(it) }
        if (prizeFound.isNotEmpty()) {
            score += 20
            patterns.add("Prize/Lottery Scam: \"${prizeFound.first()}\"")
        }

        // ── Rule 9: Job/Investment (+15) ──────────────────────────
        val jobFound = JOB_INVESTMENT_PATTERNS.filter { text.contains(it) }
        if (jobFound.isNotEmpty()) {
            score += 15
            patterns.add("Job/Investment Scam: \"${jobFound.first()}\"")
        }

        // ── Rule 10: Delivery Scam (+15) ──────────────────────────
        val deliveryFound = DELIVERY_PATTERNS.filter { text.contains(it) }
        if (deliveryFound.isNotEmpty()) {
            score += 15
            patterns.add("Delivery/Courier Scam: \"${deliveryFound.first()}\"")
        }

        // ── Rule 11: Foreign / Unknown Script (+20) ───────────────
        // Any message in a non-Latin, non-Devanagari script is a strong
        // anomaly signal for Indian users (Chinese, Arabic, Cyrillic etc.)
        val foreignScript = detectForeignScript(message)
        if (foreignScript != null) {
            score += 20
            patterns.add("Foreign Language Detected: $foreignScript")
        }

        // ── Sender trust (+5) ─────────────────────────────────────
        val senderTrust = classifySender(sender)
        if (senderTrust == SenderTrust.UNKNOWN) {
            score += 5
            patterns.add("Unknown Sender")
        }

        val behavioralScore = score.coerceAtMost(100)

        // ── Community score ───────────────────────────────────────
        val commScore = communityScore(communityReportCount)
        if (communityReportCount > 0) {
            patterns.add("Reported by $communityReportCount user${if (communityReportCount > 1) "s" else ""}")
        }

        // ── Sequence bonus (cross-signal behavioral) ──────────────
        if (sequenceBonus > 0 && sequencePatternName != null) {
            patterns.add("🔗 Sequence: $sequencePatternName")
        }

        val finalScore = (behavioralScore + commScore + sequenceBonus).coerceAtMost(100)

        val riskLevel = when {
            finalScore <= 30 -> RiskLevel.SAFE
            finalScore <= 70 -> RiskLevel.SUSPICIOUS
            else             -> RiskLevel.HIGH_RISK
        }

        return AnalysisResult(
            riskScore            = finalScore,
            behavioralScore      = behavioralScore,
            communityScore       = commScore,
            communityReportCount = communityReportCount,
            sequenceBonus        = sequenceBonus,
            sequencePatternName  = sequencePatternName,
            riskLevel            = riskLevel,
            detectedPatterns     = patterns,
            hasUrl               = hasUrl,
            senderTrust          = senderTrust,
            summary              = buildSummary(riskLevel, patterns, finalScore, communityReportCount, sequencePatternName)
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Detect if message body contains characters from a foreign script
     * that would be unusual for Indian users.
     * Returns the script name if detected, null otherwise.
     */
    private fun detectForeignScript(text: String): String? {
        for (ch in text) {
            val block = Character.UnicodeBlock.of(ch) ?: continue
            when (block) {
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
                Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
                Character.UnicodeBlock.HIRAGANA,
                Character.UnicodeBlock.KATAKANA  -> return "Chinese/Japanese"

                Character.UnicodeBlock.HANGUL_SYLLABLES,
                Character.UnicodeBlock.HANGUL_JAMO -> return "Korean"

                Character.UnicodeBlock.ARABIC,
                Character.UnicodeBlock.ARABIC_EXTENDED_A -> return "Arabic"

                Character.UnicodeBlock.CYRILLIC  -> return "Cyrillic/Russian"

                Character.UnicodeBlock.THAI      -> return "Thai"

                Character.UnicodeBlock.HEBREW    -> return "Hebrew"

                else -> continue
            }
        }
        return null
    }

    private fun classifySender(sender: String): SenderTrust = when {
        sender.matches(Regex("\\d{10,}"))        -> SenderTrust.NUMERIC_SHORT
        sender.matches(Regex("[A-Za-z0-9-]{3,}")) -> SenderTrust.ALPHANUMERIC
        else                                     -> SenderTrust.UNKNOWN
    }

    private fun buildSummary(
        level: RiskLevel,
        patterns: List<String>,
        score: Int,
        reportCount: Int,
        sequencePattern: String?
    ): String {
        if (patterns.isEmpty()) return "No suspicious patterns detected."
        return when {
            reportCount >= 20 ->
                "⚠️ Known Fraud Number — Reported by $reportCount users. Avoid interaction."
            sequencePattern != null ->
                "🚨 $sequencePattern detected — ${level.label} ($score%). Do NOT share OTP or personal details."
            else -> {
                val top = patterns.take(2).joinToString(" + ") {
                    it.substringBefore(":").trim()
                }
                "${level.label} ($score%) — $top"
            }
        }
    }
}
