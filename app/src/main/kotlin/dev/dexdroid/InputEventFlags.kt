package dev.dexdroid

/** Destination-independent motion state; source-window routing marks are not portable. */
object InputEventFlags {

    @JvmStatic
    fun motion(flags: Int, api: Int): Int {
        // Public obscuration bits remain conservative across the bridge. Generated
        // gesture and (API 33+) cancellation state retain their Android meaning.
        // Source-window/vendor routing marks such as JoyUI's observed 0x20000
        // are not part of a fresh virtual-display gesture's portable contract.
        return flags and (1 or 2 or 8 or (if (api >= 33) 32 else 0))
    }
}
