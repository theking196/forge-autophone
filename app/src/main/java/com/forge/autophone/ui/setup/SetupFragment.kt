package com.forge.autophone.ui.setup

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.forge.autophone.AutoPhoneApp
import com.forge.autophone.data.ForgeOsConnection
import com.forge.autophone.data.ForgeOsState
import com.forge.autophone.databinding.FragmentSetupBinding
import com.forge.autophone.ui.MainActivity
import com.forge.autophone.service.AutoPhoneAccessibilityService
import com.forge.autophone.service.AutoPhoneNotificationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen 1 — Setup Wizard
 *
 * Guides the user through 3 required steps:
 *
 *   Step 1 — Enable Accessibility Service
 *             Opens android.settings.ACCESSIBILITY_SETTINGS
 *             Verified by AutoPhoneAccessibilityService.instance != null
 *
 *   Step 2 — Grant Notification Access
 *             Opens Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
 *             Verified by AutoPhoneNotificationService.instance != null
 *             Unlocks after Step 1.
 *
 *   Step 3 — Connect to Forge OS
 *             Triggers ForgeOsConnection.bind()
 *             Unlocks after Steps 1 & 2.
 *
 * The green "Ready" banner appears only when all three pass.
 */
class SetupFragment : Fragment() {

    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as AutoPhoneApp).container

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Step 1
        binding.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Step 2 — Notification access (enabled after Step 1 passes)
        binding.btnOpenNotifSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Step 3 — Forge OS (enabled after Steps 1 & 2 pass)
        binding.btnConnectForge.setOnClickListener {
            container.forgeOs.bind()
            refreshStatus()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            AutoPhoneAccessibilityService.instanceFlow.collect { refreshStatus() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            container.forgeOs.state.collect { refreshStatus() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check after user returns from any Settings screen
        viewLifecycleOwner.lifecycleScope.launch {
            delay(400)
            if (ForgeOsConnection.hasForgeOsUseApiPermission(requireContext())) {
                container.forgeOs.bind()
            }
            refreshStatus()
        }
    }

    private fun refreshStatus() {
        val accOk   = AutoPhoneAccessibilityService.instance != null
        val notifOk = AutoPhoneNotificationService.instance != null
        val forgeState = container.forgeOs.state.value
        val forgeOk = forgeState == ForgeOsState.CONNECTED

        // Step 1
        binding.step1Status.text = if (accOk) "✓ Accessibility service active" else "Not enabled"
        binding.step1Status.setTextColor(if (accOk) green() else muted())

        // Step 2 — unlocks after step 1
        binding.btnOpenNotifSettings.isEnabled = accOk
        binding.step2Status.text = when {
            notifOk -> "✓ Notification access granted"
            accOk   -> "Not granted — tap button below"
            else    -> "Complete Step 1 first"
        }
        binding.step2Status.setTextColor(if (notifOk) green() else muted())

        // Step 3 — unlocks after steps 1 & 2
        binding.btnConnectForge.isEnabled = accOk && notifOk
        binding.step3Status.text = when {
            !accOk || !notifOk                    -> "Complete Steps 1 & 2 first"
            forgeState == ForgeOsState.CONNECTED   -> "✓ Forge OS connected"
            forgeState == ForgeOsState.CONNECTING  -> "Connecting…"
            forgeState == ForgeOsState.UNAVAILABLE -> when {
                !ForgeOsConnection.isForgeOsInstalled(requireContext()) ->
                    "Forge OS app is not installed (com.forge.os)"
                !ForgeOsConnection.hasForgeOsUseApiPermission(requireContext()) ->
                    "Allow Forge OS API permission — tap Connect (system dialog)"
                else ->
                    "Cannot reach Forge OS — open Forge OS → Hub → External API → enable and approve AutoPhone"
            }
            else                                   -> "Not connected — tap Connect"
        }
        binding.step3Status.setTextColor(if (forgeOk) green() else muted())

        // Ready banner
        val allDone = accOk && notifOk && forgeOk
        binding.tvReadyBanner.visibility = if (allDone) View.VISIBLE else View.GONE
    }

    private fun green() = resources.getColor(com.forge.autophone.R.color.forge_green, null)
    private fun muted() = resources.getColor(com.forge.autophone.R.color.forge_on_surface_variant, null)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
