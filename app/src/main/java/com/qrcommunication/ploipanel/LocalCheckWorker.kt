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
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.qrcommunication.ploipanel.widget.SiteChecksWidgetRefresh
import com.qrcommunication.ploipanel.widget.WidgetRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Best-effort background scheduling for local site checks. WorkManager periodic work has a
 * 15-minute floor and no delivery guarantee: the UI and docs must say "best effort", and no
 * alert can fire while the device is offline, asleep under restriction or notifications off.
 */
internal object LocalCheckRefresh {
    private const val PERIODIC = "ploi-local-checks-periodic"
    private const val NOW = "ploi-local-checks-now"
    internal const val CHANNEL_ID = "local_checks"

    private fun store(context: Context) = LocalCheckStore(SharedPreferencesProfilePrefs(context))

    /** Keeps the periodic probe alive only while at least one target is configured. */
    fun sync(context: Context) {
        if (store(context).targets().isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(NOW)
        } else {
            schedule(context)
        }
    }

    fun schedule(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<LocalCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints).build()
        )
    }

    fun requestNow(context: Context) {
        if (store(context).targets().isEmpty()) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<LocalCheckWorker>().setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()
        )
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val display = WidgetRefresh.localizedContext(context)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, display.getString(R.string.local_checks_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    private fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    /** Posts one alert per state edge; repeated DOWN evaluations never re-notify. */
    internal fun notify(context: Context, target: MonitoredTarget, transition: CheckTransition, status: TargetStatus) {
        if (transition == CheckTransition.NONE) return
        val display = WidgetRefresh.localizedContext(context)
        val (title, text) = when (transition) {
            CheckTransition.WENT_DOWN -> display.getString(R.string.local_checks_down_title, target.label) to
                display.getString(
                    if (status.lastHttpStatus != null) R.string.local_checks_down_http else R.string.local_checks_down_unreachable,
                    status.lastHttpStatus ?: 0
                )
            CheckTransition.RECOVERED -> display.getString(R.string.local_checks_up_title, target.label) to
                display.getString(R.string.local_checks_up_text, status.lastLatencyMs ?: 0L)
            CheckTransition.NONE -> return
        }
        ensureChannel(context)
        val launch = PendingIntent.getActivity(
            context, target.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(launch)
            .setAutoCancel(true)
            .build()
        if (notificationsAllowed(context)) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(target.id.hashCode(), notification)
        }
    }
}

/** Probes every configured target once, then alerts only on real state edges. */
class LocalCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val store = LocalCheckStore(SharedPreferencesProfilePrefs(context))
        val targets = store.targets()
        if (targets.isEmpty()) {
            LocalCheckRefresh.sync(context)
            return@withContext Result.success()
        }
        val engine = SiteCheckEngine(store, UrlConnectionSiteProber())
        val alerts = store.alertsEnabled()
        targets.forEach { target ->
            val previous = store.statusOf(target.id)
            val next = engine.check(target.id)
            if (alerts) LocalCheckRefresh.notify(context, target, transitionFor(previous, next), next)
        }
        // Keep launcher watchdog widgets in sync with the freshly recorded statuses.
        SiteChecksWidgetRefresh.refreshAll(context)
        Result.success() // Offline/rate issues are per-target failures, never a worker retry storm.
    }
}
