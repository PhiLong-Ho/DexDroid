package dev.dexdroid

/** Ownership captured on main before submitting a single-view launch. */
internal class LaunchScope(
    private val generation: Int,
    private val display: Int,
    private val bridge: Any?,
) {
    fun current(generation: Int, display: Int, bridge: Any?, splitting: Boolean): Boolean =
        this.bridge != null &&
            this.generation == generation &&
            this.display >= 0 &&
            this.display == display &&
            this.bridge === bridge &&
            !splitting
}
