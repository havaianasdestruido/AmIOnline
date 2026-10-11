package com.quack.online

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.quack.online.databinding.ActivityMainBinding
import com.quack.online.network.PingResult
import com.quack.online.network.PingRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val pingRunner = PingRunner()
    private var pingJob: Job? = null
    private var isPinging = false

    private val presetTargets = listOf(
        "1.1.1.1",
        "2606:4700:4700::1111",
        "8.8.8.8",
        "2001:4860:4860::8888",
        "Custom"
    )

    private val scrollRunnable = Runnable {
        binding.scrollConsole.fullScroll(View.FOCUS_DOWN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbarAndDrawer()
        setupPresetsSpinner()
        setupButtons()
        setupAboutPage()
        setupBackHandling()
    }

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
                if (position == presetTargets.size - 1) { // Custom target option
                    binding.tilCustomTarget.visibility = View.VISIBLE
                } else {
                    binding.tilCustomTarget.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.btnActionPing.setOnClickListener {
            if (isPinging) {
                stopPing()
            } else {
                startPing()
            }
        }

        binding.btnClearLog.setOnClickListener {
            binding.tvConsoleOutput.text = ""
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

    private fun getSelectedTarget(): String {
        val selectedPosition = binding.spinnerPresets.selectedItemPosition
        return if (selectedPosition == presetTargets.size - 1) {
            binding.etCustomTarget.text.toString().trim()
        } else {
            presetTargets[selectedPosition]
        }
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

    private fun appendConsole(text: String) {
        binding.tvConsoleOutput.append(text + "\n")

        // Debounce the scroll to avoid multiple layout passes if many lines arrive at once
        binding.scrollConsole.removeCallbacks(scrollRunnable)
        binding.scrollConsole.post(scrollRunnable)
    }
}
