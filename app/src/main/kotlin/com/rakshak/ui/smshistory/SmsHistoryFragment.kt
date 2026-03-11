package com.rakshak.ui.smshistory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rakshak.databinding.FragmentSmsHistoryBinding
import com.rakshak.ui.adapters.SmsLogAdapter
import com.rakshak.viewmodel.SmsHistoryViewModel

class SmsHistoryFragment : Fragment() {

    companion object {
        const val ARG_FILTER = "filter"   // passed from Dashboard tap
    }

    private var _binding: FragmentSmsHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SmsHistoryViewModel by viewModels()
    private val adapter = SmsLogAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSmsHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply filter sent from Dashboard (default: ALL)
        val initialFilter = arguments?.getString(ARG_FILTER) ?: "ALL"
        viewModel.setFilter(initialFilter)

        setupRecyclerView()
        observeData()
        setupFilterClicks()
        setupClearButton()
    }

    private fun setupRecyclerView() {
        binding.rvSmsLog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSmsLog.adapter = adapter
    }

    private fun observeData() {
        // Update badge counts
        viewModel.totalCount.observe(viewLifecycleOwner)      { binding.tvFilterTotal.text      = it.toString() }
        viewModel.highRiskCount.observe(viewLifecycleOwner)   { binding.tvFilterHighRisk.text   = it.toString() }
        viewModel.suspiciousCount.observe(viewLifecycleOwner) { binding.tvFilterSuspicious.text = it.toString() }
        viewModel.safeCount.observe(viewLifecycleOwner)       { binding.tvFilterSafe.text       = it.toString() }

        // Update list
        viewModel.filteredLogs.observe(viewLifecycleOwner) { logs ->
            adapter.submitList(logs)
            binding.tvEmptySmsLog.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
            binding.rvSmsLog.visibility      = if (logs.isEmpty()) View.GONE    else View.VISIBLE
        }

        // Highlight active filter tab and update subtitle
        viewModel.currentFilter.observe(viewLifecycleOwner) { filter ->
            highlightFilter(filter)
            binding.tvActiveFilter.text = when (filter) {
                "High Risk"  -> "Showing: 🚨 High Risk messages only"
                "Suspicious" -> "Showing: ⚠️ Suspicious messages only"
                "Safe"       -> "Showing: ✅ Safe messages only"
                else         -> "Showing: All scanned messages"
            }
            binding.tvListLabel.text = when (filter) {
                "High Risk"  -> "🚨 High Risk Messages"
                "Suspicious" -> "⚠️ Suspicious Messages"
                "Safe"       -> "✅ Safe Messages"
                else         -> "All Messages"
            }
        }
    }

    private fun setupFilterClicks() {
        binding.cardFilterAll.setOnClickListener        { viewModel.setFilter("ALL") }
        binding.cardFilterHighRisk.setOnClickListener   { viewModel.setFilter("High Risk") }
        binding.cardFilterSuspicious.setOnClickListener { viewModel.setFilter("Suspicious") }
        binding.cardFilterSafe.setOnClickListener       { viewModel.setFilter("Safe") }
    }

    // Active tab = full opacity + higher elevation; others dimmed
    private fun highlightFilter(active: String) {
        listOf(
            binding.cardFilterAll        to "ALL",
            binding.cardFilterHighRisk   to "High Risk",
            binding.cardFilterSuspicious to "Suspicious",
            binding.cardFilterSafe       to "Safe"
        ).forEach { (card, label) ->
            card.alpha         = if (label == active) 1.0f else 0.4f
            card.cardElevation = if (label == active) 8f   else 1f
        }
    }

    private fun setupClearButton() {
        binding.btnClearSmsLog.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear Message History")
                .setMessage("This will remove all scanned message records from this device. The messages themselves are unaffected.")
                .setPositiveButton("Clear") { _, _ -> viewModel.clearLog() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
