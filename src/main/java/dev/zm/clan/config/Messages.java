package dev.zm.clan.config;

import dev.zm.clan.zMClans;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Loads the active language file and resolves placeholders in messages.
 * Placeholders use the format {key} throughout all message keys.
 */
public final class Messages {

    private final zMClans plugin;
    private FileConfiguration lang;

    public Messages(zMClans plugin) {
        this.plugin = plugin;
    }

    /** Loads (or reloads) the active language file. */
    public void load() {
        String code = plugin.getConfigManager().getLanguage(); // "EN" or "ES"
        String path = "langs/lang_" + code + ".yml";
        lang = plugin.getConfigManager().loadYaml(path);
    }

    /**
     * Returns a single-line message with placeholders replaced and colored.
     *
     * @param key          config key (e.g. "clan-created")
     * @param replacements flat pairs: key1, value1, key2, value2, ...
     */
    public String get(String key, String... replacements) {
        if (key == null)
            return "";
        if (lang == null)
            load();

        String raw = lang.getString(key);
        if (raw == null)
            raw = lang.getString("clan-" + key);
        if (raw == null)
            raw = lang.getString("invite-" + key);
        if (raw == null)
            raw = lang.getString("member-" + key);
        if (raw == null)
            raw = lang.getString("home-" + key);
        if (raw == null)
            raw = lang.getString("description-" + key);
        if (raw == null)
            raw = lang.getString("rank-" + key);
        if (raw == null)
            raw = lang.getString("admin-" + key);
        if (raw == null)
            raw = lang.getString("gui-" + key);

        if (raw == null) {
            return ColorUtil.colorize("&c[zMClans] Missing message: " + key);
        }

        String prefix = resolve(lang.getString("prefix", ""));
        raw = raw.replace("{prefix}", prefix);
        raw = applyReplacements(raw, replacements);
        return ColorUtil.colorize(raw);
    }

    /**
     * Sends a formatted message directly to a CommandSender.
     */
    public void send(CommandSender sender, String key, String... replacements) {
        if (sender != null) {
            sender.sendMessage(get(key, replacements));
        }
    }

    /**
     * Returns a list of lines (e.g. for help-lines).
     */
    public List<String> getList(String key, String... replacements) {
        if (lang == null)
            load();
        List<String> lines = lang.getStringList(key);
        String prefix = resolve(lang.getString("prefix", ""));
        return lines.stream()
                .map(l -> l.replace("{prefix}", prefix))
                .map(l -> applyReplacements(l, replacements))
                .map(ColorUtil::colorize)
                .toList();
    }

    private String resolve(String raw) {
        return raw == null ? "" : ColorUtil.colorize(raw);
    }

    private String applyReplacements(String text, String[] replacements) {
        if (text == null || replacements == null || replacements.length == 0)
            return text;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            String target = replacements[i];
            String val = replacements[i + 1];
            if (target != null && val != null) {
                text = text.replace("{" + target + "}", val);
            }
        }
        return text;
    }
}
