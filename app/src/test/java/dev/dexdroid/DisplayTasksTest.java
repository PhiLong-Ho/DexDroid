package dev.dexdroid;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class DisplayTasksTest {
    private static final String APP = "org.example.reader/.MainActivity";

    @Test
    public void android11StackRetainsTaskAndDisplayIdentity() {
        DisplayTasks.Root root =
                DisplayTasks.parse(
                                "Stack id=42 bounds=[0,0][1920,1200] displayId=7 userId=0\n"
                                        + "  taskId=99: org.example.reader/.MainActivity userId=0 visible=true\n",
                                APP)
                        .get(0);
        assertEquals(42, root.id);
        assertEquals(7, root.display);
        assertEquals(99, root.taskId);
        assertEquals(1, root.tasks);
        assertTrue(root.matches);
        assertTrue(root.visible);
        assertFalse(root.fullscreen);
    }

    @Test
    public void android16RootTaskRecognizesConfirmedFullscreen() {
        DisplayTasks.Root root =
                DisplayTasks.parse(
                                "RootTask id=17 bounds=[0,0][1920,1200] displayId=3 userId=0\n"
                                        + " configuration={winConfig={mWindowingMode=fullscreen mActivityType=standard}}\n"
                                        + "  taskId=18: org.example.reader/org.example.reader.MainActivity userId=0 visible=true\n",
                                APP)
                        .get(0);
        assertEquals(3, root.display);
        assertEquals(18, root.taskId);
        assertTrue(root.matches);
        assertTrue(root.fullscreen);
    }

    @Test
    public void freeformAndMissingConfigurationDoNotClaimFullscreen() {
        for (String config :
                new String[] {
                    "",
                    " configuration={mWindowingMode=freeform}\n",
                    " configuration={mWindowingMode=undefined}\n"
                }) {
            DisplayTasks.Root root =
                    DisplayTasks.parse(
                                    "RootTask id=1 displayId=2 userId=0\n"
                                            + config
                                            + " taskId=3: org.example.reader/.MainActivity userId=0 visible=true\n",
                                    APP)
                            .get(0);
            assertFalse(root.fullscreen);
        }
    }

    @Test
    public void rootsOnDifferentDisplaysRemainSeparate() {
        List<DisplayTasks.Root> roots =
                DisplayTasks.parse(
                        "RootTask id=1 displayId=0 userId=0\n taskId=2: org.example.reader/.MainActivity userId=0 visible=false\n"
                                + "RootTask id=3 displayId=9 userId=0\n taskId=4: org.example.reader/.MainActivity userId=0 visible=true\n",
                        APP);
        assertEquals(2, roots.size());
        assertEquals(0, roots.get(0).display);
        assertEquals(9, roots.get(1).display);
        assertFalse(roots.get(0).visible);
        assertTrue(roots.get(1).visible);
    }

    @Test
    public void otherUsersCannotLeakTasksIntoPreviousRoot() {
        List<DisplayTasks.Root> roots =
                DisplayTasks.parse(
                        "RootTask id=1 displayId=0 userId=0\n taskId=2: other/.Main userId=0 visible=true\n"
                                + "RootTask id=3 displayId=9 userId=10\n taskId=4: org.example.reader/.MainActivity userId=10 visible=true\n",
                        APP);
        assertEquals(1, roots.size());
        assertEquals(1, roots.get(0).tasks);
        assertFalse(roots.get(0).matches);
    }

    @Test
    public void packagePrefixesAreNotMatches() {
        DisplayTasks.Root root =
                DisplayTasks.parse(
                                "Stack id=1 displayId=2 userId=0\n"
                                        + " taskId=3: org.example.reader.extra/.MainActivity userId=0 visible=true\n",
                                APP)
                        .get(0);
        assertFalse(root.matches);
    }

    @Test
    public void internalInputTestIsDistinctFromPhoneController() {
        String text =
                "RootTask id=1 displayId=0 userId=0\n"
                        + " taskId=2: dev.dexdroid/dev.dexdroid.MainActivity userId=0 visible=true\n"
                        + "RootTask id=3 displayId=5 userId=0\n"
                        + " taskId=4: dev.dexdroid/dev.dexdroid.InputTestActivity userId=0 visible=true\n";
        List<DisplayTasks.Root> roots = DisplayTasks.parse(text, "dev.dexdroid/.InputTestActivity");
        assertFalse(roots.get(0).matches);
        assertTrue(roots.get(1).matches);
    }

    @Test
    public void sharedRootCountsUnrelatedTasksForMovementGuard() {
        DisplayTasks.Root root =
                DisplayTasks.parse(
                                "RootTask id=1 displayId=0 userId=0\n"
                                        + " taskId=2: org.example.reader/.MainActivity userId=0 visible=true\n"
                                        + " taskId=3: other/.Main userId=0 visible=false\n",
                                APP)
                        .get(0);
        assertEquals(2, root.tasks);
        assertTrue(root.matches);
    }

    @Test
    public void visibleMatchingTaskWinsOverLaterHiddenTask() {
        DisplayTasks.Root root =
                DisplayTasks.parse(
                                "RootTask id=1 displayId=2 userId=0\n"
                                        + " taskId=7: org.example.reader/.MainActivity userId=0 visible=true\n"
                                        + " taskId=8: org.example.reader/.MainActivity userId=0 visible=false\n",
                                APP)
                        .get(0);
        assertEquals(7, root.taskId);
        assertTrue(root.visible);
    }

    @Test
    public void malformedHeadersAndOrphanTasksCannotProvePlacement() {
        List<DisplayTasks.Root> roots =
                DisplayTasks.parse(
                        " taskId=1: org.example.reader/.MainActivity userId=0 visible=true\n"
                                + "RootTask id=2 displayId=3 userId=0\n"
                                + "RootTask id=invalid displayId=4 userId=0\n"
                                + " taskId=5: org.example.reader/.MainActivity userId=0 visible=true\n",
                        APP);
        assertEquals(1, roots.size());
        assertEquals(0, roots.get(0).tasks);
        assertFalse(roots.get(0).matches);
    }
}
