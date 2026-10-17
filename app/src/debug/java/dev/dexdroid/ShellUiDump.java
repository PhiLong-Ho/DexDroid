package dev.dexdroid;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.StringWriter;
import java.util.List;
import org.xmlpull.v1.XmlSerializer;

/** Read-only shell snapshot of phone windows; absent from production APKs. */
@android.annotation.SuppressLint("PrivateApi")
public final class ShellUiDump {
    private static final int MAX_NODES = 10000;
    private static final int MAX_DEPTH = 64;
    private final long deadline = SystemClock.uptimeMillis() + 10000;
    private int nodes;

    private ShellUiDump() {}

    public static void main(String[] args) throws Exception {
        if (android.os.Process.myUid() != 2000 || args.length != 0)
            throw new SecurityException("Phone window snapshot requires authorized shell");
        HandlerThread thread = new HandlerThread("DexDroidPhoneSnapshot");
        thread.start();
        UiAutomation automation = null;
        Object shellConnection = null;
        boolean connectAttempted = false;
        boolean connected = false;
        String result;
        try {
            Class<?> connection = Class.forName("android.app.IUiAutomationConnection");
            shellConnection =
                    Class.forName("android.app.UiAutomationConnection")
                            .getConstructor()
                            .newInstance();
            automation =
                    (UiAutomation)
                            UiAutomation.class
                                    .getConstructor(Looper.class, connection)
                                    .newInstance(thread.getLooper(), shellConnection);
            connectAttempted = true;
            UiAutomation.class
                    .getMethod("connect", int.class)
                    .invoke(automation, UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            connected = true;
            AccessibilityServiceInfo info = automation.getServiceInfo();
            if (info == null) throw new IllegalStateException("Accessibility metadata unavailable");
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation.setServiceInfo(info);
            // Setting retrieval flags does not await asynchronous window discovery.
            // Event quiet is only a settling hint; the bounded window list is the
            // readiness condition, and action safety is validated by the caller.
            try {
                automation.waitForIdle(100, 1000);
            } catch (java.util.concurrent.TimeoutException busyAccessibility) {
                // A busy event stream can still expose a complete window snapshot.
            }
            List<AccessibilityWindowInfo> windows = awaitPhoneWindows(automation);
            result = new ShellUiDump().snapshot(windows);
        } finally {
            try {
                if (connected) UiAutomation.class.getMethod("disconnect").invoke(automation);
                else if (connectAttempted)
                    shellConnection.getClass().getMethod("disconnect").invoke(shellConnection);
            } finally {
                try {
                    // connect() can register accessibility before its readiness wait fails.
                    // Disconnect first: some platform shutdown() implementations mark the
                    // connection shut down before attempting their own disconnect.
                    if (shellConnection != null)
                        shellConnection.getClass().getMethod("shutdown").invoke(shellConnection);
                } finally {
                    thread.quitSafely();
                    thread.join(5000);
                    if (thread.isAlive())
                        throw new IllegalStateException("Phone snapshot thread did not terminate");
                }
            }
        }
        // Successful XML cannot escape until the connection and looper are gone.
        System.out.println(result);
    }

    private static List<AccessibilityWindowInfo> awaitPhoneWindows(UiAutomation automation) {
        long end = SystemClock.uptimeMillis() + 2000;
        while (true) {
            List<AccessibilityWindowInfo> windows = automation.getWindowsOnAllDisplays().get(0);
            if (windows != null && windows.size() > 64)
                throw new IllegalStateException("Phone window count exceeds snapshot bounds");
            if (SystemClock.uptimeMillis() >= end)
                throw new IllegalStateException("Display-0 window discovery timed out");
            if (windows != null && !windows.isEmpty()) return windows;
            SystemClock.sleep(50);
        }
    }

    private String snapshot(List<AccessibilityWindowInfo> windows) throws Exception {
        StringWriter text = new StringWriter();
        XmlSerializer xml = Xml.newSerializer();
        xml.setOutput(text);
        xml.startDocument("UTF-8", true);
        xml.startTag(null, "hierarchy");
        xml.attribute(null, "display-id", "0");
        for (AccessibilityWindowInfo window : windows) {
            if (window.getDisplayId() != 0)
                throw new IllegalStateException("Unexpected external accessibility window");
            Rect bounds = new Rect();
            window.getBoundsInScreen(bounds);
            xml.startTag(null, "window");
            xml.attribute(null, "id", Integer.toString(window.getId()));
            xml.attribute(null, "layer", Integer.toString(window.getLayer()));
            xml.attribute(null, "bounds", bounds(bounds));
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) throw new IllegalStateException("Phone window root unavailable");
            try {
                node(xml, root, 0);
            } finally {
                root.recycle();
                window.recycle();
            }
            xml.endTag(null, "window");
        }
        xml.endTag(null, "hierarchy");
        xml.endDocument();
        return text.toString();
    }

    private void node(XmlSerializer xml, AccessibilityNodeInfo node, int depth) throws Exception {
        if (++nodes > MAX_NODES || depth > MAX_DEPTH || SystemClock.uptimeMillis() > deadline)
            throw new IllegalStateException("Phone window traversal exceeded snapshot bounds");
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        xml.startTag(null, "node");
        xml.attribute(null, "package", value(node.getPackageName()));
        xml.attribute(null, "text", value(node.getText()));
        xml.attribute(null, "content-desc", value(node.getContentDescription()));
        xml.attribute(null, "enabled", Boolean.toString(node.isEnabled()));
        xml.attribute(null, "clickable", Boolean.toString(node.isClickable()));
        xml.attribute(null, "visible-to-user", Boolean.toString(node.isVisibleToUser()));
        xml.attribute(null, "bounds", bounds(bounds));
        int children = node.getChildCount();
        if (children > MAX_NODES - nodes)
            throw new IllegalStateException("Phone child count exceeded snapshot bounds");
        for (int index = 0; index < children; index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) throw new IllegalStateException("Phone snapshot child unavailable");
            try {
                node(xml, child, depth + 1);
            } finally {
                child.recycle();
            }
        }
        xml.endTag(null, "node");
    }

    private static String value(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private static String bounds(Rect bounds) {
        return "["
                + bounds.left
                + ","
                + bounds.top
                + "]["
                + bounds.right
                + ","
                + bounds.bottom
                + "]";
    }
}
