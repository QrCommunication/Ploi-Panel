package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Web
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        feedback = ""
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

    val yes = stringResource(R.string.flag_yes)
    val no = stringResource(R.string.flag_no)
    Column(
        Modifier.padding(vertical = PanelSpacing.sm).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)
    ) {
        if (error != null) ErrorState(error!!)
        if (feedback.isNotEmpty()) SuccessBanner(feedback)

        SectionCard(title = stringResource(R.string.apps_section), icon = Icons.Outlined.Apps) {
            ResourceCard(
                title = "WordPress", icon = Icons.Outlined.Web,
                facts = listOf(ResourceFact(stringResource(R.string.g3_site_installed), if (site.projectType == "wordpress") yes else no))
            ) {
                CardAction(stringResource(R.string.app_install), icon = Icons.Outlined.Download, enabled = !busy,
                    onClick = { confirm = "install_wordpress" })
                DangerAction(stringResource(R.string.app_uninstall), onClick = { confirm = "uninstall_wordpress" }, enabled = !busy)
            }
            ResourceCard(title = "Nextcloud", icon = Icons.Outlined.Cloud) {
                CardAction(stringResource(R.string.app_install), icon = Icons.Outlined.Download, enabled = !busy,
                    onClick = { confirm = "install_nextcloud" })
                DangerAction(stringResource(R.string.app_uninstall), onClick = { confirm = "uninstall_nextcloud" }, enabled = !busy)
            }
            ResourceCard(title = "Statamic", icon = Icons.AutoMirrored.Outlined.Article) {
                CardAction(stringResource(R.string.app_install), icon = Icons.Outlined.Download, enabled = !busy,
                    onClick = { confirm = "install_statamic" })
                DangerAction(stringResource(R.string.app_uninstall), onClick = { confirm = "uninstall_statamic" }, enabled = !busy)
            }
        }

        SectionCard(title = stringResource(R.string.fastcgi_section), icon = Icons.Outlined.Speed) {
            SiteFacts(listOf(ResourceFact(stringResource(R.string.g3_site_enabled), if (site.fastcgiCache) yes else no)))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                CardAction(stringResource(R.string.fastcgi_enable), icon = Icons.Outlined.PlayCircle,
                    enabled = !busy && !site.fastcgiCache,
                    onClick = { runAction(doneMessage) { PloiApi.enableFastcgiCache(token, serverId, siteId) } })
                CardAction(stringResource(R.string.fastcgi_disable), icon = Icons.Outlined.PauseCircle,
                    enabled = !busy && site.fastcgiCache,
                    onClick = { runAction(doneMessage) { PloiApi.disableFastcgiCache(token, serverId, siteId) } })
                CardAction(stringResource(R.string.fastcgi_flush), icon = Icons.Outlined.CleaningServices,
                    enabled = !busy && site.fastcgiCache,
                    onClick = { runAction(doneMessage) { PloiApi.flushFastcgiCache(token, serverId, siteId) } })
            }
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
