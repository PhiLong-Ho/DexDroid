package dev.dexdroid

import java.util.Arrays
import java.util.HashMap
import org.junit.Assert.*
import org.junit.Test

open class MonitorProfilesTest {

    private val memory: Memory = Memory()
    private val profiles: MonitorProfiles = MonitorProfiles(memory)
    private val landscape: MonitorProfiles.Configuration =
        MonitorProfiles.Configuration(1920, 1200, 240, 0)
    private val portrait: MonitorProfiles.Configuration =
        MonitorProfiles.Configuration(1280, 800, 160, 3)

    private class Memory : MonitorProfiles.Store {
        var values: MutableMap<String?, String?>? = HashMap<String?, String?>()

        public override fun read(): Map<String?, String?> {
            return HashMap<String?, String?>(values!!)
        }

        public override fun write(next: Map<String?, String?>?) {
            values = HashMap<String?, String?>(next!!)
        }
    }

    @Test
    open fun separateMonitorsRestoreDistinctProfilesAfterRecreation() {
        val a = profiles.create("Desk", landscape, "monitor-a")
        val b = profiles.create("Travel", portrait, "monitor-b")
        val restarted = MonitorProfiles(memory)
        assertEquals(a.id, restarted.bound("monitor-a")!!.id)
        assertEquals(landscape, restarted.bound("monitor-a")!!.configuration)
        assertEquals(b.id, restarted.bound("monitor-b")!!.id)
        assertEquals(portrait, restarted.bound("monitor-b")!!.configuration)
        assertNull(restarted.bound("unknown"))
        assertNull(restarted.bound(null))
    }

    @Test
    open fun switchingBindsOnlyTheSelectedMonitorAndDoesNotEditSnapshots() {
        val a = profiles.create("Desk", landscape, "a")
        val b = profiles.create("Reading", portrait, "b")
        profiles.bind("a", b.id)
        assertEquals(b.id, profiles.bound("a")!!.id)
        assertEquals(b.id, profiles.bound("b")!!.id)
        assertEquals(landscape, profiles.find(a.id)!!.configuration)
    }

    @Test
    open fun renameAndUpdateAreExplicitAndLeaveOtherProfilesUntouched() {
        val a = profiles.create("Desk", landscape, "a")
        val b = profiles.create("Travel", portrait, "b")
        profiles.rename(a.id, " Office ")
        assertEquals("Office", profiles.find(a.id)!!.name)
        profiles.update(a.id, portrait)
        assertEquals(portrait, profiles.find(a.id)!!.configuration)
        assertEquals("Travel", profiles.find(b.id)!!.name)
        assertEquals(b.id, profiles.bound("b")!!.id)
        assertEquals(a.id, profiles.bound("a")!!.id)
    }

    @Test
    open fun deleteClearsAllReferencesButNotOtherProfilesOrBindings() {
        val a = profiles.create("Desk", landscape, "a")
        val b = profiles.create("Travel", portrait, "b")
        profiles.bind("c", a.id)
        profiles.delete(a.id)
        assertNull(profiles.bound("a"))
        assertNull(profiles.bound("c"))
        assertNull(profiles.find(a.id))
        assertEquals(b.id, profiles.bound("b")!!.id)
        assertEquals(1, profiles.all().size.toLong())
    }

    @Test
    open fun forgetRetainsProfilesAndOtherMonitorBindings() {
        val a = profiles.create("Desk", landscape, "a")
        profiles.bind("b", a.id)
        profiles.forget("a")
        assertNull(profiles.bound("a"))
        assertEquals(a.id, profiles.bound("b")!!.id)
        assertNotNull(profiles.find(a.id))
    }

    @Test
    open fun invalidAndDuplicateNamesDoNotMutateStore() {
        val a = profiles.create("Desk", landscape, "a")
        profiles.create("Travel", portrait, "b")
        val before = memory.read()
        for (name in
            Arrays.asList<String?>(
                "",
                " ",
                "\u3000",
                "\u00a0",
                "desk",
                "x\ny",
                String(CharArray(49)).replace('\u0000', 'x'),
            )) {
            assertThrows<IllegalArgumentException>(
                IllegalArgumentException::class.java!!,
                { profiles.create(name, portrait, "a") },
            )
            assertEquals(before, memory.read())
        }
        assertThrows<IllegalArgumentException>(
            IllegalArgumentException::class.java!!,
            { profiles.rename(a.id, " TRAVEL ") },
        )
        assertEquals(before, memory.read())
    }

