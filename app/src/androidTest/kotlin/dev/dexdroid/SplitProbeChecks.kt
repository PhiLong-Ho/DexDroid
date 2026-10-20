package dev.dexdroid

import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.display.DisplayManager
import android.os.*
import android.view.*
import dev.dexdroid.fixture.SplitProbeFixture
import java.io.*
import java.util.concurrent.*
import java.util.function.BooleanSupplier

/** Standalone native acceptance entry; deliberately excluded from normal product suites. */
class SplitProbeChecks(private val runner: Instrumentation?) {

    private val results: StringBuilder = StringBuilder()
    private val states: java.util.concurrent.atomic.AtomicReferenceArray<Bundle?> =
        java.util.concurrent.atomic.AtomicReferenceArray<Bundle?>(2)

    private var probe: SplitProbeActivity? = null
    private var hostDisplay: Int = 0
    private var down: Long = 0

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerLegacy(receiver: BroadcastReceiver?) {
        if (Build.VERSION.SDK_INT >= 33) throw IllegalStateException("Legacy receiver on modern OS")
        runner!!
            .getTargetContext()!!
            .registerReceiver(receiver, IntentFilter(SplitProbeFixture.ACTION))
    }

    private fun check(value: Boolean, label: String?) {
        if (!value) throw AssertionError(label + " | states=" + states.toString())
        results.append("PASS ").append(label).append('\n')
    }

    @Throws(Exception::class)
    private fun await(condition: BooleanSupplier, label: String?) {
        val end = SystemClock.uptimeMillis() + 12000
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(35)
        check(condition.getAsBoolean(), label)
    }

    private fun point(index: Int, target: String?): FloatArray {
        val value = FloatArray(2)
        runner!!.runOnMainSync({
            val view = probe!!.panes[index]!!.texture
            val location = IntArray(2)
            view!!.getLocationOnScreen(location)
            value[0] =
                (location[0] +
                    ((states.get(index)!!.getFloat(target!! + "X") * view!!.getWidth()) /
                        states.get(index)!!.getInt("width")))
            value[1] =
                (location[1] +
                    ((states.get(index)!!.getFloat(target!! + "Y") * view!!.getHeight()) /
                        states.get(index)!!.getInt("height")))
        })
        return value
    }

    @Throws(Exception::class)
    private fun mouse(
        action: Int,
        point: FloatArray?,
        buttons: Int,
        vertical: Float,
        horizontal: Float,
    ) {
        pointer(action, point, buttons, vertical, horizontal, InputDevice.SOURCE_MOUSE)
    }

