package com.forge.autophone.ui.log

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.forge.autophone.data.ActionEntry
import com.forge.autophone.databinding.ItemActionLogBinding

class ActionLogAdapter : ListAdapter<ActionEntry, ActionLogAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemActionLogBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(entry: ActionEntry) {
            b.tvTool.text   = entry.tool
            b.tvArgs.text   = entry.args.ifBlank { "—" }
            b.tvOutput.text = entry.output
            b.tvTime.text   = entry.timeLabel
            b.tvMs.text     = if (entry.durationMs > 0) "${entry.durationMs}ms" else ""

            val statusColor = if (entry.ok)
                b.root.context.getColor(com.forge.autophone.R.color.forge_green)
            else
                b.root.context.getColor(com.forge.autophone.R.color.forge_red)
            b.viewStatus.setBackgroundColor(statusColor)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemActionLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ActionEntry>() {
            override fun areItemsTheSame(a: ActionEntry, b: ActionEntry) = a.id == b.id
            override fun areContentsTheSame(a: ActionEntry, b: ActionEntry) = a == b
        }
    }
}
