# Troubleshooting

## Chrome incognito shows a blank monitor

Chrome can mark incognito windows with Android's FLAG_SECURE. Android blanks
secure windows on non-secure virtual displays, including DexDroid's current
TextureView transport. The Samsung Android 16 report was checked: Chrome's
window had that flag, and the authorized shell did not have
CAPTURE_SECURE_VIDEO_OUTPUT. This is a display-policy limit, not a failed page load.

A banking/brokerage app may protect particular windows, particular flows, or none
of the screens being displayed. An app working here does not prove that all its
screens or all protected apps can display. DexDroid has not inspected those apps'
private screens and does not change their protection settings.

Samsung's browser team states that Samsung Internet Secret mode works in direct
monitor/tablet DeX, but not in DeX for PC, whose mirroring is subject to capture
protection. This is evidence about Samsung Internet, not a guarantee for every
Chrome/Samsung version. Direct system-integrated DeX and DexDroid's non-root
virtual transport are different display paths.

### In-app guidance

DexDroid checks visible-window protection metadata through the authorized Shizuku
shell while an app occupies its desktop. A detected protected window shows a small
**Protected screen** notice with Info/Close icons on the monitor, and Help in the
phone status panel. Tap Info for the same offline instructions on either screen.
Closing the notice hides it for that episode. Returning to an ordinary window
clears it; a later protected window can show it again.

This is a periodic best-effort metadata check, not incognito detection or black-pixel
analysis. It ignores protected windows on the phone and other displays. OEM changes
or unavailable metadata can prevent detection; open **Settings > Protected screen
help** manually in that case. No page content, URLs or window titles are retained.
The check skips empty desktops and stops with the desktop session. Detection has a
two-second interval plus query/dispatch time, so the notice is not instantaneous.

### Optional Chrome setting: user-controlled

If you choose to allow Chrome incognito screenshots and thumbnails:

1. Open Chrome **on the phone**, using a normal tab.
2. Enter `chrome://flags/#incognito-screenshot` in the address bar.
3. Find **Incognito Screenshot**, choose **Enabled**, then use Chrome's
   **Relaunch** button. Chrome's flag may be absent or controlled by policy on
   some versions/devices; if absent, there is no verified equivalent here.
4. Reopen Chrome through DexDroid, open a new incognito tab and check the monitor.
   This is a workaround to test, not guaranteed on every Chrome/Android version.
5. To undo it, return to the same flag, select **Default**, and relaunch Chrome.

This opt-in allows incognito screenshots and may expose incognito thumbnails.
It does not merely grant an exception to DexDroid. DexDroid never changes this
flag automatically; the reported phone's setting was left unchanged. Do not
close incognito tabs or clear browser data as a troubleshooting step.

Sources: [Android secure virtual-display rules](https://developer.android.com/reference/android/hardware/display/DisplayManager#VIRTUAL_DISPLAY_FLAG_SECURE),
[Chromium flag name/description](https://chromium.googlesource.com/chromium/src/+/main/chrome/browser/flag_descriptions.h),
[Chromium implementation note](https://chromium.googlesource.com/chromium/src/+/746dd029a71161894b527b2f409d108728eaccb2),
[Samsung browser team's DeX explanation](https://r1.community.samsung.com/t5/%EC%84%9C%EB%B9%84%EC%8A%A4-%EA%B8%B0%ED%83%80/%EB%8D%B1%EC%8A%A4%EC%97%90%EC%84%9C-%EC%82%BC%EC%84%B1%EC%9D%B8%ED%84%B0%EB%84%B7-%EB%B9%84%EB%B0%80%EB%AA%A8%EB%93%9C-%EC%82%AC%EC%9A%A9-%EB%AA%BB%ED%95%98%EB%82%98%EC%9A%94/td-p/23304222).

## Apps takes time to populate

The menu opens with a small monochrome progress indicator and **Loading apps**
while discovery runs in the background. Both screens share the cached list.
Subsequent opens use it immediately; package changes show **Updating apps** while
refreshing. Search stays usable during loading. Close, Back and outside dismissal
remain available. If discovery fails, tap **Couldn't load apps. Tap to retry.**

## Android 17 experimental support

Android 17 is experimental in 0.12.5. On the tested API 37 emulator image
CE2A.260420.019, Android's display-compatibility recovery can try to restart an
app on a display that has already been removed, crashing system_server during
teardown. This is a known platform limitation; a software-renderer child-app test
passed but does not establish reliable operation on every Android 17 device.

Android 11 hardware and Android 16 emulator checks passed separately. If the
Android 17 system restarts, unlock the device and restart Shizuku before reopening
DexDroid. Changing display permissions or retaining obsolete displays is not a
verified remedy. See the [compatibility diagnosis](changes/android16-input-readiness.md#completed-stable-candidate-device-verification-2026-10-18)
for exact test scope and retained failure details.
