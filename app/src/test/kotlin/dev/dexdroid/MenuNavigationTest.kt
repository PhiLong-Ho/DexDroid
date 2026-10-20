package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

open class MenuNavigationTest {
    @Test
    open fun settingsSubmenusReturnThroughVisitedParents() {
        val navigation = MenuNavigation<String?>()
        navigation.open("controls")
        navigation.open("settings")
        navigation.open("profiles")
        assertEquals("settings", navigation.back({ x -> true }))
        assertEquals("controls", navigation.back({ x -> true }))
        assertNull(navigation.back({ x -> true }))
    }

    @Test
    open fun closeAndRootShortcutDiscardOldJourney() {
        val navigation = MenuNavigation<String?>()
        navigation.open("settings")
        navigation.open("diagnostics")
        navigation.clear()
        navigation.open("apps")
        assertFalse(navigation.hasParent({ x -> true }))
        assertNull(navigation.back({ x -> true }))
    }

    @Test
    open fun invalidatedPickerIsSkippedWithoutLosingValidLayoutParent() {
        val navigation = MenuNavigation<String?>()
        navigation.open("layout")
        navigation.open("apps")
        navigation.open("orientation")
        assertEquals("layout", navigation.back({ x -> x != "apps" }))
        assertNull(navigation.back({ x -> true }))
    }

    @Test
    open fun repeatedDestinationDoesNotCreateBackLoop() {
        val navigation = MenuNavigation<String?>()
        navigation.open("settings")
        navigation.open("profiles")
        navigation.open("profiles")
        assertEquals("settings", navigation.back({ x -> true }))
        assertNull(navigation.back({ x -> true }))
    }

    @Test
    open fun rebuildingReturnedPagePreservesItsAncestors() {
        val navigation = MenuNavigation<String?>()
        navigation.open("controls")
        navigation.open("settings-old")
        navigation.open("diagnostics")
        assertEquals("settings-old", navigation.back({ x -> true }))
        navigation.replace("settings-new")
        navigation.open("display")
        assertEquals("settings-new", navigation.back({ x -> true }))
        assertEquals("controls", navigation.back({ x -> true }))
    }

    @Test
    open fun unavailableParentsDoNotOfferMisleadingUpNavigation() {
        val navigation = MenuNavigation<String?>()
        navigation.open("obsolete-apps")
        navigation.open("settings")
        assertFalse(navigation.hasParent({ x -> !x!!.startsWith("obsolete") }))
        assertNull(navigation.back({ x -> !x!!.startsWith("obsolete") }))
        assertNull(navigation.current())
    }
}
