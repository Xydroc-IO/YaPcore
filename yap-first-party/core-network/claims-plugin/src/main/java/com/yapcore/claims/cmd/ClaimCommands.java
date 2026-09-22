package com.yapcore.claims.cmd;

import com.yapcore.claims.Claim;
import com.yapcore.claims.ClaimService;
import com.yapcore.claims.ClaimVisualizer;
import com.yapcore.claims.TaxService;
import com.yapcore.claims.db.ClaimRepository;
import com.yapcore.claims.gui.ClaimsMenus;
import com.yapcore.regions.FlagValue;
import com.yapcore.regions.RegionFlag;
import com.yapcore.regions.RegionMessageKind;
import com.yapcore.messages.YapMessages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClaimCommands implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final ClaimService claims;
    private final TaxService taxes;
    private final ClaimsMenus menus;

    public ClaimCommands(JavaPlugin plugin, ClaimService claims, TaxService taxes, ClaimsMenus menus) {
        this.plugin = plugin;
        this.claims = claims;
        this.taxes = taxes;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            YapMessages.playersOnly(sender);
            return true;
        }
        if (!Perms.require(sender, "yapdata.claim")) {
            return true;
        }
        if (!claims.config().claimsEnabled()) {
            player.sendMessage("§cClaims disabled.");
            return true;
        }
        try {
            if (args.length == 0) {
                menus.openClaims(player);
                return true;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            return switch (sub) {
                case "tool", "shovel" -> {
                    player.getInventory().addItem(new ItemStack(claims.config().claimsTool()));
                    player.getInventory().addItem(new ItemStack(claims.config().claimsInspectTool()));
                    if (claims.config().claimsMode() == com.yapcore.claims.ClaimsConfig.ClaimMode.CHUNK) {
                        player.sendMessage("§aClaim tools given. Right-click a block with the shovel to claim a §f"
                                + claims.config().claimsPlotSize() + "×" + claims.config().claimsPlotSize()
                                + " §aplot (§f$" + String.format("%.0f", claims.config().claimsCostAmount())
                                + "§a, max §f" + claims.config().claimsMaxClaims() + "§a).");
                    } else {
                        player.sendMessage("§aClaim tools given (shovel + stick). Two corners to claim.");
                    }
                    yield true;
                }
                case "claim", "chunk", "hereclaim" -> {
                    player.sendMessage(claims.claimChunkAt(player, player.getLocation()));
                    yield true;
                }
                case "blocks", "slots", "limit" -> {
                    int owned = 0;
                    for (var c : claims.repo().listOwned(player.getUniqueId())) {
                        if (!c.isSubdivision()
                                && c.serverId() != null
                                && c.serverId().equalsIgnoreCase(claims.config().serverId())) {
                            owned++;
                        }
                    }
                    player.sendMessage("§aClaims: §f" + owned + "/" + claims.config().claimsMaxClaims()
                            + " §7slots on this server");
                    if (claims.config().claimsCostEnabled()) {
                        double cost = claims.config().claimsCostAmount();
                        String costStr = Math.rint(cost) == cost
                                ? String.format("%.0f", cost) : String.format("%.2f", cost);
                        player.sendMessage("§aClaim cost: §f$" + costStr + " §7each (YaP economy)");
                    }
                    if (claims.config().claimsUseClaimBlocks()) {
                        int b = claims.repo().getBlocks(player.getUniqueId(),
                                claims.config().claimsStartingBlocks());
                        player.sendMessage("§aClaim blocks: §f" + b);
                    }
                    yield true;
                }
                case "list" -> {
                    menus.openClaims(player);
                    yield true;
                }
                case "show", "borders", "view", "toggle" -> {
                    boolean on = claims.toggleBorderView(player.getUniqueId());
                    if (on) {
                        player.sendMessage("§aClaim borders ON §7— lime particles on your claims nearby. Toggle off with §f/claim show§7.");
                        List<Claim> near = claims.ownedNear(player, 96);
                        if (near.isEmpty()) {
                            player.sendMessage("§7No owned claims nearby to outline.");
                        } else {
                            ClaimVisualizer.drawFrame(player, near);
                        }
                    } else {
                        player.sendMessage("§7Claim borders OFF.");
                    }
                    yield true;
                }
                case "subdivide", "sub" -> {
                    claims.setMode(player.getUniqueId(), ClaimService.SelectMode.SUBDIVIDE);
                    player.sendMessage("§aSubdivision mode ON. Mark two corners inside your claim with the shovel.");
                    yield true;
                }
                case "mode" -> {
                    claims.setMode(player.getUniqueId(), ClaimService.SelectMode.CLAIM);
                    player.sendMessage("§aClaim mode (top-level) ON.");
                    yield true;
                }
                case "tax" -> {
                    if (taxes == null) {
                        player.sendMessage("§cClaim tax is disabled (economy or claims.tax off).");
                    } else {
                        player.sendMessage(taxes.status(player));
                    }
                    yield true;
                }
                case "paytax" -> {
                    if (taxes == null) {
                        player.sendMessage("§cClaim tax is disabled (economy or claims.tax off).");
                    } else {
                        player.sendMessage(taxes.payTax(player));
                    }
                    yield true;
                }
                case "here", "info" -> {
                    var opt = claims.getAt(player.getLocation());
                    if (opt.isEmpty()) {
                        player.sendMessage("§7Wilderness.");
                    } else {
                        Claim c = opt.get();
                        String kind = c.isSubdivision() ? "subdivision" : "claim";
                        player.sendMessage("§a" + kind + " §f#" + c.id()
                                + (c.isSubdivision() ? " §7parent §f#" + c.parentId() : "")
                                + " §7area §f" + c.area()
                                + (c.taxFrozen() ? " §c[TAX FROZEN]" : "")
                                + (c.isSubdivision() ? "" : " §7tax §f$" + String.format("%.2f", c.taxDue())));
                        ClaimVisualizer.show(plugin, player, c, claims.config().claimsVisualSeconds());
                    }
                    yield true;
                }
                case "abandon" -> {
                    var opt = claims.getAt(player.getLocation());
                    if (opt.isEmpty()) {
                        player.sendMessage("§cStand in a claim you own.");
                        yield true;
                    }
                    if (claims.abandon(player, opt.get())) {
                        player.sendMessage("§aAbandoned claim §f#" + opt.get().id());
                    } else {
                        player.sendMessage("§cCannot abandon.");
                    }
                    yield true;
                }
                case "trust" -> {
                    if (args.length < 2) {
                        player.sendMessage("Usage: /claim trust <player> [access|build|manage]");
                        yield true;
                    }
                    yield trust(player, args, true);
                }
                case "untrust" -> {
                    if (args.length < 2) {
                        player.sendMessage("Usage: /claim untrust <player>");
                        yield true;
                    }
                    yield trust(player, args, false);
                }
                case "reload" -> {
                    if (!player.hasPermission("yapdata.admin") && !player.hasPermission("yapdata.claims.admin")) {
                        YapMessages.noPermission(player, "yapdata.claims.admin");
                        yield true;
                    }
                    claims.reloadLocal();
                    YapMessages.reloaded(player, "Claims");
                    yield true;
                }
                case "flag" -> {
                    yield setClaimFlag(player, args);
                }
                case "message" -> {
                    yield setClaimMessage(player, args);
                }
                default -> {
                    player.sendMessage("Usage: /claim [claim|tool|show|slots|list|subdivide|here|abandon|trust|flag|message]");
                    yield true;
                }
            };
        } catch (Exception e) {
            YapMessages.commandFailed(player, e);
            return true;
        }
    }

    private boolean setClaimFlag(Player player, String[] args) throws Exception {
        if (args.length < 4 || !"set".equalsIgnoreCase(args[1])) {
            player.sendMessage("Usage: /claim flag set <flag> <allow|deny>");
            return true;
        }
        var opt = claims.getAt(player.getLocation());
        if (opt.isEmpty()) {
            player.sendMessage("§cStand in your claim.");
            return true;
        }
        Claim c = opt.get();
        if (!c.owner().equals(player.getUniqueId()) && !player.hasPermission("yapdata.claims.admin")
                && !claims.hasTrust(c, player.getUniqueId(), ClaimRepository.TrustLevel.MANAGE)) {
            player.sendMessage("§cNo manage permission.");
            return true;
        }
        RegionFlag flag = RegionFlag.parse(args[2]).orElse(null);
        if (flag == null) {
            player.sendMessage("§cUnknown flag.");
            return true;
        }
        FlagValue value = FlagValue.parse(args[3]);
        claims.flags().setFlag(c.id(), flag, value);
        player.sendMessage("§aSet §f" + flag.name() + " §a→ §f" + value.name() + " §aon claim #" + c.id());
        return true;
    }

    private boolean setClaimMessage(Player player, String[] args) throws Exception {
        if (args.length < 3) {
            player.sendMessage("Usage: /claim message set greeting|farewell <text>");
            player.sendMessage("Usage: /claim message clear greeting|farewell");
            return true;
        }
        var opt = claims.getAt(player.getLocation());
        if (opt.isEmpty()) {
            player.sendMessage("§cStand in your claim.");
            return true;
        }
        Claim c = opt.get();
        if (!c.owner().equals(player.getUniqueId()) && !player.hasPermission("yapdata.claims.admin")
                && !claims.hasTrust(c, player.getUniqueId(), ClaimRepository.TrustLevel.MANAGE)) {
            player.sendMessage("§cNo manage permission.");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("set".equals(action)) {
            if (args.length < 4) {
                player.sendMessage("Usage: /claim message set greeting|farewell <text>");
                return true;
            }
            RegionMessageKind kind = RegionMessageKind.parse(args[2]).orElse(null);
            if (kind == null) {
                player.sendMessage("§cKind must be greeting or farewell.");
                return true;
            }
            String text = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
            claims.setMessage(c.id(), kind, text);
            player.sendMessage("§aSet §f" + kind.name().toLowerCase(Locale.ROOT)
                    + " §amessage on claim #" + c.id());
            return true;
        }
        if ("clear".equals(action)) {
            RegionMessageKind kind = RegionMessageKind.parse(args[2]).orElse(null);
            if (kind == null) {
                player.sendMessage("§cKind must be greeting or farewell.");
                return true;
            }
            claims.clearMessage(c.id(), kind);
            player.sendMessage("§aCleared §f" + kind.name().toLowerCase(Locale.ROOT)
                    + " §amessage on claim #" + c.id());
            return true;
        }
        player.sendMessage("Usage: /claim message set|clear ...");
        return true;
    }

    private boolean trust(Player player, String[] args, boolean add) throws Exception {
        var opt = claims.getAt(player.getLocation());
        if (opt.isEmpty()) {
            player.sendMessage("§cStand in your claim.");
            return true;
        }
        Claim c = opt.get();
        if (!c.owner().equals(player.getUniqueId()) && !player.hasPermission("yapdata.claims.admin")
                && !claims.hasTrust(c, player.getUniqueId(), ClaimRepository.TrustLevel.MANAGE)) {
            player.sendMessage("§cNo manage permission.");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId() == null) {
            player.sendMessage("§cUnknown player.");
            return true;
        }
        if (!add) {
            claims.repo().removeTrust(c.id(), target.getUniqueId());
            claims.invalidateTrust(c.id());
            player.sendMessage("§aUntrusted §f" + args[1]);
            return true;
        }
        ClaimRepository.TrustLevel level = ClaimRepository.TrustLevel.BUILD;
        if (args.length >= 3) {
            level = ClaimRepository.TrustLevel.parse(args[2]);
        }
        claims.repo().setTrust(c.id(), target.getUniqueId(), level);
        claims.invalidateTrust(c.id());
        player.sendMessage("§aTrusted §f" + args[1] + " §aas §f" + level
                + (c.isSubdivision() ? " §7(on subdivision)" : ""));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], "claim", "chunk", "tool", "show", "borders", "slots", "blocks", "list", "subdivide", "mode",
                    "tax", "paytax", "here", "abandon", "trust", "untrust", "flag", "message");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("message")) {
            return filter(args[1], "set", "clear");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("message")) {
            return filter(args[2], "greeting", "farewell");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("trust")) {
            return filter(args[2], "access", "build", "manage");
        }
        return List.of();
    }

    private static List<String> filter(String prefix, String... opts) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : opts) {
            if (o.startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
