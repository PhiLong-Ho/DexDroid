package dev.dexdroid

import android.content.Context
import android.graphics.Color
import android.text.InputFilter
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.ArrayList

/** One profile-management view shared by the portrait phone and rotated monitor shell. */
@android.annotation.SuppressLint("ViewConstructor")
class MonitorProfilesPanel(
    context: Context?,
    private val session: SessionService?,
    private val monitor: Boolean,
) : LinearLayout(context) {
    private val style: ControllerStyle?
    private val runningActions: MutableList<View?> = ArrayList<View?>()
    private val editActions: MutableList<View?> = ArrayList<View?>()
    private var name: EditText? = null
    private var status: TextView? = null
    private var message: TextView? = null
    private var snapshotIdentity: String? = null
    private var keep: Button? = null
    private var revert: Button? = null
    private var forget: View? = null
    private var selectedId: String? = null
    private var deleteArmed: String? = null
    private var snapshot: Map<String?, String?>? = null
    private var creating: Boolean = false

    init {
        style = ControllerStyle(context)
        setTag("profiles-panel")
        setOrientation(LinearLayout.VERTICAL)
        setForceDarkAllowed(false)
        creating = session!!.monitorProfiles().all().isEmpty()
        render(null)
    }

    private fun text(value: String?, tag: String?): TextView {
        val view = style!!.label(value, 12)
        view.setTag(tag)
        view.setTextColor(Color.LTGRAY)
        view.setPadding(0, style!!.dp(6f), 0, style!!.dp(6f))
        addView(view)
        return view
    }

    private fun action(
        title: String?,
        tag: String?,
        icon: Int,
        needsDesktop: Boolean,
        command: Runnable?,
    ): View {
        val row = style!!.settingRow(this)
        val image = ImageView(getContext())
        image.setImageResource(icon)
        image.setColorFilter(Color.WHITE)
        row.addView(image, LinearLayout.LayoutParams(style!!.dp(24f), style!!.dp(24f)))
        val label = style!!.label(title, 14)
        label.setPadding(style!!.dp(12f), 0, 0, 0)
        row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        row.setTag(tag)
        row.setContentDescription(title)
        row.setFocusable(true)
        row.setOnClickListener({ v -> command!!.run() })
        (if (needsDesktop) runningActions else editActions).add(row)
        return row
    }

    private fun nextName(): String {
        val profiles = session!!.monitorProfiles().all()
        var number = 1
        while (true) {
            val proposed = "Profile " + number
            if (
                profiles
                    .stream()!!
                    .noneMatch({ profile ->
                        profile!!.name!!.equals(proposed!!, ignoreCase = true)
                    })
            )
                return proposed
            number++
        }
    }

    private fun render(draft: String?) {
        removeAllViews()
        name = null
        forget = null
        runningActions.clear()
        editActions.clear()
        snapshot = MonitorProfilePreferences(session!!.prefs).read()
        snapshotIdentity = session!!.monitorIdentity()
        val selected = session!!.monitorProfiles().find(selectedId)
        if (selected == null) selectedId = null
        status = text("", "profiles-status")
        text(session!!.monitorMatchingDescription(), "profiles-matching")
        message = text("", "profiles-message")
        val confirmation = LinearLayout(getContext())
        keep = style!!.button(confirmation, "Keep", { session!!.confirm(true) })
        keep!!.setTag("profiles-keep")
        revert = style!!.button(confirmation, "Revert", { session!!.confirm(false) })
        revert!!.setTag("profiles-revert")
        addView(confirmation)
        if (selected == null && !creating) {
            profileList()
            refresh()
            return
        }
        name = EditText(getContext())
        style!!.search(name)
        name!!.setTag("profile-name")
        name!!.setHint("Profile name")
        name!!.setContentDescription("Profile name")
        name!!.setFilters(arrayOf<InputFilter?>(InputFilter.LengthFilter(48)))
        name!!.setImeOptions(
            (android.view.inputmethod.EditorInfo.IME_ACTION_DONE or
                android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI)
        )
        name!!.setText(
            if (draft != null) draft else if (selected == null) nextName() else selected!!.name
        )
        addView(name, LinearLayout.LayoutParams(-1, -2))
        if (monitor) {
            name!!.setShowSoftInputOnFocus(false)
            val editor = name
            val keyboard =
                MonitorSearchKeyboard(getContext(), editor, Runnable({ editor!!.clearFocus() }))
            keyboard.setTag("profile-keyboard")
            addView(keyboard, LinearLayout.LayoutParams(-1, -2))
            name!!.setOnClickListener({ v -> keyboard.showWithin(style!!.dp(240f)) })
            name!!.setOnFocusChangeListener({ v, focused ->
                if (focused) keyboard.showWithin(style!!.dp(240f))
            })
        }
        if (selected == null) {
            action(
                "Save current as profile",
                "profile-create",
                R.drawable.ic_save,
                true,
                {
                    val id = session!!.saveMonitorProfile(name!!.getText()!!.toString())
                    if (id != null) {
                        selectedId = id
                        creating = false
                        render(null)
                    } else name!!.setError("Check the name and finish any pending display change")
                },
            )
        } else {
            val id = selected!!.id
            text(selected!!.configuration!!.summary(), "profile-configuration")
            action(
                "Apply profile",
                "profile-apply",
                R.drawable.ic_rotation,
                true,
                { session!!.applyMonitorProfile(id) },
            )
            action(
                "Rename profile",
                "profile-rename",
                R.drawable.ic_settings,
                false,
                {
                    session!!.renameMonitorProfile(id, name!!.getText()!!.toString())
                    deleteArmed = null
                    render(null)
                },
            )
            action(
                "Update from current",
                "profile-update",
                R.drawable.ic_save,
                true,
                {
                    session!!.updateMonitorProfile(id)
                    deleteArmed = null
                    render(null)
                },
            )
            action(
                if (id == deleteArmed) "Confirm delete profile" else "Delete profile",
                "profile-delete",
                R.drawable.ic_close,
                false,
                {
                    if (id == deleteArmed) {
                        session!!.deleteMonitorProfile(id)
                        selectedId = null
                        creating = false
                        deleteArmed = null
                    } else deleteArmed = id
                    render(null)
                },
            )
        }
        action(
            "All profiles",
            "profile-list",
            R.drawable.ic_apps,
            false,
            {
                selectedId = null
                creating = false
                deleteArmed = null
                render(null)
            },
        )
        refresh()
    }

    private fun select(id: String?, create: Boolean) {
        selectedId = id
        creating = create
        deleteArmed = null
        render(null)
        post({
            var parent: android.view.ViewParent? = getParent()
            while (parent is View) {
                if (parent is android.widget.ScrollView) {
                    (parent as android.widget.ScrollView).fullScroll(View.FOCUS_UP)
                    break
                }
                parent = parent!!.getParent()
            }
        })
    }

    private fun profileList() {
        action("New profile", "profile-new", R.drawable.ic_save, false, { select(null, true) })
        val profiles = session!!.monitorProfiles().all()
        if (profiles.isEmpty())
            text(
                "No profiles yet. Configure the desktop, then save its current settings.",
                "profiles-empty",
            )
        val bound = session!!.monitorProfiles().bound(session!!.monitorIdentity())
        for (profile in profiles) {
            val row = style!!.settingRow(this)
            row.setTag("profile-row-" + profile!!.id!!)
            row.setSelected(bound != null && bound!!.id == profile!!.id)
            val label =
                style!!.label(
                    (profile!!.name +
                        "\n" +
                        profile!!.configuration!!.summary() +
                        (if (bound != null && bound!!.id == profile!!.id)
                            "\nRemembered for this monitor"
                        else "")),
                    14,
                )
            row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            val edit =
                style!!.iconAction(
                    R.drawable.ic_settings,
                    "Edit " + profile!!.name!!,
                    { select(profile!!.id, false) },
                )
            row.addView(edit, LinearLayout.LayoutParams(style!!.dp(48f), style!!.dp(48f)))
            row.setOnClickListener({ v -> select(profile!!.id, false) })
            row.setContentDescription(profile!!.name + ", " + profile!!.configuration!!.summary())
        }
        forget =
            action(
                "Forget this monitor",
                "profile-forget",
                R.drawable.ic_close,
                false,
                Runnable({ session!!.resetMonitor() }),
            )
    }

    fun refresh() {
        val current = MonitorProfilePreferences(session!!.prefs).read()
        if ((current != snapshot || snapshotIdentity != session!!.monitorIdentity())) {
            val editing = name != null && name!!.hasFocus()
            val draft = if (editing) name!!.getText()!!.toString() else null
            render(draft)
            if (editing && name != null) name!!.requestFocus()
            return
        }
        status!!.setText(
            getContext()!!.getString(
                R.string.monitor_profile_status,
                session!!.monitorProfileStatus(),
            )
        )
        message!!.setText(session!!.status!!.split(("\n").toRegex(), 2).toTypedArray()[0])
        val pending = session!!.awaitingConfirmation()
        keep!!.setVisibility(if (pending) View.VISIBLE else View.GONE)
        revert!!.setVisibility(if (pending) View.VISIBLE else View.GONE)
        keep!!.setEnabled(pending && !session!!.busy)
        revert!!.setEnabled(pending)
        val mutable = !session!!.busy && !pending
        for (button in editActions) button!!.setEnabled(mutable)
        for (button in runningActions) button!!.setEnabled(
            mutable && session!!.external != null && session!!.id >= 0
        )
        if (forget != null) forget!!.setEnabled(mutable && session!!.external != null)
        if (name != null) name!!.setEnabled(mutable)
        for (action in editActions) action!!.setAlpha(if (action!!.isEnabled()) 1f else .45f)
        for (action in runningActions) action!!.setAlpha(if (action!!.isEnabled()) 1f else .45f)
    }
}
