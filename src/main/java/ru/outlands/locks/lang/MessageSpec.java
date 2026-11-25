package ru.outlands.locks.lang;

/** One message from a language file. {@code sound} is null when the message has no sound. */
public record MessageSpec(Type type, String text, SoundSpec sound) {

    public enum Type { CHAT, ACTIONBAR, NONE }
}
