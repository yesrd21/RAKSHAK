package com.rakshak.ui.calllog

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rakshak.analyzer.RiskLevel
import com.rakshak.databinding.FragmentCallLogBinding
import com.rakshak.ui.adapters.CallLogAdapter
import com.rakshak.viewmodel.CallLogViewModel

class CallLogFragment : Fragment() {

    private var _binding: FragmentCallLogBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CallLogViewModel by viewModels()
    private val adapter = CallLogAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCallLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeCallLogs()
        observeCounts()
        setupTestButton()
        observeTestResult()
        setupCommunitySearch()
        setupClearButton()
    }

    private fun setupRecyclerView() {
        binding.rvCallLog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCallLog.adapter = adapter
    }

    private fun observeCallLogs() {
        viewModel.callLogs.observe(viewLifecycleOwner) { logs ->
            adapter.submitList(logs)
            if (logs.isNullOrEmpty()) {
                binding.tvEmptyLog.visibility = View.VISIBLE
                binding.rvCallLog.visibility  = View.GONE
            } else {
                binding.tvEmptyLog.visibility = View.GONE
                binding.rvCallLog.visibility  = View.VISIBLE
            }
        }
    }

    private fun observeCounts() {
        viewModel.totalCount.observe(viewLifecycleOwner) {
            binding.tvTotalCalls.text = it.toString()
        }
        viewModel.correlatedCount.observe(viewLifecycleOwner) {
            binding.tvCorrelatedCalls.text = it.toString()
        }
    }

    private fun setupTestButton() {
        binding.btnTestNumber.setOnClickListener {
            val number = binding.etTestNumber.text.toString().trim()
            if (number.isBlank()) {
                binding.etTestNumber.error = "Enter a phone number"
                return@setOnClickListener
            }
            viewModel.testNumber(number)
        }
    }

    private fun observeTestResult() {
        viewModel.testResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe

            binding.cardTestResult.visibility = View.VISIBLE

            val (emoji, color) = when (result.riskLevel) {
                RiskLevel.HIGH_RISK  -> "🚨" to "#B71C1C"
                RiskLevel.SUSPICIOUS -> "⚠️" to "#E65100"
                RiskLevel.SAFE       -> "✅" to "#2E7D32"
            }

            binding.tvTestResultTitle.text =
                "$emoji ${result.riskLevel.label} — Score: ${result.riskScore}/100"
            binding.tvTestResultTitle.setTextColor(Color.parseColor(color))

            val detail = if (result.detectedSignals.isEmpty()) {
                "No suspicious signals detected."
            } else {
                result.detectedSignals.joinToString("\n") { "• $it" }
            }
            binding.tvTestResultDetail.text = detail
        }
    }

    private fun setupCommunitySearch() {
        binding.btnCommunitySearch.setOnClickListener {
            val number = binding.etCommunitySearch.text.toString().trim()
            if (number.isBlank()) {
                binding.etCommunitySearch.error = "Enter a number to search"
                return@setOnClickListener
            }
            viewModel.searchCommunity(number)
        }

        viewModel.communityLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressCommunitySearch.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnCommunitySearch.isEnabled = !loading
        }

        viewModel.communityResults.observe(viewLifecycleOwner) { results ->
            if (results.isEmpty()) return@observe
            binding.cardCommunityResult.visibility = View.VISIBLE

            val callReports = results.filter { it.sourceType == "call" }
            val smsReports  = results.filter { it.sourceType == "sms" || it.sourceType.isBlank() }
            val totalReports = results.sumOf { it.reportCount }

            val title = when {
                totalReports >= 20 -> "🚨 Known Fraud Number — $totalReports total reports"
                totalReports > 0   -> "⚠️ Reported ${totalReports}x in community"
                else               -> "✅ No community reports found"
            }
            binding.tvCommunityResultTitle.text = title

            val detail = buildString {
                if (callReports.isNotEmpty())
                    appendLine("📞 Call reports: ${callReports.size} (${callReports.sumOf { it.reportCount }} total)")
                if (smsReports.isNotEmpty())
                    appendLine("📱 SMS reports: ${smsReports.size} (${smsReports.sumOf { it.reportCount }} total)")
                results.firstOrNull()?.category?.let { append("Category: $it") }
            }.trim()
            binding.tvCommunityResultDetail.text = detail.ifBlank { "Number found in fraud registry." }
        }
    }

    private fun setupClearButton() {
        binding.btnClearLog.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear Call Log")
                .setMessage("Remove all flagged call records from this device?")
                .setPositiveButton("Clear") { _, _ ->
                    viewModel.clearLog()
                    binding.cardTestResult.visibility = View.GONE
                    binding.etTestNumber.text?.clear()
                    viewModel.clearTestResult()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
