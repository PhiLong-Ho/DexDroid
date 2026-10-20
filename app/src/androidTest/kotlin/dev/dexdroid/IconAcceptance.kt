package dev.dexdroid

import android.app.Instrumentation
import android.graphics.*
import android.graphics.drawable.*
import java.io.*

/** Checks installed launcher resources through Android's real vector renderer. */
class IconAcceptance(private val runner: Instrumentation?) {
    private val results: StringBuilder = StringBuilder()

    private fun check(value: Boolean, label: String?) {
        if (!value) throw AssertionError(label)
        results.append("PASS ").append(label).append('\n')
    }

    private fun vector(id: Int): Drawable {
        return runner!!.getTargetContext()!!.getResources()!!.getDrawable(id, null)!!.mutate()
    }

    private fun foreground(drawable: Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(-size / 4, -size / 4, size * 5 / 4, size * 5 / 4)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun mask(name: String, size: Int): Path {
        val half = size / 2f
        val path = Path()
        if (name == "Circle") path.addCircle(half, half, half, Path.Direction.CW)
        else if (name == "Rounded square")
            path.addRoundRect(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                size * 24f / 108,
                size * 24f / 108,
                Path.Direction.CW,
            )
        else if (name == "Squircle") {
            for (i in 0..256) {
                val angle = i.toDouble() * Math.PI * 2.0 / 256
                val cos = Math.cos(angle)
                val sin = Math.sin(angle)
                val x = half + Math.copySign(Math.sqrt(Math.abs(cos)) * half, cos).toFloat()
                val y = half + Math.copySign(Math.sqrt(Math.abs(sin)) * half, sin).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        } else path.addRect(0f, 0f, size.toFloat(), size.toFloat(), Path.Direction.CW)
        return path
    }

    private fun clipped(foreground: Bitmap, mask: Path?, background: Boolean): Bitmap {
        val result =
            Bitmap.createBitmap(
                foreground.getWidth(),
                foreground.getHeight(),
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(result)
        canvas.clipPath(mask!!)
        if (background) canvas.drawColor(-0xf7f7f8)
        canvas.drawBitmap(foreground, 0f, 0f, null)
        return result
    }

    private fun alpha(bitmap: Bitmap): Long {
        var sum: Long = 0
        for (y in 0 until bitmap.getHeight()) for (x in 0 until bitmap.getWidth()) sum +=
            Color.alpha(bitmap.getPixel(x, y)).toLong()
        return sum
    }

    private fun grayscale(bitmap: Bitmap, label: String?) {
        var ink = 0
        var dark = 0
        for (y in 0 until bitmap.getHeight()) for (x in 0 until bitmap.getWidth()) {
            val pixel = bitmap.getPixel(x, y)
            checkColor(pixel, label)
            if (Color.alpha(pixel) > 200) {
                if (Color.red(pixel) > 220) ink++
                if (Color.red(pixel) < 20) dark++
            }
        }
        check(
            ink > bitmap.getWidth() * bitmap.getHeight() / 30,
            label!! + " has readable white artwork",
        )
        check(
            dark > bitmap.getWidth() * bitmap.getHeight() / 3,
            label!! + " retains black negative space",
        )
    }

    private fun checkColor(pixel: Int, label: String?) {
        if (
            (Color.alpha(pixel) != 0 &&
                ((Color.red(pixel) != Color.green(pixel) ||
                    Color.green(pixel) != Color.blue(pixel))))
        )
            throw AssertionError(label!! + " contains an accent color")
    }

    @Throws(Exception::class)
    private fun save(bitmap: Bitmap, file: File) {
        FileOutputStream(file)
            .use({ stream ->
                check(
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream),
                    "exported " + file.getName(),
                )
            })
    }

    private fun eye(localX: Float): FloatArray {
        val angle = Math.PI * 225 / 180
        val x = 61 + .85f * (localX * Math.cos(angle) + 11.5 * Math.sin(angle)).toFloat()
        val y = 38 + .85f * (localX * Math.sin(angle) - 11.5 * Math.cos(angle)).toFloat()
        return floatArrayOf((x * 1.5f - 27) * 512 / 108, (y * 1.5f - 27) * 512 / 108)
    }

    @Throws(Exception::class)
    fun run(): String {
        val context = runner!!.getTargetContext()
        val installed =
            context!!.getPackageManager()!!.getApplicationIcon(context!!.getPackageName()!!)
        check(installed is AdaptiveIconDrawable, "installed app exposes an adaptive launcher icon")
        val adaptive = installed as AdaptiveIconDrawable
        check(
            (adaptive!!.getBackground() is ColorDrawable &&
                (adaptive!!.getBackground() as ColorDrawable).getColor() == -0xf7f7f8),
            "installed adaptive background matches the black app theme",
        )
        val directory = File(context!!.getExternalCacheDir(), "icon-acceptance")
        check(directory.isDirectory() || directory.mkdirs(), "preview directory available")
        val raw = foreground(vector(R.drawable.ic_launcher_foreground), 512)
        val mono = foreground(vector(R.drawable.ic_launcher_monochrome), 512)
        val originalAlpha = alpha(raw)
        check(originalAlpha > 0, "packaged foreground renders visible artwork")
        check(raw.sameAs(mono), "themed and standard icons use the same silhouette")
        for (localX in floatArrayOf(-3.5f, 3.5f)) {
            val p = eye(localX)
            check(
                Color.alpha(raw.getPixel(Math.round(p[0]), Math.round(p[1]))) < 20,
                "Android eye remains a transparent cutout",
            )
        }
        val tinted = vector(R.drawable.ic_launcher_monochrome)
        tinted.setTint(Color.BLACK)
        val black = foreground(tinted, 512)
        check(alpha(black) == originalAlpha, "theme tint preserves silhouette and eye cutouts")
        black.recycle()
        val sheet = Bitmap.createBitmap(896, 280, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(-0xdfdfe0)
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        text.setColor(Color.WHITE)
        text.setTextSize(16f)
        text.setTextAlign(Paint.Align.CENTER)
        val shapes = arrayOf<String?>("Circle", "Rounded square", "Squircle", "Square")
        for (i in shapes!!.indices) {
            val name = shapes!![i]
            val bounded = clipped(raw, mask(name!!, 512), false)
            check(
                alpha(bounded) == originalAlpha,
                name!! + " mask does not clip the robot or monitor",
            )
            bounded.recycle()
            val icon = clipped(raw, mask(name!!, 512), true)
            grayscale(icon, name)
            canvas.drawBitmap(
                icon,
                null,
                Rect(16 + i * 224, 16, 208 + i * 224, 208),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            canvas.drawText(name!!, (112 + i * 224).toFloat(), 242f, text)
            if (i == 1) save(icon, File(directory, "dexdroid-icon.png"))
            icon.recycle()
        }
        save(sheet, File(directory, "icon-mask-preview.png"))
        sheet.recycle()
        for (size in intArrayOf(48, 64, 96)) {
            val small = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            adaptive!!.setBounds(0, 0, size, size)
            adaptive!!.draw(Canvas(small))
            grayscale(small, "installed icon at " + size + " px")
            small.recycle()
        }
        val system = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        adaptive!!.setBounds(0, 0, 512, 512)
        adaptive!!.draw(Canvas(system))
        save(system, File(directory, "system-launcher-icon.png"))
        system.recycle()
        raw.recycle()
        mono.recycle()
        return results.toString()
    }
}
