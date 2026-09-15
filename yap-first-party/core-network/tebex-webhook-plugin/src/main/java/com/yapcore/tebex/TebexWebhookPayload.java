package com.yapcore.tebex;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Parse Tebex webhook envelope + payment.completed subject fields. */
public final class TebexWebhookPayload {

    public record Parsed(
            String id,
            String type,
            String transactionId,
            String username,
            List<Product> products
    ) {
    }

    public record Product(String id, String username) {
    }

    private TebexWebhookPayload() {
    }

    public static Parsed parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String id = text(root, "id");
        String type = text(root, "type");
        String transactionId = "";
        String username = "";
        List<Product> products = new ArrayList<>();
        if (root.has("subject") && root.get("subject").isJsonObject()) {
            JsonObject subject = root.getAsJsonObject("subject");
            transactionId = text(subject, "transaction_id");
            username = nestedUsername(subject, "customer");
            if (subject.has("products") && subject.get("products").isJsonArray()) {
                JsonArray arr = subject.getAsJsonArray("products");
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    JsonObject p = el.getAsJsonObject();
                    String pkgId = productId(p);
                    if (pkgId.isBlank()) {
                        continue;
                    }
                    String productUser = nestedUsername(p, null);
                    if (productUser.isBlank()) {
                        productUser = username;
                    }
                    products.add(new Product(pkgId, productUser));
                }
            }
        }
        return new Parsed(id, type, transactionId, username, List.copyOf(products));
    }

    public static String validationResponseJson(String id) {
        String safe = id == null ? "" : id.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"id\":\"" + safe + "\"}";
    }

    private static String nestedUsername(JsonObject parent, String childKey) {
        JsonObject scope = parent;
        if (childKey != null) {
            if (!parent.has(childKey) || !parent.get(childKey).isJsonObject()) {
                return "";
            }
            scope = parent.getAsJsonObject(childKey);
        }
        if (!scope.has("username")) {
            return "";
        }
        JsonElement u = scope.get("username");
        if (u.isJsonObject()) {
            return text(u.getAsJsonObject(), "username");
        }
        if (u.isJsonPrimitive()) {
            return u.getAsString();
        }
        return "";
    }

    private static String productId(JsonObject product) {
        if (!product.has("id") || product.get("id").isJsonNull()) {
            return "";
        }
        JsonElement id = product.get("id");
        if (id.isJsonPrimitive()) {
            if (id.getAsJsonPrimitive().isNumber()) {
                return String.valueOf(id.getAsLong());
            }
            return id.getAsString().trim();
        }
        return "";
    }

    private static String text(JsonObject obj, String key) {
        return Optional.ofNullable(obj.get(key))
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsString)
                .orElse("");
    }
}
