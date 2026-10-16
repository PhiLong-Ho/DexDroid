package dev.dexdroid;

import static org.junit.Assert.*;

import org.junit.Test;

public class ShizukuSetupTest {
    @Test
    public void binderLookupRaceReturnsRecoveryStateAndReportsError() {
        RuntimeException lost = new RuntimeException("binder disappeared after availability check");
        java.util.List<RuntimeException> errors = new java.util.ArrayList<>();
        assertEquals(
                ShizukuSetup.State.STOPPED,
                ShizukuSetup.probe(
                        () -> {
                            throw lost;
                        },
                        errors::add));
        assertEquals(java.util.Collections.singletonList(lost), errors);
    }

    @Test
    public void missingManagerOffersInstallEvenWithStaleConnectionFlags() {
        assertEquals(ShizukuSetup.State.MISSING, ShizukuSetup.classify(false, true, true, true));
    }

    @Test
    public void stoppedServiceRequiresManagerStartup() {
        assertEquals(ShizukuSetup.State.STOPPED, ShizukuSetup.classify(true, false, false, false));
    }

    @Test
    public void runningUnauthorizedServiceRequiresAuthorization() {
        assertEquals(
                ShizukuSetup.State.PERMISSION, ShizukuSetup.classify(true, true, false, false));
    }

    @Test
    public void authorizedBinderCanReconnectBeforeDesktopBridgeExists() {
        assertEquals(ShizukuSetup.State.READY, ShizukuSetup.classify(true, true, true, false));
    }

    @Test
    public void connectedServiceKeepsManagerAvailable() {
        assertEquals(ShizukuSetup.State.CONNECTED, ShizukuSetup.classify(true, true, true, true));
    }
}
