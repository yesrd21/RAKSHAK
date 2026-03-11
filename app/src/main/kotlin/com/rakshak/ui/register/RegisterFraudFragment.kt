package com.rakshak.ui.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import com.rakshak.R
import com.rakshak.databinding.FragmentRegisterFraudBinding
import com.rakshak.viewmodel.RegisterFraudViewModel

class RegisterFraudFragment : Fragment() {

    private var _binding: FragmentRegisterFraudBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RegisterFraudViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterFraudBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCategoryDropdown()
        setupSourceTypeToggle()
        setupSubmitButton()
        observeSubmitStatus()
    }

    private fun setupCategoryDropdown() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            viewModel.fraudCategories
        )
        binding.actvCategory.setAdapter(adapter)
    }

    /**
     * When user selects Call — hide SMS message field, show call description field.
     * When user selects Email — hide phone field, show email + message fields.
     * When user selects SMS — show all original fields.
     */
    private fun setupSourceTypeToggle() {
        binding.chipGroupSourceType.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.chipCall) -> {
                    // Call mode: phone visible, email hidden, message hidden, description visible
                    binding.tilSourceNumber.visibility    = View.VISIBLE
                    binding.tvOrDivider.visibility        = View.GONE
                    binding.tilSourceEmail.visibility     = View.GONE
                    binding.tilFraudMessage.visibility    = View.GONE
                    binding.tilCallDescription.visibility = View.VISIBLE
                    binding.tilSourceNumber.hint = "Scam Call Number (e.g. +14155552671)"
                }
                checkedIds.contains(R.id.chipEmail) -> {
                    // Email mode: phone hidden, email visible, message visible, description hidden
                    binding.tilSourceNumber.visibility    = View.GONE
                    binding.tvOrDivider.visibility        = View.GONE
                    binding.tilSourceEmail.visibility     = View.VISIBLE
                    binding.tilFraudMessage.visibility    = View.VISIBLE
                    binding.tilCallDescription.visibility = View.GONE
                    binding.tilSourceNumber.hint = "Source Phone Number"
                }
                else -> {
                    // SMS mode (default): all visible
                    binding.tilSourceNumber.visibility    = View.VISIBLE
                    binding.tvOrDivider.visibility        = View.VISIBLE
                    binding.tilSourceEmail.visibility     = View.VISIBLE
                    binding.tilFraudMessage.visibility    = View.VISIBLE
                    binding.tilCallDescription.visibility = View.GONE
                    binding.tilSourceNumber.hint = "Source Phone Number"
                }
            }
        }
    }

    private fun getSelectedSourceType(): String {
        return when (binding.chipGroupSourceType.checkedChipId) {
            R.id.chipCall  -> "call"
            R.id.chipEmail -> "email"
            else           -> "sms"
        }
    }

    private fun setupSubmitButton() {
        binding.btnSubmitReport.setOnClickListener {
            val sourceType = getSelectedSourceType()
            // For calls, use description field as message
            val message = if (sourceType == "call") {
                binding.etCallDescription.text.toString()
            } else {
                binding.etFraudMessage.text.toString()
            }

            viewModel.submitFraudReport(
                sourceNumber   = binding.etSourceNumber.text.toString(),
                sourceEmail    = binding.etSourceEmail.text.toString(),
                messagePattern = message,
                category       = binding.actvCategory.text.toString(),
                sourceType     = sourceType
            )
        }
    }

    private fun observeSubmitStatus() {
        viewModel.submitStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is RegisterFraudViewModel.SubmitStatus.Loading -> {
                    binding.btnSubmitReport.isEnabled = false
                    binding.progressSubmit.visibility = View.VISIBLE
                }
                is RegisterFraudViewModel.SubmitStatus.Success -> {
                    binding.btnSubmitReport.isEnabled = true
                    binding.progressSubmit.visibility = View.GONE
                    clearForm()
                    Snackbar.make(binding.root, "✅ Report submitted!", Snackbar.LENGTH_LONG).show()
                    viewModel.resetStatus()
                }
                is RegisterFraudViewModel.SubmitStatus.Error -> {
                    binding.btnSubmitReport.isEnabled = true
                    binding.progressSubmit.visibility = View.GONE
                    Snackbar.make(binding.root, "❌ ${status.message}", Snackbar.LENGTH_LONG).show()
                    viewModel.resetStatus()
                }
                null -> { /* idle */ }
            }
        }
    }

    private fun clearForm() {
        binding.etSourceNumber.text?.clear()
        binding.etSourceEmail.text?.clear()
        binding.etFraudMessage.text?.clear()
        binding.etCallDescription.text?.clear()
        binding.actvCategory.text?.clear()
        binding.chipSms.isChecked = true  // reset to SMS mode
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
