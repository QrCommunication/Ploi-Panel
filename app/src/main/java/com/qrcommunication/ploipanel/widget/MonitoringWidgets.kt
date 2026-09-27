package com.qrcommunication.ploipanel.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.app.KeyguardManager
import android.view.View
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.qrcommunication.ploipanel.AppLanguage
import com.qrcommunication.ploipanel.MainActivity
import com.qrcommunication.ploipanel.PloiApi
import com.qrcommunication.ploipanel.PloiHttpException
import com.qrcommunication.ploipanel.R
import com.qrcommunication.ploipanel.SharedPreferencesProfilePrefs
import com.qrcommunication.ploipanel.UiPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

internal object WidgetRefresh {
    private const val PERIODIC = "ploi-widget-periodic"
    private const val NOW = "ploi-widget-now"

    private fun localizedContext(context: Context): Context {
        val language = UiPreferences(SharedPreferencesProfilePrefs(context)).language()
        val configuration = Configuration(context.resources.configuration)
        when (language) {
            AppLanguage.FRENCH -> configuration.setLocale(Locale.FRENCH)
            AppLanguage.ENGLISH -> configuration.setLocale(Locale.ENGLISH)
            AppLanguage.SYSTEM -> Unit
        }
        return context.createConfigurationContext(configuration)
    }

    fun schedule(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()
        )
    }
    fun request(context: Context) {
        schedule(context)
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WidgetWorker>().setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()
        )
    }
    fun cancelIfEmpty(context: Context) {
        if (widgetIds(context).isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(NOW)
        }
    }

    fun widgetIds(context: Context): List<Int> {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(SingleServerWidget::class.java, MultiServerWidget::class.java)
            .flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }.distinct()
    }

    fun render(context: Context, id: Int, error: Boolean = false) {
        val displayContext = localizedContext(context)
        val store = WidgetData(context)
        val config = store.config(id)
        val locked = (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
        val manager = AppWidgetManager.getInstance(context)
        val view = RemoteViews(context.packageName, R.layout.widget_monitoring)
        val validProfile = config != null && store.profiles.profiles().any { it.id == config.profileId }
        val rows = if (config != null && !locked && validProfile) store.readings(id, config) else emptyMap()
        val single = manager.getAppWidgetInfo(id)?.provider?.className == SingleServerWidget::class.java.name
        val title = if (single) R.string.widget_single else R.string.widget_multi
        view.setTextViewText(R.id.widget_title, displayContext.getString(title))
        val content = when {
            config == null -> displayContext.getString(R.string.widget_setup)
            locked -> displayContext.getString(R.string.widget_locked)
            !validProfile -> displayContext.getString(R.string.widget_profile_missing)
            else -> {
                config.serverIds.map { server ->
                    val reading = rows[server]
                    if (reading == null) displayContext.getString(R.string.widget_no_data)
                    else {
                        val values = config.metrics.sorted().joinToString(" · ") { metric ->
                            val label = when (metric) {
                                "cpu" -> R.string.metric_cpu
                                "ram" -> R.string.metric_ram
                                "disk" -> R.string.metric_disk
                                else -> R.string.metric_load
                            }
                            "${displayContext.getString(label)}: ${metricValue(reading.sample, metric)}"
                        }
                        "${reading.name}: $values${if (isStale(reading.sample, System.currentTimeMillis()) || error) " · ${displayContext.getString(R.string.widget_stale)}" else ""}"
                    }
                }.joinToString("\n")
            }
        }
        view.setTextViewText(R.id.widget_content, content)
        val chart = if (single) config?.let { configured ->
            configured.serverIds.singleOrNull()?.let { rows[it] }?.let { reading ->
                gaugeBitmap(
                    gaugeValues(reading.sample, configured.metrics),
                    mapOf(
                        "cpu" to displayContext.getString(R.string.metric_cpu),
                        "ram" to displayContext.getString(R.string.metric_ram),
                        "disk" to displayContext.getString(R.string.metric_disk)
                    )
                )
            }
        } else null
        if (chart != null) {
            view.setImageViewBitmap(R.id.widget_chart, chart)
            view.setViewVisibility(R.id.widget_chart, View.VISIBLE)
        } else view.setViewVisibility(R.id.widget_chart, View.GONE)
        val newest = rows.values.maxOfOrNull { reading -> reading.fetchedAt }
        view.setTextViewText(R.id.widget_time, when {
            locked -> displayContext.getString(R.string.widget_unlock)
            error -> displayContext.getString(R.string.widget_fetch_error)
            newest != null -> displayContext.getString(
                R.string.widget_updated,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,
                    displayContext.resources.configuration.locales[0]).format(Date(newest))
            )
            else -> displayContext.getString(R.string.widget_best_effort)
        })
        // Only opaque IDs cross the launcher intent; MainActivity validates them after unlock.
        // Never include a Ploi token in the intent.
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("widget_profile_id", config?.profileId)
            putExtra("widget_server_id", if (single) config?.serverIds?.singleOrNull() ?: -1L else -1L)
        }
        view.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
            context, id, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
        manager.updateAppWidget(id, view)
    }
}

class SingleServerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetRefresh.render(context, it) }
        WidgetRefresh.request(context)
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetData(context).delete(it) }
        WidgetRefresh.cancelIfEmpty(context)
    }
    override fun onEnabled(context: Context) { WidgetRefresh.schedule(context) }
}

class MultiServerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetRefresh.render(context, it) }
        WidgetRefresh.request(context)
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetData(context).delete(it) }
        WidgetRefresh.cancelIfEmpty(context)
    }
    override fun onEnabled(context: Context) { WidgetRefresh.schedule(context) }
}

class WidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val store = WidgetData(context)
        val ids = WidgetRefresh.widgetIds(context)
        val locked = (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
        if (locked) { ids.forEach { WidgetRefresh.render(context, it) }; return@withContext Result.success() }
        val results = mutableMapOf<Pair<String, Long>, WidgetReading?>()
        var rateLimited = false
        ids.forEach { id ->
            val config = store.config(id) ?: return@forEach
            val token = store.profiles.tokenFor(config.profileId)
            if (token == null) { WidgetRefresh.render(context, id, true); return@forEach }
            val cached = store.readings(id, config).toMutableMap()
            var failed = false
            for (serverId in config.serverIds) {
                if (rateLimited) { failed = true; break }
                val key = config.profileId to serverId
                val reading = if (key in results) results[key] else try {
                    val detail = PloiApi.server(token, serverId)
                    PloiApi.monitoring(token, serverId)?.let { WidgetReading(detail.name, it, System.currentTimeMillis()) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (limited: PloiHttpException) {
                    if (limited.status == 429) rateLimited = true
                    null
                } catch (_: Exception) { null }.also { results[key] = it }
                if (reading == null) failed = true else cached[serverId] = reading
            }
            if (cached.isNotEmpty()) store.saveReadings(id, config, cached)
            WidgetRefresh.render(context, id, failed)
        }
        Result.success() // Rate limits/offline: retain stale data; next periodic run is best effort.
    }
}
