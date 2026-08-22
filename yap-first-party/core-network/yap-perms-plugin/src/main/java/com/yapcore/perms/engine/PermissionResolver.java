package com.yapcore.perms.engine;

import com.yapcore.perms.EffectiveUser;
import com.yapcore.perms.PermsConfig;
import com.yapcore.perms.db.PermsRepository;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
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
        try {
            PermsRepository.UserRow user = repository.loadUser(uuid, name, config.defaultGroup());
            Set<String> groupNames = collectGroups(user);
            Map<String, Boolean> merged = new LinkedHashMap<>();
            String prefix = "";
            String suffix = "";
            int weight = Integer.MIN_VALUE;
            String primary = user.primaryGroup();

            List<PermsRepository.GroupRow> sorted = new ArrayList<>();
            for (String groupName : groupNames) {
                PermsRepository.GroupRow group = groups.get(groupName);
                if (group != null) {
                    sorted.add(group);
                }
            }
            sorted.sort(Comparator.comparingInt(PermsRepository.GroupRow::weight));

            for (PermsRepository.GroupRow group : sorted) {
                mergeNodes(merged, group.nodes());
                if (group.weight() >= weight) {
                    weight = group.weight();
                    prefix = group.prefix();
                    suffix = group.suffix();
                }
            }
            mergeNodes(merged, user.nodes());

            if (user.metaPrefix() != null) {
                prefix = user.metaPrefix();
            }
            if (user.metaSuffix() != null) {
                suffix = user.metaSuffix();
            }

            if (weight == Integer.MIN_VALUE) {
                weight = 0;
            }
            return new EffectiveUser(uuid, name, primary, prefix, suffix, weight, Map.copyOf(merged));
        } catch (SQLException e) {
            return new EffectiveUser(uuid, name, config.defaultGroup(), "", "", 0, Map.of());
        }
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

    private static void mergeNodes(Map<String, Boolean> target, Map<String, Boolean> source) {
        for (Map.Entry<String, Boolean> e : source.entrySet()) {
            target.put(e.getKey(), e.getValue());
        }
    }

    public Map<String, List<String>> tracks() {
        return tracks;
    }

    public Map<String, PermsRepository.GroupRow> groups() {
        return groups;
    }
}
