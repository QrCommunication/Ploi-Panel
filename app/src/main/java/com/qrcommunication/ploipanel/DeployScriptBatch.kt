package com.qrcommunication.ploipanel

import kotlinx.coroutines.CancellationException

internal data class DeployTarget(val serverId: Long, val siteId: Long, val domain: String, val serverName: String) {
    init {
        require(serverId > 0 && siteId > 0 && domain.isNotBlank())
    }
}

internal enum class DeployApplyStatus { VERIFIED, UNVERIFIED, FAILED, PREFLIGHT_FAILED, SKIPPED }
internal data class DeployApplyResult(
    val target: DeployTarget, val status: DeployApplyStatus, val failure: Throwable? = null
)
internal data class DeployBatchOutcome(val targets: List<DeployApplyResult>, val abortedBeforeWrite: Boolean)

internal interface DeployScriptGateway {
    suspend fun read(serverId: Long, siteId: Long): String
    suspend fun write(serverId: Long, siteId: Long, script: String)
}

/**
 * All Ploi operations are per site. Read all targets before the first mutation, then write
 * sequentially and verify each result. A failure after a PATCH may mean an unknown outcome:
 * report it, never retry a write automatically or claim success without reading back.
 */
internal class DeployScriptBatch(private val gateway: DeployScriptGateway) {
    suspend fun apply(
        script: String, selected: List<DeployTarget>, onProgress: suspend (Int, Int) -> Unit = { _, _ -> }
    ): DeployBatchOutcome {
        require(selected.isNotEmpty()) { "Select at least one site" }
        require(selected.distinctBy { it.serverId to it.siteId }.size == selected.size) { "Duplicate sites" }
        require(script.isNotBlank() && script.length <= DEPLOY_SCRIPT_MAX_LENGTH) { "Invalid deploy script" }
        val rendered = selected.map { target -> render(script, target) }
        val result = mutableListOf<DeployApplyResult>()
        for (target in selected) {
            try {
                gateway.read(target.serverId, target.siteId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                return DeployBatchOutcome(selected.map { site ->
                    DeployApplyResult(site, if (site == target) DeployApplyStatus.PREFLIGHT_FAILED else DeployApplyStatus.SKIPPED,
                        if (site == target) failure else null)
                }, abortedBeforeWrite = true)
            }
        }
        selected.forEachIndexed { index, target ->
            val content = rendered[index]
            try {
                gateway.write(target.serverId, target.siteId, content)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                result += DeployApplyResult(target, DeployApplyStatus.FAILED, failure)
                onProgress(index + 1, selected.size)
                if (failure is PloiHttpException && failure.status == 429) {
                    result += selected.drop(index + 1).map { DeployApplyResult(it, DeployApplyStatus.SKIPPED) }
                    return DeployBatchOutcome(result, abortedBeforeWrite = false)
                }
                return@forEachIndexed
            }
            val verified = try {
                gateway.read(target.serverId, target.siteId) == content
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            result += DeployApplyResult(target,
                if (verified) DeployApplyStatus.VERIFIED else DeployApplyStatus.UNVERIFIED)
            onProgress(index + 1, selected.size)
        }
        return DeployBatchOutcome(result, abortedBeforeWrite = false)
    }

    companion object {
        /** {{domain}} is always shell-quoted to avoid introducing shell metacharacters. */
        fun render(script: String, target: DeployTarget): String {
            require(target.domain.matches(Regex("[A-Za-z0-9.*-]{1,253}"))) { "Invalid domain placeholder" }
            val values = mapOf(
                "domain" to "'${target.domain}'",
                "server_id" to target.serverId.toString(),
                "site_id" to target.siteId.toString()
            )
            val output = Regex("\\{\\{([^{}]+)}}").replace(script) { match ->
                values[match.groupValues[1]] ?: throw IllegalArgumentException("Unknown placeholder")
            }
            require(output.isNotBlank() && output.length <= DEPLOY_SCRIPT_MAX_LENGTH) { "Rendered script too long" }
            return output
        }
    }
}
