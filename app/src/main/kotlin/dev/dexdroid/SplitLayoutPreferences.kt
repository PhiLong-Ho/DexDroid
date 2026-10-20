package dev.dexdroid

import java.util.LinkedHashMap

/** Only durable workspace choices; display/task/input identities never enter preferences. */
internal class SplitLayoutPreferences {
    companion object {
        @JvmStatic
        fun read(values: Map<String?, *>): SplitLayout {
            val result = SplitLayout()
            result.enabled = java.lang.Boolean.TRUE == values.get("split.enabled")
            result.vertical = java.lang.Boolean.TRUE == values.get("split.vertical")
            result.swapped = java.lang.Boolean.TRUE == values.get("split.swapped")
            val ratio = values.get("split.ratio")
            if (ratio is Float && java.lang.Float.isFinite((ratio as Float?)!!))
                result.ratio = Math.max(.25f, Math.min(.75f, (ratio as Float?)!!))
            return result
        }

        @JvmStatic
        fun write(layout: SplitLayout): Map<String?, Any?> {
            val result = LinkedHashMap<String?, Any?>()
            result.put("split.enabled", layout.enabled)
            result.put("split.vertical", layout.vertical)
            result.put("split.swapped", layout.swapped)
            result.put(
                "split.ratio",
                if (java.lang.Float.isFinite(layout.ratio))
                    Math.max(.25f, Math.min(.75f, layout.ratio))
                else .5f,
            )
            return result
        }
    }
}
