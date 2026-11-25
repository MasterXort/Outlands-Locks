package ru.outlands.locks.lock;

import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.UUID;

/** What a lock stores: its id and whether the first key has been cut. */
public record LockData(UUID id, boolean keyCut) {

    /** Empty when there is no lock_id or it is not a UUID. A missing key_cut counts as false. */
    public static Optional<LockData> read(PersistentDataContainer pdc, LockKeys keys) {
        String raw = pdc.get(keys.lockId(), PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        UUID id;
        try {
            id = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        Boolean cut = pdc.get(keys.keyCut(), PersistentDataType.BOOLEAN);
        return Optional.of(new LockData(id, cut != null && cut));
    }

    public void writeTo(PersistentDataContainer pdc, LockKeys keys) {
        pdc.set(keys.lockId(), PersistentDataType.STRING, id.toString());
        pdc.set(keys.keyCut(), PersistentDataType.BOOLEAN, keyCut);
    }

    public static void clear(PersistentDataContainer pdc, LockKeys keys) {
        pdc.remove(keys.lockId());
        pdc.remove(keys.keyCut());
    }
}
