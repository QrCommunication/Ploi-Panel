package com.qrcommunication.ploipanel

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.qrcommunication.ploipanel.widget.WidgetRefresh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Best-effort CPU/RAM/disk threshold alerts from Ploi Monitoring. Same limits as local checks:
 * WorkManager's 15-minute floor, no alert while the phone is offline or restricted, and Ploi
 * samples may themselves be delayed.
 */
internal object ThresholdAlerts {
    private const val PERIODIC = "ploi-threshold-alerts"
    internal const val CHANNEL_ID = "monitoring_thresholds"

    fun sync(context: Context) {
        val store = ThresholdAlertStore(SharedPreferencesProfilePrefs(context))
        val work = WorkManager.getInstance(context)
        if (store.rules().isEmpty()) {
            work.cancelUniqueWork(PERIODIC)
        } else {
            work.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ThresholdAlertWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            )
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val display = WidgetRefresh.localizedContext(context)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, display.getString(R.string.threshold_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun notify(context: Context, rule: ThresholdRule, transition: ThresholdTransition) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val display = WidgetRefresh.localizedContext(context)
        val metric = display.getString(
            when (transition.metric) {
                AlertMetric.CPU -> R.string.metric_cpu
                AlertMetric.RAM -> R.string.metric_ram
                AlertMetric.DISK -> R.string.metric_disk
            }
        )
        val value = transition.value.toInt()
        val (title, text) = when (transition.event) {
            ThresholdEvent.BREACHED -> display.getString(R.string.threshold_breached_title, rule.serverName, metric) to
                display.getString(R.string.threshold_breached_text, metric, value, transition.threshold)
            ThresholdEvent.RECOVERED -> display.getString(R.string.threshold_recovered_title, rule.serverName, metric) to
                display.getString(R.string.threshold_recovered_text, metric, value)
        }
        val id = (rule.key + transition.metric.name).hashCode()
        val launch = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(launch)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, notification)
    }
}

/** Reads the latest Ploi sample of each watched server once and notifies only on state edges. */
class ThresholdAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val prefs = SharedPreferencesProfilePrefs(context)
        val store = ThresholdAlertStore(prefs)
        val rules = store.rules()
        if (rules.isEmpty()) {
            ThresholdAlerts.sync(context)
            return@withContext Result.success()
        }
        val profiles = ProfileStore(prefs, KeystoreTokenCipher())
        for (rule in rules) {
            val token = profiles.tokenFor(rule.profileId) ?: continue
            val sample = try {
                PloiApi.monitoring(token, rule.serverId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (limited: PloiHttpException) {
                if (limited.status == 429) break // respect the account budget; next run retries
                null
            } catch (_: Exception) {
                null
            } ?: continue
            val (next, transitions) = evaluateThresholds(rule, sample, store.state(rule))
            store.saveState(rule, next)
            transitions.forEach { ThresholdAlerts.notify(context, rule, it) }
        }
        Result.success()
    }
}
