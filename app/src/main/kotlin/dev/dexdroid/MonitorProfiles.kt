package dev.dexdroid

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.ArrayList
import java.util.Base64
import java.util.Comparator
import java.util.HashMap
import java.util.Objects
import java.util.TreeSet
import java.util.UUID

/** Named display snapshots and monitor bindings, independent of Android display IDs. */
class MonitorProfiles(private val store: Store?) {
    interface Store {
        fun read(): Map<String?, String?>?

        fun write(values: Map<String?, String?>?)
    }

    class Configuration(
        @JvmField val width: Int,
        @JvmField val height: Int,
        @JvmField val dpi: Int,
        @JvmField val rotation: Int,
    ) {

        init {
            if (
                (width < 480 ||
                    width > 4096 ||
                    height < 480 ||
                    height > 4096 ||
                    dpi < 120 ||
                    dpi > 480 ||
                    rotation < 0 ||
                    rotation > 3)
            )
                throw IllegalArgumentException(
                    "Use dimensions 480-4096, DPI 120-480 and a valid orientation"
                )
        }

        fun summary(): String {
            return width.toString() +
                " x " +
                height +
                " / " +
                dpi +
                " DPI / " +
                rotation * 90 +
                " degrees"
        }

        public override fun equals(value: Any?): Boolean {
            if (!(value is Configuration)) return false
            val other = value as Configuration?
            return (width == other!!.width &&
                height == other!!.height &&
                dpi == other!!.dpi &&
                rotation == other!!.rotation)
        }

        public override fun hashCode(): Int {
            return Objects.hash(width, height, dpi, rotation)
        }
    }

    class Profile(
        @JvmField val id: String?,
        @JvmField val name: String?,
        @JvmField val configuration: Configuration?,
    )

    fun all(): List<Profile?> {
        return list(store!!.read()!!)
    }

    fun find(id: String?): Profile? {
        return find(store!!.read()!!, id)
    }

    fun bound(identity: String?): Profile? {
        if (identity == null) return null
        val values = store!!.read()
        return find(values!!, values!!.get("m." + identity))
    }

    fun remembered(identity: String?): Configuration? {
        val values = store!!.read()
        val working = values!!.get("w." + identity)
        if (working != null) {
            try {
                val fields = working!!.split(("\\|").toRegex()).toTypedArray()
                if (fields.size == 4)
                    return Configuration(
                        Integer.parseInt(fields[0]),
                        Integer.parseInt(fields[1]),
                        Integer.parseInt(fields[2]),
                        Integer.parseInt(fields[3]),
                    )
            } catch (ignored: IllegalArgumentException) {
                // A corrupt working override falls back to the valid named snapshot.
            }
        }
        val profile = find(values!!, values!!.get("m." + identity))
        return if (profile == null) null else profile!!.configuration
    }

    fun remember(identity: String?, configuration: Configuration?) {
        if (identity == null || identity!!.isEmpty())
            throw IllegalArgumentException("Connect a monitor first")
        val values = HashMap<String?, String?>(store!!.read()!!)
        values.put(
            "w." + identity,
            (configuration!!.width.toString() +
                "|" +
                configuration!!.height +
                "|" +
                configuration!!.dpi +
                "|" +
                configuration!!.rotation),
        )
        store!!.write(values)
    }

    fun clearWorking(identity: String?) {
        val values = HashMap<String?, String?>(store!!.read()!!)
        values.remove("w." + identity)
        store!!.write(values)
    }

    fun create(name: String?, configuration: Configuration?, identity: String?): Profile {
        val values = HashMap<String?, String?>(store!!.read()!!)
        if (list(values).size >= 100)
            throw IllegalArgumentException("Delete an unused profile before creating another")
        val profile =
            Profile(UUID.randomUUID().toString(), uniqueName(values, name, null), configuration)
        values.put("p." + profile.id!!, serialize(profile))
        if (identity != null) {
            values.put("m." + identity, profile.id)
            values.remove("w." + identity)
        }
        store!!.write(values)
        return profile
    }

    fun rename(id: String?, name: String?) {
        val values = HashMap<String?, String?>(store!!.read()!!)
        val profile = required(values, id)
        values.put(
            "p." + id,
            serialize(Profile(id, uniqueName(values, name, id), profile.configuration)),
        )
        store!!.write(values)
    }

    fun update(id: String?, configuration: Configuration?) {
        val values = HashMap<String?, String?>(store!!.read()!!)
        val profile = required(values, id)
        values.put("p." + id, serialize(Profile(id, profile.name, configuration)))
        store!!.write(values)
    }

    fun bind(identity: String?, id: String?) {
        if (identity == null || identity!!.isEmpty())
            throw IllegalArgumentException("Connect a monitor first")
        val values = HashMap<String?, String?>(store!!.read()!!)
        required(values, id)
        values.put("m." + identity, id)
        values.remove("w." + identity)
        store!!.write(values)
    }

    fun forget(identity: String?) {
        val values = HashMap<String?, String?>(store!!.read()!!)
        values.remove("m." + identity)
        values.remove("w." + identity)
        store!!.write(values)
    }

