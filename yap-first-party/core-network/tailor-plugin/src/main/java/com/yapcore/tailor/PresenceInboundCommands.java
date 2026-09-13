package com.yapcore.tailor;

import org.bukkit.entity.Player;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inbound {@code yap:presence} wardrobe / skin / PNG-upload command handling.
 */
final class PresenceInboundCommands {

    private final PresenceChannel channel;
    private final TailorServiceImpl service;
    private final ConcurrentHashMap<UUID, PngUpload> pngUploads;

    PresenceInboundCommands(
            PresenceChannel channel,
            TailorServiceImpl service,
            ConcurrentHashMap<UUID, PngUpload> pngUploads) {
        this.channel = channel;
        this.service = service;
        this.pngUploads = pngUploads;
    }

    void handleWardrobeCommand(Player player, String text) {
        String[] p = text.split("\\|", -1);
        if (p.length < 2) {
            return;
        }
        String op = p[1].trim().toUpperCase(java.util.Locale.ROOT);
        try {
            switch (op) {
                case "APPLY" -> {
                    if (p.length < 3) {
                        channel.sendUi(player, PresenceUiCodec.uiErr("Missing slot id"));
                        return;
                    }
                    long id = Long.parseLong(p[2].trim());
                    service.applyWardrobeSlot(player, id);
                    channel.sendUi(player, PresenceUiCodec.uiOk("Applied slot " + id));
                    channel.sendWardrobe(player);
                    channel.sendSkin(player);
                }
                case "DELETE" -> {
                    if (p.length < 3) {
                        channel.sendUi(player, PresenceUiCodec.uiErr("Missing slot id"));
                        return;
                    }
                    long id = Long.parseLong(p[2].trim());
                    service.deleteWardrobeSlot(player.getUniqueId(), id);
                    channel.sendUi(player, PresenceUiCodec.uiOk("Deleted slot " + id));
                    channel.sendWardrobe(player);
                }
                case "SAVE" -> {
                    if (p.length < 3) {
                        channel.sendUi(player, PresenceUiCodec.uiErr("Missing name"));
                        return;
                    }
                    String name = PresenceUiCodec.unb64(p[2].trim());
                    if (name.isBlank()) {
                        name = p[2].trim();
                    }
                    WardrobeSlot slot = service.saveWardrobeSlot(player.getUniqueId(), name);
                    channel.sendUi(player, PresenceUiCodec.uiOk("Saved " + slot.name()));
                    channel.sendWardrobe(player);
                }
                case "RENAME" -> {
                    if (p.length < 4) {
                        channel.sendUi(player, PresenceUiCodec.uiErr("Missing rename args"));
                        return;
                    }
                    long id = Long.parseLong(p[2].trim());
                    String name = PresenceUiCodec.unb64(p[3].trim());
                    if (name.isBlank()) {
                        name = p[3].trim();
                    }
                    WardrobeSlot renamed = service.renameSlot(player.getUniqueId(), id, name);
                    channel.sendUi(player, PresenceUiCodec.uiOk("Renamed to " + renamed.name()));
                    channel.sendWardrobe(player);
                }
                default -> channel.sendUi(player, PresenceUiCodec.uiErr("Unknown wardrobe op"));
            }
        } catch (Exception e) {
            channel.sendUi(player, PresenceUiCodec.uiErr(e.getMessage() == null ? "Wardrobe failed" : e.getMessage()));
        }
    }

