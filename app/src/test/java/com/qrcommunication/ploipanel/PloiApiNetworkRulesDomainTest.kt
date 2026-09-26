package com.qrcommunication.ploipanel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Parsing, validation and wire-level coverage for every documented route of the
 * `network-rules` domain (/api/servers/{server}/network-rules, 4 routes).
 */
class PloiApiNetworkRulesDomainTest {
    private val token = "test-token"
    private val recorded = CopyOnWriteArrayList<HttpRequest>()
    private var scriptedBody = "{}"

    private fun installFakeClient() {
        val transport = HttpTransport { request ->
            recorded.add(request)
            HttpResponse(200, scriptedBody, emptyMap())
        }
        PloiApi.httpClient = PloiHttpClient(transport = transport)
    }

    @After fun restoreClient() {
        PloiApi.httpClient = PloiHttpClient()
    }

    /** Documented list shape (developers.ploi.io/network-rules/list-network-rules). */
    private fun ruleListJson(): String = JSONObject()
        .put(
            "data",
            JSONArray().put(
                JSONObject()
                    .put("id", 1)
                    .put("name", "Name Rule")
                    .put("port", 100)
                    .put("from_ip_address", JSONObject.NULL)
                    .put("rule_type", "allow")
                    .put("status", "active")
                    .put("created_at", "2021-01-21 09:09:59")
            )
        )
        .put("meta", JSONObject().put("current_page", 1).put("last_page", 1))
        .toString()

    /** Documented single-rule shape (get + create responses share it). */
    private fun ruleJson(): String = JSONObject()
        .put(
            "data",
            JSONObject()
                .put("id", 1)
                .put("name", "My Rule")
                .put("port", 1000)
                .put("from_ip_address", "127.0.0.1")
                .put("rule_type", "allow")
                .put("status", "created")
                .put("created_at", "2021-01-21 09:09:59")
        )
        .toString()

    // ---- GET /api/servers/{server}/network-rules ----

    @Test fun parsesNetworkRuleListWithDocumentedShape() {
        val page = PloiApi.parseNetworkRules(ruleListJson())
        assertEquals(1, page.currentPage)
        assertFalse(page.hasNext)
        val rule = page.rules.single()
        assertEquals(1L, rule.id)
        assertEquals("Name Rule", rule.name)
        assertEquals("100", rule.port) // documented as a number, kept as text for ranges
        assertEquals("", rule.fromIpAddress) // documented null tolerated
        assertEquals("allow", rule.ruleType)
        assertEquals("active", rule.status)
        assertEquals("2021-01-21 09:09:59", rule.createdAt)
        assertEquals("", rule.protocol) // absent from the documented response shape
    }

    @Test fun networkRulesHitsDocumentedRouteWithPagination() {
        installFakeClient()
        scriptedBody = ruleListJson()
        val page = PloiApi.networkRules(token, serverId = 7, page = 2, perPage = 50)
        assertEquals(1, page.rules.size)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/7/network-rules?page=2&per_page=50", request.url)
        recorded.clear()
        PloiApi.networkRules(token, serverId = 7)
        assertEquals("https://ploi.io/api/servers/7/network-rules?page=1&per_page=15", recorded.single().url)
        assertThrowsIAE { PloiApi.networkRules(token, serverId = 0) }
        assertThrowsIAE { PloiApi.networkRules(token, serverId = -1) }
        assertThrowsIAE { PloiApi.networkRules(token, serverId = 7, page = 0) }
        assertThrowsIAE { PloiApi.networkRules(token, serverId = 7, perPage = 51) }
    }

    // ---- GET /api/servers/{server}/network-rules/{id} ----

