package com.forge.autophone.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.forge.autophone.R
import com.forge.autophone.data.CapturedNotification
import com.forge.autophone.databinding.ItemNotificationBinding

/**
 * Adapter for the live notifications list on the Dashboard.
 *
 * Each card shows: app name, title, body preview, time, and action buttons:
 *   - Dismiss — cancels the notification
 *   - Reply   — visible only when canReply == true; triggers the callback
 */
class NotificationsAdapter(
    private val onDismiss: (CapturedNotification) -> Unit,
    private val onReply:   (CapturedNotification) -> Unit,
) : ListAdapter<CapturedNotification, NotificationsAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemNotificationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(n: CapturedNotification) {
            b.tvApp.text   = n.appLabel
            b.tvTitle.text = n.title.ifBlank { n.appLabel }
            b.tvBody.text  = n.body
            b.tvTime.text  = n.timeLabel

            b.btnDismiss.setOnClickListener { onDismiss(n) }
            b.btnReply.visibility = if (n.canReply)
                android.view.View.VISIBLE else android.view.View.GONE
            b.btnReply.setOnClickListener { onReply(n) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CapturedNotification>() {
            override fun areItemsTheSame(a: CapturedNotification, b: CapturedNotification) =
                a.key == b.key
            override fun areContentsTheSame(a: CapturedNotification, b: CapturedNotification) =
                a == b
        }
    }
}
