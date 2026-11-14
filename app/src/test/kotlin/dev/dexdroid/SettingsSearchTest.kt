package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

class SettingsSearchTest {
    @Test
    fun relatedTermsFindExistingParentSettings() {
        assertEquals(listOf("background"), SettingsSearch.ids(" BATTERY "))
        assertEquals(listOf("app"), SettingsSearch.ids("scroll   SPEED"))
        assertEquals(listOf("developer"), SettingsSearch.ids("usb debugging"))
        assertEquals(listOf("shizuku"), SettingsSearch.ids("pairing"))
        assertEquals(listOf("protected"), SettingsSearch.ids("incognito"))
        assertTrue(SettingsSearch.ids("phone screen").contains("phone-screen"))
        assertTrue(SettingsSearch.ids("panel off").contains("phone-screen"))
        assertEquals(listOf("navigation"), SettingsSearch.ids("taskbar"))
        assertEquals(listOf("center-apps"), SettingsSearch.ids("hide icon"))
        assertEquals(listOf("pointer", "center"), SettingsSearch.ids("mouse"))
    }

    @Test
    fun emptyAndNoResultsAreDeterministic() {
        assertEquals(SettingsSearch.entries.map { it.id }, SettingsSearch.ids(" \n \t "))
        assertTrue(SettingsSearch.ids("unfindablexyz").isEmpty())
        assertTrue(SettingsSearch.ids("battery keyboard").isEmpty())
        assertEquals(SettingsSearch.ids("developer mode"), SettingsSearch.ids("DEVELOPER MODE"))
    }

    @Test
    fun allSettingsRemainDiscoverableIncludingRenamedShutdown() {
        assertEquals(SettingsSearch.entries.size, SettingsSearch.entries.map { it.id }.toSet().size)
        SettingsSearch.entries.forEach { assertTrue(SettingsSearch.matches(it.id, it.title)) }
        assertTrue(SettingsSearch.matches("developer", "Developer options"))
        assertTrue(SettingsSearch.matches("developer", "Turn off debugging"))
        assertTrue(SettingsSearch.matches("developer", "wireless"))
    }
}
