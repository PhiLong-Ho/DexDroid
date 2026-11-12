package dev.dexdroid

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Actual Pad recognizer, real service timer/fade and composed monitor pixels on a synthetic app.
 */
internal class PointerIdleAcceptance(
    private val runner: Instrumentation,
    private val session: SessionService,
    private val pad: View,
) {
    private val results = StringBuilder()

    private fun main(action: () -> Unit) = runner.runOnMainSync { action() }

    private fun check(ok: Boolean, label: String) {
        if (!ok) throw AssertionError(label)
        results.append("PASS $label\n")
    }

    private fun read(predicate: () -> Boolean): Boolean {
        var value = false
        main { value = predicate() }
        return value
    }

    private fun await(label: String, timeout: Long = 7000, predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (!read(predicate) && SystemClock.uptimeMillis() < end) Thread.sleep(15)
        check(read(predicate), label)
    }

    private fun cursor() =
        session.monitorWindow()!!.decorView.findViewWithTag<View>("monitor-cursor")

    private var down = 0L

    private fun contact(action: Int, count: Int = 1) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) down = now
        val props =
            Array(count) { index ->
                MotionEvent.PointerProperties().apply {
                    id = index
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
        val points =
            Array(count) { index ->
                MotionEvent.PointerCoords().apply {
                    x = pad.width * .4f + index * 15
                    y = pad.height * .5f
                    pressure = 1f
                }
            }
        val event =
            MotionEvent.obtain(
                down,
                now,
                action,
                count,
                props,
                points,
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
        try {
            main { pad.dispatchTouchEvent(event) }
        } finally {
            event.recycle()
        }
    }

    private fun shot(name: String, visible: Boolean) {
        val done = CountDownLatch(1)
        main { cursor().postOnAnimation { cursor().postOnAnimation { done.countDown() } } }
        check(done.await(3, TimeUnit.SECONDS), "cursor frame settles: $name")
        val window = session.monitorWindow()!!
        val bitmap =
            Bitmap.createBitmap(
                window.decorView.width,
                window.decorView.height,
                Bitmap.Config.ARGB_8888,
            )
        val copied = CountDownLatch(1)
        var code = -1
        main {
            PixelCopy.request(
                window,
                bitmap,
                {
                    code = it
                    copied.countDown()
                },
                Handler(Looper.getMainLooper()),
            )
        }
        try {
            check(
                copied.await(5, TimeUnit.SECONDS) && code == PixelCopy.SUCCESS,
                "cursor monitor capture: $name",
            )
            val point =
                Geometry.map(
                    session.cx / session.width(),
                    session.cy / session.height(),
                    session.rotation,
                    bitmap.width.toFloat(),
                    bitmap.height.toFloat(),
                    session.width(),
                    session.height(),
                )
            val pixel = bitmap.getPixel(point[0].toInt(), point[1].toInt())
            check(
                (pixel == Color.rgb(94, 234, 212)) == visible,
                "composed cursor center ${if(visible) "visible" else "absent"}: $name",
            )
            val folder =
                File(runner.targetContext.externalCacheDir, "submenu-acceptance").apply { mkdirs() }
            File(folder, "pointer-$name.png").outputStream().use {
                check(
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it),
                    "saved cursor evidence $name",
                )
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun run(): String {
        main {
            session.cancelPhoneInput()
            session.cx = session.width() * .35f
            session.cy = session.height() * .4f
            session.move(0f, 0f)
        }
        val touches = InputTestActivity.touchEvents
        shot("visible", true)
        Thread.sleep(4400)
        main {
            check(
                session.pointerVisible && cursor().alpha == 1f,
                "pointer stays opaque before idle deadline",
            )
            session.notifyUi()
            session.key(KeyEvent.KEYCODE_A)
        }
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            await("idle reaches native fade despite status/key activity", 1800) {
                session.pointerVisible && cursor().alpha > 0f && cursor().alpha < 1f
            }
        } else {
            await("idle hides with system animations disabled", 1800) { !session.pointerVisible }
        }
        main {
            session.move(1f, 0f)
            check(
                session.pointerVisible && cursor().alpha == 1f,
                "movement interrupts fade immediately",
            )
        }
        Thread.sleep(300)
        check(read { session.pointerVisible }, "cancelled fade cannot hide restored pointer")
        await("pointer disappears after renewed idle") { !session.pointerVisible }
        check(InputTestActivity.touchEvents == touches, "idle hiding injects no app touch")
        shot("hidden", false)
        contact(MotionEvent.ACTION_DOWN)
        Thread.sleep(PointerIdle.IDLE_MS + PointerIdle.FADE_MS + 100)
        check(
            read { session.pointerVisible && cursor().alpha == 1f },
            "stationary raw Pad hold survives idle interval",
        )
        contact(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), 2)
        contact(MotionEvent.ACTION_POINTER_UP or (1 shl 8), 2)
        Thread.sleep(PointerIdle.IDLE_MS + PointerIdle.FADE_MS + 100)
        check(read { session.pointerVisible }, "intermediate lift retains remaining-finger hold")
        contact(MotionEvent.ACTION_UP)
        await("final release starts new idle interval") { !session.pointerVisible }
        val beforeTap = InputTestActivity.touchEvents
        contact(MotionEvent.ACTION_DOWN)
        contact(MotionEvent.ACTION_UP)
        await("first wake tap delivered") { InputTestActivity.touchEvents >= beforeTap + 2 }
        check(
            InputTestActivity.touchEvents == beforeTap + 2,
            "wake tap is exactly one native down/up pair",
        )
        check(
            read { session.pointerVisible && cursor().alpha == 1f },
            "wake tap restores full opacity",
        )
        shot("wake", true)
        contact(MotionEvent.ACTION_DOWN)
        main { session.hideMonitorPointer() }
        contact(MotionEvent.ACTION_CANCEL)
        check(
            read { !session.pointerVisible },
            "abandoned Pad release cannot revive hidden pointer",
        )
        return results.toString()
    }
}
