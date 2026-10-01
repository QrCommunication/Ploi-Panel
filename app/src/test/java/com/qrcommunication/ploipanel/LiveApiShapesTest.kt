package com.qrcommunication.ploipanel

import com.qrcommunication.ploipanel.widget.isStale
import com.qrcommunication.ploipanel.widget.sampleTime
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/**
 * Shapes captured from the live Ploi API (values anonymised, structure unchanged). These cover two
 * bugs found only against the real account: the monitored overview's named-series payload and the
 * zone-less monitor timestamp.
 */
class LiveApiShapesTest {
    @Test fun monitoredOverviewNamedSeriesBecomeSamples() {
        val json = """{"data":[{"id":1,"name":"web","ip":"10.0.0.1","url":"u","statistics":[
            {"name":"Disk","data":{"23:50":22,"23:55":23,"00:00":23}},
            {"name":"CPU","data":{"23:50":0.7,"23:55":5.5,"00:00":1}},
            {"name":"RAM","data":{"23:50":52.21,"23:55":51.38,"00:00":null}}]}]}"""
        val samples = PloiApi.parseMonitoredServers(json).single().statistics
        assertEquals(3, samples.size)
        // Order is Ploi's, not lexical: 23:50, 23:55, then 00:00 after midnight.
        assertEquals(listOf("23:50", "23:55", "00:00"), samples.map { it.date })
        assertEquals(listOf("0.7", "5.5", "1"), samples.map { it.cpu })
        assertEquals("22", samples.first().disk)
        assertEquals("a null reading stays unknown", "", samples.last().ram)
        assertNull(monitoringPercent(samples.last().ram))
    }

    @Test fun clockLabelsOrderAcrossMidnightRegardlessOfInputOrder() {
        assertEquals(listOf("23:50", "23:55", "00:00", "00:05"), orderClockLabels(listOf("00:05", "23:55", "00:00", "23:50")))
        assertEquals(listOf("00:47", "00:52", "01:42"), orderClockLabels(listOf("01:42", "00:47", "00:52")))
        assertEquals(listOf("10:00", "x"), orderClockLabels(listOf("x", "10:00")))
    }

    @Test fun olderPerSampleShapeStillParses() {
        val samples = PloiApi.parseMonitoredStatistics(JSONArray("""[{"cpu":20,"ram":1,"disk":2,"date":"b"},{"cpu":10,"date":"a"}]"""))
        assertEquals(listOf("10", "20"), samples.map { it.cpu })
    }

    @Test fun projectsWithEmbeddedServerObjectsParse() {
        // Live shape (the documented one lists bare IDs): this used to fail as "malformed payload".
        val json = """{"data":[{"id":13421,"title":"Praticonnect","servers":[
            {"id":105361,"name":"praticonnect","status":"active","ip":"62.0.0.1"},
            {"id":110501,"name":"praticonnect-staging","status":"active","ip":"62.0.0.2"}],
            "sites":[],"created_at":"2026-03-07 11:25:55"},
            {"id":8606,"title":"Giga Apps","servers":[7,8],"sites":[{"id":3,"root_domain":"a.example"}],"created_at":""}],
            "meta":{"current_page":1,"last_page":1}}"""
        val page = PloiApi.parseProjects(json)
        assertEquals(listOf(105361L, 110501L), page.projects[0].serverIds)
        assertEquals("praticonnect-staging", page.projects[0].serverNames[110501L])
        assertEquals(listOf(7L, 8L), page.projects[1].serverIds)
        assertEquals("a.example", page.projects[1].sites.single().rootDomain)
    }

    @Test fun zoneLessMonitorTimestampIsUtc() {
        // "/monitor" sends 2026-09-29 23:42:15.000 for a reading shown as 01:42 in CEST.
        val millis = sampleTime("2026-09-29 23:42:15.000")
        assertEquals(1790725335000L, millis)
        assertEquals(1790725335000L, sampleTime("2026-09-29 23:42:15"))
        assertEquals(1790725335000L, sampleTime("2026-09-29T23:42:15Z"))
        assertNull(sampleTime("23:42"))
        assertNull(sampleTime(""))
    }

    @Test fun realTimestampsAreNoLongerAlwaysStale() {
        val sample = MonitorSample("1", "2", "3", "0", "2026-09-29 23:42:15.000")
        assertFalse(isStale(sample, 1790725335000L + 5 * 60_000L))
        assertTrue(isStale(sample, 1790725335000L + 25 * 60_000L))
    }

    @Test fun sampleTimeIsShownInLocalZone() {
        val text = formatSampleTime("2026-09-29 23:42:15.000", Locale.FRANCE, TimeZone.getTimeZone("Europe/Paris"))
        assertTrue(text, text.contains("01:42"))
        assertEquals("raw text kept when unparseable", "00:47", formatSampleTime("00:47"))
    }
}
