package ru.outlands.locks.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text pipeline: own {@code {...}} placeholders, then PlaceholderAPI {@code %...%}, then MiniMessage. */
public final class TextRenderer {

    private static final Pattern PAPI_TOKEN = Pattern.compile("%[^% ]+%");

    private TextRenderer() {
    }

    /**
     * @param papi resolves a single {@code %token%}; null when PlaceholderAPI is absent or the audience is the console
     */
    public static Component render(String text, Map<String, String> placeholders, UnaryOperator<String> papi) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        if (papi != null) {
            result = applyPapi(result, papi);
        }
        return MiniMessage.miniMessage().deserialize(result);
    }

    private static String applyPapi(String text, UnaryOperator<String> papi) {
        Matcher matcher = PAPI_TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = papi.apply(matcher.group());
            matcher.appendReplacement(out, Matcher.quoteReplacement(LegacyColors.toMiniMessage(value)));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
