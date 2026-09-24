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
}
