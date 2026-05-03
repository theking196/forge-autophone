package com.forge.autophone.ui.schedules

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.forge.autophone.R
import com.forge.autophone.data.Schedule
import com.forge.autophone.data.ScheduleRepository
import com.forge.autophone.databinding.ItemScheduleBinding

/**
 * RecyclerView adapter for the Schedules list.
 *
 * Call [setRunningEntries] whenever [ScheduleRepository.runningSchedules] emits
 * a new value. The adapter then shows an animated "Running…" chip on any card
 * whose schedule id appears in the running set. All other cards are rendered normally.
 */
class SchedulesAdapter(
    private val onToggle: (Schedule, Boolean) -> Unit,
    private val onEdit:   (Schedule) -> Unit,
    private val onRunNow: (Schedule) -> Unit,
) : ListAdapter<Schedule, SchedulesAdapter.VH>(DIFF) {

    private var runningEntries: List<ScheduleRepository.RunningEntry> = emptyList()

    /** Called from the Fragment whenever the runningSchedules flow emits. */
    fun setRunningEntries(entries: List<ScheduleRepository.RunningEntry>) {
        runningEntries = entries
        notifyItemRangeChanged(0, itemCount, PAYLOAD_RUNNING)
    }

    inner class VH(private val b: ItemScheduleBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(s: Schedule, runningEntry: ScheduleRepository.RunningEntry?) {
            b.tvName.text    = s.name
            b.tvTrigger.text = s.triggerLabel
            b.tvLastRun.text = s.lastRunLabel
            b.tvRunCount.text =
                "${s.runCount} run${if (s.runCount != 1) "s" else ""}"
            b.tvPlan.text =
                s.actionPlan.take(80) + if (s.actionPlan.length > 80) "…" else ""

            b.switchEnabled.setOnCheckedChangeListener(null)
            b.switchEnabled.isChecked = s.isEnabled
            b.switchEnabled.setOnCheckedChangeListener { _, checked -> onToggle(s, checked) }

            b.btnRunNow.setOnClickListener { onRunNow(s) }
            b.root.setOnClickListener { onEdit(s) }

            applyRunningState(runningEntry)
        }

        fun applyRunningState(runningEntry: ScheduleRepository.RunningEntry?) {
            val running = runningEntry != null
            b.chipRunning.visibility = if (running) View.VISIBLE else View.GONE
            if (running) {
                b.chipRunning.text =
                    "Running: ${runningEntry!!.planSummary.take(32)}…"
            }
            // Dim the Run-now button while already running
            b.btnRunNow.isEnabled = !running
            b.btnRunNow.alpha = if (running) 0.4f else 1f

            // Pulse the card border orange while running
            val strokeColor = if (running)
                b.root.context.getColor(R.color.forge_orange)
            else
                b.root.context.getColor(R.color.forge_border)
            b.root.strokeColor = strokeColor
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = getItem(position)
        holder.bind(s, runningEntries.firstOrNull { it.scheduleId == s.id })
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: List<Any>) {
        if (payloads.contains(PAYLOAD_RUNNING)) {
            val s = getItem(position)
            holder.applyRunningState(runningEntries.firstOrNull { it.scheduleId == s.id })
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    companion object {
        private const val PAYLOAD_RUNNING = "running_state"

        val DIFF = object : DiffUtil.ItemCallback<Schedule>() {
            override fun areItemsTheSame(a: Schedule, b: Schedule) = a.id == b.id
            override fun areContentsTheSame(a: Schedule, b: Schedule) = a == b
        }
    }
}
