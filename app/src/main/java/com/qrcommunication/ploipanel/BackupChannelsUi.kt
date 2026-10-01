package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
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
    lock: AppLock,
    activity: FragmentActivity,
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
    var pendingDetach by remember { mutableStateOf<Pair<Long, String>?>(null) }

    val attachedMessage = stringResource(R.string.backup_channel_attached)
    val detachedMessage = stringResource(R.string.backup_channel_detached)

    LaunchedEffect(token, backupId) {
        loading = true
        error = null
        channels = null
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
        feedback = ""
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
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)
            ) {
                CardAction(
                    stringResource(R.string.attach_backup_channel), icon = Icons.Outlined.NotificationAdd,
                    enabled = !busy, onClick = { attaching = true }
                )
                if (loading) LoadingState(rows = 1)
                if (error != null) ErrorState(error!!)
                if (feedback.isNotEmpty()) SuccessBanner(feedback)
                channels?.let { list ->
                    if (list.isEmpty()) EmptyState(Icons.Outlined.Notifications, stringResource(R.string.empty_backup_channels))
                    list.forEach { channel ->
                        ResourceCard(
                            title = channel.label.ifBlank { channel.type }, icon = Icons.Outlined.Notifications,
                            facts = listOfNotNull(
                                ResourceFact(
                                    stringResource(R.string.backup_channel_location_label),
                                    stringResource(backupLocationLabel(channel.location))
                                ),
                                channel.createdAt.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g2_created_label), it) }
                            )
                        ) {
                            DangerAction(
                                stringResource(R.string.detach_backup_channel),
                                onClick = { pendingDetach = channel.id to channel.location },
                                enabled = !busy
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.close)) } }
    )

    pendingDetach?.let { (channelId, location) ->
        SensitiveConfirmDialog(
            lock, activity,
            stringResource(R.string.confirm_detach_backup_channel),
            R.string.detach_backup_channel,
            onConfirmed = {
                pendingDetach = null
                runAction(detachedMessage) { detachChannel(channelId, location) }
            },
            onDismiss = { pendingDetach = null }
        )
    }
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
    var channelId by remember { mutableLongStateOf(0L) }
    var location by remember { mutableStateOf("failed-backup") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.attach_backup_channel)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)
            ) {
                PagedOptionPicker(
                    key = token, label = stringResource(R.string.backup_channel_label),
                    emptyLabel = stringResource(R.string.backup_no_channels_available),
                    load = { page -> PloiApi.notificationChannels(token, page = page, perPage = 50).let {
                        PickerOptions(it.channels, it.currentPage, it.lastPage)
                    } }
                ) { channel ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = channelId == channel.id,
                                onClick = { channelId = channel.id }
                            )
                            Text(channel.label.ifBlank { channel.type })
                        }
                }
                SectionHeader(stringResource(R.string.backup_channel_location_label))
                BACKUP_CHANNEL_LOCATIONS.forEach { candidate ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = location == candidate, onClick = { location = candidate })
                        Text(stringResource(backupLocationLabel(candidate)))
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
