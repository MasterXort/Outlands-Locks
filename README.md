# Outlands-Locks

Locks and keys for chests, wooden doors and wooden trapdoors on Paper and Purpur. A player hangs a lock on a chest, cuts one key for it, and from then on only that key (or a copy of it) opens the chest. Locks are shown on the chest as an item display. Items can be plain vanilla items or [Nexo](https://nexomc.com) items.

## Requirements

- Paper or Purpur 1.21.11
- Java 21
- Optional: [Nexo](https://nexomc.com) (custom item models), [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) (placeholders in messages)

Other versions are not supported.

## Installation

1. Put `Outlands-Locks-<version>.jar` into `plugins/`.
2. Start the server. `config.yml` and `languages/en.yml`, `languages/ru.yml` are created in `plugins/Outlands-Locks/`.
3. Adjust `config.yml` and run `/outlands-locks reload` (or restart).

## Getting the items

There are three items: **lock**, **blank key** and **key**.

| Mode | Lock and blank key | Key |
|---|---|---|
| `vanilla` | `/outlands-locks give <player> <lock\|blank_key> [amount]` | Cut from a blank key on a locked chest, or copied from another key |
| `nexo` | Through Nexo (recipes, shops, commands); `give` hands out the Nexo items too | Same as above |

A key cannot be given directly: it is useless without a lock id. Admins can copy one from a locked chest with `/outlands-locks copykey`.

Items are recognised by a marker stored on the item, not by material, so an ordinary item of the same material is never a key. By default the plugin writes no lore or serial numbers on items (see `item-lore`).

## How it works

Chests, trapped chests, wooden doors and wooden trapdoors (every wood, bamboo, cherry, crimson, warped) can be locked. Copper chests, ender chests, iron and copper doors and trapdoors, and fence gates are left alone. Which doors and trapdoors count as wooden is what the vanilla block tags `wooden_doors` and `wooden_trapdoors` say.

Hanging a lock, cutting the first key, copying a key and tying a lock to a key need the permission `outlands.locks.use`. Everybody has it unless a permission plugin takes it away, and without it the click is refused with a message. Opening with a key and taking a lock off do not need it.

| Action | How |
|---|---|
| Install a lock | Sneak + right-click an unlocked chest with a lock. A double chest gets one lock for both halves. Anyone who has the chest open at that moment and could not open it now is sent away from it |
| Cut the first key | Sneak + right-click a locked chest with a blank key. Anyone can do it, once per lock |
| Open | Hold a matching key in the main or off hand and right-click |
| Copy a key | Hold a blank key in the main hand and the key in the off hand, then sneak + right-click (in the air or on a block). One blank becomes a copy and a sound plays. The key is kept. The copy is plain: no custom name or lore |
| Remove a lock | Sneak + left-click with a matching key in the **main** hand. The lock comes back with its id, so old keys still fit, and a new key cannot be cut if one already was |
| Remove a lock with no key cut yet | Sneak + left-click, no key needed (any item or an empty hand). The lock comes back as it was |
| Tie a lock to a key you already have | Hold a lock whose key is not cut in the main hand and the key in the off hand, then sneak + right-click (in the air or on a block). The lock now carries the key's id and counts as cut: hang it with another sneak + right-click on a chest, and that key opens every chest with such a lock. No other key can be cut for it. One lock of a stack is tied at a time |

A used lock can be hung on another chest: the same keys open both.

Doors and trapdoors work the same way with these differences:

- Each door leaf and each trapdoor has its own lock (a double door takes two locks; tie both to one key to open both with it). Click either half of a door.
- The lock is shown on both faces of a **closed** door or trapdoor and is taken away while it stands open (it comes back when it is shut). A key is needed to open; once it is open, anyone can shut it and the lock holds again.
- Redstone (buttons, levers, plates, signals) cannot open a locked closed door or trapdoor.
- The block under a locked door cannot be broken by hand, so the door cannot be dropped to lose its lock. In creative, or with `outlands.locks.bypass`, it can be: the door goes with it and the lock drops, as when the door itself is broken.
- A locked door or trapdoor is not destroyed when the server would do it, for example when an explosion, a fire or a falling block takes the block under a door: it stays where it is, with its lock. Commands run in the `destroy` mode (`/setblock ... destroy`, `/fill ... destroy`) go through the same step, so take the lock off first (`/outlands-locks remove` or `destroy`).
- Mobs cannot open them: villagers, piglins and the like are refused, and a mob that still manages to open one (illagers, for example) finds it shut again at once. A mob may shut an open one.

Protection of locked chests and doors:

- Cannot be broken in survival or adventure. In creative the chest breaks and the lock drops.
- Hoppers, hopper minecarts, droppers and dispensers cannot take items out of or put items into them. Copper golems do not take items from them or put items into them.
- Pistons (normal and sticky) do nothing when they would push, pull or break a locked chest, door or trapdoor, or the block under a locked door.
- Only explosions from the entity types listed in `explosions.allowed-sources` break them (TNT and TNT minecart by default). The lock is destroyed with the chest. Other explosions and entities such as the wither leave the chest intact.
- A chest placed next to a locked chest stays single and unlocked.
- Lock displays missing after `/kill` or entity cleanup are restored when the chunk loads, and displays left without a chest are removed.

Respect for other plugins: interactions already cancelled by protection plugins such as Towny are ignored.

## Commands and permissions

All commands need `outlands.locks.admin`. Commands that act on a chest use the chest you are looking at.

| Command | What it does |
|---|---|
| `/outlands-locks inspect` | Shows the lock id, whether the key is cut, and the coordinates |
| `/outlands-locks remove` | Removes the lock and drops it next to the chest with its id and key state |
| `/outlands-locks destroy` | Erases the lock and its display, nothing is dropped |
| `/outlands-locks copykey` | Gives you a key for the lock |
| `/outlands-locks copylock` | Gives you a copy of the lock (same id and key state) |
| `/outlands-locks give <player> <lock\|blank_key> [amount]` | Gives a lock or a blank key |
| `/outlands-locks reload` | Reloads `config.yml` and `languages/` |

Tab completion is available for all subcommands and for the `give` arguments.

`reload` reads and checks everything first; on an error the previous settings stay and the reason is logged. Placed locks, keys and displays are not touched. New display settings apply to displays created afterwards. Changing `item-provider` needs a restart.

| Permission | Default | Purpose |
|---|---|---|
| `outlands.locks.admin` | op | All `/outlands-locks` commands |
| `outlands.locks.bypass` | op | Open and break any locked chest without a key (the lock drops when the chest breaks) |
| `outlands.locks.use` | everyone | Hang a lock, cut the first key, copy a key, tie a lock to a key. Opening with a key and taking a lock off do not need it. Take it away from a group to keep it from making locks |

## Configuration

`plugins/Outlands-Locks/config.yml`:

```yaml
language: en
item-provider: vanilla

items:
  vanilla:
    lock:
      material: IRON_INGOT
      item-model: ""
      custom-model-data: ~
    blank-key:
      material: IRON_NUGGET
      item-model: ""
      custom-model-data: ~
    key:
      material: GOLD_NUGGET
      item-model: ""
      custom-model-data: ~
  nexo:
    lock: lock
    blank-key: blank_key
    key: key

display:
  offset: { right: 0.0, up: 0.1, out: -0.05 }
  scale: 0.4
  rotation: { yaw: 0.0, pitch: 0.0, roll: 0.0 }

door-display:
  offset: { right: 0.0, up: 0.0, out: 0.02 }
  scale: 0.3
  rotation: { yaw: 0.0, pitch: 0.0, roll: 0.0 }

trapdoor-display:
  offset: { right: 0.0, up: 0.0, out: 0.02 }
  scale: 0.3
  rotation: { yaw: 0.0, pitch: 0.0, roll: 0.0 }

explosions:
  allowed-sources: [TNT, TNT_MINECART]

item-lore: false
```

| Key | Meaning |
|---|---|
| `language` | File name from `languages/` without `.yml` |
| `item-provider` | `vanilla` or `nexo`. One mode for the whole plugin. With `nexo` and no Nexo installed the plugin disables itself |
| `items.vanilla.*` | Material of each item, optional `item-model` (namespaced key) and `custom-model-data` (integer) for resource packs. Use non-placeable materials |
| `items.nexo.*` | Nexo item ids |
| `display.offset` | Blocks from the centre of the chest front: `right`, `up`, `out` (away from the chest) |
| `display.scale`, `display.rotation` | Size and extra rotation in degrees on top of the chest facing |
| `door-display` | The same for doors: `right` is toward the handle, `up` is from handle height, `out` is away from the surface. A lock is shown on each face of the door |
| `trapdoor-display` | The same for trapdoors, with settings of their own: `right` is along the trapdoor, `up` is from the middle of an open trapdoor (it does nothing while closed), `out` is away from the surface |
| `item-lore` | `false` by default: the plugin writes nothing on items. `true`: locks and keys the plugin builds or ties get lore lines from the language file (`lore:` section): a key shows its lock id, a lock shows its id (when it has one) and "set up for a key" or "not set up". The plugin then replaces the lore of those items. Items that already exist are not relabelled |
| `explosions.allowed-sources` | Entity types (`EntityType` names) whose explosions break a locked chest. `[]` means none |

An invalid value in the active item section is logged and disables the plugin on start; on `reload` it keeps the old settings.

## Updating

When you replace the jar with a newer version, the plugin compares `config.yml`, `languages/en.yml` and `languages/ru.yml` with the ones inside the jar on every start and **adds the entries they are missing**, with their comments. Your values, comments and order are not changed and nothing is removed. The old file is kept next to it as `<name>.bak`, and the log shows one line such as `config.yml: added 2 missing entries (item-lore, door-display)`. A file that is not valid YAML is left alone. Your own language files are not edited: keys missing there are read from the built-in English file, with a warning in the log. `/outlands-locks reload` does not edit files.

## Languages

Messages, sounds and the names of vanilla items live in `plugins/Outlands-Locks/languages/<name>.yml`. English (`en`) and Russian (`ru`) are bundled.

- To add a language, copy `en.yml` to `languages/<name>.yml`, translate it and set `language: <name>`.
- Keep the same keys as in `en.yml`. A key missing from your file is taken from the built-in English file and a warning is logged.
- A missing or broken language file falls back to built-in English with an error in the log.

Each message has a `type` (`chat`, `actionbar` or `none`), a `text` and a `sound`:

```yaml
lock-installed:
  type: actionbar
  text: "<gray>You attached a lock."
  sound: { key: block.chain.place, volume: 1.0, pitch: 1.0 }
```

Set `sound: none` for silence.

### Lore lines

With `item-lore: true` the lines come from the `lore:` section of the language file (`lock-id`, `lock-unbound`, `lock-bound`, `key-id`), MiniMessage with `{lock_id}`. An empty line is left out.

### Text and placeholders

Text uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html). Built-in placeholders in curly braces: `{player}`, `{lock_id}`, `{key_cut}`, `{world}`, `{x}`, `{y}`, `{z}` (chest messages) and `{item}`, `{amount}` (`give`). Each message gets the ones that make sense for it.

If PlaceholderAPI is installed, any `%placeholder%` works as well, resolved for the receiving player. Console output is not processed by PlaceholderAPI. Legacy colour codes (`&` and `§`) returned by PlaceholderAPI expansions are converted, so they mix correctly with MiniMessage. Without PlaceholderAPI the text is shown as written.

Order: built-in placeholders, then PlaceholderAPI, then MiniMessage.

### Sounds

`sound.key` accepts any namespaced sound key:

- Vanilla: `block.chest.locked` or `minecraft:block.chest.locked`
- Resource pack or Nexo: the key of the sound as it is registered, for example `mynamespace:lock.click`

The sound is played by key, so keys the server does not know are not an error: they play only if the client's resource pack has them, otherwise nothing is heard.

For Nexo, register the sound in `plugins/Nexo/sounds.yml` and use its `id` as the key:

```yaml
sounds:
  - id: mynamespace:lock.click
    sound: mynamespace:lock/click
```

Then set `sound: { key: mynamespace:lock.click, volume: 1.0, pitch: 1.0 }` in the language file. An `id` without a namespace belongs to the `minecraft` namespace.

Sounds work in both item modes; Nexo is not needed for them.

## Notes

- Locks live in the chest itself (persistent data), so there is no database. Keys carry the lock id the same way.
- Renaming a key with a plugin that rewrites item data may drop the lock id from the key. Make sure such plugins keep the item's persistent data.
- Not covered for doors and trapdoors: other plugins that toggle or break blocks. A mob that opens a locked door through its own goal opens it for a moment (you may hear the door sound) before the plugin shuts it.
- Commands such as `/setblock`, `/fill`, WorldEdit, or other plugins can remove a chest without the plugin noticing. A leftover display is cleaned up when the chunk loads.

## License

[PolyForm Noncommercial License 1.0.0](LICENSE). You may use, copy, change and share the plugin for any noncommercial purpose: a hobby or community server, study, testing, and so on. You may **not sell it** or use it for commercial advantage. Whoever you give a copy to must get the license (or its URL) and the `Required Notice:` line from the `LICENSE` file, so the author stays credited. Running it on a server that earns money may count as commercial use; if in doubt, ask the author.
