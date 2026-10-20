package dev.dexdroid

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Rect
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Xml
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.io.StringWriter
import org.xmlpull.v1.XmlSerializer

/** Read-only shell snapshot of phone windows; absent from production APKs. */
@android.annotation.SuppressLint("PrivateApi")
class ShellUiDump private constructor() {
    private val deadline: Long = SystemClock.uptimeMillis() + 10000
    private var nodes: Int = 0

    @Throws(Exception::class)
    private fun snapshot(windows: List<AccessibilityWindowInfo?>): String? {
        val text = StringWriter()
        val xml = Xml.newSerializer()
        xml!!.setOutput(text)
        xml!!.startDocument("UTF-8", true)
        xml!!.startTag(null, "hierarchy")
        xml!!.attribute(null, "display-id", "0")
        for (window in windows) {
            if (window!!.getDisplayId() != 0)
                throw IllegalStateException("Unexpected external accessibility window")
            val bounds = Rect()
            window!!.getBoundsInScreen(bounds)
            xml!!.startTag(null, "window")
            xml!!.attribute(null, "id", Integer.toString(window!!.getId()))
            xml!!.attribute(null, "layer", Integer.toString(window!!.getLayer()))
            xml!!.attribute(null, "bounds", bounds(bounds))
            val root = window!!.getRoot()
            if (root == null) throw IllegalStateException("Phone window root unavailable")
            try {
                node(xml, root, 0)
            } finally {
                root!!.recycle()
                window!!.recycle()
            }
            xml!!.endTag(null, "window")
        }
        xml!!.endTag(null, "hierarchy")
        xml!!.endDocument()
        return text.toString()
    }

    @Throws(Exception::class)
    private fun node(xml: XmlSerializer?, node: AccessibilityNodeInfo?, depth: Int) {
        if (++nodes > MAX_NODES || depth > MAX_DEPTH || SystemClock.uptimeMillis() > deadline)
            throw IllegalStateException("Phone window traversal exceeded snapshot bounds")
        val bounds = Rect()
        node!!.getBoundsInScreen(bounds)
        xml!!.startTag(null, "node")
        xml!!.attribute(null, "package", value(node!!.getPackageName()))
        xml!!.attribute(null, "text", value(node!!.getText()))
        xml!!.attribute(null, "content-desc", value(node!!.getContentDescription()))
        xml!!.attribute(null, "enabled", java.lang.Boolean.toString(node!!.isEnabled()))
        xml!!.attribute(null, "clickable", java.lang.Boolean.toString(node!!.isClickable()))
        xml!!.attribute(
            null,
            "visible-to-user",
            java.lang.Boolean.toString(node!!.isVisibleToUser()),
        )
        xml!!.attribute(null, "bounds", bounds(bounds))
        val children = node!!.getChildCount()
        if (children > MAX_NODES - nodes)
            throw IllegalStateException("Phone child count exceeded snapshot bounds")
        for (index in 0 until children) {
            val child = node!!.getChild(index)
            if (child == null) throw IllegalStateException("Phone snapshot child unavailable")
            try {
                node(xml, child, depth + 1)
            } finally {
                child!!.recycle()
            }
        }
        xml!!.endTag(null, "node")
    }

    companion object {
        private val MAX_NODES: Int = 10000
        private val MAX_DEPTH: Int = 64

        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            if (android.os.Process.myUid() != 2000 || args.size != 0)
                throw SecurityException("Phone window snapshot requires authorized shell")
            // Raw app_process has no framework-prepared main Looper. Android 16's
            // accessibility client needs its queue even for window-only snapshots.
            // This process-owned queue lives until CLI exit; no main-loop dispatch
            // or Application initialization is needed. The coordinator below
            // disconnects UiAutomation, then quits and joins its owned callback
            // thread before successful XML can escape.
            val mainLooper = Looper.getMainLooper()
            val currentLooper = Looper.myLooper()
            if (currentLooper != null && currentLooper !== mainLooper)
                throw IllegalStateException("Incompatible shell thread Looper")
            if (mainLooper == null) Looper.prepareMainLooper()
            val thread = HandlerThread("DexDroidPhoneSnapshot")
            thread.start()
            var automation: UiAutomation? = null
            var shellConnection: Any? = null
            var connectAttempted = false
            var connected = false
            val result: String?
            try {
                val connection = Class.forName("android.app.IUiAutomationConnection")
                shellConnection =
                    Class.forName("android.app.UiAutomationConnection")
                        .getConstructor()
                        .newInstance()
                automation =
                    UiAutomation::class
                        .java!!
                        .getConstructor(Looper::class.java, connection)
                        .newInstance(thread.getLooper(), shellConnection) as UiAutomation
                connectAttempted = true
                UiAutomation::class
                    .java!!
                    .getMethod("connect", Int::class.javaPrimitiveType)
                    .invoke(automation, UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                connected = true
                val info = automation!!.getServiceInfo()
                if (info == null) throw IllegalStateException("Accessibility metadata unavailable")
                info!!.flags =
                    info!!.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                automation!!.setServiceInfo(info)
                // Setting retrieval flags does not await asynchronous window discovery.
                // Event quiet is only a settling hint; the bounded window list is the
                // readiness condition, and action safety is validated by the caller.
                try {
                    automation!!.waitForIdle(100, 1000)
                } catch (busyAccessibility: java.util.concurrent.TimeoutException) {
                    // A busy event stream can still expose a complete window snapshot.
                }

                val windows = awaitPhoneWindows(automation!!)
                result = ShellUiDump().snapshot(windows)
            } finally {
                try {
                    if (connected)
                        UiAutomation::class.java!!.getMethod("disconnect").invoke(automation)
                    else if (connectAttempted)
                        shellConnection!!.javaClass.getMethod("disconnect").invoke(shellConnection)
                } finally {
                    try {
                        // connect() can register accessibility before its readiness wait fails.
                        // Disconnect first: some platform shutdown() implementations mark the
                        // connection shut down before attempting their own disconnect.
                        if (shellConnection != null)
                            shellConnection!!
                                .javaClass
                                .getMethod("shutdown")
                                .invoke(shellConnection)
                    } finally {
                        thread.quitSafely()
                        thread.join(5000)
                        if (thread.isAlive())
                            throw IllegalStateException("Phone snapshot thread did not terminate")
                    }
                }
            }
            // Successful XML cannot escape until the connection and looper are gone.
            println(result)
        }

        private fun awaitPhoneWindows(automation: UiAutomation): List<AccessibilityWindowInfo?> {
            val end = SystemClock.uptimeMillis() + 2000
            while (true) {
                val windows = automation.getWindowsOnAllDisplays().get(0)
                if (windows != null && windows!!.size > 64)
                    throw IllegalStateException("Phone window count exceeds snapshot bounds")
                if (SystemClock.uptimeMillis() >= end)
                    throw IllegalStateException("Display-0 window discovery timed out")
                if (windows != null && !windows!!.isEmpty()) return windows
                SystemClock.sleep(50)
            }
        }

        private fun value(value: CharSequence?): String {
            return if (value == null) "" else value!!.toString()
        }

        private fun bounds(bounds: Rect): String {
            return ("[" +
                bounds.left +
                "," +
                bounds.top +
                "][" +
                bounds.right +
                "," +
                bounds.bottom +
                "]")
        }
    }
}
