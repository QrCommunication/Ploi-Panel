package com.qrcommunication.ploipanel.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qrcommunication.ploipanel.MonitorSample
import com.qrcommunication.ploipanel.R
import com.qrcommunication.ploipanel.widget.bindGauges
import com.qrcommunication.ploipanel.widget.bindStatus
import com.qrcommunication.ploipanel.widget.rowGaugeSlots
import com.qrcommunication.ploipanel.widget.singleGaugeSlots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real launcher widget layouts (RemoteViews applied to a host view) with the binding
 * helpers the widget providers use, light and dark, at typical launcher cell sizes. The readings
 * are the live values captured from the account (resto-zen-prod / scell-io / giga-apps).
 * Opt-in (-Pscreenshots); output only.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WidgetScreenshots {
    private val out = File(System.getProperty("ploi.screenshots.out") ?: "build/screenshots").apply { mkdirs() }

    private fun render(context: Context, views: RemoteViews, widthDp: Int, heightDp: Int, name: String, rows: List<RemoteViews> = emptyList()) {
        val density = context.resources.displayMetrics.density
        val host = FrameLayout(context)
        val applied = views.apply(context, host)
        // ListView rows come from a RemoteViewsService on devices; inflate them in place here.
        (applied.findViewById<View>(R.id.widget_server_list) as? ListView)?.let { list -> replaceList(context, list, rows) }
        val w = (widthDp * density).toInt()
        val h = (heightDp * density).toInt()
        host.addView(applied, ViewGroup.LayoutParams(w, h))
        host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, w, h)
        val bitmap = Bitmap.createBitmap(w + 48, h + 48, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(if ((context.resources.configuration.uiMode and 0x30) == 0x20) 0xFF3A4550.toInt() else 0xFF8FA9B8.toInt())
        canvas.translate(24f, 24f)
        host.draw(canvas)
        File(out, "widget-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun replaceList(context: Context, list: ListView, rows: List<RemoteViews>) {
        val parent = list.parent as ViewGroup
        val index = parent.indexOfChild(list)
        val column = android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL }
        val density = context.resources.displayMetrics.density
        rows.forEach { row ->
            val view = row.apply(context, column)
            column.addView(view, android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (6 * density).toInt() })
        }
        parent.removeViewAt(index)
        parent.addView(column, index, list.layoutParams)
    }

    private fun single(context: Context, name: String) {
        val view = RemoteViews(context.packageName, R.layout.widget_monitoring)
        view.setTextViewText(R.id.widget_title, context.getString(R.string.widget_single))
        view.setViewVisibility(R.id.widget_server_name, View.VISIBLE)
        view.setTextViewText(R.id.widget_server_name, "scell-io")
        bindStatus(context, view, R.id.widget_status, "active")
        bindGauges(context, view, singleGaugeSlots, MonitorSample("9.2", "78.07", "92", "0.2", "2026-09-29 23:42:15.000"), setOf("cpu", "ram", "disk", "load"))
        view.setViewVisibility(R.id.widget_load, View.VISIBLE)
        view.setTextViewText(R.id.widget_load, context.getString(R.string.widget_load_short, "0.2"))
        view.setViewVisibility(R.id.widget_content, View.GONE)
        view.setTextViewText(R.id.widget_time, context.getString(R.string.widget_updated_short, "01:42"))
        render(context, view, 180, 180, "single-$name")
        render(context, view, 180, 250, "single-tall-$name")
    }

    private fun row(context: Context, serverName: String, status: String, sample: MonitorSample?): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_multi_row)
        row.setTextViewText(R.id.widget_row_name, serverName)
        bindStatus(context, row, R.id.widget_row_status, status)
        val shown = bindGauges(context, row, rowGaugeSlots, sample, setOf("cpu", "ram", "disk"))
        row.setViewVisibility(R.id.widget_row_metrics, if (shown == 0) View.VISIBLE else View.GONE)
        if (shown == 0) row.setTextViewText(R.id.widget_row_metrics, context.getString(R.string.widget_no_data))
        return row
    }

    private fun multi(context: Context, name: String) {
        val view = RemoteViews(context.packageName, R.layout.widget_multi)
        view.setTextViewText(R.id.widget_title, context.getString(R.string.widget_multi))
        view.setViewVisibility(R.id.widget_count, View.VISIBLE)
        view.setTextViewText(R.id.widget_count, context.getString(R.string.widget_multi_count, 3, 3))
        view.setViewVisibility(R.id.widget_state, View.GONE)
        view.setTextViewText(R.id.widget_time, context.getString(R.string.widget_updated, "30/09/2026 01:42"))
        render(context, view, 300, 260, "multi-$name", listOf(
            row(context, "resto-zen-prod", "active", MonitorSample("0.7", "52.94", "22", "0", "")),
            row(context, "scell-io", "active", MonitorSample("9.2", "78.07", "92", "0.2", "")),
            row(context, "giga-apps", "unreachable", null)
        ))
    }

    @Test @Config(qualifiers = "fr-rFR-notnight-xhdpi")
    fun light() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        single(context, "light"); multi(context, "light")
    }

    @Test @Config(qualifiers = "fr-rFR-night-xhdpi")
    fun dark() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        single(context, "dark"); multi(context, "dark")
    }
}
