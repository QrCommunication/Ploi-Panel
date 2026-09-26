package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Localized label of a documented backup notification location. */
internal fun backupLocationLabel(location: String): Int = when (location) {
    "before-backup" -> R.string.location_before_backup
    "after-backup" -> R.string.location_after_backup
    else -> R.string.location_failed_backup
}

/**
 * Notification channels of one backup (database or site file): one row per
 * (channel, location) as documented; attach picks one of the account channels plus a
 * documented location, detach removes the channel from that row's location only.
 */
@Composable
internal fun BackupChannelsDialog(
    token: String,
    backupId: Long,
    title: String,
    listChannels: suspend () -> List<BackupNotificationChannel>,
    attachChannel: suspend (channelId: Long, location: String) -> List<BackupNotificationChannel>,
    detachChannel: suspend (channelId: Long, location: String) -> List<BackupNotificationChannel>,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf<List<BackupNotificationChannel>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var feedback by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var attaching by remember { mutableStateOf(false) }

    val attachedMessage = stringResource(R.string.backup_channel_attached)
    val detachedMessage = stringResource(R.string.backup_channel_detached)

    LaunchedEffect(token, backupId) {
        loading = true
        error = null
        try {
            channels = withContext(Dispatchers.IO) { listChannels() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            channels = null
            error = failure
        } finally {
            loading = false
        }
    }

    fun runAction(message: String, block: suspend () -> List<BackupNotificationChannel>) {
        busy = true
        error = null
        scope.launch {
            try {
                channels = withContext(Dispatchers.IO) { block() }
                feedback = message
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_channels_title, title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = { attaching = true }, enabled = !busy) {
                    Text(stringResource(R.string.attach_backup_channel))
                }
                if (loading) CircularProgressIndicator()
                if (error != null) ApiErrorText(error!!)
                if (feedback.isNotEmpty()) Text(feedback)
                channels?.let { list ->
                    if (list.isEmpty()) Text(stringResource(R.string.empty_backup_channels))
                    list.forEach { channel ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    channel.label.ifBlank { channel.type },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    stringResource(
                                        R.string.backup_channel_location,
                                        stringResource(backupLocationLabel(channel.location))
                                    )
                                )
                                if (channel.createdAt.isNotBlank()) {
                                    Text(stringResource(R.string.detail_created, channel.createdAt))
                                }
                                OutlinedButton(
                                    onClick = {
                                        runAction(detachedMessage) { detachChannel(channel.id, channel.location) }
                                    },
                                    enabled = !busy
                                ) {
                                    Text(
                                        stringResource(R.string.detach_backup_channel),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.close)) } }
    )

    if (attaching) {
        AttachBackupChannelDialog(
            token = token, busy = busy,
            onAttach = { channelId, location ->
                attaching = false
                runAction(attachedMessage) { attachChannel(channelId, location) }
            },
            onDismiss = { attaching = false }
        )
    }
}

/** Attach form: account notification channel + one documented location. */
@Composable
private fun AttachBackupChannelDialog(
    token: String, busy: Boolean,
    onAttach: (channelId: Long, location: String) -> Unit, onDismiss: () -> Unit
) {
    var available by remember { mutableStateOf<List<NotificationChannel>?>(null) }
    var optionsError by remember { mutableStateOf<Throwable?>(null) }
    var channelId by remember { mutableLongStateOf(0L) }
    var location by remember { mutableStateOf("failed-backup") }

    LaunchedEffect(token) {
        try {
            available = withContext(Dispatchers.IO) {
                PloiApi.notificationChannels(token, perPage = 50).channels
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            optionsError = failure
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.attach_backup_channel)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (optionsError != null) ApiErrorText(optionsError!!)
                if (available == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.backup_loading_options), Modifier.padding(top = 12.dp))
                    }
                }
                available?.let { list ->
                    Text(stringResource(R.string.backup_channel_label))
                    if (list.isEmpty()) Text(stringResource(R.string.backup_no_channels_available))
                    list.forEach { channel ->
                        Row(Modifier.fillMaxWidth()) {
                            RadioButton(
                                selected = channelId == channel.id,
                                onClick = { channelId = channel.id }
                            )
                            Text(channel.label.ifBlank { channel.type }, Modifier.padding(top = 12.dp))
                        }
                    }
                }
                Text(stringResource(R.string.backup_channel_location_label))
                BACKUP_CHANNEL_LOCATIONS.forEach { candidate ->
                    Row(Modifier.fillMaxWidth()) {
                        RadioButton(selected = location == candidate, onClick = { location = candidate })
                        Text(stringResource(backupLocationLabel(candidate)), Modifier.padding(top = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAttach(channelId, location) },
                enabled = !busy && channelId > 0
            ) { Text(stringResource(R.string.attach_channel_submit)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
