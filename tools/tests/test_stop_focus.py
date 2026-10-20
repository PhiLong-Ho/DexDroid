import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_stop_focus


def phone_xml(label="Stop desktop", package="dev.dexdroid", layer=2):
    return (
        '<hierarchy display-id="0">'
        f'<window id="1" layer="{layer}" bounds="[0,0][1080,2400]">'
        f'<node package="{package}" bounds="[0,0][1080,2400]" '
        'enabled="true" visible-to-user="true" clickable="false">'
        f'<node package="{package}" content-desc="{label}" bounds="[900,100][1000,200]" '
        'enabled="true" visible-to-user="true" clickable="true"/>'
        "</node></window></hierarchy>"
    )


class StopFocusEvidenceTests(unittest.TestCase):
    def test_phone_action_uses_display_zero_package_and_native_bounds(self):
        action = check_stop_focus.phone_action(phone_xml(), "Stop desktop")
        self.assertEqual(0, action["display_id"])
        self.assertEqual("dev.dexdroid", action["package"])
        self.assertEqual("1", action["window_id"])
        self.assertEqual([950, 150], action["point"])

    def test_external_or_unidentified_hierarchy_cannot_supply_stop(self):
        for xml in (
            phone_xml().replace('display-id="0"', 'display-id="5"'),
            phone_xml().replace(' display-id="0"', ""),
            phone_xml(package="com.android.chrome"),
        ):
            with self.subTest(xml=xml), self.assertRaises(RuntimeError):
                check_stop_focus.phone_action(xml, "Stop desktop")

    def test_phone_action_accepts_visible_clickable_ancestor_for_text_label(self):
        xml = (
            phone_xml("Chrome")
            .replace('content-desc="Chrome"', 'text="Chrome&#10;Landscape"')
            .replace(
                'clickable="true"/>',
                'clickable="true"><node package="dev.dexdroid" text="Chrome" '
                'bounds="[910,120][990,180]" enabled="true" visible-to-user="true" '
                'clickable="false"/></node>',
            )
        )
        action = check_stop_focus.phone_action(xml, "Chrome")
        self.assertEqual([900, 100, 1000, 200], action["clickable_bounds"])

    def test_ambiguous_disabled_hidden_or_malformed_phone_action_refuses_tap(self):
        xml = phone_xml()
        window = xml[xml.index("<window") : xml.index("</hierarchy>")]
        cases = [
            xml.replace("</hierarchy>", window.replace('id="1"', 'id="2"') + "</hierarchy>"),
            xml.replace('enabled="true"', 'enabled="false"'),
            xml.replace('visible-to-user="true"', 'visible-to-user="false"'),
            xml.replace('clickable="true"', 'clickable="false"'),
            xml.replace("[900,100][1000,200]", "[900,100][1000,100]"),
            xml.replace("[900,100][1000,200]", "[900,100]"),
            xml.replace("[900,100][1000,200]", "[-100,100][0,200]"),
            xml.replace(' layer="2"', ""),
            xml.replace(' id="1"', ""),
        ]
        for invalid in cases:
            with self.subTest(xml=invalid), self.assertRaises(RuntimeError):
                check_stop_focus.phone_action(invalid, "Stop desktop")

    def test_higher_phone_window_blocks_action_but_nonoverlap_and_lower_do_not(self):
        for layer, bounds, blocked in (
            (3, "[800,0][1080,400]", True),
            (2, "[800,0][1080,400]", True),
            (1, "[800,0][1080,400]", False),
            (3, "[0,400][1080,2400]", False),
        ):
            overlay = f'<window id="2" layer="{layer}" bounds="{bounds}"/>'
            xml = phone_xml().replace("</hierarchy>", overlay + "</hierarchy>")
            with self.subTest(layer=layer, bounds=bounds):
                if blocked:
                    with self.assertRaises(RuntimeError):
                        check_stop_focus.phone_action(xml, "Stop desktop")
                else:
                    self.assertEqual(
                        [950, 150], check_stop_focus.phone_action(xml, "Stop desktop")["point"]
                    )

    def test_dump_completes_before_exactly_one_explicit_phone_tap(self):
        with tempfile.TemporaryDirectory() as folder:
            check = check_stop_focus.Check("adb", "fixture", Path(folder))
            check.apk_path = "/data/app/~~fixture/dev.dexdroid/base.apk"
            with mock.patch.object(check, "shell", side_effect=[phone_xml(), ""]) as shell:
                check.tap("Stop desktop", "stop")
            self.assertEqual(
                [
                    mock.call(
                        "env",
                        f"CLASSPATH={check.apk_path}",
                        "app_process",
                        "/system/bin",
                        "dev.dexdroid.ShellUiDump",
                    ),
                    mock.call("input", "-d", "0", "tap", "950", "150"),
                ],
                shell.call_args_list,
            )
            action = json.loads((Path(folder) / "stop-action.json").read_text(encoding="utf-8"))
            self.assertEqual([950, 150], action["point"])
            self.assertEqual(
                phone_xml(), (Path(folder) / "stop-controller.xml").read_text(encoding="utf-8")
            )

    def test_failed_snapshot_or_selection_never_injects_input(self):
        for result in (
            RuntimeError("snapshot cleanup failed"),
            phone_xml(package="com.android.chrome"),
            "",
        ):
            with self.subTest(result=result), tempfile.TemporaryDirectory() as folder:
                check = check_stop_focus.Check("adb", "fixture", Path(folder))
                check.apk_path = "/data/app/fixture/base.apk"
                effect = result if isinstance(result, Exception) else [result]
                with mock.patch.object(check, "shell", side_effect=effect) as shell:
                    with self.assertRaises((RuntimeError, check_stop_focus.ET.ParseError)):
                        check.tap("Stop desktop", "stop")
                self.assertEqual(1, shell.call_count)
                self.assertNotEqual("input", shell.call_args.args[0])

    def test_absent_action_waits_for_fresh_snapshot_and_keeps_every_attempt(self):
        absent = phone_xml("Status", "com.android.systemui")
        with tempfile.TemporaryDirectory() as folder:
            check = check_stop_focus.Check("adb", "fixture", Path(folder))
            check.apk_path = "/data/app/fixture/base.apk"
            with (
                mock.patch.object(check, "shell", side_effect=[absent, phone_xml(), ""]) as shell,
                mock.patch.object(check_stop_focus.time, "monotonic", return_value=0),
                mock.patch.object(check_stop_focus.time, "sleep"),
            ):
                check.tap("Stop desktop", "stop")
            self.assertEqual(
                ["env", "env", "input"], [call.args[0] for call in shell.call_args_list]
            )
            self.assertEqual(("input", "-d", "0", "tap", "950", "150"), shell.call_args.args)
            self.assertEqual(
                absent,
                (Path(folder) / "stop-controller-attempt-001.xml").read_text(encoding="utf-8"),
            )
            self.assertEqual(
                phone_xml(),
                (Path(folder) / "stop-controller-attempt-002.xml").read_text(encoding="utf-8"),
            )
            attempts = json.loads(
                (Path(folder) / "stop-readiness.json").read_text(encoding="utf-8")
            )
            self.assertEqual(["not-ready", "ready"], [attempt["result"] for attempt in attempts])

    def test_absent_action_timeout_keeps_snapshot_and_never_taps(self):
        absent = phone_xml("Status", "com.android.systemui")
        with tempfile.TemporaryDirectory() as folder:
            check = check_stop_focus.Check("adb", "fixture", Path(folder))
            check.apk_path = "/data/app/fixture/base.apk"
            with (
                mock.patch.object(check, "shell", return_value=absent) as shell,
                mock.patch.object(check_stop_focus.time, "monotonic", side_effect=[0, 10]),
                mock.patch.object(check_stop_focus.time, "sleep") as sleep,
            ):
                with self.assertRaisesRegex(RuntimeError, "Timed out"):
                    check.tap("Stop desktop", "stop")
            self.assertEqual(1, shell.call_count)
            sleep.assert_not_called()
            self.assertEqual(
                absent,
                (Path(folder) / "stop-controller-attempt-001.xml").read_text(encoding="utf-8"),
            )
            self.assertFalse((Path(folder) / "stop-action.json").exists())

    def test_safe_nonclickable_action_polls_fresh_snapshot_before_one_tap(self):
        pending = phone_xml("Chrome").replace('clickable="true"', 'clickable="false"')
        with tempfile.TemporaryDirectory() as folder:
            check = check_stop_focus.Check("adb", "fixture", Path(folder))
            check.apk_path = "/data/app/fixture/base.apk"
            with (
                mock.patch.object(
                    check, "shell", side_effect=[pending, phone_xml("Chrome"), ""]
                ) as shell,
                mock.patch.object(check_stop_focus.time, "monotonic", return_value=0),
                mock.patch.object(check_stop_focus.time, "sleep"),
            ):
                check.tap("Chrome", "chrome")
            self.assertEqual(
                ["env", "env", "input"], [call.args[0] for call in shell.call_args_list]
            )
            attempts = json.loads(
                (Path(folder) / "chrome-readiness.json").read_text(encoding="utf-8")
            )
            self.assertEqual(["not-ready", "ready"], [row["result"] for row in attempts])
            self.assertEqual(
                pending,
                (Path(folder) / "chrome-controller-attempt-001.xml").read_text(encoding="utf-8"),
            )

    def test_unsafe_nonclickable_label_is_never_polled_as_pending(self):
        pending = phone_xml("Chrome").replace('clickable="true"', 'clickable="false"')
        window = pending[pending.index("<window") : pending.index("</hierarchy>")]
        ready = phone_xml("Chrome")
        # Separate, nonoverlapping labels are ambiguous even if only one is clickable.
        mixed = ready.replace(
            "</node></window>",
            '<node package="dev.dexdroid" content-desc="Chrome" '
            'bounds="[100,300][200,400]" enabled="true" visible-to-user="true" '
            'clickable="false"/></node></window>',
        )
        cases = [
            mixed,
            pending.replace('enabled="true"', 'enabled="false"'),
            pending.replace('visible-to-user="true"', 'visible-to-user="false"'),
            pending.replace('package="dev.dexdroid"', 'package="other.app"'),
            pending.replace("[900,100][1000,200]", "[900,100]"),
            pending.replace('clickable="false"', 'clickable="unknown"'),
            pending.replace("</hierarchy>", window.replace('id="1"', 'id="2"') + "</hierarchy>"),
            pending.replace(
                "</hierarchy>", '<window id="2" layer="3" bounds="[0,0][1080,2400]"/></hierarchy>'
            ),
        ]
        for xml in cases:
            with self.subTest(xml=xml), tempfile.TemporaryDirectory() as folder:
                check = check_stop_focus.Check("adb", "fixture", Path(folder))
                check.apk_path = "/data/app/fixture/base.apk"
                with mock.patch.object(check, "shell", return_value=xml) as shell:
                    with self.assertRaises(RuntimeError) as failure:
                        check.tap("Chrome", "chrome")
                self.assertNotIsInstance(failure.exception, check_stop_focus.ActionNotReady)
                self.assertEqual(1, shell.call_count)
                self.assertEqual("env", shell.call_args.args[0])

    def test_action_snapshot_after_readiness_deadline_never_taps(self):
        with tempfile.TemporaryDirectory() as folder:
            check = check_stop_focus.Check("adb", "fixture", Path(folder))
            check.apk_path = "/data/app/fixture/base.apk"
            with (
                mock.patch.object(check, "shell", return_value=phone_xml()) as shell,
                mock.patch.object(check_stop_focus.time, "monotonic", side_effect=[0, 10]),
            ):
                with self.assertRaisesRegex(RuntimeError, "Timed out"):
                    check.tap("Stop desktop", "stop")
            self.assertEqual(1, shell.call_count)
            attempts = json.loads(
                (Path(folder) / "stop-readiness.json").read_text(encoding="utf-8")
            )
            self.assertEqual("late-ready", attempts[-1]["result"])

    def test_unsafe_action_is_not_polled_away_after_readiness_absence(self):
        absent = phone_xml("Status", "com.android.systemui")
        base = phone_xml()
        window = base[base.index("<window") : base.index("</hierarchy>")]
        unsafe = [
            base.replace('enabled="true"', 'enabled="false"'),
            base.replace('visible-to-user="true"', 'visible-to-user="false"'),
            phone_xml(package="com.android.chrome"),
            base.replace("</hierarchy>", window.replace('id="1"', 'id="2"') + "</hierarchy>"),
            base.replace(
                "</hierarchy>", '<window id="2" layer="3" bounds="[0,0][1080,2400]"/></hierarchy>'
            ),
        ]
        for xml in unsafe:
            with self.subTest(xml=xml), tempfile.TemporaryDirectory() as folder:
                check = check_stop_focus.Check("adb", "fixture", Path(folder))
                check.apk_path = "/data/app/fixture/base.apk"
                with (
                    mock.patch.object(
                        check, "shell", side_effect=[absent, xml, phone_xml()]
                    ) as shell,
                    mock.patch.object(check_stop_focus.time, "monotonic", return_value=0),
                    mock.patch.object(check_stop_focus.time, "sleep"),
                ):
                    with self.assertRaises(RuntimeError):
                        check.tap("Stop desktop", "stop")
                self.assertEqual(["env", "env"], [call.args[0] for call in shell.call_args_list])

    def test_native_input_failure_never_retries_dump_or_tap(self):
        with tempfile.TemporaryDirectory() as folder:
            check = check_stop_focus.Check("adb", "fixture", Path(folder))
            check.apk_path = "/data/app/fixture/base.apk"
            with mock.patch.object(
                check, "shell", side_effect=[phone_xml(), RuntimeError("input failed")]
            ) as shell:
                with self.assertRaisesRegex(RuntimeError, "input failed"):
                    check.tap("Stop desktop", "stop")
            self.assertEqual(["env", "input"], [call.args[0] for call in shell.call_args_list])

    def test_stop_observation_issues_no_controller_launch_input_or_dump_helper(self):
        with tempfile.TemporaryDirectory() as folder:
            check = check_stop_focus.Check("adb", "fixture", Path(folder))
            stopped = False
            after_stop = []

            def shell(*args):
                if stopped:
                    after_stop.append(args)
                if args == ("dumpsys", "display"):
                    return "" if stopped else 'DisplayInfo{"DexDroid Desktop 1", displayId 101}'
                if args == ("am", "stack", "list"):
                    return "Stack id=3 displayId=101\n taskId=3: com.android.chrome/Main"
                if args == ("dumpsys", "window", "displays"):
                    return "  Display: mDisplayId=0\n mCurrentFocus=Window{x dev.dexdroid/.MainActivity}\n"
                return ""

            def tap(label, prefix):
                nonlocal stopped
                if label == "Stop desktop":
                    stopped = True

            with (
                mock.patch.object(check, "shell", side_effect=shell),
                mock.patch.object(check, "tap", side_effect=tap) as taps,
                mock.patch.object(check, "screenshot"),
                mock.patch.object(check_stop_focus.time, "monotonic", side_effect=range(0, 200, 2)),
                mock.patch.object(check_stop_focus.time, "sleep"),
            ):
                result = check.cycle(1, 15)
            self.assertEqual("passed", result["result"])
            self.assertEqual(
                ["Apps", "Chrome", "Stop desktop"], [call.args[0] for call in taps.call_args_list]
            )
            self.assertTrue(after_stop)
            self.assertTrue(
                all(args[0] == "dumpsys" or args == ("am", "stack", "list") for args in after_stop)
            )

    def test_phone_capture_uses_primary_physical_id_not_external_or_logical_zero(self):
        metadata = (
            'Display 4632669017991922437 (HWC display 1): displayName="FHD"\n'
            'Display 4630946315032985731 (HWC display 0): displayName=""\n'
            'Display 11529215046945448540 (Virtual display): displayName="DexDroid"\n'
        )
        with tempfile.TemporaryDirectory() as folder:
            check = check_stop_focus.Check("adb", "fixture", Path(folder))
            raw = b"\x89PNG\r\n\x1a\nfixture-bytes"
            with (
                mock.patch.object(check, "shell", return_value=metadata),
                mock.patch.object(
                    check_stop_focus.subprocess,
                    "run",
                    return_value=subprocess.CompletedProcess([], 0, raw, b""),
                ) as capture,
            ):
                check.screenshot("phone.png")
            self.assertEqual("4630946315032985731", capture.call_args.args[0][-1])
            self.assertEqual(["-p", "-d"], capture.call_args.args[0][-3:-1])
            self.assertEqual(raw, (Path(folder) / "phone.png").read_bytes())
            self.assertEqual(
                metadata, (Path(folder) / "phone.png.displays.txt").read_text(encoding="utf-8")
            )

    def test_missing_or_ambiguous_primary_physical_display_refuses_capture(self):
        for dump in (
            "Display 99 (HWC display 1): external\n",
            "Display 1 (HWC display 0): primary\nDisplay 2 (HWC display 0): ambiguous\n",
        ):
            with self.subTest(dump=dump), self.assertRaises(RuntimeError):
                check_stop_focus.phone_physical_display(dump)

    def test_external_controller_does_not_mask_chrome_phone_focus(self):
        dump = """  Display: mDisplayId=5 stacks=1
  mCurrentFocus=Window{x u0 dev.dexdroid/dev.dexdroid.MainActivity}
  Display: mDisplayId=0 stacks=2
  mCurrentFocus=Window{y u0 com.android.chrome/com.google.android.apps.chrome.Main}
"""
        self.assertFalse(check_stop_focus.controller_focused(check_stop_focus.phone_focus(dump)))

    def test_phone_controller_accepts_full_and_short_component_names(self):
        for component in ["dev.dexdroid/.MainActivity", "dev.dexdroid/dev.dexdroid.MainActivity"]:
            dump = f"  Display: mDisplayId=0 stacks=2\n  mCurrentFocus=Window{{x u0 {component}}}\n"
            self.assertTrue(check_stop_focus.controller_focused(check_stop_focus.phone_focus(dump)))

    def test_missing_phone_focus_is_not_a_pass(self):
        with self.assertRaises(RuntimeError):
            check_stop_focus.phone_focus("  Display: mDisplayId=5 stacks=1\n  mCurrentFocus=null\n")
        self.assertFalse(check_stop_focus.controller_focused("null"))

    def test_only_owned_virtual_displays_count_as_desktops(self):
        dump = """DisplayInfo{"HDMI Screen", displayId 5, real 1920 x 1200}
DisplayInfo{"DexDroid Desktop 123", displayId 101, real 1920 x 1200}
DisplayInfo{"DexDroid Desktop 123", displayId 101, real 1920 x 1200}
DisplayInfo{"DexDroid Desktop 456", displayId 102, real 900 x 1200}
"""
        self.assertEqual({101, 102}, check_stop_focus.desktop_ids(dump))

    def test_chrome_phone_task_is_not_external_launch_evidence(self):
        stacks = """Stack id=3 displayId=101
  taskId=3: dev.dexdroid/dev.dexdroid.InputTestActivity
Stack id=8 displayId=0
  taskId=8: com.android.chrome/com.google.android.apps.chrome.Main
"""
        self.assertFalse(check_stop_focus.chrome_on_desktop(stacks, {101}))
        self.assertTrue(
            check_stop_focus.chrome_on_desktop(
                stacks.replace("displayId=0", "displayId=101"), {101}
            )
        )
