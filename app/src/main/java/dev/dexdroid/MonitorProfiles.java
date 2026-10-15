package dev.dexdroid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

/** Named display snapshots and monitor bindings, independent of Android display IDs. */
final class MonitorProfiles {
    interface Store {
        Map<String, String> read();

        void write(Map<String, String> values);
    }

    static final class Configuration {
        final int width, height, dpi, rotation;

        Configuration(int width, int height, int dpi, int rotation) {
            if (width < 480
                    || width > 4096
                    || height < 480
                    || height > 4096
                    || dpi < 120
                    || dpi > 480
                    || rotation < 0
                    || rotation > 3)
                throw new IllegalArgumentException(
                        "Use dimensions 480-4096, DPI 120-480 and a valid orientation");
            this.width = width;
            this.height = height;
            this.dpi = dpi;
            this.rotation = rotation;
        }

        String summary() {
            return width + " x " + height + " / " + dpi + " DPI / " + rotation * 90 + " degrees";
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof Configuration)) return false;
            Configuration other = (Configuration) value;
            return width == other.width
                    && height == other.height
                    && dpi == other.dpi
                    && rotation == other.rotation;
        }

        @Override
        public int hashCode() {
            return Objects.hash(width, height, dpi, rotation);
        }
    }

    static final class Profile {
        final String id, name;
        final Configuration configuration;

        Profile(String id, String name, Configuration configuration) {
            this.id = id;
            this.name = name;
            this.configuration = configuration;
        }
    }

    private final Store store;

    MonitorProfiles(Store store) {
        this.store = store;
    }

    static String identity(String stableId, String name, List<String> modes) {
        // Small local:0/local:1 identifiers identify ports on older/vendor ROMs.
        if (stableId != null && stableId.matches("local:[0-9]{4,}"))
            return "physical." + digest(stableId);
        return "capabilities." + digest(name + "\n" + String.join("\n", new TreeSet<>(modes)));
    }

    private static String digest(String value) {
        try {
            byte[] bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes)
                result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String checkedName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()
                || clean.codePoints()
                        .allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
                || clean.length() > 48
                || clean.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(
                    "Use a profile name of 1-48 characters without control characters");
        return clean;
    }

    private static Profile decode(String id, String text) {
        if (id == null || text == null) return null;
        try {
            UUID.fromString(id);
            String[] fields = text.split("\\|", -1);
            if (fields.length != 6 || !fields[0].equals("1")) return null;
            String name =
                    checkedName(
                            StandardCharsets.UTF_8
                                    .newDecoder()
                                    .decode(
                                            java.nio.ByteBuffer.wrap(
                                                    Base64.getDecoder().decode(fields[5])))
                                    .toString());
            return new Profile(
                    id,
                    name,
                    new Configuration(
                            Integer.parseInt(fields[1]),
                            Integer.parseInt(fields[2]),
                            Integer.parseInt(fields[3]),
                            Integer.parseInt(fields[4])));
        } catch (IllegalArgumentException | java.nio.charset.CharacterCodingException e) {
            return null;
        }
    }

    private static String serialize(Profile profile) {
        Configuration c = profile.configuration;
        return "1|"
                + c.width
                + "|"
                + c.height
                + "|"
                + c.dpi
                + "|"
                + c.rotation
                + "|"
                + encode(profile.name);
    }

    private static List<Profile> list(Map<String, String> values) {
        List<Profile> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!entry.getKey().startsWith("p.")) continue;
            Profile profile = decode(entry.getKey().substring(2), entry.getValue());
            if (profile != null) result.add(profile);
        }
        result.sort(
                Comparator.comparing((Profile p) -> p.name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(p -> p.id));
        return result;
    }

    List<Profile> all() {
        return list(store.read());
    }

    Profile find(String id) {
        return find(store.read(), id);
    }

    private static Profile find(Map<String, String> values, String id) {
        String record = values.get("p." + id);
        return record == null ? null : decode(id, record);
    }

    Profile bound(String identity) {
        if (identity == null) return null;
        Map<String, String> values = store.read();
        return find(values, values.get("m." + identity));
    }

    Configuration remembered(String identity) {
        Map<String, String> values = store.read();
        String working = values.get("w." + identity);
        if (working != null) {
            try {
                String[] fields = working.split("\\|", -1);
                if (fields.length == 4)
                    return new Configuration(
                            Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                            Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
            } catch (IllegalArgumentException ignored) {
                // A corrupt working override falls back to the valid named snapshot.
            }
        }
        Profile profile = find(values, values.get("m." + identity));
        return profile == null ? null : profile.configuration;
    }

    void remember(String identity, Configuration configuration) {
        if (identity == null || identity.isEmpty())
            throw new IllegalArgumentException("Connect a monitor first");
        Map<String, String> values = new HashMap<>(store.read());
        values.put(
                "w." + identity,
                configuration.width
                        + "|"
                        + configuration.height
                        + "|"
                        + configuration.dpi
                        + "|"
                        + configuration.rotation);
        store.write(values);
    }

    void clearWorking(String identity) {
        Map<String, String> values = new HashMap<>(store.read());
        values.remove("w." + identity);
        store.write(values);
    }

    private static String uniqueName(Map<String, String> values, String name, String except) {
        String clean = checkedName(name);
        for (Profile profile : list(values))
            if (!profile.id.equals(except) && profile.name.equalsIgnoreCase(clean))
                throw new IllegalArgumentException("A profile with this name already exists");
        return clean;
    }

    Profile create(String name, Configuration configuration, String identity) {
        Map<String, String> values = new HashMap<>(store.read());
        if (list(values).size() >= 100)
            throw new IllegalArgumentException("Delete an unused profile before creating another");
        Profile profile =
                new Profile(
                        UUID.randomUUID().toString(),
                        uniqueName(values, name, null),
                        configuration);
        values.put("p." + profile.id, serialize(profile));
        if (identity != null) {
            values.put("m." + identity, profile.id);
            values.remove("w." + identity);
        }
        store.write(values);
        return profile;
    }

    void rename(String id, String name) {
        Map<String, String> values = new HashMap<>(store.read());
        Profile profile = required(values, id);
        values.put(
                "p." + id,
                serialize(new Profile(id, uniqueName(values, name, id), profile.configuration)));
        store.write(values);
    }

    void update(String id, Configuration configuration) {
        Map<String, String> values = new HashMap<>(store.read());
        Profile profile = required(values, id);
        values.put("p." + id, serialize(new Profile(id, profile.name, configuration)));
        store.write(values);
    }

    private static Profile required(Map<String, String> values, String id) {
        Profile profile = find(values, id);
        if (profile == null) throw new IllegalArgumentException("Profile no longer exists");
        return profile;
    }

    void bind(String identity, String id) {
        if (identity == null || identity.isEmpty())
            throw new IllegalArgumentException("Connect a monitor first");
        Map<String, String> values = new HashMap<>(store.read());
        required(values, id);
        values.put("m." + identity, id);
        values.remove("w." + identity);
        store.write(values);
    }

    void forget(String identity) {
        Map<String, String> values = new HashMap<>(store.read());
        values.remove("m." + identity);
        values.remove("w." + identity);
        store.write(values);
    }

    void delete(String id) {
        Map<String, String> values = new HashMap<>(store.read());
        values.remove("p." + id);
        for (Map.Entry<String, String> entry : new HashMap<>(values).entrySet())
            if (entry.getKey().startsWith("m.") && entry.getValue().equals(id))
                values.remove("w." + entry.getKey().substring(2));
        values.entrySet()
                .removeIf(entry -> entry.getKey().startsWith("m.") && entry.getValue().equals(id));
        store.write(values);
    }

    void importLegacy(String identity, String legacyKey, Configuration configuration) {
        Map<String, String> values = new HashMap<>(store.read());
        String marker = "legacy." + digest(legacyKey);
        if (values.containsKey(marker)) return;
        values.put(marker, "1");
        if (configuration != null && find(values, values.get("m." + identity)) == null) {
            String name = "Imported monitor";
            for (int suffix = 2; ; suffix++) {
                try {
                    uniqueName(values, name, null);
                    break;
                } catch (IllegalArgumentException e) {
                    name = "Imported monitor " + suffix;
                }
            }
            Profile profile = new Profile(UUID.randomUUID().toString(), name, configuration);
            values.put("p." + profile.id, serialize(profile));
            values.put("m." + identity, profile.id);
        }
        store.write(values);
    }
}
