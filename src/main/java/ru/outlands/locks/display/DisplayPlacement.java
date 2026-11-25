package ru.outlands.locks.display;

import org.bukkit.util.Vector;

/** Where and how a lock display is put: world position, entity yaw and pitch, and the extra transformation. */
public record DisplayPlacement(Vector position, float yaw, float pitch, float scale,
                               float extraYaw, float extraPitch, float extraRoll) {
}
