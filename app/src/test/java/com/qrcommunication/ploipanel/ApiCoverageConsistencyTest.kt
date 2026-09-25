package com.qrcommunication.ploipanel

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Machine-checkable guard for docs/api-coverage.json and docs/api-coverage.md:
 * counts, uniqueness and per-route status tables must stay in sync with the inventory.
 */
class ApiCoverageConsistencyTest {
    private fun findUpwards(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        fail("$relative not found above " + (System.getProperty("user.dir") ?: "."))
        throw IllegalStateException("unreachable")
    }

    private val json: JSONObject by lazy { JSONObject(findUpwards("docs/api-coverage.json").readText()) }
    private val markdown: String by lazy { findUpwards("docs/api-coverage.md").readText() }

    @Test fun countsMatchOperationEntries() {
        val operations = json.getJSONArray("operations")
        val counts = json.getJSONObject("counts")
        assertEquals(operations.length(), counts.getInt("documented_operations"))
        val unique = mutableSetOf<String>()
        (0 until operations.length()).forEach { index ->
            val operation = operations.getJSONObject(index)
            assertTrue(
                "Duplicate (method, path): ${operation.getString("method")} ${operation.getString("path")}",
                unique.add(operation.getString("method") + " " + operation.getString("path"))
            )
        }
        assertEquals(unique.size, counts.getInt("unique_method_path"))
    }

    @Test fun statusesAreDocumentedAndMarkdownTotalsMatchJson() {
        val operations = json.getJSONArray("operations")
        val knownStatuses = json.getJSONObject("status_meaning").keys().asSequence().toSet()
        val perStatus = mutableMapOf<String, Int>()
        (0 until operations.length()).forEach { index ->
            val operation = operations.getJSONObject(index)
            val status = operation.getString("status")
            assertTrue("Undocumented status: $status", status in knownStatuses)
            perStatus.merge(status, 1, Int::plus)
        }
        val notImplemented = perStatus["not_implemented"] ?: 0
        val implemented = perStatus["implemented_locally_unverified_live"] ?: 0
        assertTrue(
            "Markdown header not synced with JSON counts",
            markdown.contains("**$notImplemented non implémentées**") &&
                markdown.contains("**$implemented implémentées")
        )
    }

    @Test fun everyJsonRouteAppearsInMarkdownTable() {
        val operations = json.getJSONArray("operations")
        (0 until operations.length()).forEach { index ->
            val operation = operations.getJSONObject(index)
            val path = operation.getString("path")
            assertTrue("Route missing from api-coverage.md: $path", markdown.contains("`$path`"))
        }
    }

    @Test fun implementedRoutesHaveAndroidCallSites() {
        val sources = findUpwards("app/src/main/java/com/qrcommunication/ploipanel/PloiApi.kt").readText()
        val operations = json.getJSONArray("operations")
        (0 until operations.length()).forEach { index ->
            val operation = operations.getJSONObject(index)
            if (operation.getString("status") != "implemented_locally_unverified_live") return@forEach
            // Every implemented route's concrete path segments must appear in the client source.
            val segments = operation.getString("path")
                .removePrefix("/api")
                .split("/")
                .filter { it.isNotEmpty() && !it.startsWith("{") }
            segments.forEach { segment ->
                assertTrue(
                    "Route ${operation.getString("path")} marked implemented but '$segment' absent from PloiApi.kt",
                    sources.contains("/$segment")
                )
            }
        }
    }
}
