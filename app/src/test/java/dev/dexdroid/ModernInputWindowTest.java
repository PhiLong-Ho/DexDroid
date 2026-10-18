package dev.dexdroid;

import static org.junit.Assert.*;

import org.junit.Test;

public class ModernInputWindowTest {
    static String report(String config) {
        return "Input Dispatcher State:\n  DispatchEnabled: true\n  DispatchFrozen: false\n"
                + "  Display: 12\n    logicalSize=1920x1200\n    Windows:\n"
                + "      0: name=fixture, displayId=12, inputConfig="
                + config
                + ", frame=[0,0][1920,1200], applicationInfo.name=ActivityRecord{fixture}, applicationInfo.token=0x123, touchableRegion=[0,0][1920,1200], ownerUid=10234, dispatchingTimeout=5000ms, token=0x456, touchOcclusionMode=BLOCK_UNTRUSTED\n"
                + "  Connections:\n";
    }

    @Test
    public void distinguishesReadyPendingAndUnknownEvidence() {
        assertEquals(
                ModernInputWindowState.State.READY,
                ModernInputWindowState.inspect(report("0x0"), 12, 10234));
        assertEquals(
                ModernInputWindowState.State.READY,
                ModernInputWindowState.inspect(report("NOT_FOCUSABLE"), 12, 10234));
        for (String flag :
                new String[] {
                    "NOT_VISIBLE",
                    "NOT_TOUCHABLE",
                    "NO_INPUT_CHANNEL",
                    "PAUSE_DISPATCHING",
                    "DROP_INPUT"
                })
            assertEquals(
                    flag,
                    ModernInputWindowState.State.PENDING,
                    ModernInputWindowState.inspect(report(flag), 12, 10234));
        for (String invalid :
                new String[] {
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
                    report("0x0").replace("displayId=12", "displayId=13")
                })
            assertEquals(
                    invalid,
                    ModernInputWindowState.State.UNKNOWN,
                    ModernInputWindowState.inspect(invalid, 12, 10234));
        assertEquals(
                ModernInputWindowState.State.UNKNOWN,
                ModernInputWindowState.inspect(report("0x0"), 13, 10234));
        assertEquals(
                ModernInputWindowState.State.PENDING,
                ModernInputWindowState.inspect(report("0x0"), 12, 10235));
    }

    @Test
    public void ignoresOtherDisplaysAndRequiresNativeAppGeometry() {
        String valid = report("0x0");
        for (String pending :
                new String[] {
                    valid.replace(
                            "applicationInfo.name=ActivityRecord{fixture}",
                            "applicationInfo.name="),
                    valid.replace("[0,0][1920,1200]", "[0,0][0,1200]"),
                    valid.replace("touchableRegion=[0,0][1920,1200]", "touchableRegion=<empty>"),
                    valid.replace("applicationInfo.token=0x123", "applicationInfo.token=<null>")
                })
            assertEquals(
                    ModernInputWindowState.State.PENDING,
                    ModernInputWindowState.inspect(pending, 12, 10234));
        assertEquals(
                ModernInputWindowState.State.PENDING,
                ModernInputWindowState.inspect(
                        valid.replace("    Windows:\n", "    Windows:\n")
                                .replaceAll("(?m)^      0:.*\\n", ""),
                        12,
                        10234));
    }

    @Test
    public void requiresStableOwnedTaskComponentUidAndPropagatesFailures() throws Exception {
        ModernInputWindow.Target first = new ModernInputWindow.Target(7, 10234, "fixture/Parent");
        for (ModernInputWindow.Target later :
                new ModernInputWindow.Target[] {
                    null,
                    new ModernInputWindow.Target(8, 10234, "fixture/Parent"),
                    new ModernInputWindow.Target(7, 10235, "fixture/Parent"),
                    new ModernInputWindow.Target(7, 10234, "fixture/Child")
                }) {
            ModernInputWindow window =
                    new ModernInputWindow(
                            12,
                            new ModernInputWindow.Source() {
                                int calls;

                                public ModernInputWindow.Target target() {
                                    return calls++ == 0 ? first : later;
                                }

                                public String windows() {
                                    return report("NOT_TOUCHABLE");
                                }
                            });
            assertNull(window.pendingComponent());
        }
        for (String config : new String[] {"NOT_TOUCHABLE", "0x0", "UNKNOWN"}) {
            ModernInputWindow window =
                    new ModernInputWindow(
                            12,
                            new ModernInputWindow.Source() {
                                public ModernInputWindow.Target target() {
                                    return first;
                                }

                                public String windows() {
                                    return report(config);
                                }
                            });
            assertEquals(
                    config.equals("NOT_TOUCHABLE") ? "fixture/Parent" : null,
                    window.pendingComponent());
        }
        ModernInputWindow failed =
                new ModernInputWindow(
                        12,
                        new ModernInputWindow.Source() {
                            public ModernInputWindow.Target target() {
                                return first;
                            }

                            public String windows() throws Exception {
                                throw new SecurityException("permission denied");
                            }
                        });
        assertThrows(SecurityException.class, failed::pendingComponent);
    }
}
