package com.rakshak.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.rakshak.database.entities.FirestoreFraudReport
import kotlinx.coroutines.tasks.await

/**
 * FirestoreRepository
 *
 * Handles all Firebase Firestore operations for the fraud report registry.
 * Room database is NOT touched here — it remains for local scan stats only.
 *
 * Firestore Collection: "fraud_reports"
 */
class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("fraud_reports")

    companion object {
        private const val TAG = "FirestoreRepository"
    }

    // ── Write Operations ────────────────────────────────────────────────────

    /**
     * Add a fraud report to Firestore.
     * If the sourceIdentifier already exists → increment reportCount + update timestamp.
     * Otherwise → create a new document.
     *
     * @return the documentId of the inserted/updated document
     */
    suspend fun addFraudReport(report: FirestoreFraudReport): Result<String> {
        return try {
            val existing = findByIdentifier(report.sourceIdentifier)

            if (existing != null) {
                // Already reported — just increment count and update timestamp
                incrementReportCount(existing.documentId)
                Result.success(existing.documentId)
            } else {
                // New report — write full document
                val docRef = collection.add(report.toMap()).await()
                Log.d(TAG, "New fraud report created: ${docRef.id}")
                Result.success(docRef.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "addFraudReport failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Increment reportCount by 1 and refresh timestamp for an existing document.
     */
    suspend fun incrementReportCount(documentId: String) {
        try {
            collection.document(documentId).update(
                mapOf(
                    "reportCount" to FieldValue.increment(1),
                    "timestamp"   to System.currentTimeMillis()
                )
            ).await()
            Log.d(TAG, "incrementReportCount: $documentId")
        } catch (e: Exception) {
            Log.e(TAG, "incrementReportCount failed: ${e.message}")
        }
    }

    // ── Read Operations ─────────────────────────────────────────────────────

    /**
     * Search fraud reports by phone, email, or message keyword.
     * Firestore doesn't support full-text search natively, so we run
     * three targeted prefix-range queries and merge the results.
     */
    suspend fun searchFraudReports(query: String): List<FirestoreFraudReport> {
        if (query.isBlank()) return getAllReports()

        val results = mutableMapOf<String, FirestoreFraudReport>()
        val q = query.trim()

        // Helper: range query for prefix match on a field
        suspend fun prefixQuery(field: String) {
            try {
                val end = q.dropLast(1) + (q.last() + 1)
                val snap = collection
                    .whereGreaterThanOrEqualTo(field, q)
                    .whereLessThan(field, end)
                    .limit(50)
                    .get().await()
                snap.documents.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    results[doc.id] = FirestoreFraudReport.fromMap(doc.id, data)
                }
            } catch (e: Exception) {
                Log.w(TAG, "prefixQuery($field) failed: ${e.message}")
            }
        }

        prefixQuery("sourceIdentifier")
        prefixQuery("email")
        prefixQuery("category")
        prefixQuery("sourceType")

        return results.values
            .sortedByDescending { it.timestamp }
    }

    /**
     * Get ALL reports ordered by timestamp descending (for default list view).
     */
    suspend fun getAllReports(): List<FirestoreFraudReport> {
        return try {
            val snap = collection
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .get().await()
            snap.documents.mapNotNull { doc ->
                doc.data?.let { FirestoreFraudReport.fromMap(doc.id, it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAllReports failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get the community report count for a specific sender (phone/ID).
     * Used during SMS analysis to apply community risk score.
     * Returns 0 if not found or on network error (fail-safe).
     */
    suspend fun getReportCountForSender(sender: String): Int {
        return try {
            val snap = collection
                .whereEqualTo("sourceIdentifier", sender)
                .limit(1)
                .get().await()
            if (snap.isEmpty) 0
            else (snap.documents[0].getLong("reportCount") ?: 0L).toInt()
        } catch (e: Exception) {
            Log.w(TAG, "getReportCountForSender($sender) failed — defaulting to 0")
            0  // Fail-safe: no community data, use behavioral score only
        }
    }

    // ── Private Helpers ─────────────────────────────────────────────────────

    private suspend fun findByIdentifier(identifier: String): FirestoreFraudReport? {
        return try {
            val snap = collection
                .whereEqualTo("sourceIdentifier", identifier)
                .limit(1)
                .get().await()
            if (snap.isEmpty) null
            else snap.documents[0].data?.let {
                FirestoreFraudReport.fromMap(snap.documents[0].id, it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "findByIdentifier failed: ${e.message}")
            null
        }
    }
}
