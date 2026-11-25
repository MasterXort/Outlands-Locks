package ru.outlands.locks.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds to a file of the server whatever the bundled file of this version has and the file lacks, as text: the missing
 * entries are copied with their comments, and nothing that is already there is touched, moved or removed. The file is
 * not rewritten through Bukkit because that would reformat it (inline maps, owner's comments, order).
 *
 * <p>The files are read line by line in the shape the bundled ones have: block style, keys without quotes. Scalars,
 * lists, block scalars ({@code text: |-}) and inline maps and lists are copied whole and never merged element by
 * element, so a trimmed {@code offset: { right: 0.1 }} stays as the owner left it.
 */
public final class ConfigUpdater {

    private static final Pattern KEY = Pattern.compile("^( *)([A-Za-z0-9_.\\-]+):(?:\\s+(.*))?$");
    private static final int LISTED = 8;
    /** The byte order mark some editors put at the start of a UTF-8 file. */
    private static final String BOM = String.valueOf((char) 0xFEFF);

    private ConfigUpdater() {
    }

    /**
     * @param file         the file of the server; left alone when it does not exist or is not valid YAML
     * @param bundledText  the file of this version
     * @return the paths of the entries that were added (an entry is one key with everything under it)
     */
    public static List<String> update(Path file, String bundledText, Consumer<String> info, Consumer<String> warn) {
        String name = file.getFileName().toString();
        try {
            String original = Files.readString(file, StandardCharsets.UTF_8);
            Merged merged = merge(original, bundledText);
            if (merged == null) {
                warn.accept(name + ": not updated, the file could not be read as YAML or the result did not check out");
                return List.of();
            }
            if (merged.added().isEmpty()) {
                return List.of();
            }
            Path backup = file.resolveSibling(name + ".bak");
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            Path temp = file.resolveSibling(name + ".tmp");
            Files.writeString(temp, merged.text(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            info.accept(name + ": added " + merged.added().size() + " missing entries (" + listed(merged.added())
                    + "). The previous file is saved as " + backup.getFileName());
            return merged.added();
        } catch (IOException e) {
            warn.accept(name + ": not updated: " + e.getMessage());
            return List.of();
        }
    }

    /** The merged text and the entries added; null when the owner's file is broken or the result fails its checks. */
    static Merged merge(String original, String bundledText) {
        boolean bom = original.startsWith(BOM);
        String text = bom ? original.substring(1) : original;
        YamlConfiguration user = parse(text);
        YamlConfiguration bundled = parse(bundledText);
        if (user == null || bundled == null) {
            return null;
        }

        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        boolean endsWithNewline = text.endsWith("\n");
        List<String> userLines = lines(text);
        List<String> bundledLines = lines(bundledText);

        List<Entry> userEntries = parseBlock(userLines, 0, userLines.size());
        List<Entry> bundledEntries = parseBlock(bundledLines, 0, bundledLines.size());

        List<Insertion> insertions = new ArrayList<>();
        mergeBlock(userEntries, bundledEntries, userLines, bundledLines, "", -1, 0, insertions);
        if (insertions.isEmpty()) {
            return new Merged(original, List.of());
        }

        List<String> added = new ArrayList<>();
        for (Insertion insertion : insertions) {
            added.add(insertion.path());
        }
        // At one line a block's own new entries go before the next level's: they belong to the block above.
        insertions.sort(Comparator.comparingInt(Insertion::position)
                .thenComparing(Comparator.comparingInt((Insertion i) -> depth(i.path())).reversed()));
        Map<Integer, List<String>> byPosition = new TreeMap<>();
        for (Insertion insertion : insertions) {
            byPosition.computeIfAbsent(insertion.position(), p -> new ArrayList<>()).addAll(insertion.lines());
        }
        List<String> result = new ArrayList<>(userLines);
        List<Integer> positions = new ArrayList<>(byPosition.keySet());
        for (int i = positions.size() - 1; i >= 0; i--) {
            result.addAll(positions.get(i), byPosition.get(positions.get(i)));
        }
        String joined = String.join(newline, result) + (endsWithNewline ? newline : "");
        YamlConfiguration check = parse(joined);
        if (check == null || !holds(check, user, bundled, added)) {
            return null;
        }
        return new Merged((bom ? BOM : "") + joined, added);
    }

    record Merged(String text, List<String> added) {
    }

    private record Insertion(int position, List<String> lines, String path) {
    }

    /** One key with its leading comments and everything under it. {@code end} is exclusive. */
    private record Entry(String key, int indent, int start, int keyLine, int end, boolean atom,
                         List<Entry> children, boolean blankBefore) {
    }

    // ---- merging ----

    /**
     * @param parentKeyLine the line of the parent key (-1 at the top), where a block with no entries gets its first one
     * @param childIndent   the indent the entries of this block should have when the owner's block has none
     */
    private static void mergeBlock(List<Entry> user, List<Entry> bundled, List<String> userLines,
                                   List<String> bundledLines, String prefix, int parentKeyLine, int childIndent,
                                   List<Insertion> out) {
        int position = user.isEmpty() ? parentKeyLine + 1 : user.get(user.size() - 1).end();
        int indent = user.isEmpty() ? childIndent : user.get(0).indent();
        for (Entry b : bundled) {
            Entry u = find(user, b.key());
            String path = prefix + b.key();
            if (u == null) {
                out.add(new Insertion(position, render(b, bundledLines, indent, prefix.isEmpty() || b.blankBefore()),
                        path));
            } else if (!b.atom() && !u.atom()) {
                int deeper = u.children().isEmpty() ? u.indent() + 2 : u.children().get(0).indent();
                mergeBlock(u.children(), b.children(), userLines, bundledLines, path + ".", u.keyLine(), deeper, out);
            }
        }
    }

    private static Entry find(List<Entry> entries, String key) {
        for (Entry entry : entries) {
            if (entry.key().equals(key)) {
                return entry;
            }
        }
        return null;
    }

    /** The lines of the entry at the indent of its new neighbours, with a blank line before it when wanted. */
    private static List<String> render(Entry entry, List<String> source, int indent, boolean blankBefore) {
        List<String> lines = new ArrayList<>();
        if (blankBefore) {
            lines.add("");
        }
        String pad = " ".repeat(indent);
        for (int i = entry.start(); i < entry.end(); i++) {
            String line = source.get(i);
            if (line.isBlank()) {
                lines.add("");
                continue;
            }
            int lead = leading(line);
            lines.add(pad + line.substring(Math.min(lead, entry.indent())));
        }
        return lines;
    }

    // ---- reading ----

    private static List<String> lines(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\r?\n", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static List<Entry> parseBlock(List<String> lines, int from, int to) {
        List<Entry> entries = new ArrayList<>();
        int blockIndent = -1;
        int leadStart = -1;
        boolean blank = false;
        int i = from;
        while (i < to) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                blank = true;
                leadStart = -1;
                i++;
                continue;
            }
            if (trimmed.startsWith("#")) {
                if (leadStart == -1) {
                    leadStart = i;
                }
                i++;
                continue;
            }
            Matcher m = KEY.matcher(line);
            if (!m.matches() || (blockIndent != -1 && m.group(1).length() != blockIndent)) {
                leadStart = -1;
                i++;
                continue;
            }
            int indent = m.group(1).length();
            if (blockIndent == -1) {
                blockIndent = indent;
            }
            int end = i + 1;
            while (end < to && (lines.get(end).isBlank() || leading(lines.get(end)) > indent)) {
                end++;
            }
            while (end > i + 1 && lines.get(end - 1).isBlank()) {
                end--;
            }
            String value = m.group(3) == null ? "" : m.group(3).trim();
            boolean atom = !value.isEmpty() && !value.startsWith("#");
            List<Entry> children = List.of();
            if (!atom) {
                String first = firstContent(lines, i + 1, end);
                if (first == null || first.startsWith("-")) {
                    atom = true;
                } else {
                    children = parseBlock(lines, i + 1, end);
                }
            }
            entries.add(new Entry(m.group(2), indent, leadStart == -1 ? i : leadStart, i, end, atom, children, blank));
            leadStart = -1;
            blank = false;
            i = end;
        }
        return entries;
    }

    /** The first line of the range that is neither blank nor a comment, trimmed; null if there is none. */
    private static String firstContent(List<String> lines, int from, int to) {
        for (int i = from; i < to; i++) {
            String trimmed = lines.get(i).trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return trimmed;
            }
        }
        return null;
    }

    private static int leading(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    // ---- checking ----

    private static YamlConfiguration parse(String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            return null;
        }
        return yaml;
    }

    /** Every value the owner had is unchanged, and everything that was added can be read. */
    private static boolean holds(YamlConfiguration result, YamlConfiguration before, YamlConfiguration bundled,
                                 List<String> added) {
        for (String key : before.getKeys(true)) {
            Object old = before.get(key);
            if (!(old instanceof ConfigurationSection) && !java.util.Objects.equals(old, result.get(key))) {
                return false;
            }
        }
        for (String path : added) {
            if (bundled.isConfigurationSection(path)) {
                for (String leaf : bundled.getConfigurationSection(path).getKeys(true)) {
                    if (!result.contains(path + "." + leaf)) {
                        return false;
                    }
                }
            } else if (!result.contains(path)) {
                return false;
            }
        }
        return true;
    }

    private static int depth(String path) {
        return (int) path.chars().filter(c -> c == '.').count();
    }

    private static String listed(List<String> paths) {
        if (paths.size() <= LISTED) {
            return String.join(", ", paths);
        }
        return String.join(", ", paths.subList(0, LISTED)) + ", ...";
    }
}