    @Throws(Exception::class)
    private fun pointer(
        action: Int,
        point: FloatArray?,
        buttons: Int,
        vertical: Float,
        horizontal: Float,
        source: Int,
    ) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_SCROLL) down = now
        val property = MotionEvent.PointerProperties()
        property.id = 0
        property.toolType =
            if (source == InputDevice.SOURCE_MOUSE) MotionEvent.TOOL_TYPE_MOUSE
            else MotionEvent.TOOL_TYPE_FINGER
        val coord = MotionEvent.PointerCoords()
        coord.x = point!![0]
        coord.y = point!![1]
        coord.pressure =
            (if (buttons == 0 && source == InputDevice.SOURCE_MOUSE) 0 else 1).toFloat()
        coord.setAxisValue(MotionEvent.AXIS_VSCROLL, vertical)
        coord.setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal)
        val event =
            MotionEvent.obtain(
                down,
                now,
                action,
                1,
                arrayOf<MotionEvent.PointerProperties?>(property),
                arrayOf<MotionEvent.PointerCoords?>(coord),
                0,
                buttons,
                1f,
                1f,
                -1,
                0,
                source,
                0,
            )
        try {
            check(
                DeviceTestSupport.injectOnDisplay(event, hostDisplay),
                "host pointer injection " + action + "/" + source,
            )
        } finally {
            event!!.recycle()
        }
    }

    @Throws(Exception::class)
    private fun click(index: Int, target: String?) {
        val p = point(index, target)
        mouse(MotionEvent.ACTION_DOWN, p, MotionEvent.BUTTON_PRIMARY, 0f, 0f)
        Thread.sleep(100)
        mouse(MotionEvent.ACTION_UP, p, 0, 0f, 0f)
        Thread.sleep(100)
    }

    @Throws(Exception::class)
    private fun key(action: Int, code: Int, repeat: Int, meta: Int) {
        val now = SystemClock.uptimeMillis()
        val event =
            KeyEvent(now, now, action, code, repeat, meta, -1, 0, 0, InputDevice.SOURCE_KEYBOARD)
        check(
            DeviceTestSupport.injectOnDisplay(event, hostDisplay),
            "host key injection " + code + "/" + action,
        )
    }

    @Throws(Exception::class)
    private fun type(index: Int, code: Int, meta: Int, expected: String?) {
        click(index, "editor")
        key(KeyEvent.ACTION_DOWN, code, 0, meta)
        key(KeyEvent.ACTION_UP, code, 0, meta)
        await(
            { expected == states.get(index)!!.getString("text") },
            "native EditText typed in pane " + index,
        )
    }

    @Throws(Exception::class)
    private fun capture(name: String?) {
        Thread.sleep(300)
        val window = probe!!.presentation!!.getWindow()
        val image =
            Bitmap.createBitmap(
                window!!.getDecorView().getWidth(),
                window!!.getDecorView().getHeight(),
                Bitmap.Config.ARGB_8888,
            )
        val copied = CountDownLatch(1)
        val result = intArrayOf(-1)
        runner!!.runOnMainSync({
            PixelCopy.request(
                window!!,
                image,
                { value ->
                    result[0] = value
                    copied.countDown()
                },
                Handler(Looper.getMainLooper()!!),
            )
        })
        check(
            copied.await(5, TimeUnit.SECONDS) && result!![0] == PixelCopy.SUCCESS,
            "monitor PixelCopy " + name!!,
        )
        val folder =
            File(runner!!.getTargetContext()!!.getExternalFilesDir(null), "split-probe-acceptance")
        if (!folder.isDirectory() && !folder.mkdirs())
            throw IOException("Cannot create evidence folder")
        try {
            FileOutputStream(File(folder, name!! + ".png"))
                .use({ output -> image.compress(Bitmap.CompressFormat.PNG, 100, output) })
        } finally {
            image.recycle()
        }
    }

    fun run(): String {
        val receiver =
            object : BroadcastReceiver() {
                public override fun onReceive(context: Context?, intent: Intent) {
                    states.set(
                        if ("Left" == intent.getStringExtra("pane")) 0 else 1,
                        intent.getExtras(),
                    )
                }
            }
        try {
            if (Build.VERSION.SDK_INT >= 33)
                runner!!
                    .getTargetContext()!!
                    .registerReceiver(
                        receiver,
                        IntentFilter(SplitProbeFixture.ACTION),
                        Context.RECEIVER_EXPORTED,
                    )
            else registerLegacy(receiver)
            DeviceTestSupport.startInputDriver(runner)
            val packageName = runner!!.getContext()!!.getPackageName()
            probe =
                runner!!.startActivitySync(
                    Intent(runner!!.getTargetContext(), SplitProbeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(
                            "left",
                            (packageName!! + "/dev.dexdroid.fixture.SplitProbeFixture\$Left"),
                        )
                        .putExtra(
                            "right",
                            (packageName!! + "/dev.dexdroid.fixture.SplitProbeFixture\$Right"),
                        ),
                    ActivityOptions.makeBasic()!!.setLaunchDisplayId(0)!!.toBundle(),
                ) as SplitProbeActivity
            await(
                {
                    (states.get(0) != null &&
                        states.get(1) != null &&
                        states.get(0)!!.getInt("width") > 100 &&
                        states.get(1)!!.getInt("width") > 100)
                },
                "two native apps rendered and reported",
            )
            hostDisplay = probe!!.presentation!!.getDisplay()!!.getDisplayId()
            check(
                states.get(0)!!.getInt("display") != states.get(1)!!.getInt("display"),
                "independent app display IDs",
            )
            check(
                (states.get(0)!!.getInt("uid") !=
                    runner!!.getTargetContext()!!.getApplicationInfo()!!.uid),
                "fixture runs under different app UID",
            )
            check(
                (states.get(0)!!.getInt("display") == probe!!.panes[0]!!.id &&
                    states.get(1)!!.getInt("display") == probe!!.panes[1]!!.id),
                "verified pane placement",
            )
            capture("monitor-initial")
            type(0, KeyEvent.KEYCODE_L, KeyEvent.META_SHIFT_ON, "L")
            check(
                states.get(1)!!.getString("text")!!.isEmpty(),
                "left typing does not change right editor",
            )
            type(1, KeyEvent.KEYCODE_R, 0, "r")
            check(
                "L" == states.get(0)!!.getString("text"),
                "right typing does not change left editor",
            )
            key(
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_F1,
                3,
                KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON,
            )
            await(
                {
                    (states.get(1)!!.getInt("keyCode") == KeyEvent.KEYCODE_F1 &&
                        states.get(1)!!.getInt("keyRepeat") == 3)
                },
                "hardware key repeat preserved",
            )
            check(
                (((states.get(1)!!.getInt("keyMeta") and
                    (KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON))) ==
                    (KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON)),
                "Ctrl/Alt modifiers preserved",
            )
            key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F1, 0, 0)
            Thread.sleep(100)
            val leftKeys = states.get(0)!!.getInt("keyUps")
            val rightKeys = states.get(1)!!.getInt("keyUps")
            key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F2, 0, 0)
            await(
                { states.get(1)!!.getInt("keyCode") == KeyEvent.KEYCODE_F2 },
                "held key starts in right pane",
            )
            runner!!.runOnMainSync({ probe!!.select(0) })
            key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F2, 0, 0)
            await(
                { states.get(1)!!.getInt("keyUps") == rightKeys + 1 },
                "held key release stays with original pane",
            )
            check(
                states.get(0)!!.getInt("keyUps") == leftKeys,
                "selection does not split key sequence",
            )
            for (i in 0..1) {
                val pane = i
                val otherClicks = states.get(1 - i)!!.getInt("clicks")
                click(i, "button")
                await(
                    { states.get(pane)!!.getInt("clicks") == 1 },
                    "semantic native mouse click pane " + i,
                )
                check(
                    !states.get(pane)!!.getBoolean("focusLostDuringContact"),
                    "native click retains focus during contact " + i,
                )
                check(
                    states.get(1 - i)!!.getInt("clicks") == otherClicks,
                    "no mouse click in other pane " + i,
                )
                val before = states.get(i)!!.getInt("wheels")
                mouse(MotionEvent.ACTION_SCROLL, point(i, "pad"), 0, -1.5f, .5f)
                await(
                    { states.get(pane)!!.getInt("wheels") == before + 1 },
                    "wheel routed to pane " + i,
                )
                check(
                    (states.get(i)!!.getFloat("wheelV") == -1.5f &&
                        states.get(i)!!.getFloat("wheelH") == .5f &&
                        states.get(i)!!.getInt("wheelSource") == InputDevice.SOURCE_MOUSE),
                    "wheel axes and mouse source preserved " + i,
                )
            }
            val rightMoves = states.get(1)!!.getInt("moves")
            val rightUps = states.get(1)!!.getInt("ups")
            mouse(MotionEvent.ACTION_DOWN, point(0, "pad"), MotionEvent.BUTTON_PRIMARY, 0f, 0f)
            mouse(MotionEvent.ACTION_MOVE, point(1, "pad"), MotionEvent.BUTTON_PRIMARY, 0f, 0f)
            mouse(MotionEvent.ACTION_UP, point(1, "pad"), 0, 0f, 0f)
            await(
                { states.get(0)!!.getInt("moves") > 0 && states.get(0)!!.getInt("ups") > 0 },
                "drag crossing divider stays with left pane",
            )
            check(
                (states.get(1)!!.getInt("moves") == rightMoves &&
                    states.get(1)!!.getInt("ups") == rightUps),
                "cross-divider drag never enters other app",
            )
            check(
                (states.get(0)!!.getInt("pointerSource") == InputDevice.SOURCE_MOUSE &&
                    (states.get(0)!!.getInt("lastHeldButtons") == MotionEvent.BUTTON_PRIMARY)),
                "drag mouse source and primary button preserved",
            )
            capture("monitor-equal")
            val leftWidth = states.get(0)!!.getInt("width")
            val rightWidth = states.get(1)!!.getInt("width")
            runner!!.runOnMainSync({ probe!!.setRatio(.35f) })
            await(
                {
                    (states.get(0)!!.getInt("width") < leftWidth &&
                        states.get(1)!!.getInt("width") > rightWidth)
                },
                "split changes actual app display dimensions",
            )
            for (i in 0..1) {
                val pane = i
                click(i, "button")
                await(
                    { states.get(pane)!!.getInt("clicks") == 2 },
                    "resized native click alignment " + i,
                )
            }
            for (i in 0..1) {
                val pane = i
                val p = point(i, "button")
                pointer(MotionEvent.ACTION_DOWN, p, 0, 0f, 0f, InputDevice.SOURCE_TOUCHSCREEN)
                Thread.sleep(100)
                pointer(MotionEvent.ACTION_UP, p, 0, 0f, 0f, InputDevice.SOURCE_TOUCHSCREEN)
                await(
                    { states.get(pane)!!.getInt("clicks") == 3 },
                    "native touchscreen click after resize " + i,
                )
                check(
                    !states.get(pane)!!.getBoolean("focusLostDuringContact"),
                    "touch contact retains focus after resize " + i,
                )
            }
            capture("monitor-resized")
            val expected = arrayOf<String?>("L", "r")
            for (iteration in 0..4) {
                for (i in 0..1) {
                    expected[i] += "a"
                    type(i, KeyEvent.KEYCODE_A, 0, expected!![i])
                }
            }
            check(
                ("Laaaaa" == states.get(0)!!.getString("text") &&
                    "raaaaa" == states.get(1)!!.getString("text")),
                "ten alternating pane selections retain all typed characters",
            )
            results
                .append("CONFIG Android ")
                .append(Build.VERSION.RELEASE)
                .append(" ")
                .append(Build.MODEL)
                .append(" | host ")
                .append(hostDisplay)
                .append(" | injected framework events, not physical peripherals\n")
        } catch (error: Throwable) {
            results.append("FAIL ").append(android.util.Log.getStackTraceString(error)).append('\n')
        } finally {
            try {
                if (probe != null) {
                    val left = if (probe!!.panes[0] == null) -1 else probe!!.panes[0]!!.id
                    val right = if (probe!!.panes[1] == null) -1 else probe!!.panes[1]!!.id
                    runner!!.runOnMainSync({ probe!!.stopExperiment(false) })
                    val manager =
                        runner!!
                            .getTargetContext()!!
                            .getSystemService<DisplayManager?>(DisplayManager::class.java!!)
                    await(
                        {
                            (manager!!.getDisplay(left) == null &&
                                manager!!.getDisplay(right) == null)
                        },
                        "Stop releases both app displays",
                    )
                    runner!!.runOnMainSync({ probe!!.finish() })
                    runner!!.runOnMainSync({})
                }
            } catch (error: Throwable) {
                results.append("FAIL cleanup ").append(error).append('\n')
            }

            DeviceTestSupport.stopInputDriver()
            runner!!.getTargetContext()!!.unregisterReceiver(receiver)
        }
        return results.toString()
    }
}