    void handleSkinCommand(Player player, String text) {
        String[] p = text.split("\\|", -1);
        if (p.length < 2) {
            return;
        }
        String op = p[1].trim().toUpperCase(java.util.Locale.ROOT);
        if (p.length < 3 && !"PNGEND".equals(op) && !"CAPEPNGEND".equals(op) && !"CLEAR".equals(op)) {
            return;
        }
        try {
            switch (op) {
                case "URL" -> {
                    String url = PresenceUiCodec.unb64(p[2].trim());
                    if (url.isBlank()) {
                        channel.sendUi(player, PresenceUiCodec.uiErr("Empty URL"));
                        return;
                    }
                    service.applySkin(player, url);
                    channel.sendUi(player, PresenceUiCodec.uiOk("Skin applied"));
                    channel.sendWardrobe(player);
                    channel.sendSkin(player);
                }
                case "MODEL" -> {
                    boolean slim = "1".equals(PresenceUiCodec.normalizeModelToken(p[2]));
                    service.setModel(player, slim ? SkinModel.SLIM : SkinModel.WIDE);
                    channel.sendUi(player, PresenceUiCodec.uiOk(slim ? "Model slim" : "Model wide"));
                    channel.sendWardrobe(player);
                    channel.sendSkin(player);
                }
                case "CAPE" -> {
                    String cape = p.length >= 3 ? PresenceUiCodec.unb64(p[2].trim()) : "";
                    service.setCape(player, cape.isBlank() ? null : cape);
                    channel.sendUi(player, PresenceUiCodec.uiOk(cape.isBlank() ? "Cape cleared" : "Cape set"));
                    channel.sendWardrobe(player);
                    channel.sendSkin(player);
                }
                case "CLEAR" -> {
                    service.clearSkin(player);
                    channel.sendUi(player, PresenceUiCodec.uiOk("Skin cleared"));
                    channel.sendWardrobe(player);
                    channel.sendSkin(player);
                }
                case "PNG" -> {
                    if (p.length < 3) {
                        channel.sendUi(player, PresenceUiCodec.uiErr("Missing PNG data"));
                        return;
                    }
                    applyPngUpload(player, false, p[2].trim());
                }
                case "CAPEPNG" -> {
                    if (p.length < 3) {
                        channel.sendUi(player, PresenceUiCodec.uiErr("Missing cape PNG data"));
                        return;
                    }
                    applyPngUpload(player, true, p[2].trim());
                }
                case "PNGPART", "CAPEPNGPART" -> handlePngPart(player, op.startsWith("CAPE"), p);
                case "PNGEND", "CAPEPNGEND" -> finishPngUpload(player, op.startsWith("CAPE"));
                default -> channel.sendUi(player, PresenceUiCodec.uiErr("Unknown skin op: " + op));
            }
        } catch (Exception e) {
            channel.sendUi(player, PresenceUiCodec.uiErr(e.getMessage() == null ? "Skin failed" : e.getMessage()));
        }
    }

    private void handlePngPart(Player player, boolean cape, String[] p) {
        // SKIN|PNGPART|<index>|<total>|<b64>
        if (p.length < 5) {
            channel.sendUi(player, PresenceUiCodec.uiErr("Bad PNGPART"));
            return;
        }
        int index;
        int total;
        try {
            index = Integer.parseInt(p[2].trim());
            total = Integer.parseInt(p[3].trim());
        } catch (NumberFormatException e) {
            channel.sendUi(player, PresenceUiCodec.uiErr("Bad PNGPART numbers"));
            return;
        }
        if (total < 1 || total > 256 || index < 0 || index >= total) {
            channel.sendUi(player, PresenceUiCodec.uiErr("PNGPART out of range"));
            return;
        }
        UUID id = player.getUniqueId();
        PngUpload upload = pngUploads.compute(id, (k, existing) -> {
            if (existing == null || existing.cape != cape || existing.totalParts != total) {
                return new PngUpload(cape, total);
            }
            return existing;
        });
        if (upload.parts[index] == null) {
            upload.parts[index] = p[4].trim();
            upload.received++;
        } else {
            upload.parts[index] = p[4].trim();
        }
    }

    private void finishPngUpload(Player player, boolean cape) {
        PngUpload upload = pngUploads.remove(player.getUniqueId());
        if (upload == null || upload.cape != cape) {
            channel.sendUi(player, PresenceUiCodec.uiErr("No PNG upload in progress"));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < upload.totalParts; i++) {
            if (upload.parts[i] == null) {
                channel.sendUi(player, PresenceUiCodec.uiErr("Missing PNG part " + i));
                return;
            }
            sb.append(upload.parts[i]);
        }
        applyPngUpload(player, cape, sb.toString());
    }

    private void applyPngUpload(Player player, boolean cape, String b64) {
        try {
            byte[] png = Base64.getDecoder().decode(b64);
            if (cape) {
                service.applyCapeBytes(player, png);
                channel.sendUi(player, PresenceUiCodec.uiOk("Cape file applied"));
            } else {
                service.applySkinBytes(player, png);
                channel.sendUi(player, PresenceUiCodec.uiOk("Skin file applied"));
            }
            channel.sendWardrobe(player);
            channel.sendSkin(player);
        } catch (IllegalArgumentException e) {
            channel.sendUi(player, PresenceUiCodec.uiErr("Invalid base64 PNG"));
        } catch (Exception e) {
            channel.sendUi(player, PresenceUiCodec.uiErr(e.getMessage() == null ? "PNG apply failed" : e.getMessage()));
        }
    }

    /** In-flight chunked PNG upload state. */
    static final class PngUpload {
        final boolean cape;
        final int totalParts;
        final String[] parts;
        int received;

        PngUpload(boolean cape, int totalParts) {
            this.cape = cape;
            this.totalParts = totalParts;
            this.parts = new String[totalParts];
        }
    }
}
