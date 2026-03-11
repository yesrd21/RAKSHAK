package com.rakshak.ui.complaint

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.rakshak.databinding.FragmentComplaintBinding

/**
 * ComplaintFragment
 *
 * Displays official Indian cybercrime helpline information.
 * Provides direct call and web portal access.
 */
class ComplaintFragment : Fragment() {

    private var _binding: FragmentComplaintBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentComplaintBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCallHelpline.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))
            startActivity(intent)
        }

        binding.btnOpenPortal.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in"))
            startActivity(intent)
        }

        binding.btnOpenNcrp.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.ncrp.in"))
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
