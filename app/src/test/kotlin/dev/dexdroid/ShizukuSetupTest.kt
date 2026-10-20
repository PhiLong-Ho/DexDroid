package dev.dexdroid

import java.util.function.Consumer
import org.junit.Assert.*
import org.junit.Test

open class ShizukuSetupTest {
    @Test
    open fun binderLookupRaceReturnsRecoveryStateAndReportsError() {
        val lost = RuntimeException("binder disappeared after availability check")
        val errors = java.util.ArrayList<RuntimeException?>()
        assertEquals(
            ShizukuSetup.State.STOPPED,
            ShizukuSetup.probe({ throw lost }, Consumer<RuntimeException?>({ errors.add(it) })),
        )
        assertEquals(listOf<RuntimeException?>(lost), errors)
    }

    @Test
    open fun missingManagerOffersInstallEvenWithStaleConnectionFlags() {
        assertEquals(ShizukuSetup.State.MISSING, ShizukuSetup.classify(false, true, true, true))
    }

    @Test
    open fun stoppedServiceRequiresManagerStartup() {
        assertEquals(ShizukuSetup.State.STOPPED, ShizukuSetup.classify(true, false, false, false))
    }

    @Test
    open fun runningUnauthorizedServiceRequiresAuthorization() {
        assertEquals(ShizukuSetup.State.PERMISSION, ShizukuSetup.classify(true, true, false, false))
    }

    @Test
    open fun authorizedBinderCanReconnectBeforeDesktopBridgeExists() {
        assertEquals(ShizukuSetup.State.READY, ShizukuSetup.classify(true, true, true, false))
    }

    @Test
    open fun connectedServiceKeepsManagerAvailable() {
        assertEquals(ShizukuSetup.State.CONNECTED, ShizukuSetup.classify(true, true, true, true))
    }
}
