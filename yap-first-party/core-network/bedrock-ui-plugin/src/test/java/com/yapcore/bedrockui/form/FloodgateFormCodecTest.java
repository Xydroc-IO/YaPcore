package com.yapcore.bedrockui.form;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloodgateFormCodecTest {

    @Test
    void simpleJsonRoundTripWire() {
        String json = FloodgateFormCodec.simpleJson("Title", "Hello", "A", "B");
        assertTrue(json.contains("\"type\":\"form\""));
        assertTrue(json.contains("\"title\":\"Title\""));
        assertTrue(json.contains("\"buttons\":[{\"text\":\"A\"},{\"text\":\"B\"}]"));

        short formId = 42;
        byte[] outbound = FloodgateFormCodec.encodeOutbound(FloodgateFormCodec.TYPE_SIMPLE, formId, json);
        assertEquals(FloodgateFormCodec.TYPE_SIMPLE, outbound[0]);
        assertEquals(formId, FloodgateFormCodec.readFormId(new byte[]{outbound[1], outbound[2]}));
        String decodedJson = new String(outbound, 3, outbound.length - 3, StandardCharsets.UTF_8);
        assertEquals(json, decodedJson);

        // Simulate button-0 response
        byte[] inbound = response(formId, "0");
        FloodgateFormCodec.InboundResponse resp = FloodgateFormCodec.decodeInbound(inbound);
        assertEquals(formId, resp.formId());
        assertEquals("0", resp.rawData());
        assertFalse(resp.closed());
    }

    @Test
    void modalAndCustomJson() {
        String modal = FloodgateFormCodec.modalJson("M", "C", "Yes", "No");
        assertTrue(modal.contains("\"type\":\"modal\""));
        assertTrue(modal.contains("\"button1\":\"Yes\""));
        assertTrue(modal.contains("\"button2\":\"No\""));

        String custom = FloodgateFormCodec.customJson("Cfg", "[{\"type\":\"label\",\"text\":\"Hi\"}]");
        assertTrue(custom.contains("\"type\":\"custom_form\""));
        assertTrue(custom.contains("\"content\":[{\"type\":\"label\",\"text\":\"Hi\"}]"));

        byte[] out = FloodgateFormCodec.encodeOutbound(FloodgateFormCodec.TYPE_CUSTOM, (short) 7, custom);
        assertEquals(FloodgateFormCodec.TYPE_CUSTOM, out[0]);
        assertEquals(7, FloodgateFormCodec.readFormId(new byte[]{out[1], out[2]}));
    }

    @Test
    void closeResponseVariants() {
        short id = 99;
        FloodgateFormCodec.InboundResponse onlyId = FloodgateFormCodec.decodeInbound(new byte[]{
                (byte) ((id >> 8) & 0xFF), (byte) (id & 0xFF)
        });
        assertTrue(onlyId.closed());
        assertEquals("null", onlyId.rawData());

        FloodgateFormCodec.InboundResponse empty = FloodgateFormCodec.decodeInbound(response(id, ""));
        assertTrue(empty.closed());

        FloodgateFormCodec.InboundResponse nullLit = FloodgateFormCodec.decodeInbound(response(id, "null"));
        assertTrue(nullLit.closed());
    }

    @Test
    void escapesQuotesInJson() {
        String json = FloodgateFormCodec.simpleJson("T\"x", "C\\y", "B\"1");
        assertTrue(json.contains("\\\""));
        assertTrue(json.contains("\\\\"));
    }

    @Test
    void rejectsShortInbound() {
        assertThrows(IllegalArgumentException.class, () -> FloodgateFormCodec.decodeInbound(new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> FloodgateFormCodec.decodeInbound(null));
    }

    private static byte[] response(short formId, String body) {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[2 + raw.length];
        data[0] = (byte) ((formId >> 8) & 0xFF);
        data[1] = (byte) (formId & 0xFF);
        System.arraycopy(raw, 0, data, 2, raw.length);
        return data;
    }
}
