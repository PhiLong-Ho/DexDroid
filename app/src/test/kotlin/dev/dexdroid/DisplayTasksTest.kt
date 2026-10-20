package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

open class DisplayTasksTest {

    @Test
    fun strictComponentEvidencePreservesLegacyPackageMatching() {
        for (component in
            arrayOf(
                "org.example.reader/.MainActivity",
                "org.example.reader/org.example.reader.MainActivity",
            )) {
            val root =
                DisplayTasks.parse(
                        "RootTask id=3 displayId=9 userId=0\n taskId=4: $component userId=0 visible=false\n",
                        APP,
                    )[0]!!
            assertTrue(root.matches)
            assertTrue(root.exactComponent)
            assertEquals(false, root.nativeVisible)
        }
        val other =
            DisplayTasks.parse(
                    "RootTask id=3 displayId=9 userId=0\n taskId=4: org.example.reader/.Other userId=0 visible=false\n",
                    APP,
                )[0]!!
        assertTrue(other.matches)
        assertFalse(other.exactComponent)
        assertNull(other.nativeVisible)
        val foreign =
            DisplayTasks.parse(
                    "RootTask id=3 displayId=9 userId=0\n taskId=4: org.example.reader/.com.other.Main userId=0 visible=false\n",
                    "org.example.reader/com.other.Main",
                )[0]!!
        assertFalse(foreign.exactComponent)
    }

    @Test
    fun foregroundEvidenceRequiresUnambiguousNativeVisibility() {
        for (suffix in
            arrayOf(
                "",
                " visible=unknown",
                " visible=false visible=true",
                " visible=false visible=false",
                " visible=false visible=unknown",
                " visible=false visible = true",
            )) {
            val root =
                DisplayTasks.parse(
                        "RootTask id=3 displayId=9 userId=0\n taskId=4: org.example.reader/.MainActivity userId=0$suffix\n",
                        APP,
                    )[0]!!
            assertTrue(root.exactComponent)
            assertNull(root.nativeVisible)
        }
        val visible =
            DisplayTasks.parse(
                    "RootTask id=3 displayId=9 userId=0\n taskId=4: org.example.reader/.MainActivity userId=0 visible=true\n",
                    APP,
                )[0]!!
        assertEquals(true, visible.nativeVisible)
    }

    @Test
    fun foregroundEvidenceRejectsExtraOrMalformedOwnershipRows() {
        val header =
            "RootTask id=3 displayId=9 userId=0\n configuration={mWindowingMode=fullscreen}\n"
        val row = " taskId=4: org.example.reader/.MainActivity userId=0 visible=false\n"
        for (extra in
            arrayOf(
                " taskId=5: other/.Main userId=10 visible=false\n",
                " taskId=broken\n",
                " taskId = 5\n",
            )) {
            val roots = DisplayTasks.parse(header + row + extra, APP)
            assertEquals(1, roots[0]!!.tasks)
            assertEquals(2, roots[0]!!.nativeTaskRows)
            assertNull(ForegroundLaunch.eligible(roots, 9))
        }
        for (users in
            arrayOf(
                "userId=0 userId=0",
                "userId=0 userId=10",
                "userId=0 userId = 0",
                "userId=0 taskId=5:",
            )) {
            val roots =
                DisplayTasks.parse(
                    header + " taskId=4: org.example.reader/.MainActivity $users visible=false\n",
                    APP,
                )
            assertTrue(roots[0]!!.matches)
            assertNull(roots[0]!!.nativeVisible)
            assertNull(ForegroundLaunch.eligible(roots, 9))
        }
        val duplicatedHeader =
            DisplayTasks.parse(header.replace("userId=0", "userId=0 userId=0") + row, APP)
        assertFalse(duplicatedHeader[0]!!.nativeUserZero)
        assertNull(ForegroundLaunch.eligible(duplicatedHeader, 9))
    }

    @Test
    open fun android11StackRetainsTaskAndDisplayIdentity() {
        val root =
            DisplayTasks.parse(
                    ("Stack id=42 bounds=[0,0][1920,1200] displayId=7 userId=0\n" +
                        "  taskId=99: org.example.reader/.MainActivity userId=0 visible=true\n"),
                    APP,
                )
                .get(0)
        assertEquals(42, root!!.id.toLong())
        assertEquals(7, root!!.display.toLong())
        assertEquals(99, root!!.taskId.toLong())
        assertEquals(1, root!!.tasks.toLong())
        assertTrue(root!!.matches)
        assertTrue(root!!.visible)
        assertFalse(root!!.fullscreen)
    }

    @Test
    open fun android16RootTaskRecognizesConfirmedFullscreen() {
        val root =
            DisplayTasks.parse(
                    ("RootTask id=17 bounds=[0,0][1920,1200] displayId=3 userId=0\n" +
                        " configuration={winConfig={mWindowingMode=fullscreen mActivityType=standard}}\n" +
                        "  taskId=18: org.example.reader/org.example.reader.MainActivity userId=0 visible=true\n"),
                    APP,
                )
                .get(0)
        assertEquals(3, root!!.display.toLong())
        assertEquals(18, root!!.taskId.toLong())
        assertTrue(root!!.matches)
        assertTrue(root!!.fullscreen)
    }

