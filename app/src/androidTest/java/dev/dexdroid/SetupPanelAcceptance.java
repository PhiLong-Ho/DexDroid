package dev.dexdroid;

import android.app.*;
import android.content.*;
import android.widget.*;

/** Real native panel with controlled dependency states; never uninstalls/stops Shizuku. */
final class SetupPanelAcceptance {
    static String run(Instrumentation runner, MainActivity phone) {
        StringBuilder result = new StringBuilder();
        runner.runOnMainSync(
                () -> {
                    ShizukuSetup.State[] state = {ShizukuSetup.State.MISSING};
                    ShizukuSetup.Destination[] opened = {null};
                    int[] retries = {0};
                    boolean[] reject = {false};
                    ShizukuSetup.Panel panel =
                            new ShizukuSetup.Panel(
                                    phone,
                                    () -> state[0],
                                    () -> retries[0]++,
                                    destination -> {
                                        if (reject[0])
                                            throw new ActivityNotFoundException(
                                                    "controlled missing handler");
                                        opened[0] = destination;
                                    },
                                    "fixture");
                    Dialog window = new Dialog(phone);
                    window.setContentView(panel.view);
                    window.show();
                    try {
                        for (ShizukuSetup.State value : ShizukuSetup.State.values()) {
                            state[0] = value;
                            panel.refresh();
                            TextView label = panel.view.findViewWithTag("fixture-shizuku-state");
                            Button action = panel.view.findViewWithTag("fixture-shizuku-action");
                            require(
                                    label.getText().toString().contains(value.label),
                                    "state visible " + value,
                                    result);
                            require(
                                    action.getText().toString().equals(value.action),
                                    "state action " + value,
                                    result);
                            opened[0] = null;
                            int before = retries[0];
                            action.performClick();
                            if (value == ShizukuSetup.State.PERMISSION
                                    || value == ShizukuSetup.State.READY)
                                require(
                                        retries[0] == before + 1 && opened[0] == null,
                                        "authorization/reconnect uses callback without launching external app "
                                                + value,
                                        result);
                            else
                                require(
                                        opened[0]
                                                == (value == ShizukuSetup.State.MISSING
                                                        ? ShizukuSetup.Destination.DOWNLOAD
                                                        : ShizukuSetup.Destination.MANAGER),
                                        "explicit destination " + value,
                                        result);
                            android.view.View manager =
                                    panel.view.findViewWithTag("fixture-shizuku-manager");
                            if (value == ShizukuSetup.State.PERMISSION
                                    || value == ShizukuSetup.State.READY) {
                                require(
                                        manager.isShown(),
                                        "manager remains available during authorization/reconnect "
                                                + value,
                                        result);
                                manager.performClick();
                                require(
                                        opened[0] == ShizukuSetup.Destination.MANAGER,
                                        "manager shortcut opens correct destination " + value,
                                        result);
                            } else
                                require(
                                        !manager.isShown(),
                                        "no duplicate manager action " + value,
                                        result);
                        }
                        for (ShizukuSetup.State value :
                                new ShizukuSetup.State[] {
                                    ShizukuSetup.State.MISSING, ShizukuSetup.State.STOPPED
                                }) {
                            state[0] = value;
                            reject[0] = true;
                            panel.refresh();
                            panel.view.findViewWithTag("fixture-shizuku-action").performClick();
                            TextView feedback =
                                    panel.view.findViewWithTag("fixture-shizuku-feedback");
                            require(
                                    feedback.getVisibility() == android.view.View.VISIBLE
                                            && feedback.getText().toString().contains("phone")
                                            && window.isShowing(),
                                    "unavailable destination preserves setup with actionable feedback "
                                            + value,
                                    result);
                        }
                        require(
                                ShizukuSetup.DOWNLOAD.equals("https://shizuku.rikka.app/download/"),
                                "official installation destination",
                                result);
                        require(
                                phone.getPackageManager()
                                                .getLaunchIntentForPackage(ShizukuSetup.PACKAGE)
                                        != null,
                                "real installed manager discoverable through package visibility",
                                result);
                    } finally {
                        window.dismiss();
                    }
                });
        return result.toString();
    }

    private static void require(boolean condition, String label, StringBuilder result) {
        if (!condition) throw new AssertionError(label);
        result.append("PASS Shizuku setup ").append(label).append('\n');
    }
}
