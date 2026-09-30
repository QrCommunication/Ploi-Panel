package com.qrcommunication.ploipanel.screenshots

import com.qrcommunication.ploipanel.PloiApi
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in (-Pscreenshots) check that every read parser accepts the payload the live account really
 * returns. Payloads are captured beforehand into PLOI_LIVE_SAMPLES_DIR (mode 700, never committed).
 * Prints one line per endpoint so a mismatch between the docs and reality is visible at once.
 */
class LiveParserAudit {
    @Test fun everyReadParserAcceptsLivePayloads() {
        val dir = System.getenv("PLOI_LIVE_SAMPLES_DIR")?.let(::File)
        assumeTrue("PLOI_LIVE_SAMPLES_DIR not set", dir?.isDirectory == true)
        val parsers: Map<String, (String) -> Any?> = mapOf(
            "servers" to PloiApi::parseServers, "server" to PloiApi::parseServerDetail,
            "logs" to PloiApi::parseServerLogs, "monitor" to PloiApi::parseMonitoringHistory,
            "monitored" to PloiApi::parseMonitoredServers, "sites" to PloiApi::parseSites, "site" to PloiApi::parseSite,
            "databases" to PloiApi::parseDatabases, "daemons" to PloiApi::parseDaemons, "crontabs" to PloiApi::parseCrontabs,
            "network" to PloiApi::parseNetworkRules, "sysusers" to PloiApi::parseSystemUsers, "sshkeys" to PloiApi::parseSshKeys,
            "insights" to PloiApi::parseInsights, "user" to PloiApi::parseUser, "providers" to PloiApi::parseProviders,
            "projects" to PloiApi::parseProjects, "scripts" to PloiApi::parseScripts, "statuspages" to PloiApi::parseStatusPages,
            "templates" to PloiApi::parseWebserverTemplates, "backups_db" to PloiApi::parseDatabaseBackups,
            "backups_file" to PloiApi::parseFileBackups, "certificates" to PloiApi::parseCertificates,
            "redirects" to PloiApi::parseRedirects, "queues" to PloiApi::parseQueueWorkers, "aliases" to PloiApi::parseAliases,
            "authusers" to PloiApi::parseAuthUsers, "deployscript" to PloiApi::parseDeployScript,
            "sitelog" to PloiApi::parseSiteLogs, "repository" to PloiApi::parseSiteRepository,
            "monitors" to PloiApi::parseSiteMonitors, "tenants" to PloiApi::parseTenants,
            "notif" to PloiApi::parseNotificationChannels, "sourcecontrol" to PloiApi::parseSourceControlProviders,
            "backupcfg" to PloiApi::parseBackupConfigurations
        )
        val failures = mutableListOf<String>()
        parsers.forEach { (name, parse) ->
            val file = File(dir, "$name.json")
            if (!file.isFile) { println("SKIP  $name (no sample)"); return@forEach }
            try {
                val result = parse(file.readText())
                val summary = result.toString().let { if (it.length > 140) it.take(140) + "…" else it }
                println("OK    $name -> $summary")
            } catch (failure: Throwable) {
                println("FAIL  $name -> ${failure.javaClass.simpleName}: ${failure.message}")
                failures += "$name: ${failure.javaClass.simpleName}: ${failure.message}"
            }
        }
        check(failures.isEmpty()) { failures.joinToString("\n") }
    }
}
