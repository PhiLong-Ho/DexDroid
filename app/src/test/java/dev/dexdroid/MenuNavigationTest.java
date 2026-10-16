package dev.dexdroid;

import static org.junit.Assert.*;

import org.junit.Test;

public class MenuNavigationTest {
    @Test
    public void settingsSubmenusReturnThroughVisitedParents() {
        MenuNavigation<String> navigation = new MenuNavigation<>();
        navigation.open("controls");
        navigation.open("settings");
        navigation.open("profiles");
        assertEquals("settings", navigation.back(x -> true));
        assertEquals("controls", navigation.back(x -> true));
        assertNull(navigation.back(x -> true));
    }

    @Test
    public void closeAndRootShortcutDiscardOldJourney() {
        MenuNavigation<String> navigation = new MenuNavigation<>();
        navigation.open("settings");
        navigation.open("diagnostics");
        navigation.clear();
        navigation.open("apps");
        assertFalse(navigation.hasParent(x -> true));
        assertNull(navigation.back(x -> true));
    }

    @Test
    public void invalidatedPickerIsSkippedWithoutLosingValidLayoutParent() {
        MenuNavigation<String> navigation = new MenuNavigation<>();
        navigation.open("layout");
        navigation.open("apps");
        navigation.open("orientation");
        assertEquals("layout", navigation.back(x -> !x.equals("apps")));
        assertNull(navigation.back(x -> true));
    }

    @Test
    public void repeatedDestinationDoesNotCreateBackLoop() {
        MenuNavigation<String> navigation = new MenuNavigation<>();
        navigation.open("settings");
        navigation.open("profiles");
        navigation.open("profiles");
        assertEquals("settings", navigation.back(x -> true));
        assertNull(navigation.back(x -> true));
    }

    @Test
    public void rebuildingReturnedPagePreservesItsAncestors() {
        MenuNavigation<String> navigation = new MenuNavigation<>();
        navigation.open("controls");
        navigation.open("settings-old");
        navigation.open("diagnostics");
        assertEquals("settings-old", navigation.back(x -> true));
        navigation.replace("settings-new");
        navigation.open("display");
        assertEquals("settings-new", navigation.back(x -> true));
        assertEquals("controls", navigation.back(x -> true));
    }

    @Test
    public void unavailableParentsDoNotOfferMisleadingUpNavigation() {
        MenuNavigation<String> navigation = new MenuNavigation<>();
        navigation.open("obsolete-apps");
        navigation.open("settings");
        assertFalse(navigation.hasParent(x -> !x.startsWith("obsolete")));
        assertNull(navigation.back(x -> !x.startsWith("obsolete")));
        assertNull(navigation.current());
    }
}
