package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PloiApiParsingTest {
    @Test fun parsesServersAndPagination() {
        val page = PloiApi.parseServers("""{
          "data":[{"id":12,"name":"alpha","status":"Server active","ip_address":"192.0.2.1"}],
          "meta":{"current_page":2,"last_page":3,"total":51}
        }""")
        assertEquals(12L, page.servers.single().id)
        assertEquals("alpha", page.servers.single().name)
        assertEquals(2, page.currentPage)
        assertEquals(3, page.lastPage)
    }

    @Test fun parsesEmptyServerPage() {
        val page = PloiApi.parseServers("""{"data":[],"meta":{"current_page":1,"last_page":1}}""")
        assertTrue(page.servers.isEmpty())
        assertFalse(page.hasNext)
    }

    @Test fun parsesMonitoringNewestTimestampRegardlessOfOrder() {
        val sample = PloiApi.parseMonitoring("""{"data":[
           {"cpu":"8.0","ram":"21","disk":"40","load_average":"0.2","date":"2026-01-02 08:00:00"},
           {"cpu":"7.0","ram":"20","disk":"39","load_average":"0.1","date":"2026-01-02 07:55:00"}
        ]}""")
        assertEquals("8.0", sample?.cpu)
        assertEquals("2026-01-02 08:00:00", sample?.date)
    }

    @Test fun emptyMonitoringIsUnavailable() {
        assertEquals(null, PloiApi.parseMonitoring("""{"data":[]}"""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHeaderInjectionInToken() {
        PloiApi.validateToken("token\r\nInjected: yes")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidPageSize() {
        PloiApi.validatePageSize(51)
    }

    @Test fun parsesProviderPlansRegionsAndPagination() {
        val page = PloiApi.parseProviders("""{
          "data":[{"id":7,"name":"UpCloud","label":"Production","provider":{
            "name":"UpCloud","plans":[{"id":"1xCPU-1GB","name":"Starter","description":"1GB RAM"}],
            "regions":[{"id":"nl-ams1","name":"Amsterdam"}]
          }}],"meta":{"current_page":1,"last_page":2}
        }""")
        assertTrue(page.hasNext)
        assertEquals(7L, page.providers.single().id)
        assertEquals("1xCPU-1GB", page.providers.single().plans.single().id)
        assertEquals("nl-ams1", page.providers.single().regions.single().id)
    }

    @Test fun parsesProviderDetail() {
        val provider = PloiApi.parseProvider("""{"data":{"id":3,"name":"Hetzner","label":null,
           "provider":{"name":"Hetzner","plans":[],"regions":[]}}}""")
        assertEquals("Hetzner", provider.displayName)
        assertTrue(provider.plans.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedProviderPagination() {
        PloiApi.parseProviders("""{"data":[],"meta":{"current_page":2,"last_page":1}}""")
    }

    @Test fun parsesSitesAndPagination() {
        val page = PloiApi.parseSites("""{"data":[{"id":4,"server_id":9,"domain":"example.org",
            "status":"active","php_version":8.4,"web_directory":"/public","health_url":null,
            "disk_usage":{"human":"117 MB"}}],"meta":{"current_page":1,"last_page":2}}""")
        assertEquals(4L, page.sites.single().id)
        assertEquals("example.org", page.sites.single().domain)
        assertEquals("8.4", page.sites.single().phpVersion)
        assertEquals("117 MB", page.sites.single().diskUsage)
        assertTrue(page.hasNext)
    }

    @Test fun parsesSiteDetailWithNullAndMissingOptionalFields() {
        val site = PloiApi.parseSite("""{"data":{"id":5,"server_id":9,"domain":"hello.example",
            "status":"active","php_version":null,"web_directory":"/","health_url":null}}""")
        assertEquals(9L, site.serverId)
        assertEquals("", site.phpVersion)
        assertEquals("", site.healthUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidSiteIdentifier() {
        PloiApi.validateResourceId(0)
    }
}
