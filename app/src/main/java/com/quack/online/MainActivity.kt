package com.quack.online

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import com.google.android.material.color.DynamicColors
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.quack.online.databinding.ActivityMainBinding
import com.quack.online.monitoring.MonitoringConfig
import com.quack.online.monitoring.MonitoringService
import com.quack.online.monitoring.SharedPreferencesMonitoringRepository
import com.quack.online.monitoring.MonitoringStatus
import com.quack.online.network.NetworkUtils
import com.quack.online.network.PingResult
import com.quack.online.network.PingRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private companion object {
        // The rendered console may grow past the retained size before it is re-rendered from
        // the bounded snapshot, which keeps TextView content and memory use bounded.
        const val MAX_RENDERED_CONSOLE_CHARS = 64 * 1024
        const val REQUEST_POST_NOTIFICATIONS = 1001
        const val THEME_MATERIAL_YOU = 0
        const val THEME_CLASSIC = 1
        const val THEME_AMOLED = 2
    }

    private lateinit var binding: ActivityMainBinding
    private val pingRunner = PingRunner()
    private var pingJob: Job? = null
    private var isPinging = false

    // Persistent monitoring state (configuration + statistics) shared with MonitoringService.
    private val repository = SharedPreferencesMonitoringRepository(this)
    private var monitoringResolveJob: Job? = null

    // Retained console output is bounded (500 lines / 32 KiB); the TextView is re-rendered
    // from it instead of growing forever.
    private val consoleLog = ConsoleLog()
    private val pendingConsoleLines = StringBuilder()
    private var isConsoleUpdateScheduled = false
    private var renderedConsoleChars = 0
    private var consoleScrollPending = false
    private var consoleScrollAnchorY = 0

    // Hoisted to avoid allocating a new anonymous Runnable on every console update
    private val scrollRunnable = Runnable {
        consoleScrollPending = false
        // Do not yank the view down if the user scrolled up while the frame was pending.
        if (binding.scrollConsole.scrollY >= consoleScrollAnchorY) {
            binding.scrollConsole.fullScroll(View.FOCUS_DOWN)
        }
    }

    private val updateConsoleRunnable = Runnable {
        isConsoleUpdateScheduled = false
        if (pendingConsoleLines.isNotEmpty()) {
            val scrollView = binding.scrollConsole
            // Only follow the output when the user is already reading the bottom of the log.
            val shouldFollowOutput = !scrollView.canScrollVertically(1)
            consoleScrollAnchorY = scrollView.scrollY
            if (renderedConsoleChars + pendingConsoleLines.length > MAX_RENDERED_CONSOLE_CHARS) {
                // Re-render from the bounded snapshot instead of letting the TextView grow forever.
                binding.tvConsoleOutput.text = consoleLog.snapshot()
                renderedConsoleChars = binding.tvConsoleOutput.text?.length ?: 0
            } else {
                // Pass StringBuilder directly (as CharSequence) to avoid String allocation overhead via .toString()
                binding.tvConsoleOutput.append(pendingConsoleLines)
                renderedConsoleChars += pendingConsoleLines.length
            }
            pendingConsoleLines.clear()
            if (shouldFollowOutput) scheduleConsoleScroll()
        }
    }

    private val presetTargets = listOf(
        "1.1.1.1",
        "2606:4700:4700::1111",
        "8.8.8.8",
        "2001:4860:4860::8888",
        "Custom"
    )

    // Continuous monitoring interval presets (milliseconds). Default: 1 minute.
    private val intervalLabels = listOf(
        getString(R.string.interval_15_seconds),
        getString(R.string.interval_30_seconds),
        getString(R.string.interval_1_minute),
        getString(R.string.interval_2_minutes),
        getString(R.string.interval_5_minutes),
        getString(R.string.interval_10_minutes),
        getString(R.string.interval_custom),
    )
    private val intervalValuesMs = listOf(
        15_000L, 30_000L, 60_000L, 120_000L, 300_000L, 600_000L, -1L
    )
    private val MIN_INTERVAL_SECONDS = MonitoringConfig.MIN_INTERVAL_MS / 1000

    private val themePreferences by lazy { getSharedPreferences("appearance", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val selectedTheme = themePreferences.getInt("theme", THEME_MATERIAL_YOU)
        setTheme(when (selectedTheme) {
            THEME_CLASSIC -> R.style.Theme_AmIOnline_Classic
            THEME_AMOLED -> R.style.Theme_AmIOnline_Amoled
            else -> R.style.Theme_AmIOnline
        })
        super.onCreate(savedInstanceState)
        if (selectedTheme == THEME_MATERIAL_YOU) DynamicColors.applyToActivityIfAvailable(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbarAndDrawer()
        setupPresetsSpinner()
        setupButtons()
        setupMonitoring()
        setupAboutPage()
        setupBackHandling()
    }

    override fun onResume() {
        super.onResume()
        repository.registerListener(monitoringStateListener)
        updateMonitoringUi()
        // If monitoring was explicitly started but the system killed the service process,
        // restart it so monitoring resumes. Configuration and counters are persisted.
        val notificationsAvailable = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (repository.monitoringActive && notificationsAvailable) {
            ContextCompat.startForegroundService(this, MonitoringService.startIntent(this))
        }
    }

    override fun onPause() {
        super.onPause()
        repository.unregisterListener(monitoringStateListener)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                attemptStartMonitoring()
            } else {
                Toast.makeText(this, R.string.monitoring_notification_permission_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    private val monitoringStateListener = { updateMonitoringUi() }

    /* ---------------------------------------------------------------- toolbar/drawer ---------------------------------------------------------------- */

    private fun setupToolbarAndDrawer() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.title_ping)

        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.menu_ping,
            R.string.menu_about
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_ping -> showPingPage()
                R.id.nav_about -> showAboutPage()
                R.id.nav_theme -> showThemePicker()
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
        binding.navView.setCheckedItem(R.id.nav_ping)
    }

    private fun showPingPage() {
        binding.layoutPingPage.visibility = View.VISIBLE
        binding.layoutAboutPage.visibility = View.GONE
        supportActionBar?.title = getString(R.string.title_ping)
        binding.navView.setCheckedItem(R.id.nav_ping)
    }

    private fun showAboutPage() {
        binding.layoutPingPage.visibility = View.GONE
        binding.layoutAboutPage.visibility = View.VISIBLE
        supportActionBar?.title = getString(R.string.title_about)
        binding.navView.setCheckedItem(R.id.nav_about)
    }

    private fun showThemePicker() {
        val choices = arrayOf(
            getString(R.string.theme_material_you),
            getString(R.string.theme_classic),
            getString(R.string.theme_amoled)
        )
        val currentTheme = themePreferences.getInt("theme", THEME_MATERIAL_YOU)
        AlertDialog.Builder(this)
            .setTitle(R.string.theme_dialog_title)
            .setSingleChoiceItems(choices, currentTheme) { dialog, selection ->
                if (selection != currentTheme) {
                    themePreferences.edit().putInt("theme", selection).apply()
                    dialog.dismiss()
                    recreate()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /* ---------------------------------------------------------------- destination selection ---------------------------------------------------------------- */

    private fun setupPresetsSpinner() {
        val adapterNames = listOf(
            getString(R.string.preset_cf_v4),
            getString(R.string.preset_cf_v6),
            getString(R.string.preset_google_v4),
            getString(R.string.preset_google_v6),
            getString(R.string.preset_custom)
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, adapterNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPresets.adapter = adapter

        binding.spinnerPresets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isCustom = position == presetTargets.size - 1
                binding.tilCustomTarget.visibility = if (isCustom) View.VISIBLE else View.GONE
                if (!isCustom) {
                    resolveAndDisplayTarget(presetTargets[position])
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Resolve the initially selected target so the UI shows its IP right away.
        resolveAndDisplayTarget(presetTargets[0])
    }

    private fun getSelectedTarget(): String {
        val selectedPosition = binding.spinnerPresets.selectedItemPosition
        return if (selectedPosition == presetTargets.size - 1) {
            binding.etCustomTarget.text.toString().trim()
        } else {
            presetTargets[selectedPosition]
        }
    }

    /**
     * Resolves the target's IP on a background thread and shows it in the monitoring section.
     * Literal IPs are displayed directly; host names show "name (ip)". Resolution failures are
     * shown gracefully — the address is still usable, but monitoring cannot start until it resolves.
     */
    private fun resolveAndDisplayTarget(target: String) {
        monitoringResolveJob?.cancel()
        monitoringResolveJob = lifecycleScope.launch {
            val ip = withContext(Dispatchers.IO) {
                try {
                    NetworkUtils.resolveIpAddress(target)
                } catch (e: Exception) {
                    null
                }
            }
            withContext(Dispatchers.Main) {
                binding.tvMonitoringTarget.text = buildString {
                    append(target)
                    if (ip != null && ip != target) {
                        append(" ($ip)")
                    }
                }
                // Persist the resolved IP so the service can use it without re-resolving.
                // A failed resolution is intentionally not persisted: pinging the raw hostname
                // later would count a DNS failure as an attempt.
                if (ip != null) {
                    val current = repository.config
                    if (current != null) {
                        repository.saveConfig(current.target, current.intervalMs, ip, current.displayName)
                    }
                }
            }
        }
    }

    /* ---------------------------------------------------------------- ping (unchanged behaviour) ---------------------------------------------------------------- */

    private fun setupButtons() {
        binding.btnActionPing.setOnClickListener {
            if (isPinging) {
                stopPing()
            } else {
                startPing()
            }
        }

        binding.btnClearLog.setOnClickListener {
            consoleLog.clear()
            binding.tvConsoleOutput.text = ""
            pendingConsoleLines.clear()
        }

        binding.btnCopyLog.setOnClickListener {
            val textToCopy = binding.tvConsoleOutput.text.toString()
            if (textToCopy.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Ping Output", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupAboutPage() {
        binding.tvGithubUrl.setOnClickListener {
            val url = getString(R.string.github_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else if (binding.layoutAboutPage.visibility == View.VISIBLE) {
                    showPingPage()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun startPing() {
        val target = getSelectedTarget()
        if (target.isEmpty()) {
            Toast.makeText(this, "Please enter a valid target host or IP", Toast.LENGTH_SHORT).show()
            return
        }

        isPinging = true
        binding.btnActionPing.text = getString(R.string.btn_stop)
        updateStatusBadge(getString(R.string.status_pinging), R.color.status_pinging)

        appendConsole("--- Pinging $target ---")

        pingJob = lifecycleScope.launch {
            pingRunner.executePing(target).collect { result ->
                when (result) {
                    is PingResult.Output -> {
                        appendConsole(result.line)
                    }
                    is PingResult.Finished -> {
                        if (result.isSuccess) {
                            updateStatusBadge(getString(R.string.status_online), R.color.status_online)
                            appendConsole("--- Completed: ONLINE ---")
                        } else {
                            updateStatusBadge(getString(R.string.status_offline), R.color.status_offline)
                            appendConsole("--- Completed: OFFLINE (Exit code ${result.exitCode}) ---")
                        }
                        resetPingState()
                    }
                    is PingResult.Error -> {
                        updateStatusBadge(getString(R.string.status_offline), R.color.status_offline)
                        appendConsole("--- Error: ${result.message} ---")
                        resetPingState()
                    }
                }
            }
        }
    }

    private fun stopPing() {
        pingJob?.cancel()
        pingJob = null
        appendConsole("--- Ping stopped by user ---")
        updateStatusBadge(getString(R.string.status_idle), R.color.status_idle)
        resetPingState()
    }

    private fun resetPingState() {
        isPinging = false
        binding.btnActionPing.text = getString(R.string.btn_ping)
    }

    private fun updateStatusBadge(text: String, colorRes: Int) {
        binding.tvStatusBadge.text = text
        binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun scheduleConsoleScroll() {
        if (consoleScrollPending) return
        consoleScrollPending = true
        // Post the scroll to execute after the text view layout updates
        binding.scrollConsole.post(scrollRunnable)
    }

    private fun appendConsole(text: String) {
        // Keep retained output bounded; the TextView is re-rendered from this log when needed.
        consoleLog.append(text)
        pendingConsoleLines.append(text).append('\n')

        if (!isConsoleUpdateScheduled) {
            isConsoleUpdateScheduled = true
            binding.tvConsoleOutput.post(updateConsoleRunnable)
        }
    }

    /* ---------------------------------------------------------------- continuous monitoring ---------------------------------------------------------------- */

    private fun setupMonitoring() {
        val intervalAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervalLabels)
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMonitoringInterval.adapter = intervalAdapter

        // Select the persisted interval preset.
        val persistedInterval = repository.config?.intervalMs ?: MonitoringConfig.DEFAULT_INTERVAL_MS
        val presetIndex = intervalValuesMs.indexOf(persistedInterval)
        binding.spinnerMonitoringInterval.setSelection(if (presetIndex < 0) 2 else presetIndex) // default to 1 minute

        binding.spinnerMonitoringInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isCustom = position == intervalLabels.size - 1
                binding.tilMonitoringCustomInterval.visibility = if (isCustom) View.VISIBLE else View.GONE
                if (!isCustom) {
                    saveMonitoringInterval(intervalValuesMs[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.etMonitoringCustomInterval.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyCustomInterval()
                true
            } else false
        }

        binding.btnMonitoringAction.setOnClickListener {
            if (repository.monitoringActive) {
                stopMonitoring()
            } else {
                attemptStartMonitoring()
            }
        }

        binding.btnMonitoringResetStats.setOnClickListener {
            repository.resetStats()
            Toast.makeText(this, R.string.monitoring_statistics_reset, Toast.LENGTH_SHORT).show()
        }

        updateMonitoringUi()
    }

    private fun applyCustomInterval() {
        val text = binding.etMonitoringCustomInterval.text.toString().trim()
        val seconds = text.toIntOrNull() ?: run {
            Toast.makeText(this, getString(R.string.monitoring_invalid_interval), Toast.LENGTH_SHORT).show()
            return@applyCustomInterval
        }
        val ms = seconds * 1000L
        if (ms < MonitoringConfig.MIN_INTERVAL_MS) {
            Toast.makeText(this, getString(R.string.monitoring_interval_too_short, MIN_INTERVAL_SECONDS), Toast.LENGTH_LONG).show()
            return@applyCustomInterval
        }
        saveMonitoringInterval(ms)
        binding.etMonitoringCustomInterval.setText("")
    }

    private fun saveMonitoringInterval(intervalMs: Long) {
        val current = repository.config
        if (current == null) return
        repository.saveConfig(current.target, intervalMs, current.resolvedIp, current.displayName)
        // No application restart needed: the engine re-reads the interval every cycle, and if
        // monitoring is running we ask it to reschedule immediately.
        if (repository.monitoringActive) {
            ContextCompat.startForegroundService(this, MonitoringService.updateConfigIntent(this))
        }
    }

    private fun attemptStartMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS,
            )
            return
        }
        startMonitoring()
    }

    private fun startMonitoring() {
        val target = getSelectedTarget()
        if (target.isEmpty()) {
            Toast.makeText(this, getString(R.string.monitoring_target_required), Toast.LENGTH_SHORT).show()
            return
        }

        // Resolve the address before starting. Unresolvable targets are rejected with a clear
        // error instead of silently counting DNS failures as probes.
        monitoringResolveJob?.cancel()
        monitoringResolveJob = lifecycleScope.launch {
            val ip = withContext(Dispatchers.IO) {
                try {
                    NetworkUtils.resolveIpAddress(target)
                } catch (e: Exception) {
                    null
                }
            }
            withContext(Dispatchers.Main) {
                if (ip == null) {
                    Toast.makeText(this@MainActivity,
                        getString(R.string.error_dns_resolution, target), Toast.LENGTH_LONG).show()
                    return@withContext
                }
                val current = repository.config
                repository.saveConfig(target, current?.intervalMs ?: MonitoringConfig.DEFAULT_INTERVAL_MS, ip,
                    current?.displayName ?: target)
                repository.monitoringActive = true
                ContextCompat.startForegroundService(this@MainActivity, MonitoringService.startIntent(this@MainActivity))
                Toast.makeText(this@MainActivity, getString(R.string.monitoring_started, target), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopMonitoring() {
        if (!repository.monitoringActive) return
        repository.monitoringActive = false
        ContextCompat.startForegroundService(this, MonitoringService.stopIntent(this))
        Toast.makeText(this, R.string.monitoring_stopped, Toast.LENGTH_SHORT).show()
    }

    private fun updateMonitoringUi() {
        val active = repository.monitoringActive
        binding.btnMonitoringAction.text = if (active) getString(R.string.btn_stop_monitoring) else getString(R.string.btn_start_monitoring)
        binding.btnMonitoringAction.isEnabled = true

        val config = repository.config
        binding.tvMonitoringTarget.text = buildString {
            append(config?.target ?: getString(R.string.monitoring_target_none))
            val ip = config?.resolvedIp
            if (ip != null && ip != config?.target) append(" ($ip)")
        }

        val stats = repository.stats
        binding.tvMonitoringStats.text = getString(R.string.monitoring_stats_summary,
            stats.totalAttempts, stats.successfulAttempts, stats.failedAttempts)

        binding.tvMonitoringLastCheck.text = if (stats.lastCheckMs > 0L) {
            getString(R.string.monitoring_last_check, java.text.DateFormat.getTimeInstance().format(java.util.Date(stats.lastCheckMs)))
        } else {
            getString(R.string.monitoring_never)
        }

        val statusLine = when {
            active -> {
                val ip = config?.resolvedIp ?: config?.target ?: ""
                getString(R.string.monitoring_status_active, repository.lastStatus, ip)
            }
            else -> getString(R.string.monitoring_status_inactive)
        }
        binding.tvMonitoringStatus.text = statusLine
        val statusColorRes = if (!active) R.color.status_idle else when (repository.lastStatus) {
            MonitoringStatus.ONLINE -> R.color.status_online
            MonitoringStatus.OFFLINE, MonitoringStatus.ERROR -> R.color.status_offline
            MonitoringStatus.CHECKING -> R.color.status_pinging
            else -> R.color.status_idle
        }
        binding.tvMonitoringStatus.setTextColor(ContextCompat.getColor(this, statusColorRes))

        binding.tvMonitoringBackgroundIndicator.visibility = if (active) View.VISIBLE else View.GONE

        // Manual ping keeps working while monitoring is active; they run independently.
        binding.btnActionPing.isEnabled = true
    }
}
