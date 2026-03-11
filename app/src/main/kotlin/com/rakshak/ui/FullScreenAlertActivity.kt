package com.rakshak.ui

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.rakshak.databinding.ActivityFullScreenAlertBinding

class FullScreenAlertActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CALLER     = "extra_caller"
        const val EXTRA_SCORE      = "extra_score"
        const val EXTRA_REASON     = "extra_reason"
        const val EXTRA_TERMINATED = "extra_terminated"

        private val PATTERN = longArrayOf(0, 800, 200, 800, 200, 800, 200, 800, 200, 800)

        fun createIntent(
            context: Context,
            caller: String,
            score: Int,
            reason: String,
            wasTerminated: Boolean
        ): Intent = Intent(context, FullScreenAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CALLER,     caller)
            putExtra(EXTRA_SCORE,      score)
            putExtra(EXTRA_REASON,     reason)
            putExtra(EXTRA_TERMINATED, wasTerminated)
        }
    }

    private lateinit var binding: ActivityFullScreenAlertBinding
    private var ringtone: android.media.Ringtone? = null
    private var originalRingerMode: Int = AudioManager.RINGER_MODE_NORMAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        binding = ActivityFullScreenAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val caller     = intent.getStringExtra(EXTRA_CALLER)     ?: "Unknown"
        val score      = intent.getIntExtra(EXTRA_SCORE, 0)
        val reason     = intent.getStringExtra(EXTRA_REASON)     ?: "Multiple fraud signals detected"
        val terminated = intent.getBooleanExtra(EXTRA_TERMINATED, false)

        bindUI(caller, score, reason, terminated)

        // Delay slightly to ensure window is attached before vibrating
        binding.root.postDelayed({
            forceVibrate()
            playAlarmSound()
        }, 300)
    }

    private fun bindUI(caller: String, score: Int, reason: String, terminated: Boolean) {
        if (terminated) {
            binding.tvStatus.text       = "🛡️ CALL AUTO-TERMINATED"
            binding.tvSubStatus.text    = "Rakshak AI ended this call to protect you"
            binding.btnHangUp.text      = "✓ Call Already Ended"
            binding.btnHangUp.isEnabled = false
            binding.btnHangUp.alpha     = 0.5f
        } else {
            binding.tvStatus.text       = "🚨 FRAUD CALL DETECTED"
            binding.tvSubStatus.text    = "HANG UP IMMEDIATELY"
            binding.btnHangUp.text      = "🔴 HANG UP NOW"
            binding.btnHangUp.isEnabled = true
        }

        binding.tvCaller.text = if (caller.isBlank() || caller == "Unknown")
            "📵 Hidden / Private Number" else "📞 $caller"

        binding.tvScore.text   = "Risk Score: $score / 100"
        val filled = (score / 10).coerceIn(0, 10)
        binding.tvRiskBar.text = "▓".repeat(filled) + "░".repeat(10 - filled)
        binding.tvReason.text  = reason

        binding.btnHangUp.setOnClickListener  { stopAll(); finish() }
        binding.btnDismiss.setOnClickListener { stopAll(); finish() }
        binding.btnReport.setOnClickListener  {
            stopAll()
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in")))
        }
    }

    // ── Force vibration — works on Xiaomi MIUI ────────────────────────────
    private var vibrateThread: Thread? = null
    private var isVibrating = false

    private fun forceVibrate() {
        isVibrating = true
        vibrateThread = Thread {
            while (isVibrating) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                        vm.defaultVibrator.vibrate(
                            VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        @Suppress("DEPRECATION")
                        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        v.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        @Suppress("DEPRECATION")
                        v.vibrate(600)
                    }
                    Thread.sleep(800) // 600ms vibrate + 200ms gap
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    android.util.Log.e("FullScreenAlert", "Vibrate loop failed: ${e.message}")
                    break
                }
            }
        }.also { it.start() }
    }

    // ── Play alarm sound — forces Xiaomi to also vibrate ─────────────────
    private fun playAlarmSound() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            originalRingerMode = audioManager.ringerMode

            // Force ring mode so sound + vibration works
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return

            ringtone = RingtoneManager.getRingtone(this, alarmUri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone?.play()
        } catch (e: Exception) {
            android.util.Log.e("FullScreenAlert", "Ringtone failed: ${e.message}")
        }
    }

    private fun stopAll() {
        // Stop vibration thread
        isVibrating = false
        vibrateThread?.interrupt()
        vibrateThread = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator.cancel()
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
            }
        } catch (e: Exception) { }

        // Stop ringtone and restore ringer mode
        try {
            ringtone?.stop()
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.ringerMode = originalRingerMode
        } catch (e: Exception) { }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON   or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* blocked */ }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }
}