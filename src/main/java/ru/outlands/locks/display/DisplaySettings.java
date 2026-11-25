package ru.outlands.locks.display;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Lock display tuning. Offsets are in blocks, relative to the centre of the chest's front face:
 * right and up as seen by a viewer facing the chest, out away from the chest. Angles are in degrees.
 * Doors use the same record with their own meaning of the offsets, see {@link #DOOR_DEFAULT}.
 */
public record DisplaySettings(double right, double up, double out, double scale,
                              double yaw, double pitch, double roll) {

    public static final DisplaySettings DEFAULT = new DisplaySettings(0.0, 0.1, -0.05, 0.4, 0.0, 0.0, 0.0);

    /** Doors: right is toward the handle, up is from handle height, out is away from the surface of the door. */
    public static final DisplaySettings DOOR_DEFAULT = new DisplaySettings(0.0, 0.0, 0.02, 0.3, 0.0, 0.0, 0.0);

    /** Trapdoors: right is along the trapdoor, up is from the middle of an open one, out is away from the surface. */
    public static final DisplaySettings TRAPDOOR_DEFAULT = new DisplaySettings(0.0, 0.0, 0.02, 0.3, 0.0, 0.0, 0.0);

    /** Missing keys use defaults; a non-numeric value or a non-positive scale is an error. */
    public static DisplaySettings parse(ConfigurationSection section, String path) {
        return parse(section, path, DEFAULT);
    }

    /** Same, with the given values standing in for missing keys. */
    public static DisplaySettings parse(ConfigurationSection section, String path, DisplaySettings defaults) {
        if (section == null) {
            return defaults;
        }
        return new DisplaySettings(
                number(section, "offset.right", defaults.right, path),
                number(section, "offset.up", defaults.up, path),
                number(section, "offset.out", defaults.out, path),
                scale(section, path, defaults),
                number(section, "rotation.yaw", defaults.yaw, path),
                number(section, "rotation.pitch", defaults.pitch, path),
                number(section, "rotation.roll", defaults.roll, path));
    }

    private static double scale(ConfigurationSection section, String path, DisplaySettings defaults) {
        double scale = number(section, "scale", defaults.scale, path);
        if (scale <= 0) {
            throw new IllegalArgumentException(path + ".scale: must be greater than 0");
        }
        return scale;
    }

    private static double number(ConfigurationSection section, String key, double fallback, String path) {
        if (!section.contains(key)) {
            return fallback;
        }
        Object value = section.get(key);
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) {
            throw new IllegalArgumentException(path + "." + key + ": not a number");
        }
        return n.doubleValue();
    }
}
