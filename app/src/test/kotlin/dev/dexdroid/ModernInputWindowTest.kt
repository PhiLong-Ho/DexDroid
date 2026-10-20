package dev.dexdroid

import org.junit.Assert.*
import org.junit.Test

open class ModernInputWindowTest {

    @Test
    open fun distinguishesReadyPendingAndUnknownEvidence() {
        assertEquals(
            ModernInputWindowState.State.READY,
            ModernInputWindowState.inspect(report("0x0"), 12, 10234),
        )
        assertEquals(
            ModernInputWindowState.State.READY,
            ModernInputWindowState.inspect(report("NOT_FOCUSABLE"), 12, 10234),
        )
        for (flag in
            arrayOf<String?>(
                "NOT_VISIBLE",
                "NOT_TOUCHABLE",
                "NO_INPUT_CHANNEL",
                "PAUSE_DISPATCHING",
                "DROP_INPUT",
            )) assertEquals(
            flag,
            ModernInputWindowState.State.PENDING,
            ModernInputWindowState.inspect(report(flag), 12, 10234),
        )
        for (invalid in
            arrayOf<String?>(
                "",
                report("NEW_PLATFORM_FLAG"),
                report("DROP_INPUT_IF_OBSCURED"),
                report("SPY"),
                report("0x8"),
                report("0x0").replace("  Connections:\n", ""),
                report("0x0").replace("dispatchingTimeout=5000ms, ", ""),
                report("0x0").replace("DispatchEnabled: true", "DispatchEnabled: false"),
                report("0x0").replace("DispatchFrozen: false", "DispatchFrozen: true"),
                report("0x0") + "DUMP TIMEOUT",
                report("0x0").replace("displayId=12", "displayId=13"),
            )) assertEquals(
            invalid,
            ModernInputWindowState.State.UNKNOWN,
            ModernInputWindowState.inspect(invalid, 12, 10234),
        )
        assertEquals(
            ModernInputWindowState.State.UNKNOWN,
            ModernInputWindowState.inspect(report("0x0"), 13, 10234),
        )
        assertEquals(
            ModernInputWindowState.State.PENDING,
            ModernInputWindowState.inspect(report("0x0"), 12, 10235),
        )
    }

    @Test
    open fun ignoresOtherDisplaysAndRequiresNativeAppGeometry() {
        val valid = report("0x0")
        for (pending in
            arrayOf<String?>(
                valid.replace(
                    "applicationInfo.name=ActivityRecord{fixture}",
                    "applicationInfo.name=",
                ),
                valid.replace("[0,0][1920,1200]", "[0,0][0,1200]"),
                valid.replace("touchableRegion=[0,0][1920,1200]", "touchableRegion=<empty>"),
                valid.replace("applicationInfo.token=0x123", "applicationInfo.token=<null>"),
            )) assertEquals(
            ModernInputWindowState.State.PENDING,
            ModernInputWindowState.inspect(pending, 12, 10234),
        )
        assertEquals(
            ModernInputWindowState.State.PENDING,
            ModernInputWindowState.inspect(
                valid
                    .replace("    Windows:\n", "    Windows:\n")
                    .replace(("(?m)^      0:.*\\n").toRegex(), ""),
                12,
                10234,
            ),
        )
    }

    @Test
    @Throws(Exception::class)
    open fun requiresStableOwnedTaskComponentUidAndPropagatesFailures() {
        val first = ModernInputWindow.Target(7, 10234, "fixture/Parent")
        for (later in
            arrayOf<ModernInputWindow.Target?>(
                null,
                ModernInputWindow.Target(8, 10234, "fixture/Parent"),
                ModernInputWindow.Target(7, 10235, "fixture/Parent"),
                ModernInputWindow.Target(7, 10234, "fixture/Child"),
            )) {
            val window =
                ModernInputWindow(
                    12,
                    object : ModernInputWindow.Source {
                        var calls: Int = 0

                        public override fun target(): ModernInputWindow.Target? {
                            return if (calls++ == 0) first else later
                        }

                        public override fun windows(): String {
                            return report("NOT_TOUCHABLE")
                        }
                    },
                )
            assertNull(window.pendingComponent())
        }
        for (config in arrayOf<String?>("NOT_TOUCHABLE", "0x0", "UNKNOWN")) {
            val window =
                ModernInputWindow(
                    12,
                    object : ModernInputWindow.Source {
                        public override fun target(): ModernInputWindow.Target {
                            return first
                        }

                        public override fun windows(): String {
                            return report(config)
                        }
                    },
                )
            assertEquals(
                if (config == "NOT_TOUCHABLE") "fixture/Parent" else null,
                window.pendingComponent(),
            )
        }
        val failed =
            ModernInputWindow(
                12,
                object : ModernInputWindow.Source {
                    public override fun target(): ModernInputWindow.Target {
                        return first
                    }

                    @Throws(Exception::class)
                    public override fun windows(): String? {
                        throw SecurityException("permission denied")
                    }
                },
            )
        assertThrows<SecurityException>(
            SecurityException::class.java!!,
            org.junit.function.ThrowingRunnable({ failed.pendingComponent() }),
        )
    }

