package com.yapcore.portals.cmd;

import com.yapcore.portals.PortalShape;
import org.bukkit.command.CommandSender;

import java.util.function.Predicate;

final class PortalCommandParse {

    private PortalCommandParse() {
    }

    static int indexOf(String[] args, String token, int from) {
        for (int i = Math.max(0, from); i < args.length; i++) {
            if (token.equalsIgnoreCase(args[i])) {
                return i;
            }
        }
        return -1;
    }

    static int parseInt(String raw, CommandSender sender) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            sender.sendMessage("§cNot a number: §f" + raw);
            return Integer.MIN_VALUE;
        }
    }

    static String[] copyFrom(String[] args, int from) {
        if (from >= args.length) {
            return new String[0];
        }
        String[] out = new String[args.length - from];
        System.arraycopy(args, from, out, 0, out.length);
        return out;
    }

    /** Link server id; {@code hub} is an alias for {@code lobby}. */
    static String resolveTarget(String raw) {
        String t = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if ("hub".equals(t)) {
            return "lobby";
        }
        return t;
    }

    /**
     * {@code /portal setshape} accepts {@code <shape>}, {@code <name> <shape>}, or {@code <shape> <name>}.
     * A null name means “the portal the player is standing in”.
     */
    static SetShapeRequest setShape(String[] args, Predicate<String> isPortal) {
        if (args == null || args.length < 2) {
            return SetShapeRequest.usage();
        }
        String first = args[1];
        String second = args.length >= 3 ? args[2] : null;
        boolean firstShape = PortalShape.Kind.known(first);
        boolean secondShape = second != null && PortalShape.Kind.known(second);
        if (second == null) {
            if (!firstShape) {
                return SetShapeRequest.badShape();
            }
            return SetShapeRequest.of(null, PortalShape.Kind.parse(first));
        }
        if (firstShape && !secondShape) {
            return SetShapeRequest.of(second, PortalShape.Kind.parse(first));
        }
        if (!firstShape && secondShape) {
            return SetShapeRequest.of(first, PortalShape.Kind.parse(second));
        }
        if (firstShape && isPortal != null && isPortal.test(first)) {
            return SetShapeRequest.of(first, PortalShape.Kind.parse(second));
        }
        if (secondShape && isPortal != null && isPortal.test(second)) {
            return SetShapeRequest.of(second, PortalShape.Kind.parse(first));
        }
        return SetShapeRequest.badShape();
    }

    record SetShapeRequest(String name, PortalShape.Kind shape, String error) {
        static SetShapeRequest of(String name, PortalShape.Kind shape) {
            return new SetShapeRequest(name, shape, null);
        }

        static SetShapeRequest usage() {
            return new SetShapeRequest(null, null, "usage");
        }

        static SetShapeRequest badShape() {
            return new SetShapeRequest(null, null, "shape");
        }

        boolean ok() {
            return error == null && shape != null;
        }
    }
}
