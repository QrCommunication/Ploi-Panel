package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Https
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

/** Certificates domain: SSL certificates of a site with create, download, activate and delete. */
@Composable
internal fun CertificatesScreen(token: String, serverId: Long, siteId: Long, lock: AppLock, activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    var page by remember(token, serverId, siteId) { mutableIntStateOf(1) }
    var refresh by remember(token, serverId, siteId) { mutableIntStateOf(0) }
    var result by remember(token, serverId, siteId) { mutableStateOf<CertificatePage?>(null) }
    var loading by remember(token, serverId, siteId) { mutableStateOf(true) }
    var error by remember(token, serverId, siteId) { mutableStateOf<Throwable?>(null) }
    var feedback by remember(token, serverId, siteId) { mutableStateOf("") }
    var busy by remember(token, serverId, siteId) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf<CertificateDownload?>(null) }
    var confirmActivate by remember { mutableStateOf<SiteCertificate?>(null) }
    var confirmDelete by remember { mutableStateOf<SiteCertificate?>(null) }

    val doneMessage = stringResource(R.string.action_done)

    LaunchedEffect(token, serverId, siteId, page, refresh) {
        loading = true
        error = null
        result = null
        try {
            result = withContext(Dispatchers.IO) { PloiApi.certificates(token, serverId, siteId, page) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            result = null
            error = failure
        } finally {
            loading = false
        }
    }

    fun runAction(message: String, block: suspend () -> Unit) {
        busy = true
        error = null
        feedback = ""
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                feedback = message
                refresh++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure
            } finally {
                busy = false
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PanelSpacing.md),
        contentPadding = PaddingValues(vertical = PanelSpacing.sm)
    ) {
        item {
            ListToolbar(
                onRefresh = { refresh++ }, refreshEnabled = !loading && !busy,
                primaryLabel = stringResource(R.string.new_certificate), onPrimary = { creating = true }, primaryEnabled = !busy,
                summary = result?.let { stringResource(R.string.items_count, it.certificates.size) }
            )
        }
        if (loading) item { LoadingState(rows = 2) }
        error?.let { failure -> item { ErrorState(failure, onRetry = { refresh++ }, retryEnabled = !loading && !busy) } }
        if (feedback.isNotEmpty()) item { SuccessBanner(feedback) }
        result?.let { data ->
            if (data.certificates.isEmpty()) item { EmptyState(Icons.Outlined.Https, stringResource(R.string.empty_certificates)) }
            items(data.certificates, key = { it.id }) { certificate ->
                ResourceCard(
                    title = certificate.domain, icon = Icons.Outlined.Https,
                    subtitle = if (certificate.active) stringResource(R.string.g3_site_certificate_active) else null,
                    status = certificate.status,
                    facts = listOfNotNull(
                        certificate.type.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g3_site_type), it) },
                        certificate.expiresAt.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g3_site_expires), it) }
                    )
                ) {
                    CardAction(stringResource(R.string.certificate_download), icon = Icons.Outlined.Download, enabled = !busy, onClick = {
                        busy = true
                        error = null
                        feedback = ""
                        scope.launch {
                            try {
                                download = withContext(Dispatchers.IO) {
                                    PloiApi.downloadCertificate(token, serverId, siteId, certificate.id)
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure
                            } finally {
                                busy = false
                            }
                        }
                    })
                    if (!certificate.active) {
                        CardAction(stringResource(R.string.certificate_activate), icon = Icons.Outlined.CheckCircle,
                            enabled = !busy, onClick = { confirmActivate = certificate })
                    }
                    DangerAction(stringResource(R.string.certificate_delete), onClick = { confirmDelete = certificate }, enabled = !busy)
                }
            }
            item {
                PageBar(data.currentPage, data.lastPage, data.hasNext, onPrevious = { page-- }, onNext = { page++ }, enabled = !loading)
            }
        }
    }

    if (creating) {
        CreateCertificateDialog(
            busy = busy,
            onCreate = { request ->
                creating = false
                runAction(doneMessage) { PloiApi.createCertificate(token, serverId, siteId, request) }
            },
            onDismiss = { creating = false }
        )
    }
    download?.let { certificateDownload ->
        AlertDialog(
            onDismissRequest = { download = null },
            title = { Text(stringResource(R.string.certificate_download_title)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(PanelSpacing.sm)
                ) {
                    SiteFacts(listOfNotNull(
                        certificateDownload.certificatePath.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g3_site_path), it, mono = true) },
                        certificateDownload.expiresAt.takeIf { it.isNotBlank() }?.let { ResourceFact(stringResource(R.string.g3_site_expires), it) }
                    ))
                    ExpandableMono(certificateDownload.certificate, collapsedLines = 12)
                }
            },
            confirmButton = {
                Button(onClick = { download = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    confirmActivate?.let { certificate ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_certificate_activate, certificate.domain),
            confirmLabel = R.string.certificate_activate,
            onConfirmed = {
                confirmActivate = null
                runAction(doneMessage) { PloiApi.activateCertificate(token, serverId, siteId, certificate.id) }
            },
            onDismiss = { confirmActivate = null }
        )
    }
    confirmDelete?.let { certificate ->
        SensitiveConfirmDialog(
            lock = lock,
            activity = activity,
            message = stringResource(R.string.confirm_certificate_delete, certificate.domain),
            confirmLabel = R.string.certificate_delete,
            onConfirmed = {
                confirmDelete = null
                runAction(doneMessage) { PloiApi.deleteCertificate(token, serverId, siteId, certificate.id) }
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/** Create dialog for POST …/certificates: Let's Encrypt domain or custom certificate + key. */
@Composable
private fun CreateCertificateDialog(
    busy: Boolean,
    onCreate: (CreateCertificateRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var custom by remember { mutableStateOf(false) }
    var certificate by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_certificate)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PanelSpacing.md)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PanelSpacing.sm), verticalArrangement = Arrangement.spacedBy(PanelSpacing.xs)) {
                    FilterChip(selected = !custom, onClick = { custom = false },
                        label = { Text(stringResource(R.string.certificate_letsencrypt)) })
                    FilterChip(selected = custom, onClick = { custom = true },
                        label = { Text(stringResource(R.string.certificate_custom)) })
                }
                OutlinedTextField(
                    value = certificate, onValueChange = { certificate = it },
                    label = {
                        Text(
                            stringResource(
                                if (custom) R.string.certificate_content_label else R.string.certificate_domain_label
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (custom) {
                    OutlinedTextField(
                        value = privateKey, onValueChange = { privateKey = it },
                        label = { Text(stringResource(R.string.certificate_private_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (invalid) Text(stringResource(R.string.invalid_form), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val request = try {
                        CreateCertificateRequest(
                            type = if (custom) "custom" else "letsencrypt",
                            certificate = if (custom) certificate else certificate.trim(),
                            privateKey = privateKey
                        )
                    } catch (invalidRequest: IllegalArgumentException) {
                        null
                    }
                    if (request == null) invalid = true else onCreate(request)
                },
                enabled = !busy && certificate.isNotBlank() && (!custom || privateKey.isNotBlank())
            ) { Text(stringResource(R.string.submit_action)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
