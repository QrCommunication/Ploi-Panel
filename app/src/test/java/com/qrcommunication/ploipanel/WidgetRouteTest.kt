package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetRouteTest {
    @Test fun singleServerRouteIncludesProfileAndServer() {
        assertEquals(WidgetRoute("profile-1", 42), widgetRouteFor("profile-1", 42))
    }

    @Test fun multiServerRouteOpensAuthenticatedServerList() {
        assertEquals(WidgetRoute("profile-1", null), widgetRouteFor("profile-1", -1))
    }

    @Test fun missingProfileCannotOpenWidgetRoute() {
        assertNull(widgetRouteFor(null, 42))
        assertNull(widgetRouteFor("", 42))
    }
}