    @Test
    open fun namesRoundTripUnicodeAndDelimiterCharacters() {
        val name = "Bàn làm việc | 読書"
        val profile = profiles.create(name, landscape, "desk")
        assertEquals(name, MonitorProfiles(memory).find(profile.id)!!.name)
    }

    @Test
    open fun allFourRotationsAndDimensionBoundariesPersist() {
        for (rotation in 0..3) {
            val c =
                MonitorProfiles.Configuration(
                    480,
                    4096,
                    if (rotation % 2 == 0) 120 else 480,
                    rotation,
                )
            val p = profiles.create("Rotation " + rotation, c, "monitor" + rotation)
            assertEquals(c, profiles.bound("monitor" + rotation)!!.configuration)
            assertEquals(p.id, profiles.bound("monitor" + rotation)!!.id)
        }
    }

    @Test
    open fun invalidConfigurationIsRejected() {
        for (values in
            arrayOf<IntArray?>(
                intArrayOf(479, 1200, 240, 0),
                intArrayOf(4097, 1200, 240, 0),
                intArrayOf(1920, 479, 240, 0),
                intArrayOf(1920, 4097, 240, 0),
                intArrayOf(1920, 1200, 119, 0),
                intArrayOf(1920, 1200, 481, 0),
                intArrayOf(1920, 1200, 240, -1),
                intArrayOf(1920, 1200, 240, 4),
            )) assertThrows<IllegalArgumentException>(
            IllegalArgumentException::class.java!!,
            { MonitorProfiles.Configuration(values!![0], values!![1], values!![2], values!![3]) },
        )
    }

    @Test
    open fun stablePhysicalIdentityIgnoresModeOrderNamesAndPortDisplayIds() {
        val a =
            MonitorProfiles.identity(
                "local:1234567890",
                "HDMI A",
                Arrays.asList<String?>("1920x1200@60"),
            )
        val b =
            MonitorProfiles.identity(
                "local:1234567890",
                "HDMI B",
                Arrays.asList<String?>("1280x800@60"),
            )
        assertEquals(a, b)
        assertTrue(a.startsWith("physical."))
        assertNotEquals(
            a,
            MonitorProfiles.identity(
                "local:1234567891",
                "HDMI A",
                Arrays.asList<String?>("1920x1200@60"),
            ),
        )
    }

    @Test
    open fun fallbackIdentityIsCanonicalAndDisclosesIndistinguishableMonitors() {
        val a =
            MonitorProfiles.identity(
                null,
                "HDMI",
                Arrays.asList<String?>("1920x1200@60", "1280x800@60"),
            )
        assertEquals(
            a,
            MonitorProfiles.identity(
                "local:1",
                "HDMI",
                Arrays.asList<String?>("1280x800@60", "1920x1200@60", "1920x1200@60"),
            ),
        )
        assertTrue(a.startsWith("capabilities."))
        assertNotEquals(
            a,
            MonitorProfiles.identity(
                null,
                "Other monitor",
                Arrays.asList<String?>("1920x1200@60", "1280x800@60"),
            ),
        )
        assertNotEquals(
            a,
            MonitorProfiles.identity(null, "HDMI", Arrays.asList<String?>("1920x1080@60")),
        )
    }

    @Test
    open fun legacySettingsImportOnceAndNeverResurrectDeletedOrForgottenProfiles() {
        profiles.importLegacy("a", "old-key", landscape)
        val imported = profiles.bound("a")
        assertEquals(landscape, imported!!.configuration)
        profiles.importLegacy("a", "old-key", portrait)
        assertEquals(1, profiles.all().size.toLong())
        assertEquals(landscape, profiles.bound("a")!!.configuration)
        profiles.delete(imported!!.id)
        profiles.importLegacy("a", "old-key", portrait)
        assertNull(profiles.bound("a"))
        assertTrue(profiles.all().isEmpty())
        profiles.importLegacy("b", "other-key", portrait)
        profiles.forget("b")
        profiles.importLegacy("b", "other-key", landscape)
        assertNull(profiles.bound("b"))
        assertEquals(1, profiles.all().size.toLong())
    }

