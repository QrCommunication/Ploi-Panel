package com.qrcommunication.ploipanel.widget

import android.app.KeyguardManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.qrcommunication.ploipanel.R

/** A bounded, scrollable launcher collection. Only encrypted cached readings are read here. */
class MultiServerRowsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = RowsFactory(
        applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID)
    )

    private class RowsFactory(private val context: Context, private val widgetId: Int) : RemoteViewsFactory {
        private data class Snapshot(val config: WidgetConfig?, val readings: Map<Long, WidgetReading>)
        @Volatile private var snapshot = Snapshot(null, emptyMap())

        override fun onCreate() = onDataSetChanged()

        override fun onDataSetChanged() {
            val manager = AppWidgetManager.getInstance(context)
            val provider = manager.getAppWidgetInfo(widgetId)?.provider?.className
            val locked = (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
            if (provider != MultiServerWidget::class.java.name || locked) {
                snapshot = Snapshot(null, emptyMap())
                return
            }
            val data = WidgetData(context)
            val config = data.config(widgetId)
            if (config == null || data.profiles.profiles().none { it.id == config.profileId }) {
                snapshot = Snapshot(null, emptyMap())
                return
            }
            snapshot = Snapshot(config, data.readings(widgetId, config))
        }

        override fun onDestroy() { snapshot = Snapshot(null, emptyMap()) }
        override fun getCount(): Int = snapshot.config?.serverIds?.size ?: 0
        override fun getViewTypeCount(): Int = 1
        override fun hasStableIds(): Boolean = true
        override fun getItemId(position: Int): Long = snapshot.config?.serverIds?.getOrNull(position) ?: -1L
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews? {
            val current = snapshot
            val config = current.config ?: return null
            val id = config.serverIds.getOrNull(position) ?: return null
            // The host can ask for already cached rows after the phone has been locked.
            if ((context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked) return null
            val display = WidgetRefresh.localizedContext(context)
            val reading = current.readings[id]
            val row = RemoteViews(context.packageName, R.layout.widget_multi_row)
            row.setTextViewText(R.id.widget_row_name,
                reading?.name ?: display.getString(R.string.widget_unknown_server, id))
            if (reading == null) {
                row.setTextViewText(R.id.widget_row_metrics, display.getString(R.string.widget_no_data))
                row.setTextViewText(R.id.widget_row_status, "")
                row.setViewVisibility(R.id.widget_row_chart, View.GONE)
            } else {
                row.setTextViewText(R.id.widget_row_metrics, widgetMetrics(display, reading, config.metrics))
                row.setTextViewText(R.id.widget_row_status,
                    if (isStale(reading.sample, System.currentTimeMillis()))
                        display.getString(R.string.widget_stale) else "")
                val gauge = gaugeBitmap(gaugeValues(reading.sample, config.metrics), widgetGaugeLabels(display))
                if (gauge == null) row.setViewVisibility(R.id.widget_row_chart, View.GONE)
                else {
                    row.setImageViewBitmap(R.id.widget_row_chart, gauge)
                    row.setViewVisibility(R.id.widget_row_chart, View.VISIBLE)
                }
            }
            return row
        }
    }
}
