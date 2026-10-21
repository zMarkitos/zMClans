package dev.zm.clan.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts &amp; color codes, &#RRGGBB hex codes, and Adventure MiniMessage
 * tags (such as gradients, colors, bold, etc.) into Minecraft-formatted text
 * safely.
 */
public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    /**
     * Safe MiniMessage resolver that strictly permits colors, decorations,
     * gradients,
     * and rainbows, while rejecting any dangerous tags (click events, run commands,
     * hover, etc.).
     */
    private static final TagResolver SAFE_TAG_RESOLVER = TagResolver.builder()
            .resolver(StandardTags.color())
            .resolver(StandardTags.decorations())
            .resolver(StandardTags.gradient())
            .resolver(StandardTags.rainbow())
            .resolver(StandardTags.reset())
            .build();

    private static final MiniMessage SAFE_MINI_MESSAGE = MiniMessage.builder()
            .tags(SAFE_TAG_RESOLVER)
            .build();

    private ColorUtil() {
    }

    /**
     * Translates MiniMessage tags, {@code &#RRGGBB} hex colors, and {@code &} color
     * codes
     * into Minecraft section-formatted text.
     */
    public static String colorize(String text) {
        if (text == null || text.isEmpty())
            return "";

        String result = text;

        if (result.contains("<") && result.contains(">")) {
            try {
                Component comp = SAFE_MINI_MESSAGE.deserialize(result);
                result = LegacyComponentSerializer.legacySection().serialize(comp);
            } catch (Exception ignored) {
                // Ignore malformed MiniMessage and fall back to legacy hex parsing
            }
        }

        Matcher matcher = HEX_PATTERN.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);

        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    /** Strips all color codes and tags from a string, returning pure clean text. */
    public static String strip(String text) {
        if (text == null)
            return "";
        return ChatColor.stripColor(colorize(text)).trim();
    }

    /**
     * Detects blocked legacy formatting codes and their MiniMessage equivalents
     * before colorization can hide them from validation.
     */
    public static boolean containsBlockedFormat(String text, Collection<String> blockedCodes) {
        if (text == null || text.isEmpty() || blockedCodes == null || blockedCodes.isEmpty())
            return false;

        String normalized = text.toLowerCase(Locale.ROOT);
        for (String configuredCode : blockedCodes) {
            if (configuredCode == null || configuredCode.isBlank())
                continue;
            String code = configuredCode.trim().toLowerCase(Locale.ROOT);
            if (code.startsWith("&") || code.startsWith("§"))
                code = code.substring(1);
            if (code.length() != 1)
                continue;

            char legacyCode = code.charAt(0);
            for (int i = 0; i + 1 < normalized.length(); i++) {
                if ((normalized.charAt(i) == '&' || normalized.charAt(i) == '§')
                        && normalized.charAt(i + 1) == legacyCode) {
                    return true;
                }
            }

            String miniMessageTag = switch (legacyCode) {
                case 'k' -> "(?:obfuscated|obf)";
                case 'm' -> "(?:strikethrough|st)";
                case 'n' -> "(?:underlined|u)";
                case 'o' -> "(?:italic|em)";
                case 'l' -> "(?:bold|b)";
                case 'r' -> "reset";
                default -> null;
            };
            if (miniMessageTag != null && Pattern.compile(
                    "<\\s*/?\\s*" + miniMessageTag + "(?:\\s*:[^>]*)?\\s*>",
                    Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
