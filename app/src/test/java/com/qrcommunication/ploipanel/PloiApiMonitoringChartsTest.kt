package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PloiApiMonitoringChartsTest {
    @Test fun historyIsChronologicalAndLatestStillMatchesLegacyParser() {
        val json = """{"data":[
          {"cpu":"90","ram":"50","disk":"75","load_average":"1.1","date":"2026-05-01 12:10:00"},
          {"cpu":"10","ram":"20","disk":"30","load_average":"0.1","date":"2026-05-01 12:00:00"},
          {"cpu":"40","ram":"35","disk":"55","load_average":"0.5","date":"2026-05-01 12:05:00"}
        ]}"""
        val history = PloiApi.parseMonitoringHistory(json)
        assertEquals(listOf("10", "40", "90"), history.map { it.cpu })
        assertEquals(history.last(), PloiApi.parseMonitoring(json))
    }

    @Test fun emptyAndSingletonHistoryDoNotCreateSyntheticPoints() {
        assertTrue(PloiApi.parseMonitoringHistory("""{"data":[]}""").isEmpty())
        assertNull(PloiApi.parseMonitoring("""{"data":[]}"""))
        assertEquals(1, PloiApi.parseMonitoringHistory("""{"data":[{"cpu":1,"date":"2026-01-01"}]}""").size)
    }

    @Test fun monitoredServerStatisticsAreSortedAsWell() {
        val json = """{"data":[{"id":1,"name":"test","statistics":[
          {"cpu":"80","date":"2026-06-02 02:00:00"},
          {"cpu":"20","date":"2026-06-01 02:00:00"}
        ]}]}"""
        assertEquals(listOf("20", "80"), PloiApi.parseMonitoredServers(json).single().statistics.map { it.cpu })
    }

    @Test fun percentValuesAreClampedAndMalformedValuesStayUnknown() {
        assertEquals(0f, monitoringPercent("-6%"))
        assertEquals(100f, monitoringPercent("120.5"))
        assertEquals(34.25f, monitoringPercent(" 34.25 % "))
        assertEquals(0f, monitoringPercent("0"))
        listOf("", "null", "NaN", "Infinity", "unavailable", "3 monkeys", "20%%").forEach {
            assertNull(monitoringPercent(it))
        }
    }

    @Test fun uptimeTrendUsesOnlyRealFiniteChronologicalReadings() {
        val samples = listOf(
            UptimeResponse(0.8, "2026-01-03"),
            UptimeResponse(Double.NaN, "2026-01-02"),
            UptimeResponse(0.1, "2026-01-01"),
            UptimeResponse(-1.0, "2026-01-04")
        )
        assertEquals(listOf(0.1, 0.8), responseTrendValues(samples))
        assertTrue(responseTrendValues(listOf(samples[0])).size < 2)
    }

    @Test fun nullAndMalformedMetricFieldsDoNotBecomeFakeZero() {
        val sample = PloiApi.parseMonitoringHistory("""{"data":[
          {"cpu":null,"ram":"oops","disk":110,"date":"2026-01-01"}
        ]}""").single()
        assertNull(monitoringPercent(sample.cpu))
        assertNull(monitoringPercent(sample.ram))
        assertEquals(100f, monitoringPercent(sample.disk))
    }
}
