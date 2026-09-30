package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One documented API page at a time; selections live in the owning form, not in this page. */
internal data class PickerOptions<T>(val items: List<T>, val currentPage: Int, val lastPage: Int) {
    init {
        require(currentPage > 0 && lastPage >= currentPage)
    }
    val hasNext: Boolean get() = currentPage < lastPage
}

/** Stateful page number with boundary checks. A failed page can be retried without losing selections. */
internal class PickerCursor {
    var page by mutableIntStateOf(1)
        private set
    fun previous() { if (page > 1) page-- }
    fun next(options: PickerOptions<*>) { if (page == options.currentPage && options.hasNext) page++ }
}

@Composable
internal fun <T> PagedOptionPicker(
    key: Any,
    label: String,
    emptyLabel: String,
    load: suspend (Int) -> PickerOptions<T>,
    row: @Composable (T) -> Unit
) {
    val cursor = remember(key) { PickerCursor() }
    var retry by remember(key) { mutableIntStateOf(0) }
    var options by remember(key) { mutableStateOf<PickerOptions<T>?>(null) }
    var error by remember(key) { mutableStateOf<Throwable?>(null) }
    var loading by remember(key) { mutableStateOf(true) }
    val currentLoad by rememberUpdatedState(load)

    LaunchedEffect(key, cursor.page, retry) {
        options = null
        error = null
        loading = true
        try {
            options = withContext(Dispatchers.IO) { currentLoad(cursor.page) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure
        } finally {
            loading = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm)) {
                BusyIndicator(Modifier.size(20.dp))
                Text(
                    stringResource(R.string.backup_loading_options), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        error?.let { failure ->
            ApiErrorText(failure)
            OutlinedButton(onClick = { retry++ }) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.picker_retry), Modifier.padding(start = PanelSpacing.sm))
            }
        }
        options?.let { data ->
            if (data.items.isEmpty()) Text(
                emptyLabel, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            data.items.forEach { row(it) }
            PageBar(data.currentPage, data.lastPage, data.hasNext,
                onPrevious = { cursor.previous() }, onNext = { cursor.next(data) })
        }
    }
}
