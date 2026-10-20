package dev.dexdroid

import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent

/** Bounded, content-free debug evidence for the synthetic split-key fixture only. */
internal object KeyDiagnostics {
    private var records = 0

    fun synthetic(component: String?, event: KeyEvent?): Boolean {
        return BuildConfig.DEBUG &&
            component?.startsWith("dev.dexdroid.test/dev.dexdroid.fixture.SplitProbeFixture") ==
                true &&
            event != null &&
            event.deviceId == -1 &&
            event.isFromSource(InputDevice.SOURCE_KEYBOARD) &&
            event.keyCode in intArrayOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_SHIFT_LEFT)
    }

    fun tuple(event: KeyEvent): String =
        "action=${event.action} code=${event.keyCode} device=${event.deviceId} " +
            "source=${event.source} scan=${event.scanCode} down=${event.downTime} " +
            "event=${event.eventTime} flags=${event.flags} repeat=${event.repeatCount}"

    @Synchronized
    fun record(owner: String, fields: String) {
        if (!BuildConfig.DEBUG || records >= 128) return
        try {
            if (records == 127) {
                Log.d("DexDroidKeyDiag", "TRACE_TRUNCATED")
            } else {
                Log.d(
                    "DexDroidKeyDiag",
                    ("t=${SystemClock.uptimeMillis()} $owner $fields").let {
                        if (it.length <= 512 && it.all { character -> character.code in 32..126 })
                            it
                        else "RECORD_TRUNCATED $owner"
                    },
                )
            }
            records++
        } catch (_: RuntimeException) {
            // Missing diagnostic evidence is inconclusive; never replace the input outcome.
        }
    }
}
