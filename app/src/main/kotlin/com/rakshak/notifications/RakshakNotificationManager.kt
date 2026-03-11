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
import com.rakshak.analyzer.AnalysisResult
import com.rakshak.analyzer.RiskLevel
import com.rakshak.receiver.FraudSequenceStore
import com.rakshak.ui.MainActivity

object RakshakNotificationManager {

    private const val HIGH_RISK_CHANNEL_ID  = "rakshak_high_risk"
    private const val SUSPICIOUS_CHANNEL_ID = "rakshak_suspicious"
    private const val NOTIF_ID_BASE         = 1000

    private val HIGH_RISK_VIBRATION  = longArrayOf(0, 200, 100, 200, 100, 600, 100, 600, 100, 600, 100, 200, 100, 200)
    private val SUSPICIOUS_VIBRATION = longArrayOf(0, 300, 150, 300, 150, 300)

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager    = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val alarmUri   = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            manager.createNotificationChannel(
                NotificationChannel(HIGH_RISK_CHANNEL_ID, "🚨 High Risk Fraud Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Critical alerts for high-risk fraud SMS"
                    enableVibration(true); vibrationPattern = HIGH_RISK_VIBRATION
                    enableLights(true); lightColor = android.graphics.Color.RED
                    setSound(alarmUri, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(SUSPICIOUS_CHANNEL_ID, "⚠️ Suspicious Message Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Alerts for suspicious SMS"
                    enableVibration(true); vibrationPattern = SUSPICIOUS_VIBRATION
                    enableLights(true); lightColor = android.graphics.Color.parseColor("#FF9800")
                }
            )
        }
    }

    fun showFraudAlert(
        context: Context,
        sender: String,
        result: AnalysisResult,
        seqMatch: FraudSequenceStore.SequenceMatch? = null
    ) {
        val manager    = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isHighRisk = result.riskLevel == RiskLevel.HIGH_RISK ||
                         seqMatch?.smsEvent?.smsType == FraudSequenceStore.SmsType.OTP
        val channelId  = if (isHighRisk) HIGH_RISK_CHANNEL_ID else SUSPICIOUS_CHANNEL_ID

        if (isHighRisk) triggerAlarmVibration(context)

        // ── Title ─────────────────────────────────────────────────
        val title = when {
            seqMatch?.smsEvent?.smsType == FraudSequenceStore.SmsType.OTP ->
                "🚨 OTP SCAM DETECTED — DO NOT SHARE"
            seqMatch?.smsEvent?.smsType == FraudSequenceStore.SmsType.PHISHING_LINK ->
                "🚨 PHISHING LINK — DO NOT TAP"
            seqMatch != null ->
                "🚨 COORDINATED SCAM — ${seqMatch.patternName.uppercase()}"
            result.communityReportCount >= 50 ->
                "🚨 KNOWN SCAM NUMBER — DO NOT RESPOND"
            result.communityReportCount >= 20 ->
                "🚨 Fraud Alert — Reported ${result.communityReportCount}x"
            isHighRisk ->
                "🚨 HIGH RISK SMS DETECTED"
            else ->
                "⚠️ Suspicious Message — Stay Alert"
        }

        val riskBar = buildRiskBar(result.riskScore)

        // ── Body ──────────────────────────────────────────────────
        val body = buildString {
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📱 From: $sender")
            appendLine("🎯 Risk Score: ${result.riskScore}/100  $riskBar")

            // Sequence block — most important, show first
            if (seqMatch != null) {
                appendLine()
                appendLine("🔗 SEQUENCE DETECTED: ${seqMatch.patternName}")
                appendLine("   ${seqMatch.description}")
                if (seqMatch.isMultiActor) {
                    appendLine("   🚨 Multiple actors involved — coordinated attack")
                }
            }

            appendLine()

            when {
                seqMatch?.smsEvent?.smsType == FraudSequenceStore.SmsType.OTP -> {
                    appendLine("⛔ NEVER share this OTP with anyone")
                    appendLine("⛔ Banks/govt agencies never ask for OTP over call")
                    appendLine("⛔ Hang up immediately if call is still ongoing")
                }
                seqMatch?.smsEvent?.smsType == FraudSequenceStore.SmsType.PHISHING_LINK -> {
                    appendLine("⛔ DO NOT tap the link in this SMS")
                    appendLine("⛔ Do NOT enter any personal details")
                    appendLine("⛔ Report to cybercrime.gov.in / 1930")
                }
                result.communityReportCount >= 20 -> {
                    appendLine("👥 Reported by ${result.communityReportCount} Rakshak users")
                    appendLine("⛔ DO NOT click any links")
                    appendLine("⛔ DO NOT share OTP or passwords")
                }
                else -> {
                    if (result.detectedPatterns.isNotEmpty()) {
                        appendLine("🔍 Detected:")
                        result.detectedPatterns
                            .filter { !it.startsWith("🔗 Sequence") }
                            .take(3)
                            .forEach { appendLine("   • $it") }
                    }
                }
            }

            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━")
            if (isHighRisk) appendLine("⚡ TAP TO OPEN RAKSHAK AI")
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

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("Risk: ${result.riskScore}/100 — ${result.riskLevel.label}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.trim()).setBigContentTitle(title))
            .setPriority(if (isHighRisk) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(color).setColorized(true)
            .setCategory(if (isHighRisk) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(if (isHighRisk) HIGH_RISK_VIBRATION else SUSPICIOUS_VIBRATION)
            .build()

        manager.notify(NOTIF_ID_BASE + (System.currentTimeMillis() % 1000).toInt(), notification)
    }

    private fun buildRiskBar(score: Int): String {
        val filled = (score / 10).coerceIn(0, 10)
        return "▓".repeat(filled) + "░".repeat(10 - filled)
    }

    private fun triggerAlarmVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(HIGH_RISK_VIBRATION, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(HIGH_RISK_VIBRATION, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(HIGH_RISK_VIBRATION, -1)
                }
            }
        } catch (e: Exception) { /* fail silently */ }
    }
}
