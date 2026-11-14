package dev.dexdroid

import android.view.View

/** Same optional title-row action on phone and monitor; recovery cannot be hidden. */
internal class PhoneScreenButton(
    style: ControllerStyle,
    private val session: () -> SessionService?,
    prefix: String,
) {
    val view =
        style.menuIconAction(
            R.drawable.ic_phone_screen_off,
            "Turn phone screen off",
            Runnable { session()?.togglePhoneScreen() },
        )

    init {
        view.tag = "$prefix-phone-screen"
        refresh()
    }

    fun refresh() {
        val owner = session()
        val state = owner?.phoneScreenStatus()
        val recovery = state?.possibleOff == true
        view.visibility =
            if (owner != null && (owner.phoneScreenButtonEnabled() || recovery)) View.VISIBLE
            else View.GONE
        val action = if (recovery) "Turn phone screen on" else "Turn phone screen off"
        view.setImageResource(
            if (recovery) R.drawable.ic_phone_screen_on else R.drawable.ic_phone_screen_off
        )
        view.contentDescription = action
        view.tooltipText = if (recovery) action else owner?.phoneScreenExplanation() ?: action
        view.isEnabled =
            if (recovery) state?.restoring != true || state?.pending != true
            else owner?.canTurnPhoneScreenOff() == true
    }
}