    @Test
    open fun freeformAndMissingConfigurationDoNotClaimFullscreen() {
        for (config in
            arrayOf<String?>(
                "",
                " configuration={mWindowingMode=freeform}\n",
                " configuration={mWindowingMode=undefined}\n",
            )) {
            val root =
                DisplayTasks.parse(
                        ("RootTask id=1 displayId=2 userId=0\n" +
                            config +
                            " taskId=3: org.example.reader/.MainActivity userId=0 visible=true\n"),
                        APP,
                    )
                    .get(0)
            assertFalse(root!!.fullscreen)
        }
    }

    @Test
    open fun rootsOnDifferentDisplaysRemainSeparate() {
        val roots =
            DisplayTasks.parse(
                ("RootTask id=1 displayId=0 userId=0\n taskId=2: org.example.reader/.MainActivity userId=0 visible=false\n" +
                    "RootTask id=3 displayId=9 userId=0\n taskId=4: org.example.reader/.MainActivity userId=0 visible=true\n"),
                APP,
            )
        assertEquals(2, roots.size.toLong())
        assertEquals(0, roots.get(0)!!.display.toLong())
        assertEquals(9, roots.get(1)!!.display.toLong())
        assertFalse(roots.get(0)!!.visible)
        assertTrue(roots.get(1)!!.visible)
    }

    @Test
    open fun otherUsersCannotLeakTasksIntoPreviousRoot() {
        val roots =
            DisplayTasks.parse(
                ("RootTask id=1 displayId=0 userId=0\n taskId=2: other/.Main userId=0 visible=true\n" +
                    "RootTask id=3 displayId=9 userId=10\n taskId=4: org.example.reader/.MainActivity userId=10 visible=true\n"),
                APP,
            )
        assertEquals(1, roots.size.toLong())
        assertEquals(1, roots.get(0)!!.tasks.toLong())
        assertFalse(roots.get(0)!!.matches)
    }

    @Test
    open fun packagePrefixesAreNotMatches() {
        val root =
            DisplayTasks.parse(
                    ("Stack id=1 displayId=2 userId=0\n" +
                        " taskId=3: org.example.reader.extra/.MainActivity userId=0 visible=true\n"),
                    APP,
                )
                .get(0)
        assertFalse(root!!.matches)
    }

    @Test
    open fun internalInputTestIsDistinctFromPhoneController() {
        val text =
            ("RootTask id=1 displayId=0 userId=0\n" +
                " taskId=2: dev.dexdroid/dev.dexdroid.MainActivity userId=0 visible=true\n" +
                "RootTask id=3 displayId=5 userId=0\n" +
                " taskId=4: dev.dexdroid/dev.dexdroid.InputTestActivity userId=0 visible=true\n")
        val roots = DisplayTasks.parse(text, "dev.dexdroid/.InputTestActivity")
        assertFalse(roots.get(0)!!.matches)
        assertTrue(roots.get(1)!!.matches)
    }

    @Test
    open fun sharedRootCountsUnrelatedTasksForMovementGuard() {
        val root =
            DisplayTasks.parse(
                    ("RootTask id=1 displayId=0 userId=0\n" +
                        " taskId=2: org.example.reader/.MainActivity userId=0 visible=true\n" +
                        " taskId=3: other/.Main userId=0 visible=false\n"),
                    APP,
                )
                .get(0)
        assertEquals(2, root!!.tasks.toLong())
        assertTrue(root!!.matches)
    }

    @Test
    open fun visibleMatchingTaskWinsOverLaterHiddenTask() {
        val root =
            DisplayTasks.parse(
                    ("RootTask id=1 displayId=2 userId=0\n" +
                        " taskId=7: org.example.reader/.MainActivity userId=0 visible=true\n" +
                        " taskId=8: org.example.reader/.MainActivity userId=0 visible=false\n"),
                    APP,
                )
                .get(0)
        assertEquals(7, root!!.taskId.toLong())
        assertTrue(root!!.visible)
    }

    @Test
    open fun malformedHeadersAndOrphanTasksCannotProvePlacement() {
        val roots =
            DisplayTasks.parse(
                (" taskId=1: org.example.reader/.MainActivity userId=0 visible=true\n" +
                    "RootTask id=2 displayId=3 userId=0\n" +
                    "RootTask id=invalid displayId=4 userId=0\n" +
                    " taskId=5: org.example.reader/.MainActivity userId=0 visible=true\n"),
                APP,
            )
        assertEquals(1, roots.size.toLong())
        assertEquals(0, roots.get(0)!!.tasks.toLong())
        assertFalse(roots.get(0)!!.matches)
    }

    companion object {
        private val APP: String = "org.example.reader/.MainActivity"
    }
}
