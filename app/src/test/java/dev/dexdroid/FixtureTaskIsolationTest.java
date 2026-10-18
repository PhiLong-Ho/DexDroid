package dev.dexdroid;

import static org.junit.Assert.*;

import org.junit.Test;

public class FixtureTaskIsolationTest {
    private static final String PARENT =
            "dev.dexdroid.test/dev.dexdroid.fixture.LaunchParentActivity";
    private static final String READER =
            "dev.dexdroid.test/dev.dexdroid.fixture.LaunchReaderActivity";

    private String root(String header, String tasks) {
        return header + "\n" + tasks;
    }

    @Test
    public void capturesOnlyKnownSoleFixtureRootsAcrossLegacyAndModernReports() {
        String report =
                root(
                                "Stack id=12 displayId=0 userId=0",
                                " taskId=42: " + PARENT + " userId=0 visible=false\n")
                        + root(
                                "RootTask id=13 displayId=7 userId=0",
                                " taskId=43: " + READER + " userId=0 visible=true\n")
                        + root(
                                "RootTask id=14 displayId=0 userId=0",
                                " taskId=44: dev.dexdroid/.MainActivity userId=0 visible=true\n")
                        + root(
                                "RootTask id=15 displayId=0 userId=0",
                                " taskId=45: dev.dexdroid.test/dev.dexdroid.fixture.NavigationFixtureActivity userId=0 visible=false\n");
        assertEquals(2, FixtureTaskIsolation.ownedRoots(report).size());
        assertEquals(42, FixtureTaskIsolation.ownedRoots(report).get(0).taskId);
        assertEquals(13, FixtureTaskIsolation.ownedRoots(report).get(1).id);
    }

    @Test
    public void refusesSharedOtherUserMalformedAndDuplicateFixtureRoots() {
        String task = " taskId=42: " + READER + " userId=0 visible=false\n";
        String valid = root("RootTask id=12 displayId=0 userId=0", task);
        for (String report :
                new String[] {
                    "",
                    "unrecognized task report",
                    valid + " taskId=99: org.example/.Main userId=0 visible=true\n",
                    valid + " taskId=99: org.example/.Main userId=10 visible=true\n",
                    root("RootTask id=12 displayId=0 userId=10", task),
                    root(
                            "RootTask id=12 displayId=0 userId=0",
                            task.replace("userId=0", "userId=10")),
                    "Unrecognized header\n" + task,
                    valid + valid,
                    "Error: task service unavailable\n" + valid,
                    "DUMP TIMEOUT\n" + valid
                }) {
            assertThrows(
                    IllegalStateException.class, () -> FixtureTaskIsolation.ownedRoots(report));
        }
    }
}
