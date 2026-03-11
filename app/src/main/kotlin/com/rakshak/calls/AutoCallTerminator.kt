package com.rakshak.calls

import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import com.rakshak.analyzer.CallRiskAnalyzer

/**
 * AutoCallTerminator
 *
 * Automatically ends a call when risk score exceeds TERMINATION_THRESHOLD.
 *
 * Why not audio detection:
 *   Android 9+ blocks all third-party access to call audio streams.
 *   Our approach acts BEFORE the scammer speaks — faster and more private.
 *
 * Thresholds:
 *   Score 85+   → Auto-terminate + full screen alert
 *   Score 66-84 → Full screen alert only, user decides
 *   Score < 66  → Normal notification
 */
object AutoCallTerminator {

    private const val TAG = "AutoCallTerminator"

    const val TERMINATION_THRESHOLD = 85 // auto cut
    const val FULLSCREEN_THRESHOLD  = 66  // show full screen, user decides

    data class TerminationResult(
        val wasTerminated: Boolean,
        val reason: String,
        val score: Int
    )

    fun evaluate(
        context: Context,
        result: CallRiskAnalyzer.CallAnalysisResult
    ): TerminationResult {

        val score = result.riskScore

        if (score < TERMINATION_THRESHOLD) {
            return TerminationResult(
                wasTerminated = false,
                reason        = buildReason(result),
                score         = score
            )
        }

        val terminated = terminateCall(context)
        Log.d(TAG, "Auto-terminate attempted score=$score success=$terminated")

        return TerminationResult(
            wasTerminated = terminated,
            reason        = buildReason(result),
            score         = score
        )
    }

    private fun terminateCall(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                @Suppress("MissingPermission")
                telecom.endCall()
                true
            } else {
                Log.w(TAG, "Auto-termination not supported below Android 9")
                false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing ANSWER_PHONE_CALLS permission: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to terminate: ${e.message}")
            false
        }
    }

    private fun buildReason(result: CallRiskAnalyzer.CallAnalysisResult): String {
        val reasons = mutableListOf<String>()
        if (result.communityReportCount >= 20)
            reasons.add("Reported by ${result.communityReportCount} users")
        if (result.sequenceMatch != null)
            reasons.add(result.sequenceMatch.patternName)
        if (result.correlatedSms != null)
            reasons.add("Suspicious SMS from same number earlier")
        if (result.isHidden)
            reasons.add("Hidden/private caller ID")
        if (result.isInternational)
            reasons.add("International: ${result.countryDescription}")
        return reasons.ifEmpty {
            listOf("Multiple fraud signals (score: ${result.riskScore}/100)")
        }.joinToString(" • ")
    }
}
