package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label)
        if (loading) {
            BusyIndicator()
            Text(stringResource(R.string.backup_loading_options))
        }
        error?.let { failure ->
            ApiErrorText(failure)
            OutlinedButton(onClick = { retry++ }) { Text(stringResource(R.string.picker_retry)) }
        }
        options?.let { data ->
            if (data.items.isEmpty()) Text(emptyLabel)
            data.items.forEach { row(it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { cursor.previous() }, enabled = cursor.page > 1) {
                    Text(stringResource(R.string.previous))
                }
                Text(stringResource(R.string.page, data.currentPage.toString(), data.lastPage.toString()), Modifier.padding(top = 12.dp))
                OutlinedButton(onClick = { cursor.next(data) }, enabled = data.hasNext) {
                    Text(stringResource(R.string.next))
                }
            }
        }
    }
}
