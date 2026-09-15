package com.yapcore.tebex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TebexWebhookPayloadTest {

    @Test
    void validationResponseEchoesId() {
        String json = TebexWebhookPayload.validationResponseJson("2c116b11-1110-91e0-b266-b792c8da5f11");
        assertEquals("{\"id\":\"2c116b11-1110-91e0-b266-b792c8da5f11\"}", json);
    }

    @Test
    void parsesValidationWebhook() {
        String raw = """
                {"id":"2c116b11-1110-91e0-b266-b792c8da5f11","type":"validation.webhook","date":"2021-08-24T12:21:47+00:00","subject":{}}
                """;
        TebexWebhookPayload.Parsed p = TebexWebhookPayload.parse(raw);
        assertEquals("validation.webhook", p.type());
        assertEquals("2c116b11-1110-91e0-b266-b792c8da5f11", p.id());
        assertTrue(p.products().isEmpty());
    }

    @Test
    void parsesPaymentCompletedProductsAndUsername() {
        String raw = """
                {
                  "id": "wh-1",
                  "type": "payment.completed",
                  "subject": {
                    "transaction_id": "tbx-abc",
                    "customer": {
                      "username": { "id": "1", "username": "Steve" }
                    },
                    "products": [
                      { "id": 12345, "name": "VIP", "username": { "id": "1", "username": "Steve" } },
                      { "id": "67890", "name": "Kit" }
                    ]
                  }
                }
                """;
        TebexWebhookPayload.Parsed p = TebexWebhookPayload.parse(raw);
        assertEquals("payment.completed", p.type());
        assertEquals("tbx-abc", p.transactionId());
        assertEquals("Steve", p.username());
        assertEquals(2, p.products().size());
        assertEquals("12345", p.products().get(0).id());
        assertEquals("Steve", p.products().get(0).username());
        assertEquals("67890", p.products().get(1).id());
        assertEquals("Steve", p.products().get(1).username());
    }
}
