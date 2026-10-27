package dev.dexdroid.fixture

import android.os.Bundle

/** Fullscreen clickable native surface for contacts beneath the launch notice. */
class NoticeTouchFixtureActivity : NavigationFixtureActivity() {
    override fun onCreate(state: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar_Fullscreen)
        super.onCreate(state)
    }
}
