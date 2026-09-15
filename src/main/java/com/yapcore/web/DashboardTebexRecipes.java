package com.yapcore.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Operator-editable Tebex package recipes (`config/tebex-recipes.yml`). */
public final class DashboardTebexRecipes {

    public static final Path RELATIVE = Path.of("config", "tebex-recipes.yml");
    public static final Path DEFAULTS = Path.of("config", "defaults", "tebex-recipes.yml");

    private DashboardTebexRecipes() {
    }

    public static Path file(Path root) {
        return root.resolve(RELATIVE);
    }

    /** Ensure runtime file exists (copy shipped defaults when missing). */
    public static void ensureFile(Path root) throws IOException {
        Path dest = file(root);
        if (Files.isRegularFile(dest)) {
            return;
        }
        Files.createDirectories(dest.getParent());
        Path defaults = root.resolve(DEFAULTS);
        if (Files.isRegularFile(defaults)) {
            Files.copy(defaults, dest, StandardCopyOption.COPY_ATTRIBUTES);
            return;
        }
        Files.writeString(dest, defaultYaml());
    }

    public static List<Map<String, Object>> load(Path root) {
        try {
            ensureFile(root);
            Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file(root));
            return parseRecipes(yaml.get("recipes"));
        } catch (IOException e) {
            return builtinFallback();
        }
    }

    public static String loadYamlText(Path root) throws IOException {
        ensureFile(root);
        return Files.readString(file(root));
    }

    public static void saveYamlText(Path root, String yamlText) throws IOException {
        if (yamlText == null || yamlText.isBlank()) {
            throw new IllegalArgumentException("recipes yaml required");
        }
        Object loaded;
        try {
            loaded = new org.yaml.snakeyaml.Yaml().load(yamlText);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid recipes yaml: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
        if (!(loaded instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("recipes yaml must be a mapping with recipes:");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) loaded;
        if (!map.containsKey("recipes")) {
            throw new IllegalArgumentException("recipes: list required");
        }
        List<Map<String, Object>> recipes = parseRecipes(map.get("recipes"));
        if (recipes.isEmpty()) {
            throw new IllegalArgumentException("at least one recipe required");
        }
        Path dest = file(root);
        Files.createDirectories(dest.getParent());
        Files.writeString(dest, yamlText.stripTrailing() + "\n");
    }

    public static void upsertRecipe(Path root, String name, String commands) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("recipe name required");
        }
        if (commands == null || commands.isBlank()) {
            throw new IllegalArgumentException("recipe commands required");
        }
        ensureFile(root);
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file(root));
        List<Map<String, Object>> recipes = new ArrayList<>(parseRecipes(yaml.get("recipes")));
        String id = name.trim();
        boolean replaced = false;
        for (int i = 0; i < recipes.size(); i++) {
            if (id.equalsIgnoreCase(String.valueOf(recipes.get(i).get("name")))) {
                recipes.set(i, recipe(id, commands));
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            recipes.add(recipe(id, commands));
        }
        yaml.put("recipes", toYamlList(recipes));
        DashboardNetworkSnapshots.dumpYaml(file(root), yaml);
    }

    public static void deleteRecipe(Path root, String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("recipe name required");
        }
        ensureFile(root);
        Map<String, Object> yaml = DashboardNetworkSnapshots.loadYaml(file(root));
        List<Map<String, Object>> recipes = new ArrayList<>(parseRecipes(yaml.get("recipes")));
        String id = name.trim();
        recipes.removeIf(r -> id.equalsIgnoreCase(String.valueOf(r.get("name"))));
        if (recipes.isEmpty()) {
            throw new IllegalArgumentException("cannot delete the last recipe");
        }
        yaml.put("recipes", toYamlList(recipes));
        DashboardNetworkSnapshots.dumpYaml(file(root), yaml);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> parseRecipes(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            String name = DashboardNetworkSnapshots.str(map.get("name"), "").trim();
            String commands = normalizeCommands(map.get("commands"));
            if (name.isEmpty() || commands.isEmpty()) {
                continue;
            }
            out.add(recipe(name, commands));
        }
        return out;
    }

    private static List<Map<String, Object>> toYamlList(List<Map<String, Object>> recipes) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : recipes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", r.get("name"));
            row.put("commands", r.get("commands"));
            out.add(row);
        }
        return out;
    }

    private static Map<String, Object> recipe(String name, String commands) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("commands", normalizeCommands(commands));
        return row;
    }

    private static String normalizeCommands(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object line : list) {
                String s = String.valueOf(line).trim();
                if (s.isEmpty()) {
                    continue;
                }
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(s);
            }
            return sb.toString();
        }
        return String.valueOf(raw).replace("\r\n", "\n").strip();
    }

    private static List<Map<String, Object>> builtinFallback() {
        return List.of(
                recipe("VIP rank",
                        "yapperm user {username} parent set vip\nkit grant {username} vip"),
                recipe("Adventurer kit unlock",
                        "yapperm user {username} permission set yapdata.kit.adventurer true\nkit grant {username} adventurer"),
                recipe("VIP kit unlock only",
                        "yapperm user {username} permission set yapdata.kit.vip true"));
    }

    private static String defaultYaml() {
        return """
                # Package console recipes for creator.tebex.io (Execute as console).
                recipes:
                  - name: VIP rank
                    commands: |
                      yapperm user {username} parent set vip
                      kit grant {username} vip
                  - name: Adventurer kit unlock
                    commands: |
                      yapperm user {username} permission set yapdata.kit.adventurer true
                      kit grant {username} adventurer
                  - name: VIP kit unlock only
                    commands: |
                      yapperm user {username} permission set yapdata.kit.vip true
                """;
    }
}
