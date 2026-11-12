package dev.dexdroid

import android.app.Instrumentation
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Native rendered structure and wrapping, supplementing real menu navigation and capture review.
 */
internal object MenuReadabilityAssertions {
    fun revealDiagram(runner: Instrumentation, root: View, name: String) {
        val frames = java.util.concurrent.CountDownLatch(1)
        runner.runOnMainSync {
            val diagram = root.findViewWithTag<View>("guide-diagram-$name")
            diagram.requestRectangleOnScreen(
                android.graphics.Rect(0, 0, diagram.width, diagram.height),
                true,
            )
            // Idle is not a rendered-frame boundary: PixelCopy can otherwise capture
            // the preceding topic while the native view tree has already scrolled.
            diagram.postOnAnimation {
                diagram.postOnAnimation { diagram.postOnAnimation { frames.countDown() } }
            }
        }
        require(frames.await(3, java.util.concurrent.TimeUnit.SECONDS)) {
            "Guide frame did not settle: $name"
        }
        runner.runOnMainSync {
            val diagram = root.findViewWithTag<View>("guide-diagram-$name")
            val visible = android.graphics.Rect()
            require(
                diagram.getLocalVisibleRect(visible) && visible.height() >= diagram.height - 2
            ) {
                "Requested diagram is not fully visible: $name"
            }
        }
    }

