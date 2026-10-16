package dev.dexdroid;

/** Destination-independent motion state; source-window routing marks are not portable. */
final class InputEventFlags {
    private InputEventFlags() {}

    static int motion(int flags, int api) {
        // Public obscuration bits remain conservative across the bridge. Generated
        // gesture and (API 33+) cancellation state retain their Android meaning.
        // Source-window/vendor routing marks such as JoyUI's observed 0x20000
        // are not part of a fresh virtual-display gesture's portable contract.
        return flags & (1 | 2 | 8 | (api >= 33 ? 32 : 0));
    }
}
