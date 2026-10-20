package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

open class FixtureTaskIsolationTest {

    private fun root(header: String?, tasks: String?): String {
        return header + "\n" + tasks
    }

    @Test
    open fun capturesOnlyKnownSoleFixtureRootsAcrossLegacyAndModernReports() {
        val report =
            (root(
                "Stack id=12 displayId=0 userId=0",
                " taskId=42: " + PARENT + " userId=0 visible=false\n",
            ) +
                root(
                    "RootTask id=13 displayId=7 userId=0",
                    " taskId=43: " + READER + " userId=0 visible=true\n",
                ) +
                root(
                    "RootTask id=14 displayId=0 userId=0",
                    " taskId=44: dev.dexdroid/.MainActivity userId=0 visible=true\n",
                ) +
                root(
                    "RootTask id=15 displayId=0 userId=0",
                    " taskId=45: dev.dexdroid.test/dev.dexdroid.fixture.NavigationFixtureActivity userId=0 visible=false\n",
                ))
        assertEquals(2, FixtureTaskIsolation.ownedRoots(report!!).size.toLong())
        assertEquals(42, FixtureTaskIsolation.ownedRoots(report!!).get(0)!!.taskId.toLong())
        assertEquals(13, FixtureTaskIsolation.ownedRoots(report!!).get(1)!!.id.toLong())
    }

    @Test
    open fun capturesNativeBoundsBeforeUserWithoutLosingTaskIdentity() {
        val native =
            "RootTask id=55 bounds=[0,0][1080,2400] displayId=0 userId=0\n" +
                " taskId=55: " +
                PARENT +
                " bounds=[0,0][1080,2400] userId=0 visible=true topActivity=ComponentInfo{" +
                PARENT +
                "}\n"
        for (report in
            arrayOf(native, native.replace("[0,0][1080,2400]", "[-1080,-1200][0,1200]"))) {
            val captured = FixtureTaskIsolation.ownedRoots(report).single()!!
            assertEquals(55, captured.id)
            assertEquals(55, captured.taskId)
            assertEquals(0, captured.display)
        }
    }

    @Test
    open fun refusesMalformedBoundsAndAmbiguousNativeFixtureOwnership() {
        val header = "RootTask id=55 bounds=[0,0][1080,2400] displayId=0 userId=0"
        val row = " taskId=55: " + PARENT + " bounds=[0,0][1080,2400] userId=0 visible=true\n"
        val valid = root(header, row)
        val invalid =
            arrayOf(
                root(header, row.replace("[1080,2400]", "[1080]")),
                root(header, row.replace("[1080,2400]", "[1080,2400,1]")),
                root(header, row.replace("[0,0]", "[x,0]")),
                root(header, row.replace("bounds=[0,0][1080,2400]", "unexpected=1")),
                root(header, row.replace("userId=0", "userId=10")),
                root(header.replace("userId=0", "userId=10"), row),
                valid +
                    " taskId=99: org.example/.Main bounds=[0,0][1080,2400] userId=0 visible=true\n",
                valid + valid,
            )
        for (report in invalid) {
            assertThrows(IllegalStateException::class.java) {
                FixtureTaskIsolation.ownedRoots(report)
            }
        }
    }

    @Test
    open fun refusesSharedOtherUserMalformedAndDuplicateFixtureRoots() {
        val task = " taskId=42: " + READER + " userId=0 visible=false\n"
        val valid = root("RootTask id=12 displayId=0 userId=0", task)
        for (report in
            arrayOf<String?>(
                "",
                "unrecognized task report",
                valid + " taskId=99: org.example/.Main userId=0 visible=true\n",
                valid + " taskId=99: org.example/.Main userId=10 visible=true\n",
                root("RootTask id=12 displayId=0 userId=10", task),
                root(
                    "RootTask id=12 displayId=0 userId=0",
                    task!!.replace("userId=0", "userId=10"),
                ),
                "Unrecognized header\n" + task!!,
                valid + valid,
                "Error: task service unavailable\n" + valid,
                "DUMP TIMEOUT\n" + valid,
            )) {
            assertThrows<IllegalStateException?>(
                IllegalStateException::class.java!!,
                { FixtureTaskIsolation.ownedRoots(report!!) },
            )
        }
    }

    companion object {
        private val PARENT: String = "dev.dexdroid.test/dev.dexdroid.fixture.LaunchParentActivity"
        private val READER: String = "dev.dexdroid.test/dev.dexdroid.fixture.LaunchReaderActivity"
    }
}
