package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Apps domain (WordPress / Nextcloud / Statamic one-click installers) plus the
 * FastCGI cache lifecycle of the site.
 */
@Composable
internal fun AppsScreen(
    token: String, serverId: Long, siteId: Long, site: Site, lock: AppLock, activity: FragmentActivity,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var error by remember(token, serverId, siteId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId, siteId) { mutableStateOf("") }
    var busy by remember(token, serverId, siteId) { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }

    val doneMessage = stringResource(R.string.action_done)

    fun runAction(message: String, block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                feedback = message
                onChanged()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)

        Text(stringResource(R.string.apps_section), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.apps_wordpress_state, if (site.projectType == "wordpress") "✓" else "—"))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("WordPress", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirm = "install_wordpress" }, enabled = !busy) {
                        Text(stringResource(R.string.app_install))
                    }
                    OutlinedButton(onClick = { confirm = "uninstall_wordpress" }, enabled = !busy) {
                        Text(stringResource(R.string.app_uninstall), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nextcloud", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirm = "install_nextcloud" }, enabled = !busy) {
                        Text(stringResource(R.string.app_install))
                    }
                    OutlinedButton(onClick = { confirm = "uninstall_nextcloud" }, enabled = !busy) {
                        Text(stringResource(R.string.app_uninstall), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Statamic", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirm = "install_statamic" }, enabled = !busy) {
                        Text(stringResource(R.string.app_install))
                    }
                    OutlinedButton(onClick = { confirm = "uninstall_statamic" }, enabled = !busy) {
                        Text(stringResource(R.string.app_uninstall), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Text(stringResource(R.string.fastcgi_section), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.site_fastcgi, if (site.fastcgiCache) "✓" else "—"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { runAction(doneMessage) { PloiApi.enableFastcgiCache(token, serverId, siteId) } },
                enabled = !busy && !site.fastcgiCache
            ) { Text(stringResource(R.string.fastcgi_enable)) }
            OutlinedButton(
                onClick = { runAction(doneMessage) { PloiApi.disableFastcgiCache(token, serverId, siteId) } },
                enabled = !busy && site.fastcgiCache
            ) { Text(stringResource(R.string.fastcgi_disable)) }
            OutlinedButton(
                onClick = { runAction(doneMessage) { PloiApi.flushFastcgiCache(token, serverId, siteId) } },
                enabled = !busy && site.fastcgiCache
            ) { Text(stringResource(R.string.fastcgi_flush)) }
        }
    }

    confirm?.let { action ->
        val (label, message) = when (action) {
            "install_wordpress" -> R.string.app_install to R.string.confirm_app_install_wordpress
            "uninstall_wordpress" -> R.string.app_uninstall to R.string.confirm_app_uninstall_wordpress
            "install_nextcloud" -> R.string.app_install to R.string.confirm_app_install_nextcloud
            "uninstall_nextcloud" -> R.string.app_uninstall to R.string.confirm_app_uninstall_nextcloud
            "install_statamic" -> R.string.app_install to R.string.confirm_app_install_statamic
            else -> R.string.app_uninstall to R.string.confirm_app_uninstall_statamic
        }
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(message),
            confirmLabel = label,
            onConfirmed = {
                confirm = null
                runAction(doneMessage) {
                    when (action) {
                        "install_wordpress" -> PloiApi.installWordpress(token, serverId, siteId)
                        "uninstall_wordpress" -> PloiApi.uninstallWordpress(token, serverId, siteId)
                        "install_nextcloud" -> PloiApi.installNextcloud(token, serverId, siteId)
                        "uninstall_nextcloud" -> PloiApi.uninstallNextcloud(token, serverId, siteId)
                        "install_statamic" -> PloiApi.installStatamic(token, serverId, siteId)
                        else -> PloiApi.uninstallStatamic(token, serverId, siteId)
                    }
                }
            },
            onDismiss = { confirm = null }
        )
    }
}
