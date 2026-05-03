package com.forge.autophone.ui.schedules

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.forge.autophone.AutoPhoneApp
import com.forge.autophone.data.EventTrigger
import com.forge.autophone.data.RepeatMode
import com.forge.autophone.data.Schedule
import com.forge.autophone.data.TriggerType
import com.forge.autophone.databinding.FragmentScheduleEditorBinding

/**
 * Create or edit a single [Schedule].
 *
 * arguments["scheduleId"] == null → new schedule
 * arguments["scheduleId"] == id   → editing existing
 *
 * Trigger types:
 *   TIME  — HH:MM + repeat mode spinner (Once / Daily / Weekdays / Weekend / Weekly)
 *   EVENT — event type spinner (Battery low, Wi-Fi connected, Screen on, Charging, App opened)
 *
 * The action plan is free-form natural language that Forge OS agent will
 * interpret and execute using AutoPhone tools.
 */
class ScheduleEditorFragment : Fragment() {

    private var _binding: FragmentScheduleEditorBinding? = null
    private val binding get() = _binding!!

    private val scheduleRepo get() =
        (requireActivity().application as AutoPhoneApp).container.scheduleRepo

    private var existingSchedule: Schedule? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentScheduleEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Repeat mode spinner
        binding.spinnerRepeat.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item,
            RepeatMode.values().map { it.label }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Event type spinner
        binding.spinnerEvent.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item,
            EventTrigger.values().map { it.label }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Trigger type toggle
        binding.rgTriggerType.setOnCheckedChangeListener { _, checkedId ->
            val isTime = checkedId == com.forge.autophone.R.id.rb_time
            binding.layoutTimePicker.visibility = if (isTime) View.VISIBLE else View.GONE
            binding.layoutEventPicker.visibility = if (isTime) View.GONE else View.VISIBLE
        }

        // Load schedule or init blank
        val scheduleId = arguments?.getString("scheduleId")
        if (scheduleId != null) {
            existingSchedule = scheduleRepo.schedules.value.firstOrNull { it.id == scheduleId }
            existingSchedule?.let { populate(it) }
            binding.tvTitle.text = "Edit schedule"
            binding.btnDelete.visibility = View.VISIBLE
        } else {
            binding.tvTitle.text = "New schedule"
            binding.btnDelete.visibility = View.GONE
            binding.rbTime.isChecked = true
            binding.layoutTimePicker.visibility = View.VISIBLE
            binding.layoutEventPicker.visibility = View.GONE
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener { delete() }
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
    }

    private fun populate(s: Schedule) {
        binding.etName.setText(s.name)
        binding.etPlan.setText(s.actionPlan)

        if (s.triggerType == TriggerType.TIME) {
            binding.rbTime.isChecked = true
            binding.etHour.setText(s.timeHour.toString().padStart(2, '0'))
            binding.etMinute.setText(s.timeMinute.toString().padStart(2, '0'))
            binding.spinnerRepeat.setSelection(RepeatMode.values().indexOf(s.repeatMode))
            binding.layoutTimePicker.visibility = View.VISIBLE
            binding.layoutEventPicker.visibility = View.GONE
        } else {
            binding.rbEvent.isChecked = true
            val idx = s.eventTrigger?.let { EventTrigger.values().indexOf(it) } ?: 0
            binding.spinnerEvent.setSelection(idx)
            binding.layoutTimePicker.visibility = View.GONE
            binding.layoutEventPicker.visibility = View.VISIBLE
        }
    }

    private fun save() {
        val name = binding.etName.text.toString().trim()
        val plan = binding.etPlan.text.toString().trim()

        if (name.isBlank()) { binding.tilName.error = "Name is required"; return }
        binding.tilName.error = null
        if (plan.isBlank())  { binding.tilPlan.error = "Action plan is required"; return }
        binding.tilPlan.error = null

        val isTime = binding.rbTime.isChecked
        val base = existingSchedule ?: Schedule(name = "", actionPlan = "", triggerType = TriggerType.TIME)
        val updated = base.copy(
            name         = name,
            actionPlan   = plan,
            triggerType  = if (isTime) TriggerType.TIME else TriggerType.EVENT,
            timeHour     = binding.etHour.text.toString().toIntOrNull() ?: 8,
            timeMinute   = binding.etMinute.text.toString().toIntOrNull() ?: 0,
            repeatMode   = RepeatMode.values()[binding.spinnerRepeat.selectedItemPosition],
            eventTrigger = if (!isTime)
                EventTrigger.values()[binding.spinnerEvent.selectedItemPosition] else null,
        )

        if (existingSchedule != null) scheduleRepo.replace(updated) else scheduleRepo.add(updated)
        findNavController().popBackStack()
    }

    private fun delete() {
        existingSchedule?.let { s ->
            AlertDialog.Builder(requireContext())
                .setTitle("Delete \"${s.name}\"?")
                .setMessage("This schedule will be permanently removed.")
                .setPositiveButton("Delete") { _, _ ->
                    scheduleRepo.delete(s.id)
                    findNavController().popBackStack()
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
