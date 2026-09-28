package com.qrcommunication.ploipanel.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.KeyguardManager
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.edit
import androidx.core.net.toUri
import com.qrcommunication.ploipanel.CheckState
import com.qrcommunication.ploipanel.LocalCheckStore
import com.qrcommunication.ploipanel.MainActivity
import com.qrcommunication.ploipanel.MonitoredTarget
import com.qrcommunication.ploipanel.R
import com.qrcommunication.ploipanel.SharedPreferencesProfilePrefs
import com.qrcommunication.ploipanel.TargetStatus
import com.qrcommunication.ploipanel.isLocalCheckStale
import org.json.JSONArray
import java.text.DateFormat
import java.util.Date

/**
 * Launcher widget for the local site watchdog. Rows mirror the on-device check store only:
 * no Ploi API call, no token and no secret ever crosses the launcher boundary. Updates are
 * pushed by the watchdog worker and manual checks; freshness stays best effort.
 */
internal data class SiteCheckWidgetConfig(val targetIds: List<String>) {
    init {
        require(targetIds.isNotEmpty() && targetIds.size <= MAX_WIDGET_CHECKS)
        require(targetIds.all { it.isNotBlank() } && targetIds.distinct().size == targetIds.size)
    }
    companion object {
        const val MAX_WIDGET_CHECKS = 10
    }
}

/** Per-widget selection of watchdog targets. Only opaque local target IDs are persisted. */
internal class SiteChecksData(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ploi_widget_checks", Context.MODE_PRIVATE)

    fun config(id: Int): SiteCheckWidgetConfig? = try {
        val array = JSONArray(prefs.getString("config.$id", null) ?: return null)
        SiteCheckWidgetConfig((0 until array.length()).map { array.getString(it) })
    } catch (_: Exception) { null }

    fun save(id: Int, config: SiteCheckWidgetConfig) {
        prefs.edit { putString("config.$id", JSONArray(config.targetIds).toString()) }
    }

    fun delete(id: Int) { prefs.edit { remove("config.$id") } }
}

/** One rendered row: the target's last known status, with the shared staleness flag applied. */
internal data class SiteCheckRow(
    val targetId: String,
    val label: String,
    val state: CheckState,
    val latencyMs: Long?,
    val httpStatus: Int?,
    val checkedAt: Long?,
    val stale: Boolean,
)

/** Rows follow the widget selection order; targets removed from the watchdog simply disappear. */
internal fun siteCheckRows(
    targets: List<MonitoredTarget>,
    statuses: (String) -> TargetStatus,
    config: SiteCheckWidgetConfig,
    now: Long,
): List<SiteCheckRow> {
    val byId = targets.associateBy { it.id }
    return config.targetIds.mapNotNull { id ->
        val target = byId[id] ?: return@mapNotNull null
        val status = statuses(id)
        SiteCheckRow(
            targetId = id,
            label = target.label,
            state = status.state,
            latencyMs = status.lastLatencyMs,
            httpStatus = status.lastHttpStatus,
            checkedAt = status.lastCheckedAt,
            stale = isLocalCheckStale(status.lastCheckedAt, now),
        )
    }
}