    @Test
    fun diagnosticCapturesOnlyConsumedQueriesAndDoesNotFlushInsideDecision() {
        val calls = ArrayList<String>()
        val target = ModernInputWindow.Target(7, 10234, "fixture/Parent")
        val raw = report("NOT_TOUCHABLE")
        var captured: ModernInputWindow.Sample? = null
        val window =
            ModernInputWindow(
                12,
                object : ModernInputWindow.Source {
                    override fun target(): ModernInputWindow.Target {
                        calls.add("target")
                        return target
                    }

                    override fun windows(): String {
                        calls.add("windows")
                        return raw
                    }
                },
            )
        window.diagnostic =
            object : ModernInputWindow.Diagnostic {
                override fun sample(value: ModernInputWindow.Sample) {
                    calls.add("sample")
                    captured = value
                }

                override fun event(value: String) {
                    calls.add(value)
                }

                override fun flush() {
                    calls.add("flush")
                }
            }
        assertEquals("fixture/Parent", window.pendingComponent("immutable DOWN"))
        assertEquals(listOf("target", "windows", "target", "sample"), calls)
        val sample = captured!!
        assertSame(raw, sample.report)
        assertSame(target, sample.before)
        assertSame(target, sample.after)
        assertTrue(sample.windowsQueried && sample.afterQueried)
        assertEquals("decision-PENDING", sample.stage)
        assertEquals("immutable DOWN", sample.rejectedDown)
        assertTrue(sample.endNanos >= sample.startNanos)
        window.diagnosticEvent("cancel-result=true")
        window.flushDiagnostic()
        assertEquals(
            listOf("target", "windows", "target", "sample", "cancel-result=true", "flush"),
            calls,
        )
        assertNotNull(ModernInputWindow::class.java.getMethod("pendingComponent"))
    }

    @Test
    fun diagnosticRetainsNullChangedReadyAndUnknownOutcomesWithoutExtraQueries() {
        val owned = ModernInputWindow.Target(7, 10234, "fixture/Parent")
        for (scenario in listOf("before-null", "target-changed", "READY", "UNKNOWN")) {
            var targets = 0
            var windows = 0
            var sample: ModernInputWindow.Sample? = null
            val window =
                ModernInputWindow(
                    12,
                    object : ModernInputWindow.Source {
                        override fun target(): ModernInputWindow.Target? {
                            targets++
                            return if (
                                scenario == "before-null" ||
                                    scenario == "target-changed" && targets == 2
                            )
                                null
                            else owned
                        }

                        override fun windows(): String {
                            windows++
                            return report(if (scenario == "READY") "0x0" else "UNRECOGNIZED")
                        }
                    },
                )
            window.diagnostic = observer { sample = it }
            assertNull(window.pendingComponent())
            assertEquals(if (scenario == "before-null") 1 else 2, targets)
            assertEquals(if (scenario == "before-null") 0 else 1, windows)
            assertEquals(
                if (scenario == "READY" || scenario == "UNKNOWN") "decision-$scenario"
                else scenario,
                sample!!.stage,
            )
            assertEquals(scenario != "before-null", sample!!.afterQueried)
            assertEquals(scenario != "before-null", sample!!.windowsQueried)
        }
    }

    @Test
    fun diagnosticPreservesSourceFailuresAndUnqueriedPartialFields() {
        val owned = ModernInputWindow.Target(7, 10234, "fixture/Parent")
        for (failureAt in listOf("before-target", "windows", "after-target")) {
            val primary = SecurityException("source failure")
            var targets = 0
            var windows = 0
            var sample: ModernInputWindow.Sample? = null
            val window =
                ModernInputWindow(
                    12,
                    object : ModernInputWindow.Source {
                        override fun target(): ModernInputWindow.Target {
                            targets++
                            if (
                                failureAt == "before-target" ||
                                    failureAt == "after-target" && targets == 2
                            )
                                throw primary
                            return owned
                        }

                        override fun windows(): String {
                            windows++
                            if (failureAt == "windows") throw primary
                            return report("NOT_TOUCHABLE")
                        }
                    },
                )
            window.diagnostic = observer {
                sample = it
                throw AssertionError("secondary observer")
            }
            val actual = assertThrows(SecurityException::class.java) { window.pendingComponent() }
            assertSame(primary, actual)
            assertEquals(failureAt, sample!!.stage)
            assertEquals(SecurityException::class.java.name, sample!!.errorClass)
            assertEquals(if (failureAt == "after-target") 2 else 1, targets)
            assertEquals(if (failureAt == "before-target") 0 else 1, windows)
            assertEquals(failureAt == "after-target", sample!!.afterQueried)
        }
    }

    @Test
    fun failingDiagnosticCallbacksCannotChangePendingResult() {
        val target = ModernInputWindow.Target(7, 10234, "fixture/Parent")
        val window =
            ModernInputWindow(
                12,
                object : ModernInputWindow.Source {
                    override fun target() = target

                    override fun windows() = report("NOT_TOUCHABLE")
                },
            )
        window.diagnostic = observer { throw AssertionError("secondary sample") }
        assertEquals("fixture/Parent", window.pendingComponent())
        window.diagnosticEvent("cancel-attempt")
        window.flushDiagnostic()
        assertEquals("fixture/Parent", window.pendingComponent())
    }

    private fun observer(record: (ModernInputWindow.Sample) -> Unit): ModernInputWindow.Diagnostic =
        object : ModernInputWindow.Diagnostic {
            override fun sample(value: ModernInputWindow.Sample) = record(value)

            override fun event(value: String) {
                throw AssertionError("secondary event")
            }

            override fun flush() {
                throw AssertionError("secondary flush")
            }
        }

    companion object {
        open fun report(config: String?): String {
            return ("Input Dispatcher State:\n  DispatchEnabled: true\n  DispatchFrozen: false\n" +
                "  Display: 12\n    logicalSize=1920x1200\n    Windows:\n" +
                "      0: name=fixture, displayId=12, inputConfig=" +
                config +
                ", frame=[0,0][1920,1200], applicationInfo.name=ActivityRecord{fixture}, applicationInfo.token=0x123, touchableRegion=[0,0][1920,1200], ownerUid=10234, dispatchingTimeout=5000ms, token=0x456, touchOcclusionMode=BLOCK_UNTRUSTED\n" +
                "  Connections:\n")
        }
    }
}
