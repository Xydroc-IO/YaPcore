package com.yapcore.bedrock.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke: {@link BedrockUiServices#find()} resolves the chassis/backend install path
 * when Bukkit ServicesManager is unavailable (unit test without a live server).
 */
class BedrockUiServicesSmokeTest {

    @AfterEach
    void clear() {
        BedrockUiBackend.clear();
    }

    @Test
    void findReturnsInstalledBackendAndSendSimpleForm() {
        AtomicInteger forms = new AtomicInteger();
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
                forms.incrementAndGet();
                return 7;
            }

            @Override
            public int sendModalForm(org.bukkit.entity.Player player, String title, String content,
                                     String button1, String button2, Consumer<BedrockFormResult> onResult) {
                return -1;
            }

            @Override
            public int sendCustomForm(org.bukkit.entity.Player player, String title, String jsonContentArray,
                                      Consumer<BedrockFormResult> onResult) {
                return -1;
            }
        };
        BedrockUiBackend.install(stub);
        Optional<BedrockUiService> found = BedrockUiServices.find();
        assertTrue(found.isPresent());
        assertSame(stub, found.get());
        assertEquals(7, found.get().sendSimpleForm(null, "t", "c", r -> {
        }, "OK"));
        assertEquals(1, forms.get());
    }
}
