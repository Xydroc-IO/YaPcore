package com.yapcore.crossplay.form;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P4.8 forms API. */
class FormServiceTest {

    @Test
    void customBuilderProducesCustomForm() {
        FormService forms = new FormService();
        forms.setSender((user, buf) -> buf.release());
        int id = forms.custom("Steve", "Settings")
                .label("Hello")
                .toggle("Fancy", true)
                .dropdown("Mode", "A", "B")
                .slider("Volume", 0, 100, 1, 50)
                .input("Name", "type here", "")
                .send();
        assertTrue(id > 0);
        assertEquals(1, forms.pendingSnapshot().size());
        assertEquals("custom_form", forms.pendingSnapshot().get(id).type());
    }

    @Test
    void resultHandlerFires() {
        FormService forms = new FormService();
        forms.setSender((user, buf) -> buf.release());
        AtomicReference<FormService.FormResult> got = new AtomicReference<>();
        int id = forms.sendModal("Alex", "T", "C", "Yes", "No", got::set);
        // Simulate response body: formId varint + hasData bool + string
        io.netty.buffer.ByteBuf body = io.netty.buffer.Unpooled.buffer();
        com.yapcore.crossplay.bedrock.BedrockPacketCodec.writeUnsignedVarInt(body, id);
        body.writeBoolean(true);
        com.yapcore.crossplay.bedrock.BedrockPacketCodec.writeString(body, "true");
        forms.handleResponse("Alex", body);
        body.release();
        assertFalse(got.get().cancelled());
        assertEquals("true", got.get().rawData());
    }
}
