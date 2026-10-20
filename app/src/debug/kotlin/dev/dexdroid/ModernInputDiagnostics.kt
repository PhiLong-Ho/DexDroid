package dev.dexdroid

import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** One explicitly armed synthetic-fixture sample. Never included in release artifacts. */
class ModernInputDiagnostics
private constructor(private val directory: String, private val header: JSONObject) :
    ModernInputWindow.Diagnostic {
    private val selection = Selection(Profile.valueOf(header.getString("profile")))
    private var sample: ModernInputWindow.Sample? = null
    private var originalLength = 0
    private var reportHash: String? = null
    private val events = ArrayList<String>()

    override fun sample(value: ModernInputWindow.Sample) {
        if (
            !selection.take(
                value.before,
                SystemClock.elapsedRealtime(),
                header.getLong("expires_elapsed_ms"),
            )
        )
            return
        originalLength = value.report?.length ?: 0
        reportHash = value.report?.let { digest(it.toByteArray(Charsets.UTF_8)) }
        sample = value.copy(report = value.report?.take(MAX_REPORT))
    }

    override fun event(value: String) {
        if (sample != null && events.size < 16) events.add(value.take(512))
    }

    override fun flush() {
        val captured = sample ?: return
        // Clear before I/O: failures cannot retry or leak the sample into a later contact.
        sample = null
        val savedEvents = events.toList()
        events.clear()
        val begin = System.nanoTime()
        val output =
            JSONObject(header.toString())
                .put("flush_begin_nanos", begin)
                .put("sample_start_nanos", captured.startNanos)
                .put("sample_end_nanos", captured.endNanos)
                .put("display", captured.display)
                .put("before", target(captured.before))
                .put("windows_queried", captured.windowsQueried)
                .put("after_queried", captured.afterQueried)
                .put("after", target(captured.after))
                .put("stage", captured.stage)
                .put("error_class", captured.errorClass ?: JSONObject.NULL)
                .put("state", captured.state?.name ?: JSONObject.NULL)
                .put("rejected_down", captured.rejectedDown ?: JSONObject.NULL)
                .put("report", captured.report ?: JSONObject.NULL)
                .put("report_sha256", reportHash ?: JSONObject.NULL)
                .put("report_characters", originalLength)
                .put("report_truncated", originalLength > MAX_REPORT)
                .put("events", JSONArray(savedEvents))
        writeExclusive("sample.json", output.toString(2))
        writeExclusive(
            "flush.json",
            JSONObject()
                .put("nonce", header.getString("nonce"))
                .put("pid", Process.myPid())
                .put("flush_begin_nanos", begin)
                .put("flush_end_nanos", System.nanoTime())
                .toString(),
        )
    }

    private fun writeExclusive(name: String, text: String) {
        val fd =
            Os.open(
                "$directory/$name",
                OsConstants.O_WRONLY or
                    OsConstants.O_CREAT or
                    OsConstants.O_EXCL or
                    OsConstants.O_NOFOLLOW,
                384,
            )
        FileOutputStream(fd).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    internal enum class Profile {
        EMULATOR_PRIMARY,
        XIAOMI_SECONDARY_CHILD,
    }

    internal class Selection(private val profile: Profile) {
        private var consumed = false

        fun take(before: ModernInputWindow.Target?, now: Long, expiry: Long): Boolean {
            if (consumed) return false
            if (
                profile == Profile.XIAOMI_SECONDARY_CHILD &&
                    before != null &&
                    (before.uid != FIXTURE_UID || before.component != NAV_CHILD)
            )
                return false
            consumed = true
            if (now >= expiry) return false
            return profile != Profile.EMULATOR_PRIMARY ||
                before == null ||
                before.component == "dev.dexdroid/dev.dexdroid.InputTestActivity"
        }
    }

    companion object {
        private const val REQUEST = "/data/local/tmp/dexdroid-input-diagnostic.request"
        private const val MAX_REPORT = 262144
        private const val FINGERPRINT =
            "google/sdk_gphone64_x86_64/emu64xa:15/AE3A.240806.043/12960925:userdebug/dev-keys"

        private const val XIAOMI_FINGERPRINT =
            "Xiaomi/houji_global/houji:15/AQ3A.240627.003/OS2.0.206.0.VNCMIXM:user/release-keys"
        private const val FIXTURE_UID = 10362
        private const val NAV_CHILD =
            "dev.dexdroid.test/dev.dexdroid.fixture.NavigationFixtureActivity\$Child"

        internal fun profile(
            fingerprint: String,
            role: String,
            api: Int,
            uid: Int,
            display: Int,
        ): Profile? {
            if (uid != 2000 || api != 35 || display <= 0) return null
            return when {
                fingerprint == FINGERPRINT && role == "dev.dexdroid.DeskService" ->
                    Profile.EMULATOR_PRIMARY
                fingerprint == XIAOMI_FINGERPRINT && role == "dev.dexdroid.SecondaryDeskService" ->
                    Profile.XIAOMI_SECONDARY_CHILD
                else -> null
            }
        }

        private fun digest(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }

        private fun target(value: ModernInputWindow.Target?): Any =
            if (value == null) JSONObject.NULL
            else
                JSONObject()
                    .put("task", value.task)
                    .put("uid", value.uid)
                    .put("component", value.component ?: JSONObject.NULL)

        @JvmStatic
        fun attach(role: String, display: Int): ModernInputWindow.Diagnostic? {
            val profile =
                profile(Build.FINGERPRINT, role, Build.VERSION.SDK_INT, Process.myUid(), display)
                    ?: return null
            try {
                val fd = Os.open(REQUEST, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
                val bytes =
                    FileInputStream(fd).use { input ->
                        val stat = Os.fstat(fd)
                        require(
                            OsConstants.S_ISREG(stat.st_mode) &&
                                stat.st_uid == 2000 &&
                                stat.st_mode and 511 == 384 &&
                                stat.st_size in 1..4096
                        )
                        val buffer = ByteArray(4097)
                        var count = 0
                        while (count < buffer.size) {
                            val size = input.read(buffer, count, buffer.size - count)
                            if (size < 0) break
                            count += size
                        }
                        require(count in 1..4096 && count.toLong() == stat.st_size)
                        buffer.copyOf(count)
                    }
                val request = JSONObject(String(bytes, Charsets.UTF_8))
                val nonce = request.getString("nonce")
                require(nonce.matches(Regex("[0-9a-f]{32}")))
                require(request.getString("role") == role)
                require(request.getString("fingerprint") == Build.FINGERPRINT)
                if (profile == Profile.XIAOMI_SECONDARY_CHILD)
                    require(request.getInt("fixture_uid") == FIXTURE_UID)
                val boot = java.io.File("/proc/sys/kernel/random/boot_id").readText().trim()
                require(request.getString("boot_id") == boot)
                val now = SystemClock.elapsedRealtime()
                val issued = request.getLong("issued_elapsed_ms")
                val expiry = request.getLong("expires_elapsed_ms")
                require(issued <= now && expiry > now && expiry - issued in 1..1200000)
                val directory = "/data/local/tmp/dexdroid-input-$nonce"
                val stat = Os.lstat(directory)
                require(
                    OsConstants.S_ISDIR(stat.st_mode) &&
                        stat.st_uid == 2000 &&
                        stat.st_mode and 511 == 448
                )
                val header =
                    JSONObject()
                        .put("nonce", nonce)
                        .put("boot_id", boot)
                        .put("fingerprint", Build.FINGERPRINT)
                        .put("profile", profile.name)
                        .put(
                            "fixture_uid",
                            if (profile == Profile.XIAOMI_SECONDARY_CHILD) FIXTURE_UID
                            else JSONObject.NULL,
                        )
                        .put("role", role)
                        .put("pid", Process.myPid())
                        .put("display", display)
                        .put("request_sha256", digest(bytes))
                        .put("armed_elapsed_ms", now)
                        .put("expires_elapsed_ms", expiry)
                val sink = ModernInputDiagnostics(directory, header)
                // Exclusive claim binds the sole attempt before any input decision occurs.
                sink.writeExclusive("owner.json", header.toString())
                return sink
            } catch (_: Exception) {
                return null
            }
        }
    }
}
