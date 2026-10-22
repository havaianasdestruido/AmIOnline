package com.quack.online.monitoring

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.quack.online.network.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps continuous server monitoring running while the app is not
 * visible. It is the single owner of the [MonitoringEngine]; the activity only starts and
 * stops the service, so duplicate monitoring loops can never be spawned.
 *
 * Lifecycle:
 * - onStartCommand(START_MONITORING): resolves the target off the main thread, creates the
 *   notification channel, starts the engine and calls startForeground() within seconds
 *   (required on Android 8+).
 * - onStartCommand(STOP_MONITORING): stops the engine, clears the active flag, removes the
 *   notification and stops the service.
 * - onStartCommand(UPDATE_CONFIG): re-schedules the engine loop when the interval changed.
 * - onDestroy(): defensive engine shutdown.
 *
 * START_NOT_STICKY: the system never restarts the service after the user stops it or after an
 * unexpected kill — monitoring resumes only when the user explicitly starts it again.
 */
class MonitoringService : Service() {

    companion object {
        internal const val ACTION_START_MONITORING = "com.quack.online.action.START_MONITORING"
        internal const val ACTION_STOP_MONITORING = "com.quack.online.action.STOP_MONITORING"
        internal const val ACTION_UPDATE_CONFIG = "com.quack.online.action.UPDATE_CONFIG"

        /** Creates an explicit intent to start monitoring from the activity. */
        fun startIntent(context: Context): Intent =
            Intent(context, MonitoringService::class.java).apply {
                action = ACTION_START_MONITORING
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_CLEAR_TOP
            }

        /** Creates an explicit intent to stop monitoring (also used by the notification action). */
        fun stopIntent(context: Context): Intent =
            Intent(context, MonitoringService::class.java).apply {
                action = ACTION_STOP_MONITORING
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_CLEAR_TOP
            }

        /** Notifies the running service to re-read interval/target from persisted config. */
        fun updateConfigIntent(context: Context): Intent =
            Intent(context, MonitoringService::class.java).apply {
                action = ACTION_UPDATE_CONFIG
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_CLEAR_TOP
            }
    }

    private lateinit var repository: MonitoringRepository
    private lateinit var notifications: MonitoringNotificationManager
    private lateinit var engine: MonitoringEngine
    private lateinit var serviceScope: CoroutineScope
    private lateinit var serviceJob: SupervisorJob

    override fun onCreate() {
        super.onCreate()
        repository = SharedPreferencesMonitoringRepository(this)
        notifications = MonitoringNotificationManager(this)
        notifications.ensureChannel()
        engine = MonitoringEngine(repository, notifications)
        serviceJob = SupervisorJob()
        serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MONITORING -> stopMonitoring()
            ACTION_UPDATE_CONFIG -> {
                // Interval (or target) changed while the UI was visible: reschedule the loop
                // without restarting the service or the application.
                if (engine.isRunning) engine.reschedule()
            }
            ACTION_START_MONITORING, null -> startMonitoring()
        }
        return Service.START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        engine.stop()
        super.onDestroy()
    }

    private fun startMonitoring() {
        if (engine.isRunning) return
        val config = repository.config ?: return

        // Resolve off the main thread: DNS lookup must never block the service's main thread,
        // and startForeground() must be reached within seconds.
        serviceScope.launch {
            val ip = withContext(Dispatchers.IO) {
                try {
                    NetworkUtils.resolveIpAddress(config.target)
                } catch (e: Exception) {
                    null
                }
            }

            withContext(Dispatchers.Main.immediate) {
                if (ip == null) {
                    return@withContext
                }

                repository.saveConfig(
                    config.target,
                    config.intervalMs,
                    ip,
                    config.displayName.takeIf { it.isNotEmpty() } ?: config.target,
                )

                notifications.showChecking(ip, repository.stats)
                ForegroundStarter.startForeground(
                    this@MonitoringService,
                    MonitoringNotificationManager.NOTIFICATION_ID,
                    notifications.buildNotification(MonitoringStatus.CHECKING, ip, repository.stats),
                    foregroundServiceType,
                )

                repository.monitoringActive = true
                engine.start()
            }
        }
    }

    private fun stopMonitoring() {
        engine.stop()
        repository.monitoringActive = false
        notifications.cancelNotification()
        ForegroundStarter.stopForeground(this)
        stopSelf()
    }

    /**
     * Foreground service type declared in the manifest. `dataSync` is the closest platform type
     * for ongoing, user-initiated background work when no location/camera/mic sensor is involved;
     * Android 14+ requires at least one type for targetSdk 34. Passed as a string literal so the
     * same code compiles against minSdk 24.
     */
    private val foregroundServiceType: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "dataSync" else ""
}
