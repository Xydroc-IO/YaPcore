package com.yapcore.crossplay.floodgate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloodgateAuthLoginParseTest {

    @Test
    void parsesEncapsulatedOfflineChainUsername() {
        // Mimic packet_login body: i32 proto + varint encLen + LittleString identity + LittleString client
        String jwtPayload = base64Url("{\"extraData\":{\"displayName\":\"YapBeAlice\",\"XUID\":\"0\",\"identity\":\"00000000-0000-0000-0000-000000000001\"},\"identityPublicKey\":\"x\"}");
        String jwt = base64Url("{\"alg\":\"ES384\",\"x5u\":\"x\"}") + "." + jwtPayload + "." + base64Url("sig");
        String chain = "{\"chain\":[\"" + jwt + "\"]}";
        byte[] identityBytes = chain.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] clientBytes = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        io.netty.buffer.ByteBuf body = io.netty.buffer.Unpooled.buffer();
        body.writeInt(766); // protocol
        // encapsulated length = 4+identity + 4+client
        int encLen = 4 + identityBytes.length + 4 + clientBytes.length;
        writeVarInt(body, encLen);
        body.writeIntLE(identityBytes.length);
        body.writeBytes(identityBytes);
        body.writeIntLE(clientBytes.length);
        body.writeBytes(clientBytes);

        FloodgateAuth auth = new FloodgateAuth(true);
        FloodgateAuth.Identity id = auth.authenticate(body, "/127.0.0.1:1");
        body.release();
        assertEquals("YapBeAlice", id.username());
        assertFalse(id.linked()); // offline self-signed
        assertTrue(id.protocol() == 766);
    }

    private static String base64Url(String s) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void writeVarInt(io.netty.buffer.ByteBuf out, int value) {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }
}
