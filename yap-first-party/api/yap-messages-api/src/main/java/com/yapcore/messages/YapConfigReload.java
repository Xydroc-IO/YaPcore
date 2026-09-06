package com.yapcore.messages;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared reload wrapper: catch bad YAML/parse failures and report cleanly to sender + log.
 */
public final class YapConfigReload {

    public record Result(boolean ok, List<String> errors, List<String> warnings) {
        public static Result success() {
            return new Result(true, List.of(), List.of());
        }

        public static Result success(List<String> warnings) {
            return new Result(true, List.of(), List.copyOf(warnings));
        }

        public static Result failure(String... errors) {
            List<String> list = new ArrayList<>();
            for (String e : errors) {
                if (e != null && !e.isBlank()) {
                    list.add(e);
                }
            }
            return new Result(false, List.copyOf(list), List.of());
        }
    }

    private YapConfigReload() {
    }

    /** Run loader; any thrown exception becomes a failed {@link Result}. */
    public static Result run(Runnable loader) {
        Objects.requireNonNull(loader, "loader");
        try {
            loader.run();
            return Result.success();
        } catch (Exception e) {
            String msg = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            return Result.failure(msg);
        }
    }

    public static Result run(Consumer<List<String>> loaderWithWarnings) {
        Objects.requireNonNull(loaderWithWarnings, "loader");
        List<String> warnings = new ArrayList<>();
        try {
            loaderWithWarnings.accept(warnings);
            return Result.success(warnings);
        } catch (Exception e) {
            String msg = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            return Result.failure(msg);
        }
    }

    public static void report(CommandSender sender, String pluginName, Result result) {
        report(sender, null, pluginName, result);
    }

    public static void report(CommandSender sender, Logger log, String pluginName, Result result) {
        if (result == null) {
            YapMessages.send(sender, "&cReload returned no result.");
            return;
        }
        if (result.ok()) {
            YapMessages.reloaded(sender, pluginName);
            for (String w : result.warnings()) {
                YapMessages.send(sender, "&eWarning: &f{msg}", "msg", w);
                if (log != null) {
                    log.warning(pluginName + " reload warning: " + w);
                }
            }
            return;
        }
        for (String err : result.errors()) {
            YapMessages.send(sender, "&cConfig error: &f{msg}", "msg", err);
            if (log != null) {
                log.log(Level.SEVERE, pluginName + " reload failed: " + err);
            }
        }
        if (result.errors().isEmpty()) {
            YapMessages.send(sender, "&c{plugin} reload failed.", "plugin",
                    pluginName == null ? "Plugin" : pluginName);
        }
    }
}