    @Test
    open fun migrationDoesNotReplaceAnExistingNamedBindingAndInvalidLegacyIsSkipped() {
        val named = profiles.create("Office", portrait, "a")
        profiles.importLegacy("a", "old-key", landscape)
        assertEquals(named.id, profiles.bound("a")!!.id)
        assertEquals(1, profiles.all().size.toLong())
        profiles.importLegacy("b", "invalid-key", null)
        assertNull(profiles.bound("b"))
        assertEquals(1, profiles.all().size.toLong())
    }

    @Test
    open fun confirmedWorkingSettingsSurviveRestartWithoutChangingNamedSnapshots() {
        val named = profiles.create("Desk", landscape, "a")
        profiles.importLegacy("a", "old-a", null)
        profiles.remember("a", portrait)
        val restarted = MonitorProfiles(memory)
        assertEquals(portrait, restarted.remembered("a"))
        assertEquals(named.id, restarted.bound("a")!!.id)
        assertEquals(landscape, restarted.find(named.id)!!.configuration)
        assertNull(restarted.remembered("b"))
        restarted.bind("a", named.id)
        assertEquals(landscape, restarted.remembered("a"))
    }

    @Test
    open fun ordinaryKeepAfterForgetAndMigrationDoesNotDependOnLegacyImport() {
        profiles.importLegacy("a", "legacy-a", landscape)
        profiles.forget("a")
        profiles.remember("a", portrait)
        profiles.importLegacy("a", "legacy-a", landscape)
        val restarted = MonitorProfiles(memory)
        assertEquals(portrait, restarted.remembered("a"))
        assertNull(restarted.bound("a"))
        assertEquals(1, restarted.all().size.toLong())
    }

    @Test
    open fun explicitSaveForgetDeleteAndUpdateClearOnlyIntendedWorkingSettings() {
        val a = profiles.create("Desk", landscape, "a")
        profiles.bind("b", a.id)
        profiles.remember("a", portrait)
        profiles.remember("b", portrait)
        profiles.update(a.id, landscape)
        profiles.clearWorking("a")
        assertEquals(landscape, profiles.remembered("a"))
        assertEquals(portrait, profiles.remembered("b"))
        profiles.remember("a", portrait)
        profiles.create("Current", landscape, "a")
        assertEquals(landscape, profiles.remembered("a"))
        profiles.forget("a")
        assertNull(profiles.remembered("a"))
        profiles.delete(a.id)
        assertNull(profiles.remembered("b"))
        assertEquals(1, profiles.all().size.toLong())
    }

    @Test
    open fun corruptWorkingSettingsFallBackToSnapshotOrSafeDefaults() {
        profiles.create("Desk", landscape, "a")
        for (record in
            Arrays.asList<String?>("bad", "1|2|3|4", "1920|1200|240|9", "x|1200|240|0")) {
            memory.values!!.put("w.a", record)
            memory.values!!.put("w.unknown", record)
            assertEquals(landscape, profiles.remembered("a"))
            assertNull(profiles.remembered("unknown"))
        }
    }

    @Test
    open fun corruptOrMissingRecordsFallBackWithoutDamagingValidProfiles() {
        val valid = profiles.create("Office", landscape, "a")
        memory.values!!.put("p.invalid-id", "garbage")
        memory.values!!.put("p." + java.util.UUID.randomUUID(), "1|1920|1200|999|0|T2ZmaWNl")
        memory.values!!.put("m.bad", "invalid-id")
        memory.values!!.put("p.null", "1|1920|1200|240|0|T2ZmaWNl")
        memory.values!!.put("p." + java.util.UUID.randomUUID(), "1|1920|1200|240|0|/w==")
        assertNull(profiles.bound("bad"))
        assertNull(profiles.find("missing"))
        assertNull(profiles.find(null))
        assertEquals(1, profiles.all().size.toLong())
        assertEquals(valid.id, profiles.bound("a")!!.id)
        assertThrows<IllegalArgumentException>(
            IllegalArgumentException::class.java!!,
            { profiles.bind("b", "missing") },
        )
        assertThrows<IllegalArgumentException>(
            IllegalArgumentException::class.java!!,
            { profiles.update("missing", portrait) },
        )
    }
}
