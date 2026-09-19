package com.yapcore.holo.cmd;

import com.yapcore.holo.Hologram;
import com.yapcore.holo.HologramAttach;
import com.yapcore.holo.HologramClick;
import com.yapcore.holo.impl.HologramImpl;
import com.yapcore.holo.impl.HologramPages;
import com.yapcore.holo.impl.HologramServiceImpl;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Attach, clicks, pages, see-permission. */
public final class HologramCommandFeatures {

    private final HologramServiceImpl holograms;

    public HologramCommandFeatures(HologramServiceImpl holograms) {
        this.holograms = holograms;
    }

    public boolean attach(CommandSender sender, String[] args) {
        Optional<Hologram> holo = require(sender, args);
        if (holo.isEmpty()) {
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§e/yapholo attach <id> none|npc:<id>[:offset]|player:<uuid>[:offset]|entity:<uuid>[:offset]");
            return true;
        }
        HologramAttach attach = HologramAttach.parse(HologramCommandParse.join(args, 2));
        holo.get().attach(attach);
        holograms.save();
        String shown = attach.kind() == HologramAttach.Kind.NONE ? "none" : attach.serialize();
        sender.sendMessage("§aAttach §f" + holo.get().id() + " §7→ " + shown);
        return true;
    }

    public boolean click(CommandSender sender, String[] args) {
        Optional<Hologram> holo = require(sender, args);
        if (holo.isEmpty()) {
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§e/yapholo click <id> clear|LEFT:NEXT|RIGHT:CONSOLE:say hi …");
            return true;
        }
        if ("clear".equalsIgnoreCase(args[2]) || "none".equalsIgnoreCase(args[2])) {
            holo.get().setClicks(List.of());
            holograms.save();
            sender.sendMessage("§aClicks cleared.");
            return true;
        }
        List<HologramClick> clicks = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            HologramClick click = HologramClick.parse(args[i]);
            if (click != null) {
                clicks.add(click);
            }
        }
        if (clicks.isEmpty()) {
            sender.sendMessage("§cNo valid click actions.");
            return true;
        }
        holo.get().setClicks(clicks);
        holograms.save();
        sender.sendMessage("§aClicks set §7(" + clicks.size() + ")");
        return true;
    }

    public boolean see(CommandSender sender, String[] args) {
        Optional<Hologram> holo = require(sender, args);
        if (holo.isEmpty()) {
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§e/yapholo see <id> <permission>|none");
            return true;
        }
        String perm = "none".equalsIgnoreCase(args[2]) || "clear".equalsIgnoreCase(args[2]) ? "" : args[2];
        holo.get().setSeePermission(perm);
        holograms.save();
        sender.sendMessage("§aSee permission §f" + holo.get().id() + " §7→ "
                + (perm.isEmpty() ? "everyone" : perm));
        return true;
    }

    public boolean setPages(CommandSender sender, String[] args) {
        Optional<Hologram> holo = require(sender, args);
        if (holo.isEmpty() || args.length < 3) {
            sender.sendMessage("§e/yapholo setpages <id> page1line1|page1line2;;page2line1");
            return true;
        }
        holo.get().setPages(HologramPages.splitPages(HologramCommandParse.join(args, 2)));
        holograms.save();
        sender.sendMessage("§aPages updated §7(" + holo.get().pageCount() + ")");
        return true;
    }

    public boolean addPage(CommandSender sender, String[] args) {
        Optional<Hologram> holo = require(sender, args);
        if (holo.isEmpty()) {
            return true;
        }
        if (holo.get() instanceof HologramImpl impl) {
            impl.addPage();
        } else {
            List<List<String>> pages = new ArrayList<>(holo.get().pages());
            pages.add(List.of("&7New page"));
            holo.get().setPages(pages);
        }
        holograms.save();
        sender.sendMessage("§aPage added §7(" + holo.get().pageCount() + ")");
        return true;
    }

    public boolean infoExtra(CommandSender sender, Hologram h) {
        if (!h.attachment().serialize().isEmpty()) {
            sender.sendMessage("§7attach: §f" + h.attachment().serialize());
        }
        if (!h.seePermission().isBlank()) {
            sender.sendMessage("§7see: §f" + h.seePermission());
        }
        if (!h.clicks().isEmpty()) {
            sender.sendMessage("§7clicks:");
            for (HologramClick click : h.clicks()) {
                sender.sendMessage("  §f" + click.serialize());
            }
        }
        if (h.pageCount() > 1) {
            sender.sendMessage("§7pages: §f" + h.pageCount());
            int p = 1;
            for (List<String> page : h.pages()) {
                sender.sendMessage("§8page " + p++);
                int i = 1;
                for (String line : page) {
                    sender.sendMessage("  §7" + i++ + ". §f" + line);
                }
            }
        }
        return true;
    }

    private Optional<Hologram> require(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eNeed hologram id.");
            return Optional.empty();
        }
        Optional<Hologram> holo = holograms.get(args[1].toLowerCase(Locale.ROOT));
        if (holo.isEmpty()) {
            sender.sendMessage("§cUnknown hologram.");
        }
        return holo;
    }
}
