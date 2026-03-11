package com.rakshak.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.cardview.widget.CardView
import com.rakshak.R
import com.rakshak.database.entities.CallLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogAdapter : ListAdapter<CallLogEntry, CallLogAdapter.CallLogViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CallLogEntry>() {
            override fun areItemsTheSame(a: CallLogEntry, b: CallLogEntry) = a.id == b.id
            override fun areContentsTheSame(a: CallLogEntry, b: CallLogEntry) = a == b
        }
        private val DATE_FMT = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    }

    inner class CallLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCallNumber: TextView      = itemView.findViewById(R.id.tvCallNumber)
        val tvRiskScore: TextView       = itemView.findViewById(R.id.tvRiskScore)
        val cardRiskBadge: CardView = itemView.findViewById(R.id.cardRiskBadge)
        val tvSignals: TextView         = itemView.findViewById(R.id.tvSignals)
        val cardCorrelation: CardView = itemView.findViewById(R.id.cardCorrelation)
        val tvCorrelation: TextView     = itemView.findViewById(R.id.tvCorrelation)
        val tvCallTime: TextView        = itemView.findViewById(R.id.tvCallTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_log, parent, false)
        return CallLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallLogViewHolder, position: Int) {
        val entry = getItem(position)

        // Number
        holder.tvCallNumber.text = entry.callerNumber

        // Risk badge colour
        val badgeColor = when {
            entry.riskScore > 65 -> "#B71C1C"
            else                 -> "#E65100"
        }
        holder.cardRiskBadge.setCardBackgroundColor(Color.parseColor(badgeColor))
        holder.tvRiskScore.text = "${entry.riskLevel} ${entry.riskScore}%"

        // Signals — pipe-separated in DB, bullet-separated in UI
        val signalText = entry.signals
            .split("|")
            .filter { it.isNotBlank() }
            .joinToString("\n") { "• $it" }
        holder.tvSignals.text = signalText.ifBlank { "No additional signals" }

        // Correlation warning
        if (entry.isCorrelated) {
            holder.cardCorrelation.visibility = View.VISIBLE
            holder.tvCorrelation.text =
                "🚨 Received after a suspicious SMS (score: ${entry.correlatedSmsScore}). Do NOT share OTP or personal details."
        } else {
            holder.cardCorrelation.visibility = View.GONE
        }

        // Time
        holder.tvCallTime.text = DATE_FMT.format(Date(entry.timestamp))
    }
}