    private fun descendants(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) repeat(root.childCount) { addAll(descendants(root.getChildAt(it))) }
    }

    fun actionChevron(root: View) {
        require(
            descendants(root).filterIsInstance<ImageView>().any {
                it.tag == "menu-action-chevron" &&
                    it.drawable != null &&
                    it.width > 0 &&
                    !it.isClickable &&
                    !it.isFocusable &&
                    it.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        ) {
            "Menu action needs a decorative native vector chevron"
        }
    }

    fun settings(root: View): Int {
        val views = descendants(root)
        val labels =
            listOf(
                "Audio output",
                "App settings",
                "Workspace shortcut",
                "Shizuku setup",
                "Turn off Developer Mode",
                "User guide",
                "Monitor profiles",
                "Resolution and DPI",
                "Monitor touch",
                "Monitor edge navigation",
                "Android keyboard on monitor",
                "Pointer speed",
                "Scroll direction",
                "Center pointer",
                "Device capabilities",
                "Background operation",
                "Diagnostic log",
                "Protected screen help",
                "Reset monitor profile",
            )
        labels.forEach { title ->
            val text =
                views.filterIsInstance<TextView>().firstOrNull { it.text.toString() == title }
                    ?: throw AssertionError("Missing Settings label: $title")
            val row =
                text.parent.parent as? LinearLayout
                    ?: throw AssertionError("Missing labeled row: $title")
            val icon =
                (0 until row.childCount).map(row::getChildAt).firstOrNull {
                    it.tag == "menu-leading-icon"
                } as? ImageView ?: throw AssertionError("Missing vector beside $title")
            require(
                icon.drawable != null &&
                    !icon.isFocusable &&
                    !icon.isClickable &&
                    icon.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO
            ) {
                "Icon for $title must decorate its existing action, not add a focus/input target"
            }
            if (!row.isShown) return@forEach
            require(icon.width >= 23 * icon.resources.displayMetrics.density) {
                "Icon clipped: $title"
            }
            val column = text.parent as View
            require(column.left >= icon.right + 10 * icon.resources.displayMetrics.density) {
                "Icon/text overlap or missing separation: $title"
            }
        }
        return labels.size
    }

    fun guide(root: View): Int {
        repeat(13) { index ->
            val section =
                root.findViewWithTag<LinearLayout>("guide-section-$index")
                    ?: throw AssertionError("Guide topic missing: $index")
            val title =
                section.findViewWithTag<TextView>("guide-heading-$index")
                    ?: throw AssertionError("Separate guide heading missing: $index")
            require(title.isAccessibilityHeading && section.childCount > 1) {
                "Guide topic must have a heading and separate readable blocks: $index"
            }
        }
        listOf("touchpad", "navigation", "rotation", "split").forEach { name ->
            val diagram =
                root.findViewWithTag<GuideDiagram>("guide-diagram-$name")
                    ?: throw AssertionError("Offline diagram missing: $name")
            require(!diagram.isClickable && !diagram.isFocusable) {
                "Guide diagram became an action"
            }
            val labels = descendants(diagram).filterIsInstance<TextView>()
            require(labels.isNotEmpty() && labels.all { it.text.isNotBlank() }) {
                "Diagram lacks equivalent native text: $name"
            }
        }
        val text = descendants(root).filterIsInstance<TextView>()
        require(text.any { it.tag == "guide-step" } && text.any { it.tag == "guide-bullet" }) {
            "Guide procedures/facts must be distinguishable steps and bullet blocks"
        }
        require(
            MenuTextAssertions.contains(root, "does not prove") &&
                MenuTextAssertions.contains(root, "Android 11") &&
                MenuTextAssertions.contains(root, "bank") &&
                MenuTextAssertions.contains(root, "unsaved content")
        ) {
            "Guide lost important limits"
        }
        text
            .filter { it.width > 0 && it.layout != null }
            .forEach { label ->
                val layout = label.layout
                // API 35+ exposes actual glyph bounds. The horizontal-scroll envelope can
                // exceed the column because of start overhang even when visible ink fits.
                val drawingRight =
                    if (android.os.Build.VERSION.SDK_INT >= 35) {
                        val bounds = layout.computeDrawingBoundingBox()
                        val shift =
                            if (
                                label.getUseBoundsForWidth() &&
                                    label.getShiftDrawingOffsetForStartOverhang()
                            )
                                kotlin.math.max(0f, -bounds.left)
                            else 0f
                        bounds.right + shift
                    } else null
                require(
                    label.height >= layout.height + label.paddingTop + label.paddingBottom - 1
                ) {
                    "Clipped guide paragraph height: ${label.text.take(60)}"
                }
                repeat(layout.lineCount) { line ->
                    require(layout.getEllipsisCount(line) == 0) { "Guide text was ellipsized" }
                    val available = label.width - label.paddingLeft - label.paddingRight
                    val visibleRight = drawingRight ?: layout.getLineRight(line)
                    if (visibleRight > available + 2) {
                        val folder =
                            java.io.File(label.context.externalCacheDir, "submenu-acceptance")
                        check(folder.isDirectory || folder.mkdirs())
                        val bitmap =
                            android.graphics.Bitmap.createBitmap(
                                label.width + 64,
                                label.height.coerceAtMost(2048),
                                android.graphics.Bitmap.Config.ARGB_8888,
                            )
                        try {
                            val canvas = android.graphics.Canvas(bitmap)
                            canvas.drawColor(android.graphics.Color.BLACK)
                            canvas.translate(
                                label.paddingLeft.toFloat(),
                                label.paddingTop.toFloat(),
                            )
                            layout.paint.color = label.currentTextColor
                            layout.draw(canvas)
                            java.io
                                .FileOutputStream(java.io.File(folder, "guide-width-failure.png"))
                                .use {
                                    check(
                                        bitmap.compress(
                                            android.graphics.Bitmap.CompressFormat.PNG,
                                            100,
                                            it,
                                        )
                                    )
                                }
                        } finally {
                            bitmap.recycle()
                        }
                        val visible =
                            label.text
                                .subSequence(
                                    layout.getLineStart(line),
                                    layout.getLineVisibleEnd(line),
                                )
                                .toString()
                        val glyphBounds = android.graphics.Rect()
                        label.paint.getTextBounds(visible, 0, visible.length, glyphBounds)
                        java.io
                            .File(folder, "guide-width-failure.txt")
                            .writeText(
                                "width=${label.width} available=$available layoutWidth=${layout.width} line=$line visibleRight=$visibleRight right=${layout.getLineRight(line)} max=${layout.getLineMax(line)} advance=${layout.getLineWidth(line)} padding=${label.paddingLeft},${label.paddingRight}\n" +
                                    "glyphBounds=$glyphBounds visible=[$visible] measured=${label.paint.measureText(visible)} textSize=${label.textSize}\n" +
                                    "lineText=[${label.text.subSequence(layout.getLineStart(line), layout.getLineEnd(line))}]\n" +
                                    label.text,
                                Charsets.UTF_8,
                            )
                    }
                    require(visibleRight <= available + 2) {
                        "Guide text exceeds its column: width=$available layoutWidth=${layout.width} line=$line right=${layout.getLineRight(line)} max=${layout.getLineMax(line)}; ${label.text.take(60)}"
                    }
                }
            }
        return text.size
    }

    fun fontMatrix(runner: Instrumentation, context: Context): String {
        val result = StringBuilder()
        runner.runOnMainSync {
            for (scale in listOf(1f, 1.3f, 2f)) for (width in listOf(240, 360)) {
                val config =
                    Configuration(context.resources.configuration).apply { fontScale = scale }
                val adjusted = context.createConfigurationContext(config)
                val body = LinearLayout(adjusted).apply { orientation = LinearLayout.VERTICAL }
                UserGuide.add(ControllerStyle(adjusted), body)
                val pixels = (width * adjusted.resources.displayMetrics.density).toInt()
                body.measure(
                    View.MeasureSpec.makeMeasureSpec(pixels, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                body.layout(0, 0, pixels, body.measuredHeight)
                val count =
                    try {
                        guide(body)
                    } catch (error: Throwable) {
                        throw AssertionError("Native guide width=${width}dp / font=$scale", error)
                    }
                require(MenuTextAssertions.check(body) > 50) { "Guide text or font glyphs invalid" }
                require(count > 50) { "Guide content unexpectedly missing" }
                result.append(
                    "PASS native guide wrapping ${width}dp / font $scale: all 13 topics, diagrams, paragraphs and limits\n"
                )
            }
        }
        return result.toString()
    }
}
