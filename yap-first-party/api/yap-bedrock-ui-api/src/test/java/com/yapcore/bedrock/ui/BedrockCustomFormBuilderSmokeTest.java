package com.yapcore.bedrock.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockCustomFormBuilderSmokeTest {

    @AfterEach
    void clear() {
        BedrockUiBackend.clear();
    }

    @Test
    void builderEmitsContentAndOpens() {
        AtomicInteger opens = new AtomicInteger();
        AtomicReference<String> json = new AtomicReference<>();
        BedrockUiService stub = new BedrockUiService() {
            @Override
            public boolean isBedrock(org.bukkit.entity.Player player) {
                return true;
            }

            @Override
            public boolean hasNativeSession(org.bukkit.entity.Player player) {
                return true;
            }

            @Override
            public void sendActionBar(org.bukkit.entity.Player player, String text) {
            }

            @Override
            public void updateSidebar(org.bukkit.entity.Player player, String objectiveId, String title,
                                      List<String> lines) {
            }

            @Override
            public int sendSimpleForm(org.bukkit.entity.Player player, String title, String content,
                                      Consumer<BedrockFormResult> onResult, String... buttons) {
                return -1;
            }

            @Override
            public int sendModalForm(org.bukkit.entity.Player player, String title, String content,
                                     String button1, String button2, Consumer<BedrockFormResult> onResult) {
                return -1;
            }

            @Override
            public int sendCustomForm(org.bukkit.entity.Player player, String title, String jsonContentArray,
                                      Consumer<BedrockFormResult> onResult) {
                json.set(jsonContentArray);
                opens.incrementAndGet();
                return 42;
            }
        };
        BedrockUiBackend.install(stub);

        int id = stub.customForm(null, "Settings")
                .label("Hello")
                .input("Name", "enter", "")
                .toggle("Fly", false)
                .slider("Speed", 0, 10, 1, 5)
                .dropdown("Mode", "A", "B")
                .onResult(r -> {
                })
                .open();

        assertEquals(42, id);
        assertEquals(1, opens.get());
        String content = json.get();
        assertTrue(content.contains("\"type\":\"label\""));
        assertTrue(content.contains("\"type\":\"input\""));
        assertTrue(content.contains("\"type\":\"toggle\""));
        assertTrue(content.contains("\"type\":\"slider\""));
        assertTrue(content.contains("\"type\":\"dropdown\""));
    }
}
