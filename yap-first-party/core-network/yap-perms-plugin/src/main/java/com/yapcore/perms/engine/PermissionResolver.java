package com.yapcore.perms.engine;

import com.yapcore.perms.EffectiveUser;
import com.yapcore.perms.PermissionNodes;
import com.yapcore.perms.PermsConfig;
import com.yapcore.perms.db.PermsRepository;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PermissionResolver {

    private final PermsConfig config;
    private final PermsRepository repository;
    private volatile Map<String, PermsRepository.GroupRow> groups = Map.of();
    private volatile Map<String, List<String>> tracks = Map.of();

    public PermissionResolver(PermsConfig config, PermsRepository repository) {
        this.config = config;
        this.repository = repository;
    }

    public void reloadCache() throws SQLException {
        groups = repository.loadAllGroups();
        tracks = repository.loadTracks();
    }

    public EffectiveUser resolve(UUID uuid, String name) {
        return resolve(uuid, name, "", "");
    }

    public EffectiveUser resolve(UUID uuid, String name, String world, String server) {
        try {
            PermsRepository.UserRow user = repository.loadUser(uuid, name, config.defaultGroup());
            Set<String> groupNames = collectGroups(user);
            Instant now = Instant.now();
            Map<String, Boolean> merged = new LinkedHashMap<>();
            String prefix = "";
            String suffix = "";
            int weight = Integer.MIN_VALUE;
            String primary = user.primaryGroup();
            String display = primary;

            List<PermsRepository.GroupRow> sorted = new ArrayList<>();
            for (String groupName : groupNames) {
                PermsRepository.GroupRow group = groups.get(groupName);
                if (group != null) {
                    sorted.add(group);
                }
            }
            sorted.sort(Comparator.comparingInt(PermsRepository.GroupRow::weight));

            for (PermsRepository.GroupRow group : sorted) {
                mergeNodes(merged, group.nodes(), now, world, server);
                if (group.weight() >= weight) {
                    weight = group.weight();
                    prefix = group.prefix();
                    suffix = group.suffix();
                    display = group.name();
                }
            }
            mergeNodes(merged, user.nodes(), now, world, server);

            if (user.metaPrefix() != null) {
                prefix = user.metaPrefix();
            }
            if (user.metaSuffix() != null) {
                suffix = user.metaSuffix();
            }

            if (weight == Integer.MIN_VALUE) {
                weight = 0;
            }
            List<String> membership = new ArrayList<>();
            membership.add(primary);
            for (String extra : user.extraGroups()) {
                if (!membership.contains(extra)) {
                    membership.add(extra);
                }
            }
            return new EffectiveUser(uuid, name, primary, display, prefix, suffix, weight,
                    Map.copyOf(merged), List.copyOf(membership));
        } catch (SQLException e) {
            return new EffectiveUser(uuid, name, config.defaultGroup(), config.defaultGroup(),
                    "", "", 0, Map.of(), List.of(config.defaultGroup()));
        }
    }

    public String explain(UUID uuid, String name, String node, String world, String server) {
        EffectiveUser eff = resolve(uuid, name, world, server);
        boolean granted = PermissionNodes.has(eff.permissions(), node);
        String deciding = PermissionNodes.decidingPattern(eff.permissions(), node);
        StringBuilder sb = new StringBuilder();
        sb.append(granted ? "§aGRANTED" : "§cDENIED");
        sb.append(" §7").append(node);
        if (!deciding.isBlank()) {
            Boolean value = eff.permissions().get(deciding);
            if (value == null) {
                for (Map.Entry<String, Boolean> e : eff.permissions().entrySet()) {
                    if (e.getKey().equalsIgnoreCase(deciding)) {
                        value = e.getValue();
                        break;
                    }
                }
            }
            sb.append(" §7via §f").append(deciding).append("§7=").append(value);
        } else {
            sb.append(" §7(no matching node)");
        }
        sb.append(" §8[").append(eff.displayGroup()).append(" w").append(eff.weight()).append(']');
        if (world != null && !world.isBlank()) {
            sb.append(" §8world=").append(world);
        }
        return sb.toString();
    }

    public Set<String> membershipGroups(UUID uuid, String name) {
        try {
            PermsRepository.UserRow user = repository.loadUser(uuid, name, config.defaultGroup());
            Set<String> out = new LinkedHashSet<>();
            out.add(user.primaryGroup().toLowerCase());
            out.addAll(user.extraGroups());
            return out;
        } catch (SQLException e) {
            return Set.of(config.defaultGroup());
        }
    }

    private Set<String> collectGroups(PermsRepository.UserRow user) {
        Set<String> names = new LinkedHashSet<>();
        names.add(user.primaryGroup().toLowerCase());
        names.addAll(user.extraGroups());
        Set<String> expanded = new HashSet<>();
        for (String name : names) {
            expandGroup(name, expanded);
        }
        return expanded;
    }

    private void expandGroup(String groupName, Set<String> out) {
        if (!out.add(groupName)) {
            return;
        }
        PermsRepository.GroupRow row = groups.get(groupName);
        if (row == null) {
            return;
        }
        Deque<String> stack = new ArrayDeque<>(row.parents());
        while (!stack.isEmpty()) {
            String parent = stack.pop();
            if (out.add(parent)) {
                PermsRepository.GroupRow parentRow = groups.get(parent);
                if (parentRow != null) {
                    stack.addAll(parentRow.parents());
                }
            }
        }
    }

    private static void mergeNodes(Map<String, Boolean> target, List<StoredNode> source,
                                   Instant now, String world, String server) {
        if (source == null) {
            return;
        }
        for (StoredNode node : source) {
            if (node.applies(now, world, server)) {
                target.put(node.node(), node.value());
            }
        }
    }

    public Map<String, List<String>> tracks() {
        return tracks;
    }

    public Map<String, PermsRepository.GroupRow> groups() {
        return groups;
    }
}
