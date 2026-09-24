package com.yapcore.tailor;

/** Pull {@code geometryData} string field from Bedrock-canonical JSON. */
final class PresenceGeometryParse {

    private PresenceGeometryParse() {
    }

    static String extractGeometryData(String bedrockCanonicalJson) {
        if (bedrockCanonicalJson == null || bedrockCanonicalJson.isBlank()) {
            return "";
        }
        String key = "\"geometryData\"";
        int idx = bedrockCanonicalJson.indexOf(key);
        if (idx < 0) {
            return "";
        }
        int colon = bedrockCanonicalJson.indexOf(':', idx + key.length());
        if (colon < 0) {
            return "";
        }
        int i = colon + 1;
        while (i < bedrockCanonicalJson.length() && Character.isWhitespace(bedrockCanonicalJson.charAt(i))) {
            i++;
        }
        if (i >= bedrockCanonicalJson.length() || bedrockCanonicalJson.charAt(i) != '"') {
            return "";
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < bedrockCanonicalJson.length()) {
            char c = bedrockCanonicalJson.charAt(i++);
            if (c == '\\') {
                if (i >= bedrockCanonicalJson.length()) {
                    break;
                }
                char n = bedrockCanonicalJson.charAt(i++);
                switch (n) {
                    case '"', '\\', '/' -> sb.append(n);
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    default -> {
                        sb.append('\\').append(n);
                    }
                }
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
