package com.rakshak.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.rakshak.analyzer.CallRiskAnalyzer
import com.rakshak.analyzer.RiskLevel
import com.rakshak.receiver.FraudSequenceStore
import com.rakshak.calls.AutoCallTerminator
import com.rakshak.ui.FullScreenAlertActivity
import com.rakshak.ui.MainActivity

object CallAlertManager {

    private const val CALL_CHANNEL_ID = "rakshak_call_alerts"
    private const val NOTIF_ID_BASE   = 3000

    private val CALL_VIBRATION_HIGH = longArrayOf(0, 500, 200, 500, 200, 500, 200, 1000)
    private val CALL_VIBRATION_WARN = longArrayOf(0, 400, 200, 400)

    fun createCallNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            val channel = NotificationChannel(
                CALL_CHANNEL_ID,
                "🚨 Suspicious Call Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time alerts for suspicious incoming calls"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 200, 800, 200, 800, 200, 800, 200, 800)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setSound(alarmUri, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)   // bypass Do Not Disturb
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
    fun showCallAlert(
        context: Context,
        incomingNumber: String,
        result: CallRiskAnalyzer.CallAnalysisResult,
        terminationResult: AutoCallTerminator.TerminationResult? = null
    ) {
        val manager    = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isHighRisk = result.riskLevel == RiskLevel.HIGH_RISK
        val displayNum = if (result.isHidden) "📵 Hidden / Private Number" else "📞 $incomingNumber"
        val seqMatch   = result.sequenceMatch

        // ── Full screen alert for score >= 66 ─────────────────────

// Build full screen intent (required to show over active call screen)
        val fullScreenReason = terminationResult?.reason
            ?: buildString {
                if (seqMatch != null) append(seqMatch.patternName)
                if (result.communityReportCount > 0) {
                    if (isNotEmpty()) append(" • ")
                    append("Reported ${result.communityReportCount}x")
                }
                if (result.isHidden) {
                    if (isNotEmpty()) append(" • ")
                    append("Hidden number")
                }
                if (isEmpty()) append("Multiple fraud signals (${result.riskScore}/100)")
            }

        val fullScreenPendingIntent = if (result.riskScore >= AutoCallTerminator.FULLSCREEN_THRESHOLD) {
            PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                FullScreenAlertActivity.createIntent(
                    context       = context,
                    caller        = incomingNumber,
                    score         = result.riskScore,
                    reason        = fullScreenReason,
                    wasTerminated = terminationResult?.wasTerminated == true
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null
        triggerCallVibration(context, isHighRisk)

        // ── Title ─────────────────────────────────────────────────
        val title = when {
            seqMatch?.patternName == "SMS → Call Sequence" ->
                "🚨 SCAM FOLLOW-UP CALL — HANG UP NOW"
            result.communityReportCount >= 20 ->
                "🚨 KNOWN FRAUD NUMBER CALLING"
            isHighRisk ->
                "🚨 HIGH-RISK CALL INCOMING"
            else ->
                "⚠️ Suspicious Call — Be Cautious"
        }

        val riskBar = buildRiskBar(result.riskScore)

        // ── Body ──────────────────────────────────────────────────
        val body = buildString {
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine(displayNum)
            appendLine("🎯 Risk Score: ${result.riskScore}/100  $riskBar")
            appendLine()

            if (seqMatch != null) {
                appendLine("🔗 SEQUENCE: ${seqMatch.patternName}")
                appendLine("   ${seqMatch.description}")
                if (seqMatch.isMultiActor) {
                    appendLine("   🚨 Multi-actor pattern — coordinated attack")
                }
                appendLine()
            }

            if (result.communityReportCount >= 20) {
                appendLine("👥 Reported ${result.communityReportCount}x as fraud")
                appendLine()
            } else if (result.communityReportCount > 0) {
                appendLine("👥 Community: ${result.communityReportCount} report(s)")
                appendLine()
            }

            if (result.correlatedSms != null) {
                appendLine("🔗 Same-number SMS ${result.correlatedSms.riskScore} score received earlier")
                appendLine()
            }

            val filteredSignals = result.detectedSignals
                .filter { !it.startsWith("🔗") }
                .take(3)
            if (filteredSignals.isNotEmpty()) {
                appendLine("🔍 Signals:")
                filteredSignals.forEach { appendLine("   • $it") }
                appendLine()
            }

            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("⛔ NEVER share OTP, PIN, or passwords over call")
            if (seqMatch != null || isHighRisk) {
                appendLine("⛔ HANG UP — call back on official number")
                appendLine("⛔ Report: cybercrime.gov.in / 1930")
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context, System.currentTimeMillis().toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val color = when {
            result.riskScore >= 80 -> android.graphics.Color.parseColor("#B71C1C")
            result.riskScore >= 50 -> android.graphics.Color.parseColor("#E65100")
            else                   -> android.graphics.Color.parseColor("#F57F17")
        }

        val notification = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(title)
            .setContentText("$displayNum — Risk ${result.riskScore}/100")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.trim()).setBigContentTitle(title))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(color).setColorized(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(if (isHighRisk) CALL_VIBRATION_HIGH else CALL_VIBRATION_WARN)
            .setOngoing(isHighRisk)
            .also { builder ->
                if (fullScreenPendingIntent != null) {
                    builder.setFullScreenIntent(fullScreenPendingIntent, true)
                }
            }
            .build()

        manager.notify(NOTIF_ID_BASE + (System.currentTimeMillis() % 1000).toInt(), notification)
    }

    private fun buildRiskBar(score: Int): String {
        val filled = (score / 10).coerceIn(0, 10)
        return "▓".repeat(filled) + "░".repeat(10 - filled)
    }

    private fun triggerCallVibration(context: Context, isHighRisk: Boolean) {
        val pattern = if (isHighRisk) CALL_VIBRATION_HIGH else CALL_VIBRATION_WARN
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) { /* fail silently */ }
    }
}
