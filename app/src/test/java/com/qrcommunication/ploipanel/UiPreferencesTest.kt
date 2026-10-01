package com.qrcommunication.ploipanel

import org.junit.Assert.assertEquals
import org.junit.Test

class UiPreferencesTest {
    private class FakePrefs : ProfilePrefs {
        val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    @Test fun defaultsToSystemAndPersistsSelections() {
        val prefs = FakePrefs()
        val settings = UiPreferences(prefs)
        assertEquals(AppTheme.SYSTEM, settings.theme())
        assertEquals(AppLanguage.SYSTEM, settings.language())
        settings.setTheme(AppTheme.DARK)
        settings.setLanguage(AppLanguage.FRENCH)
        val restored = UiPreferences(prefs)
        assertEquals(AppTheme.DARK, restored.theme())
        assertEquals(AppLanguage.FRENCH, restored.language())
        restored.setTheme(AppTheme.LIGHT)
        restored.setLanguage(AppLanguage.ENGLISH)
        assertEquals(AppTheme.LIGHT, settings.theme())
        assertEquals(AppLanguage.ENGLISH, settings.language())
    }

    @Test fun invalidValuesFailBackToSystem() {
        val prefs = FakePrefs()
        prefs.write(UiPreferences.KEY_THEME, "obsolete")
        prefs.write(UiPreferences.KEY_LANGUAGE, "obsolete")
        val settings = UiPreferences(prefs)
        assertEquals(AppTheme.SYSTEM, settings.theme())
        assertEquals(AppLanguage.SYSTEM, settings.language())
    }
}