    fun delete(id: String?) {
        val values = HashMap<String?, String?>(store!!.read()!!)
        values.remove("p." + id)
        for (entry in HashMap<String?, String?>(values).entries) if (
            entry!!.key!!.startsWith("m.") && entry!!.value == id
        )
            values.remove("w." + entry!!.key!!.substring(2))
        values.entries.removeIf({ entry -> entry!!.key!!.startsWith("m.") && entry!!.value == id })
        store!!.write(values)
    }

    fun importLegacy(identity: String?, legacyKey: String?, configuration: Configuration?) {
        val values = HashMap<String?, String?>(store!!.read()!!)
        val marker = "legacy." + digest(legacyKey!!)
        if (values.containsKey(marker)) return
        values.put(marker, "1")
        if (configuration != null && find(values, values.get("m." + identity)) == null) {
            var name: String? = "Imported monitor"
            var suffix = 2
            while (true) {
                try {
                    uniqueName(values, name, null)
                    break
                } catch (e: IllegalArgumentException) {
                    name = "Imported monitor " + suffix
                }

                suffix++
            }
            val profile = Profile(UUID.randomUUID().toString(), name, configuration)
            values.put("p." + profile.id!!, serialize(profile))
            values.put("m." + identity, profile.id)
        }
        store!!.write(values)
    }

    companion object {

        @JvmStatic
        fun identity(stableId: String?, name: String?, modes: List<String?>?): String {
            // Small local:0/local:1 identifiers identify ports on older/vendor ROMs.
            if (stableId != null && stableId!!.matches(("local:[0-9]{4,}").toRegex()))
                return "physical." + digest(stableId!!)
            return "capabilities." +
                digest(name + "\n" + TreeSet<String?>(modes!!).joinToString("\n"))
        }

        private fun digest(value: String): String {
            try {
                val bytes =
                    MessageDigest.getInstance("SHA-256")!!.digest(
                        value.toByteArray(StandardCharsets.UTF_8)
                    )
                val result = StringBuilder()
                for (b in bytes!!) result.append(
                    String.format(java.util.Locale.ROOT, "%02x", b.toInt() and 255)
                )
                return result.toString()
            } catch (e: java.security.NoSuchAlgorithmException) {
                throw IllegalStateException(e)
            }
        }

        private fun encode(text: String): String? {
            return Base64.getEncoder()!!.encodeToString(text.toByteArray(StandardCharsets.UTF_8))
        }

        private fun checkedName(name: String?): String {
            val clean = if (name == null) "" else name!!.trim({ it <= ' ' })
            if (
                (clean.isEmpty() ||
                    clean
                        .codePoints()
                        .allMatch({ c -> Character.isWhitespace(c) || Character.isSpaceChar(c) }) ||
                    clean.length > 48 ||
                    clean.codePoints().anyMatch({ Character.isISOControl(it) }))
            )
                throw IllegalArgumentException(
                    "Use a profile name of 1-48 characters without control characters"
                )
            return clean
        }

        private fun decode(id: String?, text: String?): Profile? {
            if (id == null || text == null) return null
            try {
                UUID.fromString(id!!)
                val fields = text!!.split(("\\|").toRegex()).toTypedArray()
                if (fields.size != 6 || fields[0] != "1") return null
                val name =
                    checkedName(
                        StandardCharsets.UTF_8.newDecoder()!!.decode(
                                java.nio.ByteBuffer.wrap(Base64.getDecoder()!!.decode(fields[5])!!)
                            )
                            .toString()
                    )
                return Profile(
                    id,
                    name,
                    Configuration(
                        Integer.parseInt(fields[1]),
                        Integer.parseInt(fields[2]),
                        Integer.parseInt(fields[3]),
                        Integer.parseInt(fields[4]),
                    ),
                )
            } catch (e: IllegalArgumentException) {
                return null
            } catch (e: java.nio.charset.CharacterCodingException) {
                return null
            }
        }

        private fun serialize(profile: Profile): String {
            val c = profile.configuration
            return ("1|" +
                c!!.width +
                "|" +
                c!!.height +
                "|" +
                c!!.dpi +
                "|" +
                c!!.rotation +
                "|" +
                encode(profile.name!!))
        }

        private fun list(values: Map<String?, String?>): List<Profile?> {
            val result = ArrayList<Profile?>()
            for (entry in values.entries) {
                if (!entry!!.key!!.startsWith("p.")) continue
                val profile = decode(entry!!.key!!.substring(2), entry!!.value)
                if (profile != null) result.add(profile)
            }
            result.sortWith(
                Comparator.comparing<Profile?, String>(
                        { p: Profile? -> p!!.name!! },
                        String.CASE_INSENSITIVE_ORDER,
                    )!!
                    .thenComparing<String>({ p -> p!!.id!! })
            )
            return result
        }

        private fun find(values: Map<String?, String?>, id: String?): Profile? {
            val record = values.get("p." + id)
            return if (record == null) null else decode(id, record)
        }

        private fun uniqueName(
            values: Map<String?, String?>?,
            name: String?,
            except: String?,
        ): String {
            val clean = checkedName(name)
            for (profile in list(values!!)) if (
                profile!!.id != except && profile!!.name!!.equals(clean, ignoreCase = true)
            )
                throw IllegalArgumentException("A profile with this name already exists")
            return clean
        }

        private fun required(values: Map<String?, String?>?, id: String?): Profile {
            val profile = find(values!!, id)
            if (profile == null) throw IllegalArgumentException("Profile no longer exists")
            return profile
        }
    }
}
