package dev.dexdroid;

import static org.junit.Assert.*;

import org.junit.Test;

public class InputWindowStateTest {
    private static String window(int display, int uid) {
        return "  Display: "
                + display
                + "\n"
                + "      0: name='app', displayId="
                + display
                + ", paused=false, visible=true, flags=0x81810100, type=0x00000001, frame=[0,0][948,1200], touchableRegion=[0,0][948,1200], ownerUid="
                + uid
                + "\n";
    }

    @Test
    public void requiresOwnedTouchableWindowOnExactDisplay() {
        assertTrue(InputWindowState.ready(window(12, 10234), 12, 10234, false));
        assertFalse(InputWindowState.ready(window(13, 10234), 12, 10234, false));
        assertFalse(InputWindowState.ready(window(12, 1000), 12, 10234, false));
        assertFalse(InputWindowState.ready(window(12, 10235), 12, 10234, false));
        assertFalse(InputWindowState.ready(window(12, 10234), 0, 10234, false));
    }

    @Test
    public void rejectsStaleLetterboxHiddenPausedAndEmptyWindows() {
        String valid = window(12, 10234);
        for (String invalid :
                new String[] {
                    window(12, 1000),
                    valid.replace("visible=true", "visible=false"),
                    valid.replace("paused=false", "paused=true"),
                    valid.replace("0x81810100", "0x81810110"),
                    valid.replace("touchableRegion=[0,0][948,1200]", "touchableRegion=<empty>"),
                    valid.replace("frame=[0,0][948,1200]", "frame=[0,0][0,1200]"),
                    ""
                }) assertFalse(invalid, InputWindowState.ready(invalid, 12, 10234, false));
    }

    @Test
    public void initialKeyboardReadinessRequiresTargetDisplayFocus() {
        String valid = window(12, 10234);
        assertFalse(InputWindowState.ready(valid, 12, 10234, true));
        assertFalse(
                InputWindowState.ready(
                        "  FocusedWindows:\n    displayId=13, name='app'\n  TouchStates: <no displays touched>\n"
                                + valid,
                        12,
                        10234,
                        true));
        assertTrue(
                InputWindowState.ready(
                        "  FocusedWindows:\n    displayId=12, name='app'\n  TouchStates: <no displays touched>\n"
                                + valid,
                        12,
                        10234,
                        true));
    }

    @Test
    public void supportsSystemUidAppButRejectsSystemLetterbox() {
        String app = window(12, 1000);
        assertTrue(InputWindowState.ready(app, 12, 1000, false));
        assertFalse(
                InputWindowState.ready(
                        app.replace("type=0x00000001", "type=0x000007e6"), 12, 1000, false));
    }
}
