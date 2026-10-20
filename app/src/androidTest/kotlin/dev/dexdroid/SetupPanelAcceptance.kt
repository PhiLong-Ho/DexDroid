package dev.dexdroid

import android.app.*
import android.content.*
import android.view.View
import android.widget.*

/** Real native panel with controlled dependency states; never uninstalls/stops Shizuku. */
internal class SetupPanelAcceptance {
    companion object {
        @JvmStatic
        fun run(runner: Instrumentation, phone: MainActivity?): String {
            val result = StringBuilder()
            runner.runOnMainSync({
                val state = arrayOf<ShizukuSetup.State?>(ShizukuSetup.State.MISSING)
                val opened = arrayOf<ShizukuSetup.Destination?>(null)
                val retries = intArrayOf(0)
                val reject = booleanArrayOf(false)
                val panel =
                    ShizukuSetup.Panel(
                        phone,
                        { state!![0] },
                        { retries!![0]++ },
                        { destination ->
                            if (reject!![0])
                                throw ActivityNotFoundException("controlled missing handler")
                            opened[0] = destination
                        },
                        "fixture",
                    )
                val window = Dialog(phone!!)
                window.setContentView(panel.view!!)
                window.show()
                try {
                    for (value in ShizukuSetup.State.values()!!) {
                        state[0] = value
                        panel.refresh()
                        val label = panel.view!!.findViewWithTag<TextView?>("fixture-shizuku-state")
                        val action = panel.view!!.findViewWithTag<Button?>("fixture-shizuku-action")
                        require(
                            label!!.getText()!!.toString().contains(value!!.label!!),
                            "state visible " + value!!,
                            result,
                        )
                        require(
                            action!!.getText()!!.toString() == value!!.action,
                            "state action " + value!!,
                            result,
                        )
                        opened[0] = null
                        val before = retries!![0]
                        action!!.performClick()
                        if (
                            (value == ShizukuSetup.State.PERMISSION ||
                                value == ShizukuSetup.State.READY)
                        )
                            require(
                                retries!![0] == before + 1 && opened!![0] == null,
                                ("authorization/reconnect uses callback without launching external app " +
                                    value!!),
                                result,
                            )
                        else
                            require(
                                (opened!![0] ==
                                    (if (value == ShizukuSetup.State.MISSING)
                                        ShizukuSetup.Destination.DOWNLOAD
                                    else ShizukuSetup.Destination.MANAGER)),
                                "explicit destination " + value!!,
                                result,
                            )
                        val manager = panel.view!!.findViewWithTag<View?>("fixture-shizuku-manager")
                        if (
                            (value == ShizukuSetup.State.PERMISSION ||
                                value == ShizukuSetup.State.READY)
                        ) {
                            require(
                                manager!!.isShown(),
                                ("manager remains available during authorization/reconnect " +
                                    value!!),
                                result,
                            )
                            manager!!.performClick()
                            require(
                                opened!![0] == ShizukuSetup.Destination.MANAGER,
                                "manager shortcut opens correct destination " + value!!,
                                result,
                            )
                        } else
                            require(
                                !manager!!.isShown(),
                                "no duplicate manager action " + value!!,
                                result,
                            )
                    }
                    for (value in
                        arrayOf<ShizukuSetup.State?>(
                            ShizukuSetup.State.MISSING,
                            ShizukuSetup.State.STOPPED,
                        )) {
                        state[0] = value
                        reject[0] = true
                        panel.refresh()
                        panel.view!!
                            .findViewWithTag<View?>("fixture-shizuku-action")!!
                            .performClick()
                        val feedback =
                            panel.view!!.findViewWithTag<TextView?>("fixture-shizuku-feedback")
                        require(
                            (feedback!!.getVisibility() == android.view.View.VISIBLE &&
                                feedback!!.getText()!!.toString().contains("phone") &&
                                window.isShowing()),
                            ("unavailable destination preserves setup with actionable feedback " +
                                value!!),
                            result,
                        )
                    }
                    require(
                        ShizukuSetup.DOWNLOAD == "https://shizuku.rikka.app/download/",
                        "official installation destination",
                        result,
                    )
                    require(
                        (phone!!
                            .getPackageManager()!!
                            .getLaunchIntentForPackage(ShizukuSetup.PACKAGE) != null),
                        "real installed manager discoverable through package visibility",
                        result,
                    )
                } finally {
                    window.dismiss()
                }
            })
            return result.toString()
        }

        private fun require(condition: Boolean, label: String?, result: StringBuilder?) {
            if (!condition) throw AssertionError(label)
            result!!.append("PASS Shizuku setup ").append(label).append('\n')
        }
    }
}
