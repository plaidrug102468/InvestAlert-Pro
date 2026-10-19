package com.investalert.app.monitoring

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.investalert.app.data.HoldingStore
import com.investalert.app.notifications.AppNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ActiveMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        AppNotifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            HoldingStore(this).setActiveMonitoringEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        HoldingStore(this).setActiveMonitoringEnabled(true)
        ServiceCompat.startForeground(
            this,
            AppNotifications.MONITOR_NOTIFICATION_ID,
            AppNotifications.activeMonitoringNotification(this, ACTIVE_INTERVAL_MINUTES),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        if (monitorJob?.isActive != true) {
            monitorJob = scope.launch {
                while (isActive) {
                    runCatching { PriceCheckEngine.checkAll(this@ActiveMonitorService, notify = true) }
                    delay(ACTIVE_INTERVAL_MINUTES * 60_000L)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        HoldingStore(this).setActiveMonitoringEnabled(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.investalert.app.STOP_ACTIVE_MONITORING"
        const val ACTIVE_INTERVAL_MINUTES = 5
    }
}
