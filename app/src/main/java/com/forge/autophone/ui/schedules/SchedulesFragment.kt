package com.forge.autophone.ui.schedules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.forge.autophone.AutoPhoneApp
import com.forge.autophone.R
import com.forge.autophone.data.Schedule
import com.forge.autophone.databinding.FragmentSchedulesBinding
import com.forge.autophone.service.AutoPhoneAccessibilityService
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Screen 3 — Schedules
 *
 * Displays all user-defined automation plans. When Forge OS begins executing
 * a plan it calls [AutoPhoneBinderService.notifyScheduleStarted]; this causes
 * [ScheduleRepository.runningSchedules] to emit so the adapter can immediately
 * apply an animated "Running…" chip and orange border to that schedule card —
 * with no polling or extra threads required.
 *
 * When Forge OS calls [AutoPhoneBinderService.notifyScheduleCompleted] the
 * running indicator clears and the card updates its last-run timestamp.
 */
class SchedulesFragment : Fragment() {

    private var _binding: FragmentSchedulesBinding? = null
    private val binding get() = _binding!!

    private val repo get() =
        (requireActivity().application as AutoPhoneApp).container.scheduleRepo

    private lateinit var adapter: SchedulesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentSchedulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SchedulesAdapter(
            onToggle = { schedule, enabled -> repo.setEnabled(schedule.id, enabled) },
            onEdit   = { schedule -> openEditor(schedule.id) },
            onRunNow = { schedule -> confirmRunNow(schedule) },
        )

        binding.rvSchedules.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSchedules.adapter = adapter

        binding.fab.setOnClickListener { openEditor(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            // Combine both flows so either a schedule list change OR a running-state
            // change causes the adapter to update — no extra coupling needed.
            repo.schedules
                .combine(repo.runningSchedules) { list, running -> list to running }
                .collect { (list, running) ->
                    adapter.submitList(list)
                    adapter.setRunningEntries(running)

                    val active = list.count { it.isEnabled }
                    val runningCount = running.size
                    binding.tvCount.text = buildString {
                        append("$active active · ${list.size} total")
                        if (runningCount > 0) append(" · $runningCount running")
                    }
                    binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
        }
    }

    private fun openEditor(scheduleId: String?) {
        val args = Bundle().apply {
            if (scheduleId != null) putString("scheduleId", scheduleId)
        }
        findNavController().navigate(R.id.scheduleEditorFragment, args)
    }

    private fun confirmRunNow(schedule: Schedule) {
        val svc = AutoPhoneAccessibilityService.instance
        AlertDialog.Builder(requireContext())
            .setTitle("Run now: ${schedule.name}")
            .setMessage(
                if (svc == null)
                    "⚠ Accessibility service is not active. Enable it in Setup first.\n\nPlan:\n${schedule.actionPlan}"
                else
                    "Send this plan to Forge OS for immediate execution?\n\n${schedule.actionPlan}"
            )
            .setPositiveButton(if (svc != null) "Run" else "Go to Setup") { _, _ ->
                if (svc != null) runNow(schedule)
                else findNavController().navigate(R.id.setupFragment)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runNow(schedule: Schedule) {
        // In production this calls IForgeOsService.askAgent(schedule.actionPlan).
        // Forge OS will then call back notifyScheduleStarted → notifyScheduleCompleted
        // which drives the live running indicator automatically.
        // For now we simulate the "started" state so the UI demonstrates the flow.
        repo.markRunning(schedule.id, schedule.actionPlan.take(40))

        (requireActivity().application as AutoPhoneApp).container.actionLog.record(
            tool       = "schedule_run",
            args       = schedule.name,
            ok         = true,
            output     = "Plan sent to Forge OS",
            durationMs = 0,
        )

        Snackbar.make(binding.root, "Plan sent — waiting for Forge OS…", Snackbar.LENGTH_LONG)
            .setAction("Simulate done") {
                repo.markCompleted(schedule.id, ok = true, result = "Completed successfully")
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
