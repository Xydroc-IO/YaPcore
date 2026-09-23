package com.yapcore.tailor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class TailorConfigSkinHostTest {

    @Test
    void normalizeStripsTrailingSlashAndSkinPath() {
        assertEquals("http://cdn.example:8081", TailorConfig.normalizeSkinHostBase("http://cdn.example:8081/"));
        assertEquals("http://cdn.example:8081", TailorConfig.normalizeSkinHostBase("http://cdn.example:8081/skin"));
        assertEquals("http://cdn.example:8081", TailorConfig.normalizeSkinHostBase("http://cdn.example:8081/skin/"));
        assertNull(TailorConfig.normalizeSkinHostBase(""));
        assertNull(TailorConfig.normalizeSkinHostBase(null));
    }

    @Test
    void resolveSkinApplyBaseAlwaysLocalhost() {
        String base = ChassisSkinPush.resolveSkinApplyBase(null);
        assertEquals("http://127.0.0.1:" + ChassisSkinPush.resolvePackHttpPort() + "/skin", base);
    }

    @Test
    void publicSkinUrlDoesNotDoubleSkinSegment() {
        // Shape check matching TailorConfig after normalize
        UUID u = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String host = TailorConfig.normalizeSkinHostBase("http://example.test:8081/skin/");
        assertEquals("http://example.test:8081", host);
        String url = host + "/skin/" + u + ".png";
        assertEquals("http://example.test:8081/skin/11111111-1111-1111-1111-111111111111.png", url);
    }
}
