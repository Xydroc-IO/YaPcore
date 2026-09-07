package com.yapcore.staff.ranks;

import com.yapcore.staff.StaffCmds;

/** Builds /yapperm commands for the staff ranks UI. */
public final class PermCmds {

    public enum Mode {
        ALLOW, DENY, UNSET
    }

    public static final String[] DURATIONS = {"", "1h", "1d", "7d", "30d"};

    private PermCmds() {
    }

    public static String durationLabel(int index) {
        String d = DURATIONS[Math.floorMod(index, DURATIONS.length)];
        return d.isEmpty() ? "permanent" : d;
    }

    public static void userInfo(String player) {
        StaffCmds.runFmt("yapperm user %s info", player);
    }

    public static void setPrimary(String player, String group) {
        StaffCmds.runFmt("yapperm user %s parent set %s", player, group);
    }

    public static void addParent(String player, String group) {
        StaffCmds.runFmt("yapperm user %s parent add %s", player, group);
    }

    public static void removeParent(String player, String group) {
        StaffCmds.runFmt("yapperm user %s parent remove %s", player, group);
    }

    public static void promote(String player) {
        StaffCmds.runFmt("promote %s", player);
    }

    public static void demote(String player) {
        StaffCmds.runFmt("demote %s", player);
    }

    public static void clearMeta(String player) {
        StaffCmds.runFmt("yapperm user %s meta clear", player);
    }

    public static void userPerm(String player, String node, Mode mode, int durationIndex) {
        String dur = DURATIONS[Math.floorMod(durationIndex, DURATIONS.length)];
        switch (mode) {
            case ALLOW -> {
                if (dur.isEmpty()) {
                    StaffCmds.runFmt("yapperm user %s permission set %s true", player, node);
                } else {
                    StaffCmds.runFmt("yapperm user %s permission set %s true %s", player, node, dur);
                }
            }
            case DENY -> {
                if (dur.isEmpty()) {
                    StaffCmds.runFmt("yapperm user %s permission set %s false", player, node);
                } else {
                    StaffCmds.runFmt("yapperm user %s permission set %s false %s", player, node, dur);
                }
            }
            case UNSET -> StaffCmds.runFmt("yapperm user %s permission unset %s", player, node);
        }
    }

    public static void groupInfo(String group) {
        StaffCmds.runFmt("yapperm group info %s", group);
    }

    public static void groupList() {
        StaffCmds.run("yapperm group list");
    }

    public static void groupCreate(String group, int weight) {
        StaffCmds.runFmt("yapperm group create %s %d", group, weight);
    }

    public static void groupDelete(String group) {
        StaffCmds.runFmt("yapperm group delete %s", group);
    }

    public static void groupParentAdd(String group, String parent) {
        StaffCmds.runFmt("yapperm group parent add %s %s", group, parent);
    }

    public static void groupParentRemove(String group, String parent) {
        StaffCmds.runFmt("yapperm group parent remove %s %s", group, parent);
    }

    public static void groupPerm(String group, String node, Mode mode, int durationIndex) {
        String dur = DURATIONS[Math.floorMod(durationIndex, DURATIONS.length)];
        switch (mode) {
            case ALLOW -> {
                if (dur.isEmpty()) {
                    StaffCmds.runFmt("yapperm group permission set %s %s true", group, node);
                } else {
                    StaffCmds.runFmt("yapperm group permission set %s %s true %s", group, node, dur);
                }
            }
            case DENY -> {
                if (dur.isEmpty()) {
                    StaffCmds.runFmt("yapperm group permission set %s %s false", group, node);
                } else {
                    StaffCmds.runFmt("yapperm group permission set %s %s false %s", group, node, dur);
                }
            }
            case UNSET -> StaffCmds.runFmt("yapperm group permission unset %s %s", group, node);
        }
    }

    public static void trackList() {
        StaffCmds.run("yapperm track list");
    }

    public static void trackInfo(String track) {
        StaffCmds.runFmt("yapperm track info %s", track);
    }

    public static void check(String player, String node) {
        StaffCmds.runFmt("yapperm check %s %s", player, node);
    }

    public static void reload() {
        StaffCmds.run("yapperm reload");
    }

    public static void applyPack() {
        StaffCmds.run("yapperm applypack");
    }

    public static void dump() {
        StaffCmds.run("yapperm dump");
    }
}