    @Test fun networkRuleReadsDocumentedRoute() {
        installFakeClient()
        scriptedBody = ruleJson()
        val rule = PloiApi.networkRule(token, serverId = 7, ruleId = 1)
        assertEquals("My Rule", rule.name)
        assertEquals("1000", rule.port)
        assertEquals("127.0.0.1", rule.fromIpAddress)
        assertEquals("created", rule.status)
        val request = recorded.single()
        assertEquals("GET", request.method)
        assertEquals("https://ploi.io/api/servers/7/network-rules/1", request.url)
        assertThrowsIAE { PloiApi.networkRule(token, serverId = 0, ruleId = 1) }
        assertThrowsIAE { PloiApi.networkRule(token, serverId = 7, ruleId = 0) }
    }

    // ---- POST /api/servers/{server}/network-rules ----

    @Test fun createNetworkRulePostsDocumentedRequiredFields() {
        installFakeClient()
        scriptedBody = ruleJson()
        val rules = PloiApi.createNetworkRule(
            token, 7,
            CreateNetworkRuleRequest(name = "My Rule", port = "1000", protocol = "tcp", ruleType = "allow")
        )
        assertEquals(1, rules.single().id)
        val request = recorded.single()
        assertEquals("POST", request.method)
        assertEquals("https://ploi.io/api/servers/7/network-rules", request.url)
        val body = JSONObject(request.body.orEmpty())
        assertEquals("My Rule", body.getString("name"))
        assertEquals("1000", body.getString("port"))
        assertEquals("tcp", body.getString("type"))
        assertEquals("allow", body.getString("rule_type"))
        assertEquals(4, body.length()) // from_ip_address is optional and omitted when blank
    }

    @Test fun createNetworkRuleSendsOptionalFromIpWhenProvided() {
        installFakeClient()
        scriptedBody = ruleJson()
        PloiApi.createNetworkRule(
            token, 7,
            CreateNetworkRuleRequest(
                name = "My Rule", port = "1000:2000", protocol = "udp", ruleType = "deny",
                fromIpAddress = "127.0.0.1"
            )
        )
        val body = JSONObject(recorded.single().body.orEmpty())
        assertEquals("1000:2000", body.getString("port"))
        assertEquals("udp", body.getString("type"))
        assertEquals("deny", body.getString("rule_type"))
        assertEquals("127.0.0.1", body.getString("from_ip_address"))
    }

    @Test fun createNetworkRuleParsesDocumentedArrayResponseForMultipleIps() {
        installFakeClient()
        // Documented: several comma-separated IPs create one rule per IP, response is an array.
        val entry = JSONObject()
            .put("id", 1)
            .put("name", "My Rule")
            .put("port", 1000)
            .put("from_ip_address", "127.0.0.1")
            .put("rule_type", "allow")
            .put("status", "created")
            .put("created_at", "2021-01-21 09:09:59")
        scriptedBody = JSONObject()
            .put("data", JSONArray().put(entry).put(JSONObject(entry.toString()).put("id", 2)))
            .toString()
        val rules = PloiApi.createNetworkRule(
            token, 7,
            CreateNetworkRuleRequest(
                name = "My Rule", port = "1000", protocol = "tcp", ruleType = "allow",
                fromIpAddress = "127.0.0.1,127.0.0.2"
            )
        )
        assertEquals(listOf(1L, 2L), rules.map { it.id })
    }

