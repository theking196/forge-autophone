package com.forge.autophone.ui.dashboard

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.forge.autophone.AutoPhoneApp
import com.forge.autophone.data.CapturedNotification
import com.forge.autophone.data.ForgeOsConnection
import com.forge.autophone.data.ForgeOsState
import com.forge.autophone.databinding.FragmentDashboardBinding
import com.forge.autophone.service.AutoPhoneAccessibilityService
import com.forge.autophone.service.AutoPhoneNotificationService
import com.forge.autophone.ui.log.ActionLogAdapter
import com.forge.autophone.ui.notifications.NotificationsAdapter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Screen 2 — Dashboard
 *
 * Shows:
 *   ▸ System health banner (Accessibility + Forge OS + Notification listener)
 *   ▸ Live stats (actions today, success rate)
 *   ▸ Last 6 action log entries
 *   ▸ Live notification feed from [NotificationRepository]
 *       — each card has Dismiss and Reply (when supported) buttons
 *       — a "Grant access" chip appears when Notification Access is not yet granted
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as AutoPhoneApp).container
    private lateinit var recentAdapter: ActionLogAdapter
    private lateinit var notifAdapter: NotificationsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Recent action log list
        recentAdapter = ActionLogAdapter()
        binding.rvRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecent.adapter = recentAdapter

        // Live notifications list
        notifAdapter = NotificationsAdapter(
            onDismiss = { n -> dismissNotification(n) },
            onReply   = { n -> showReplyDialog(n) },
        )
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotifications.adapter = notifAdapter

        // "Grant Notification Access" button
        binding.btnGrantNotifAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Accessibility state
        viewLifecycleOwner.lifecycleScope.launch {
            AutoPhoneAccessibilityService.instanceFlow.collect { svc ->
                val ok = svc != null
                binding.tvAccStatus.text = if (ok) "Accessibility · Active" else "Accessibility · Off"
                binding.tvAccStatus.setTextColor(statusColor(ok))
                refreshBanner()
            }
        }

        // Forge OS connection state
        viewLifecycleOwner.lifecycleScope.launch {
            container.forgeOs.state.collect { state ->
                val ok = state == ForgeOsState.CONNECTED
                binding.tvForgeStatus.text = when (state) {
                    ForgeOsState.CONNECTED    -> "Forge OS · Connected"
                    ForgeOsState.CONNECTING   -> "Forge OS · Connecting…"
                    ForgeOsState.UNAVAILABLE  -> when {
                        !ForgeOsConnection.isForgeOsInstalled(requireContext()) ->
                            "Forge OS · Not installed"
                        !ForgeOsConnection.hasForgeOsUseApiPermission(requireContext()) ->
                            "Forge OS · Allow API permission (permission dialog)"
                        else ->
                            "Forge OS · Unreachable (enable External API in Forge OS)"
                    }
                    ForgeOsState.DISCONNECTED -> "Forge OS · Disconnected"
                }
                binding.tvForgeStatus.setTextColor(statusColor(ok))
                refreshBanner()
            }
        }

        // Action log stats + recent list
        viewLifecycleOwner.lifecycleScope.launch {
            container.actionLog.entries.collect { entries ->
                val log = container.actionLog
                binding.tvStatActions.text = log.todayCount.toString()
                binding.tvStatSuccess.text = "${log.successRate}%"
                recentAdapter.submitList(entries.take(6))
            }
        }

        // Live notifications — combine the notification list with the listener's
        // active/inactive state so the UI updates immediately when either changes.
        viewLifecycleOwner.lifecycleScope.launch {
            container.notificationRepo.notifications.collect { list ->
                updateNotificationsSection(list)
            }
        }
    }

    // ── Notifications UI ──────────────────────────────────────────────────────

    private fun updateNotificationsSection(list: List<CapturedNotification>) {
        val listenerActive = AutoPhoneNotificationService.instance != null
        binding.tvNotifStatus.text = when {
            !listenerActive -> "Notification listener not active"
            list.isEmpty()  -> "No notifications"
            else            -> "${list.size} notification${if (list.size != 1) "s" else ""}"
        }
        binding.tvNotifStatus.setTextColor(statusColor(listenerActive))
        binding.btnGrantNotifAccess.visibility =
            if (!listenerActive) View.VISIBLE else View.GONE
        binding.rvNotifications.visibility =
            if (listenerActive && list.isNotEmpty()) View.VISIBLE else View.GONE
        binding.tvNotifEmpty.visibility =
            if (listenerActive && list.isEmpty()) View.VISIBLE else View.GONE
        notifAdapter.submitList(list.take(10))
    }

    private fun dismissNotification(n: CapturedNotification) {
        val svc = AutoPhoneNotificationService.instance
        if (svc == null) {
            Snackbar.make(binding.root, "Notification listener not active", Snackbar.LENGTH_SHORT).show()
            return
        }
        val ok = svc.dismiss(n.key)
        if (!ok) Snackbar.make(binding.root, "Dismiss failed", Snackbar.LENGTH_SHORT).show()
    }

    private fun showReplyDialog(n: CapturedNotification) {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Type a reply…"
            setSingleLine(false)
            maxLines = 4
            setPadding(48, 24, 48, 8)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Reply to ${n.appLabel}")
            .setMessage("\"${n.title}\"")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isBlank()) return@setPositiveButton
                val svc = AutoPhoneNotificationService.instance
                if (svc == null) {
                    Snackbar.make(binding.root, "Listener not active", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val ok = svc.reply(n.key, text)
                Snackbar.make(binding.root,
                    if (ok) "Reply sent" else "Reply failed — action may have expired",
                    Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun refreshBanner() {
        val accOk   = AutoPhoneAccessibilityService.instance != null
        val forgeOk = container.forgeOs.state.value == ForgeOsState.CONNECTED
        val ntfOk   = AutoPhoneNotificationService.instance != null
        val allOk   = accOk && forgeOk && ntfOk
        binding.tvBanner.text = when {
            allOk       -> "✓ All systems operational"
            !accOk      -> "⚠ Enable accessibility service"
            !forgeOk    -> "⚠ Forge OS not connected"
            else        -> "⚠ Grant notification access"
        }
        binding.tvBanner.setTextColor(statusColor(allOk))
    }

    private fun statusColor(ok: Boolean) = if (ok)
        resources.getColor(com.forge.autophone.R.color.forge_green, null)
    else
        resources.getColor(com.forge.autophone.R.color.forge_orange, null)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
