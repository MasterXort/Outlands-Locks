package ru.outlands.locks.lang;

/** Sound by namespaced string key: vanilla, Nexo or resource pack. */
public record SoundSpec(String key, float volume, float pitch) {
}
