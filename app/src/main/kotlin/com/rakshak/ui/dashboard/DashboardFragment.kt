package com.rakshak.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rakshak.R
import com.rakshak.analyzer.RiskLevel
import com.rakshak.databinding.FragmentDashboardBinding
import com.rakshak.ui.smshistory.SmsHistoryFragment
import com.rakshak.viewmodel.DashboardViewModel

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeStats()
        setupStatCardClicks()   // NEW — tap cards to open history
        setupTestButton()
        observeTestResult()
    }

    private fun observeStats() {
        viewModel.scanStats.observe(viewLifecycleOwner) { stats ->
            if (stats != null) {
                binding.tvTotalScanned.text = stats.totalScanned.toString()
                binding.tvHighRisk.text     = stats.highRiskCount.toString()
                binding.tvSuspicious.text   = stats.suspiciousCount.toString()
                binding.tvSafe.text         = stats.safeCount.toString()
            }
        }
    }

    // Each stat card navigates to Message History with a pre-set filter
    private fun setupStatCardClicks() {
        fun goTo(filter: String) {
            val args = Bundle().apply {
                putString(SmsHistoryFragment.ARG_FILTER, filter)
            }
            findNavController().navigate(R.id.smsHistoryFragment, args)
        }
        binding.cardStatTotal.setOnClickListener      { goTo("ALL") }
        binding.cardStatHighRisk.setOnClickListener   { goTo("High Risk") }
        binding.cardStatSuspicious.setOnClickListener { goTo("Suspicious") }
        binding.cardStatSafe.setOnClickListener       { goTo("Safe") }
    }

    private fun setupTestButton() {
        binding.btnTestMessage.setOnClickListener {
            val message = binding.etTestMessage.text.toString().trim()
            val sender  = binding.etTestSender.text.toString().trim().ifBlank { "Unknown" }
            if (message.isBlank()) {
                binding.etTestMessage.error = "Enter a message to test"
                return@setOnClickListener
            }
            viewModel.testMessage(message, sender)
        }
    }

    private fun observeTestResult() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.btnTestMessage.isEnabled = !loading
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.testResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe

            val riskEmoji = when (result.riskLevel) {
                RiskLevel.SAFE       -> "✅"
                RiskLevel.SUSPICIOUS -> "⚠️"
                RiskLevel.HIGH_RISK  -> "🚨"
            }

            val patternText = if (result.detectedPatterns.isEmpty()) "None"
            else result.detectedPatterns.joinToString("\n• ", prefix = "• ")

            val communityLine = if (result.communityReportCount > 0)
                "\n\n👥 Community Reports: ${result.communityReportCount}\n+${result.communityScore} community score"
            else ""

            val sequenceLine = if (result.sequenceBonus > 0 && result.sequencePatternName != null)
                "\n\n🔗 Sequence Detected: ${result.sequencePatternName}\n+${result.sequenceBonus} sequence bonus"
            else ""

            val dialogMsg = """
                $riskEmoji Risk Level: ${result.riskLevel.label}
                📊 Final Score: ${result.riskScore}/100
                🔍 Behavioral: ${result.behavioralScore} | Community: ${result.communityScore} | Sequence: ${result.sequenceBonus}
                
                Detected Patterns:
                $patternText$communityLine$sequenceLine
            """.trimIndent()

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Analysis Result")
                .setMessage(dialogMsg)
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    viewModel.clearTestResult()
                }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