    @Test fun createNetworkRuleValidatesDocumentedConstraints() {
        assertThrowsIAE { CreateNetworkRuleRequest("", "1000", "tcp", "allow") }
        assertThrowsIAE { CreateNetworkRuleRequest("   ", "1000", "tcp", "allow") }
        // documented: name max 100 characters
        assertThrowsIAE { CreateNetworkRuleRequest("n".repeat(101), "1000", "tcp", "allow") }
        CreateNetworkRuleRequest("n".repeat(100), "1000", "tcp", "allow")
        // documented: port 1-65535 or a range like 1000:2000
        assertThrowsIAE { CreateNetworkRuleRequest("Rule", "", "tcp", "allow") }
        assertThrowsIAE { CreateNetworkRuleRequest("Rule", "0", "tcp", "allow") }
        assertThrowsIAE { CreateNetworkRuleRequest("Rule", "65536", "tcp", "allow") }
        assertThrowsIAE { CreateNetworkRuleRequest("Rule", "abc", "tcp", "allow") }
        assertThrowsIAE { CreateNetworkRuleRequest("Rule", "2000:1000", "tcp", "allow") } // descending range
        assertThrowsIAE { CreateNetworkRuleRequest("Rule", "1:2:3", "tcp", "allow") }
        assertThrowsIAE { CreateNetworkRuleRequest("Rule", "0:2000", "tcp", "allow") }
        CreateNetworkRuleRequest("Rule", "1", "tcp", "allow")
        CreateNetworkRuleRequest("Rule", "65535", "tcp", "allow")
        CreateNetworkRuleRequest("Rule", "1000:2000", "tcp", "allow")
        CreateNetworkRuleRequest("Rule", "1000:1000", "tcp", "allow")
        // documented: type tcp or udp, rule_type allow or deny
        assertThrowsIAE { CreateNetworkRuleRequest("Rule", "1000", "icmp", "allow") }
        assertThrowsIAE { CreateNetworkRuleRequest("Rule", "1000", "TCP", "allow") }
        assertThrowsIAE { CreateNetworkRuleRequest("Rule", "1000", "tcp", "drop") }
        assertThrowsIAE { CreateNetworkRuleRequest("Rule", "1000", "tcp", "ALLOW") }
        // documented: up to 20 comma-separated IP addresses
        assertThrowsIAE { CreateNetworkRuleRequest("Rule", "1000", "tcp", "allow", "10.0.0.1,,10.0.0.2") }
        assertThrowsIAE {
            CreateNetworkRuleRequest("Rule", "1000", "tcp", "allow", (1..21).joinToString(",") { "10.0.0.$it" })
        }
        CreateNetworkRuleRequest("Rule", "1000", "tcp", "allow", (1..20).joinToString(",") { "10.0.0.$it" })
        assertThrowsIAE {
            PloiApi.createNetworkRule(token, 0, CreateNetworkRuleRequest("Rule", "1000", "tcp", "allow"))
        }
    }

    // ---- DELETE /api/servers/{server}/network-rules/{id} ----

    @Test fun deleteNetworkRuleHitsDocumentedRouteAndToleratesEmptyBody() {
        installFakeClient()
        scriptedBody = "" // documented: empty response body
        PloiApi.deleteNetworkRule(token, serverId = 7, ruleId = 1)
        val request = recorded.single()
        assertEquals("DELETE", request.method)
        assertEquals("https://ploi.io/api/servers/7/network-rules/1", request.url)
        assertNull(request.body)
        assertThrowsIAE { PloiApi.deleteNetworkRule(token, serverId = 0, ruleId = 1) }
        assertThrowsIAE { PloiApi.deleteNetworkRule(token, serverId = 7, ruleId = -1) }
    }

    // ---- Error handling ----

    @Test fun malformedNetworkRulePayloadSurfacesTypedError() {
        installFakeClient()
        scriptedBody = """{"unexpected":true}"""
        try {
            PloiApi.networkRule(token, 7, 1)
            fail("Expected PloiMalformedPayloadException")
        } catch (expected: PloiMalformedPayloadException) {
            // typed error, not a raw JSONException
        }
        try {
            PloiApi.parseNetworkRules("""{"data":[{"rule_type":"allow"}]}""") // missing documented id/name
            fail("Expected JSONException on missing documented fields")
        } catch (expected: org.json.JSONException) {
            // required fields are enforced by the parser
        }
        try {
            PloiApi.parseNetworkRuleCreateResponse("""{"data":"nope"}""")
            fail("Expected JSONException on unexpected create payload")
        } catch (expected: org.json.JSONException) {
            // only object or array documented
        }
    }

    @Test fun paginationMetaDrivesHasNext() {
        val twoPages = JSONObject(ruleListJson())
            .put("meta", JSONObject().put("current_page", 1).put("last_page", 2))
            .toString()
        assertTrue(PloiApi.parseNetworkRules(twoPages).hasNext)
    }

    private fun assertThrowsIAE(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // validation worked
        }
    }
}
