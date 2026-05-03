package com.forge.autophone.ui.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.forge.autophone.databinding.FragmentToolTesterBinding
import com.forge.autophone.service.AutoPhoneAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PhoneTool(
    val name: String,
    val label: String,
    val argLabel: String?,
    val argHint: String?,
)

private val TOOLS = listOf(
    PhoneTool("readScreen",       "read_screen",        null,          null),
    PhoneTool("tapByText",        "find_and_tap",       "Text to find", "\"Send\" or \"OK\""),
    PhoneTool("tapAt",            "tap (x,y)",          "Coordinates",  "194,420"),
    PhoneTool("typeText",         "type",               "Text to type", "\"Hello world\""),
    PhoneTool("swipe",            "swipe",              "Direction + px","up 600"),
    PhoneTool("scroll",           "scroll",             "Direction",    "down"),
    PhoneTool("launchApp",        "launch_app",         "Package/label","com.instagram.android"),
    PhoneTool("goBack",           "go_back",            null,          null),
    PhoneTool("goHome",           "go_home",            null,          null),
    PhoneTool("openNotifications","open_notifications", null,          null),
    PhoneTool("screenshot",       "screenshot",         null,          null),
)

/**
 * Screen 4 — Tool Tester
 *
 * Lets the user or developer fire any AutoPhone tool manually without
 * going through Forge OS. Useful for testing the accessibility service,
 * verifying coordinates, and debugging tool output.
 *
 * The spinner maps to [TOOLS]; input field appears only for tools with args.
 * Output is displayed in a scrollable text view below.
 */
class ToolTesterFragment : Fragment() {

    private var _binding: FragmentToolTesterBinding? = null
    private val binding get() = _binding!!

    private var selectedIdx = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        _binding = FragmentToolTesterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val labels = TOOLS.map { "autophone_${it.label}" }
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTool.adapter = spinnerAdapter

        binding.spinnerTool.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                selectedIdx = pos
                val tool = TOOLS[pos]
                if (tool.argLabel != null) {
                    binding.tilArg.visibility = View.VISIBLE
                    binding.tilArg.hint = tool.argLabel
                    binding.etArg.hint = tool.argHint ?: ""
                } else {
                    binding.tilArg.visibility = View.GONE
                    binding.etArg.setText("")
                }
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        binding.btnRun.setOnClickListener { runSelectedTool() }

        // Show service status in the Run button
        viewLifecycleOwner.lifecycleScope.launch {
            AutoPhoneAccessibilityService.instanceFlow.collect { svc ->
                binding.btnRun.isEnabled = svc != null
                binding.tvServiceStatus.text = if (svc != null) "Accessibility active — ready" else "Accessibility not active — enable in Setup"
            }
        }
    }

    private fun runSelectedTool() {
        val tool = TOOLS[selectedIdx]
        val arg  = binding.etArg.text.toString().trim()
        val svc  = AutoPhoneAccessibilityService.instance

        if (svc == null) {
            appendOutput("ERROR: Accessibility service not active", false)
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnRun.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                when (tool.name) {
                    "readScreen"        -> svc.readScreen()
                    "tapByText"         -> svc.tapByText(stripQuotes(arg))
                    "tapAt"             -> {
                        val parts = arg.split(",").map { it.trim().toInt() }
                        svc.tapAt(parts[0], parts[1])
                    }
                    "typeText"          -> svc.typeText(stripQuotes(arg))
                    "swipe"             -> {
                        val parts = arg.trim().split(" ")
                        svc.swipe(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: 600)
                    }
                    "scroll"            -> svc.scroll(arg.ifBlank { "down" })
                    "launchApp"         -> svc.launchApp(stripQuotes(arg))
                    "goBack"            -> svc.goBack()
                    "goHome"            -> svc.goHome()
                    "openNotifications" -> svc.openNotifications()
                    "screenshot"        -> svc.screenshot()
                    else                -> """{"ok":false,"error":"unknown tool"}"""
                }
            }.getOrElse { """{"ok":false,"error":"${it.message}"}""" }

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.btnRun.isEnabled = AutoPhoneAccessibilityService.instance != null
                val ok = result.contains("\"ok\":true")
                appendOutput("autophone_${tool.label}${if (arg.isNotBlank()) " $arg" else ""}\n→ $result", ok)
            }
        }
    }

    private fun appendOutput(text: String, ok: Boolean) {
        val prefix = if (ok) "✓" else "✗"
        val current = binding.tvOutput.text.toString()
        val newText = "$prefix $text\n${if (current.isNotBlank()) "\n$current" else ""}".take(8000)
        binding.tvOutput.text = newText
    }

    private fun stripQuotes(s: String) = s.trim().trimStart('"').trimEnd('"')

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
