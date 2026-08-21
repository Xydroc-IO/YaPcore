package io.papermc.paper.brigadier;

import com.mojang.brigadier.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Helpers bridging Adventure ↔ Brigadier messages. */
public final class PaperBrigadier {

    private PaperBrigadier() {
    }

    public static Message message(Component component) {
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        return () -> plain;
    }

    public static Message message(String legacy) {
        return () -> legacy;
    }
}
