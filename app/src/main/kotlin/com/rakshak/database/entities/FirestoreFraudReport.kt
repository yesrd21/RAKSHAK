package com.rakshak.database.entities

/**
 * FirestoreFraudReport — mirrors the Firestore document structure.
 *
 * Firestore collection: "fraud_reports"
 *
 * sourceType values: "sms" | "call" | "email"
 */
data class FirestoreFraudReport(
    val documentId: String = "",
    val sourceIdentifier: String = "",
    val email: String = "",
    val messagePattern: String = "",
    val category: String = "",
    val sourceType: String = "sms",       // NEW: "sms" | "call" | "email"
    val reportCount: Long = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val riskScore: Int = 0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "sourceIdentifier" to sourceIdentifier,
        "email"            to email,
        "messagePattern"   to messagePattern,
        "category"         to category,
        "sourceType"       to sourceType,
        "reportCount"      to reportCount,
        "timestamp"        to timestamp,
        "riskScore"        to riskScore
    )

    companion object {
        fun fromMap(docId: String, data: Map<String, Any>): FirestoreFraudReport {
            return FirestoreFraudReport(
                documentId       = docId,
                sourceIdentifier = data["sourceIdentifier"] as? String ?: "",
                email            = data["email"] as? String ?: "",
                messagePattern   = data["messagePattern"] as? String ?: "",
                category         = data["category"] as? String ?: "",
                sourceType       = data["sourceType"] as? String ?: "sms",
                reportCount      = (data["reportCount"] as? Long) ?: 1L,
                timestamp        = (data["timestamp"] as? Long) ?: System.currentTimeMillis(),
                riskScore        = ((data["riskScore"] as? Long)?.toInt()) ?: 0
            )
        }
    }
}
