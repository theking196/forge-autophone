package com.forge.autophone.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.forge.autophone.AutoPhoneApp
import com.forge.autophone.databinding.FragmentActionLogBinding
import kotlinx.coroutines.launch

/**
 * Screen 3 — Action Log
 *
 * Full scrollable history of every tool call made through AutoPhone this session.
 * Each entry shows:
 *   - Tool name (monospace)
 *   - Arguments
 *   - Output / error
 *   - Timestamp and duration
 *   - Green/red left-border indicator
 *
 * Items are newest-first (index 0 = most recent).
 */
class ActionLogFragment : Fragment() {

    private var _binding: FragmentActionLogBinding? = null
    private val binding get() = _binding!!

    private val actionLog get() = (requireActivity().application as AutoPhoneApp).container.actionLog
    private lateinit var adapter: ActionLogAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentActionLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ActionLogAdapter()
        binding.rvLog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLog.adapter = adapter

        binding.btnClear.setOnClickListener { /* no-op in this version — log clears on app restart */ }

        viewLifecycleOwner.lifecycleScope.launch {
            actionLog.entries.collect { entries ->
                adapter.submitList(entries)
                binding.tvCount.text = "${entries.size} action${if (entries.size != 1) "s" else ""}"
                binding.tvEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
