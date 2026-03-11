package com.rakshak.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rakshak.R
import com.rakshak.database.entities.SmsLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsLogAdapter : ListAdapter<SmsLogEntry, SmsLogAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SmsLogEntry>() {
            override fun areItemsTheSame(a: SmsLogEntry, b: SmsLogEntry) = a.id == b.id
            override fun areContentsTheSame(a: SmsLogEntry, b: SmsLogEntry) = a == b
        }
        private val DATE_FMT = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    }

    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvSender:       TextView = v.findViewById(R.id.tvSmsSender)
        val tvRiskBadge:    TextView = v.findViewById(R.id.tvRiskBadge)
        val cardRiskBadge:  CardView = v.findViewById(R.id.cardRiskBadge)
        val tvScoreBar:     TextView = v.findViewById(R.id.tvScoreBar)
        val tvScoreNumber:  TextView = v.findViewById(R.id.tvScoreNumber)
        val tvPatterns:     TextView = v.findViewById(R.id.tvPatterns)
        val cardSequence:   CardView = v.findViewById(R.id.cardSequence)
        val tvSequence:     TextView = v.findViewById(R.id.tvSequence)
        val tvCommunity:    TextView = v.findViewById(R.id.tvCommunity)
        val tvTime:         TextView = v.findViewById(R.id.tvSmsTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_sms_log, parent, false)
    )

    override fun onBindViewHolder(h: ViewHolder, pos: Int) {
        val e = getItem(pos)

        // Sender
        h.tvSender.text = if (e.sender == "Unknown") "📵 Unknown Sender" else "📱 ${e.sender}"

        // Risk badge colour + label
        val (color, label) = when (e.riskLevel) {
            "High Risk"  -> "#B71C1C" to "🚨 HIGH RISK"
            "Suspicious" -> "#E65100" to "⚠️ SUSPICIOUS"
            else         -> "#1B5E20" to "✅ SAFE"
        }
        h.cardRiskBadge.setCardBackgroundColor(Color.parseColor(color))
        h.tvRiskBadge.text = "$label  ${e.riskScore}/100"

        // Score progress bar  ▓▓▓▓░░░░░░
        val filled = (e.riskScore / 10).coerceIn(0, 10)
        h.tvScoreBar.text = "▓".repeat(filled) + "░".repeat(10 - filled)
        h.tvScoreBar.setTextColor(when {
            e.riskScore > 70 -> Color.parseColor("#FF1744")
            e.riskScore > 30 -> Color.parseColor("#FF6D00")
            else             -> Color.parseColor("#00E676")
        })
        h.tvScoreNumber.text = "Score: ${e.riskScore}/100"

        // Detected patterns — pipe-separated in DB, bullets in UI
        val patterns = e.detectedPatterns
            .split("|")
            .filter { it.isNotBlank() }
            .joinToString("\n") { "• $it" }
        h.tvPatterns.text = patterns.ifBlank { "No suspicious patterns detected" }

        // Sequence warning banner
        if (e.sequencePattern != null) {
            h.cardSequence.visibility = View.VISIBLE
            h.tvSequence.text = "🔗 Sequence Detected: ${e.sequencePattern}"
        } else {
            h.cardSequence.visibility = View.GONE
        }

        // Community reports
        if (e.communityReportCount > 0) {
            h.tvCommunity.visibility = View.VISIBLE
            h.tvCommunity.text =
                "👥 Reported by ${e.communityReportCount} user${if (e.communityReportCount > 1) "s" else ""} in community"
        } else {
            h.tvCommunity.visibility = View.GONE
        }

        // Timestamp
        h.tvTime.text = DATE_FMT.format(Date(e.timestamp))
    }
}