internal object SiteChecksWidgetRefresh {
    fun ids(context: Context): List<Int> =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, SiteChecksWidget::class.java)).toList()

    /** Rebinds every watchdog widget after the local store changed (worker or manual checks). */
    fun refreshAll(context: Context) = ids(context).forEach { render(context, it) }

    // The Intent adapter remains required on API 29-30; deprecation points to API 31-only
    // RemoteCollectionItems, which cannot serve the app's supported older devices.
    @Suppress("DEPRECATION")
    private fun attachRows(view: RemoteViews, adapter: Intent) {
        view.setRemoteAdapter(R.id.widget_checks_list, adapter)
    }

    @Suppress("DEPRECATION")
    private fun refreshRows(manager: AppWidgetManager, id: Int) {
        manager.notifyAppWidgetViewDataChanged(id, R.id.widget_checks_list)
    }

    fun render(context: Context, id: Int) {
        val display = WidgetRefresh.localizedContext(context)
        val configs = SiteChecksData(context)
        val manager = AppWidgetManager.getInstance(context)
        val locked = (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
        val config = configs.config(id)
        val store = LocalCheckStore(SharedPreferencesProfilePrefs(context))
        val rows = if (config != null && !locked) {
            siteCheckRows(store.targets(), store::statusOf, config, System.currentTimeMillis())
        } else emptyList()
        val view = RemoteViews(context.packageName, R.layout.widget_checks)
        view.setTextViewText(R.id.widget_title, display.getString(R.string.widget_checks))
        val state = when {
            config == null -> display.getString(R.string.widget_setup)
            locked -> display.getString(R.string.widget_locked)
            rows.isEmpty() -> display.getString(R.string.widget_checks_empty)
            else -> null
        }
        view.setTextViewText(R.id.widget_state, state ?: "")
        view.setViewVisibility(R.id.widget_state, if (state == null) View.GONE else View.VISIBLE)
        view.setViewVisibility(R.id.widget_checks_list, if (state == null) View.VISIBLE else View.GONE)
        if (state == null) {
            // A distinct URI makes Android bind a separate, correctly invalidated list per widget.
            val adapter = Intent(context, SiteCheckRowsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = "ploipanel-widget://checks/$id".toUri()
            }
            attachRows(view, adapter)
        }
        val newest = rows.mapNotNull { it.checkedAt }.maxOrNull()
        view.setTextViewText(R.id.widget_time, when {
            locked -> display.getString(R.string.widget_unlock)
            newest != null -> display.getString(
                R.string.widget_checks_updated,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,
                    display.resources.configuration.locales[0]).format(Date(newest))
            )
            else -> display.getString(R.string.widget_best_effort)
        })
        // The watchdog is local and profile-free: a tap simply opens the app, which re-locks itself.
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        view.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
            context, id, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
        manager.updateAppWidget(id, view)
        refreshRows(manager, id)
    }
}

class SiteChecksWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { SiteChecksWidgetRefresh.render(context, it) }
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { SiteChecksData(context).delete(it) }
    }
}

/** A bounded, scrollable launcher collection fed exclusively by the local watchdog store. */
class SiteCheckRowsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = RowsFactory(
        applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID)
    )

    private class RowsFactory(private val context: Context, private val widgetId: Int) : RemoteViewsFactory {
        @Volatile private var rows: List<SiteCheckRow> = emptyList()

        override fun onCreate() = onDataSetChanged()

        override fun onDataSetChanged() {
            val manager = AppWidgetManager.getInstance(context)
            val provider = manager.getAppWidgetInfo(widgetId)?.provider?.className
            val locked = (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
            val config = if (provider == SiteChecksWidget::class.java.name && !locked) {
                SiteChecksData(context).config(widgetId)
            } else null
            if (config == null) {
                rows = emptyList()
                return
            }
            val store = LocalCheckStore(SharedPreferencesProfilePrefs(context))
            rows = siteCheckRows(store.targets(), store::statusOf, config, System.currentTimeMillis())
        }

        override fun onDestroy() { rows = emptyList() }
        override fun getCount(): Int = rows.size
        override fun getViewTypeCount(): Int = 1
        override fun hasStableIds(): Boolean = true
        override fun getItemId(position: Int): Long = rows.getOrNull(position)?.targetId?.hashCode()?.toLong() ?: -1L
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews? {
            val row = rows.getOrNull(position) ?: return null
            // The host can ask for already cached rows after the phone has been locked.
            if ((context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked) return null
            val display = WidgetRefresh.localizedContext(context)
            val view = RemoteViews(context.packageName, R.layout.widget_checks_row)
            view.setTextViewText(R.id.widget_check_name, row.label)
            val detail = buildList {
                row.httpStatus?.let { add(display.getString(R.string.local_checks_http_status, it)) }
                row.latencyMs?.let { add(display.getString(R.string.local_checks_latency, it)) }
                add(
                    row.checkedAt?.let {
                        display.getString(
                            R.string.local_checks_last_checked,
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,
                                display.resources.configuration.locales[0]).format(Date(it))
                        )
                    } ?: display.getString(R.string.local_checks_never_checked)
                )
            }.joinToString(" · ")
            view.setTextViewText(R.id.widget_check_detail, detail)
            val stateLabel = display.getString(
                when (row.state) {
                    CheckState.UP -> R.string.local_checks_state_up
                    CheckState.DOWN -> R.string.local_checks_state_down
                    CheckState.UNKNOWN -> R.string.local_checks_state_unknown
                }
            )
            view.setTextViewText(
                R.id.widget_check_status,
                if (row.stale) "$stateLabel · ${display.getString(R.string.local_checks_stale)}" else stateLabel
            )
            return view
        }
    }
}
