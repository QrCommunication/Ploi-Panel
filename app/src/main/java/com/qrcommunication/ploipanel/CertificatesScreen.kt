package com.qrcommunication.ploipanel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
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

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refresh++ }, enabled = !loading && !busy) {
                Text(stringResource(R.string.reload))
            }
            OutlinedButton(onClick = { creating = true }, enabled = !busy) {
                Text(stringResource(R.string.new_certificate))
            }
        }
        if (loading) CircularProgressIndicator()
        if (error != null) ApiErrorText(error!!)
        if (feedback.isNotEmpty()) Text(feedback)
        result?.let { data ->
            if (data.certificates.isEmpty()) Text(stringResource(R.string.empty_certificates))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { page-- }, enabled = page > 1) {
                    Text(stringResource(R.string.previous))
                }
                Text(
                    stringResource(R.string.page, data.currentPage.toString(), data.lastPage.toString()),
                    Modifier.padding(top = 12.dp)
                )
                OutlinedButton(onClick = { page++ }, enabled = data.hasNext) {
                    Text(stringResource(R.string.next))
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.certificates, key = { it.id }) { certificate ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(certificate.domain, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.certificate_status, certificate.type, certificate.status))
                            if (certificate.expiresAt.isNotBlank()) {
                                Text(stringResource(R.string.certificate_expires, certificate.expiresAt))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        busy = true
                                        error = null
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
                                    },
                                    enabled = !busy
                                ) { Text(stringResource(R.string.certificate_download)) }
                                if (!certificate.active) {
                                    OutlinedButton(onClick = { confirmActivate = certificate }, enabled = !busy) {
                                        Text(stringResource(R.string.certificate_activate))
                                    }
                                }
                                OutlinedButton(onClick = { confirmDelete = certificate }, enabled = !busy) {
                                    Text(
                                        stringResource(R.string.certificate_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (certificateDownload.certificatePath.isNotBlank()) {
                        Text(certificateDownload.certificatePath, style = MaterialTheme.typography.bodySmall)
                    }
                    if (certificateDownload.expiresAt.isNotBlank()) {
                        Text(stringResource(R.string.certificate_expires, certificateDownload.expiresAt))
                    }
                    Text(certificateDownload.certificate, style = MaterialTheme.typography.bodySmall)
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { custom = false }, enabled = custom) {
                        Text(stringResource(R.string.certificate_letsencrypt))
                    }
                    OutlinedButton(onClick = { custom = true }, enabled = !custom) {
                        Text(stringResource(R.string.certificate_custom))
                    }
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
