package dev.dexdroid;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class MonitorProfilesTest {
    private static final class Memory implements MonitorProfiles.Store {
        Map<String, String> values = new HashMap<>();

        public Map<String, String> read() {
            return new HashMap<>(values);
        }

        public void write(Map<String, String> next) {
            values = new HashMap<>(next);
        }
    }

    private final Memory memory = new Memory();
    private final MonitorProfiles profiles = new MonitorProfiles(memory);
    private final MonitorProfiles.Configuration landscape =
            new MonitorProfiles.Configuration(1920, 1200, 240, 0);
    private final MonitorProfiles.Configuration portrait =
            new MonitorProfiles.Configuration(1280, 800, 160, 3);

    @Test
    public void separateMonitorsRestoreDistinctProfilesAfterRecreation() {
        MonitorProfiles.Profile a = profiles.create("Desk", landscape, "monitor-a");
        MonitorProfiles.Profile b = profiles.create("Travel", portrait, "monitor-b");
        MonitorProfiles restarted = new MonitorProfiles(memory);
        assertEquals(a.id, restarted.bound("monitor-a").id);
        assertEquals(landscape, restarted.bound("monitor-a").configuration);
        assertEquals(b.id, restarted.bound("monitor-b").id);
        assertEquals(portrait, restarted.bound("monitor-b").configuration);
        assertNull(restarted.bound("unknown"));
        assertNull(restarted.bound(null));
    }

    @Test
    public void switchingBindsOnlyTheSelectedMonitorAndDoesNotEditSnapshots() {
        MonitorProfiles.Profile a = profiles.create("Desk", landscape, "a");
        MonitorProfiles.Profile b = profiles.create("Reading", portrait, "b");
        profiles.bind("a", b.id);
        assertEquals(b.id, profiles.bound("a").id);
        assertEquals(b.id, profiles.bound("b").id);
        assertEquals(landscape, profiles.find(a.id).configuration);
    }

    @Test
    public void renameAndUpdateAreExplicitAndLeaveOtherProfilesUntouched() {
        MonitorProfiles.Profile a = profiles.create("Desk", landscape, "a");
        MonitorProfiles.Profile b = profiles.create("Travel", portrait, "b");
        profiles.rename(a.id, " Office ");
        assertEquals("Office", profiles.find(a.id).name);
        profiles.update(a.id, portrait);
        assertEquals(portrait, profiles.find(a.id).configuration);
        assertEquals("Travel", profiles.find(b.id).name);
        assertEquals(b.id, profiles.bound("b").id);
        assertEquals(a.id, profiles.bound("a").id);
    }

    @Test
    public void deleteClearsAllReferencesButNotOtherProfilesOrBindings() {
        MonitorProfiles.Profile a = profiles.create("Desk", landscape, "a");
        MonitorProfiles.Profile b = profiles.create("Travel", portrait, "b");
        profiles.bind("c", a.id);
        profiles.delete(a.id);
        assertNull(profiles.bound("a"));
        assertNull(profiles.bound("c"));
        assertNull(profiles.find(a.id));
        assertEquals(b.id, profiles.bound("b").id);
        assertEquals(1, profiles.all().size());
    }

    @Test
    public void forgetRetainsProfilesAndOtherMonitorBindings() {
        MonitorProfiles.Profile a = profiles.create("Desk", landscape, "a");
        profiles.bind("b", a.id);
        profiles.forget("a");
        assertNull(profiles.bound("a"));
        assertEquals(a.id, profiles.bound("b").id);
        assertNotNull(profiles.find(a.id));
    }

    @Test
    public void invalidAndDuplicateNamesDoNotMutateStore() {
        MonitorProfiles.Profile a = profiles.create("Desk", landscape, "a");
        profiles.create("Travel", portrait, "b");
        Map<String, String> before = memory.read();
        for (String name :
                Arrays.asList(
                        "",
                        " ",
                        "\u3000",
                        "\u00a0",
                        "desk",
                        "x\ny",
                        new String(new char[49]).replace('\0', 'x'))) {
            assertThrows(
                    IllegalArgumentException.class, () -> profiles.create(name, portrait, "a"));
            assertEquals(before, memory.read());
        }
        assertThrows(IllegalArgumentException.class, () -> profiles.rename(a.id, " TRAVEL "));
        assertEquals(before, memory.read());
    }

    @Test
    public void namesRoundTripUnicodeAndDelimiterCharacters() {
        String name = "Bàn làm việc | 読書";
        MonitorProfiles.Profile profile = profiles.create(name, landscape, "desk");
        assertEquals(name, new MonitorProfiles(memory).find(profile.id).name);
    }

    @Test
    public void allFourRotationsAndDimensionBoundariesPersist() {
        for (int rotation = 0; rotation < 4; rotation++) {
            MonitorProfiles.Configuration c =
                    new MonitorProfiles.Configuration(
                            480, 4096, rotation % 2 == 0 ? 120 : 480, rotation);
            MonitorProfiles.Profile p =
                    profiles.create("Rotation " + rotation, c, "monitor" + rotation);
            assertEquals(c, profiles.bound("monitor" + rotation).configuration);
            assertEquals(p.id, profiles.bound("monitor" + rotation).id);
        }
    }

    @Test
    public void invalidConfigurationIsRejected() {
        for (int[] values :
                new int[][] {
                    {479, 1200, 240, 0},
                    {4097, 1200, 240, 0},
                    {1920, 479, 240, 0},
                    {1920, 4097, 240, 0},
                    {1920, 1200, 119, 0},
                    {1920, 1200, 481, 0},
                    {1920, 1200, 240, -1},
                    {1920, 1200, 240, 4}
                })
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new MonitorProfiles.Configuration(
                                    values[0], values[1], values[2], values[3]));
    }

    @Test
    public void stablePhysicalIdentityIgnoresModeOrderNamesAndPortDisplayIds() {
        String a =
                MonitorProfiles.identity(
                        "local:1234567890", "HDMI A", Arrays.asList("1920x1200@60"));
        String b =
                MonitorProfiles.identity(
                        "local:1234567890", "HDMI B", Arrays.asList("1280x800@60"));
        assertEquals(a, b);
        assertTrue(a.startsWith("physical."));
        assertNotEquals(
                a,
                MonitorProfiles.identity(
                        "local:1234567891", "HDMI A", Arrays.asList("1920x1200@60")));
    }

    @Test
    public void fallbackIdentityIsCanonicalAndDisclosesIndistinguishableMonitors() {
        String a =
                MonitorProfiles.identity(
                        null, "HDMI", Arrays.asList("1920x1200@60", "1280x800@60"));
        assertEquals(
                a,
                MonitorProfiles.identity(
                        "local:1",
                        "HDMI",
                        Arrays.asList("1280x800@60", "1920x1200@60", "1920x1200@60")));
        assertTrue(a.startsWith("capabilities."));
        assertNotEquals(
                a,
                MonitorProfiles.identity(
                        null, "Other monitor", Arrays.asList("1920x1200@60", "1280x800@60")));
        assertNotEquals(a, MonitorProfiles.identity(null, "HDMI", Arrays.asList("1920x1080@60")));
    }

    @Test
    public void legacySettingsImportOnceAndNeverResurrectDeletedOrForgottenProfiles() {
        profiles.importLegacy("a", "old-key", landscape);
        MonitorProfiles.Profile imported = profiles.bound("a");
        assertEquals(landscape, imported.configuration);
        profiles.importLegacy("a", "old-key", portrait);
        assertEquals(1, profiles.all().size());
        assertEquals(landscape, profiles.bound("a").configuration);
        profiles.delete(imported.id);
        profiles.importLegacy("a", "old-key", portrait);
        assertNull(profiles.bound("a"));
        assertTrue(profiles.all().isEmpty());
        profiles.importLegacy("b", "other-key", portrait);
        profiles.forget("b");
        profiles.importLegacy("b", "other-key", landscape);
        assertNull(profiles.bound("b"));
        assertEquals(1, profiles.all().size());
    }

    @Test
    public void migrationDoesNotReplaceAnExistingNamedBindingAndInvalidLegacyIsSkipped() {
        MonitorProfiles.Profile named = profiles.create("Office", portrait, "a");
        profiles.importLegacy("a", "old-key", landscape);
        assertEquals(named.id, profiles.bound("a").id);
        assertEquals(1, profiles.all().size());
        profiles.importLegacy("b", "invalid-key", null);
        assertNull(profiles.bound("b"));
        assertEquals(1, profiles.all().size());
    }

    @Test
    public void confirmedWorkingSettingsSurviveRestartWithoutChangingNamedSnapshots() {
        MonitorProfiles.Profile named = profiles.create("Desk", landscape, "a");
        profiles.importLegacy("a", "old-a", null);
        profiles.remember("a", portrait);
        MonitorProfiles restarted = new MonitorProfiles(memory);
        assertEquals(portrait, restarted.remembered("a"));
        assertEquals(named.id, restarted.bound("a").id);
        assertEquals(landscape, restarted.find(named.id).configuration);
        assertNull(restarted.remembered("b"));
        restarted.bind("a", named.id);
        assertEquals(landscape, restarted.remembered("a"));
    }

    @Test
    public void ordinaryKeepAfterForgetAndMigrationDoesNotDependOnLegacyImport() {
        profiles.importLegacy("a", "legacy-a", landscape);
        profiles.forget("a");
        profiles.remember("a", portrait);
        profiles.importLegacy("a", "legacy-a", landscape);
        MonitorProfiles restarted = new MonitorProfiles(memory);
        assertEquals(portrait, restarted.remembered("a"));
        assertNull(restarted.bound("a"));
        assertEquals(1, restarted.all().size());
    }

    @Test
    public void explicitSaveForgetDeleteAndUpdateClearOnlyIntendedWorkingSettings() {
        MonitorProfiles.Profile a = profiles.create("Desk", landscape, "a");
        profiles.bind("b", a.id);
        profiles.remember("a", portrait);
        profiles.remember("b", portrait);
        profiles.update(a.id, landscape);
        profiles.clearWorking("a");
        assertEquals(landscape, profiles.remembered("a"));
        assertEquals(portrait, profiles.remembered("b"));
        profiles.remember("a", portrait);
        profiles.create("Current", landscape, "a");
        assertEquals(landscape, profiles.remembered("a"));
        profiles.forget("a");
        assertNull(profiles.remembered("a"));
        profiles.delete(a.id);
        assertNull(profiles.remembered("b"));
        assertEquals(1, profiles.all().size());
    }

    @Test
    public void corruptWorkingSettingsFallBackToSnapshotOrSafeDefaults() {
        profiles.create("Desk", landscape, "a");
        for (String record : Arrays.asList("bad", "1|2|3|4", "1920|1200|240|9", "x|1200|240|0")) {
            memory.values.put("w.a", record);
            memory.values.put("w.unknown", record);
            assertEquals(landscape, profiles.remembered("a"));
            assertNull(profiles.remembered("unknown"));
        }
    }

    @Test
    public void corruptOrMissingRecordsFallBackWithoutDamagingValidProfiles() {
        MonitorProfiles.Profile valid = profiles.create("Office", landscape, "a");
        memory.values.put("p.invalid-id", "garbage");
        memory.values.put("p." + java.util.UUID.randomUUID(), "1|1920|1200|999|0|T2ZmaWNl");
        memory.values.put("m.bad", "invalid-id");
        memory.values.put("p.null", "1|1920|1200|240|0|T2ZmaWNl");
        memory.values.put("p." + java.util.UUID.randomUUID(), "1|1920|1200|240|0|/w==");
        assertNull(profiles.bound("bad"));
        assertNull(profiles.find("missing"));
        assertNull(profiles.find(null));
        assertEquals(1, profiles.all().size());
        assertEquals(valid.id, profiles.bound("a").id);
        assertThrows(IllegalArgumentException.class, () -> profiles.bind("b", "missing"));
        assertThrows(IllegalArgumentException.class, () -> profiles.update("missing", portrait));
    }
}
