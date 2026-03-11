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
import com.rakshak.database.entities.FirestoreFraudReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FraudReportAdapter : ListAdapter<FirestoreFraudReport, FraudReportAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FirestoreFraudReport>() {
            override fun areItemsTheSame(a: FirestoreFraudReport, b: FirestoreFraudReport) =
                a.documentId == b.documentId
            override fun areContentsTheSame(a: FirestoreFraudReport, b: FirestoreFraudReport) =
                a == b
        }
        private val DATE_FMT = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSource: TextView          = itemView.findViewById(R.id.tvSource)
        val tvRiskScore: TextView       = itemView.findViewById(R.id.tvRiskScore)
        val cardRiskBadge: CardView = itemView.findViewById(R.id.cardRiskBadge)
        val tvCategory: TextView        = itemView.findViewById(R.id.tvCategory)
        val tvReportCount: TextView     = itemView.findViewById(R.id.tvReportCount)
        val tvSourceType: TextView      = itemView.findViewById(R.id.tvSourceType)
        val cardSourceType: CardView = itemView.findViewById(R.id.cardSourceType)
        val tvMessage: TextView         = itemView.findViewById(R.id.tvMessage)
        val tvDate: TextView            = itemView.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fraud_report, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = getItem(position)

        holder.tvSource.text = report.sourceIdentifier.ifBlank { report.email }

        // Risk badge
        val badgeColor = when {
            report.riskScore > 70 -> "#F44336"
            report.riskScore > 30 -> "#FF9800"
            else                  -> "#4CAF50"
        }
        holder.cardRiskBadge.setCardBackgroundColor(Color.parseColor(badgeColor))
        holder.tvRiskScore.text = when {
            report.riskScore > 70 -> "High Risk ${report.riskScore}%"
            report.riskScore > 30 -> "Suspicious ${report.riskScore}%"
            else                  -> "Risk: ${report.riskScore}%"
        }

        holder.tvCategory.text = report.category

        holder.tvReportCount.text = "👥 ${report.reportCount} report${if (report.reportCount > 1) "s" else ""}"

        // Source type badge
        val (typeLabel, typeColor) = when (report.sourceType) {
            "call"  -> "📞 CALL"  to "#1565C0"
            "email" -> "✉️ EMAIL" to "#6A1B9A"
            else    -> "📱 SMS"   to "#2E7D32"
        }
        holder.cardSourceType.setCardBackgroundColor(Color.parseColor(typeColor))
        holder.tvSourceType.text = typeLabel

        // Message — for calls show description or "(Call fraud report)"
        holder.tvMessage.text = report.messagePattern.ifBlank {
            if (report.sourceType == "call") "(Call fraud report)" else ""
        }

        holder.tvDate.text = DATE_FMT.format(Date(report.timestamp))
    }
}
