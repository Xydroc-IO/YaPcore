package com.yapcore.bedrock.ui;

/** Result of a Bedrock modal/simple/custom form submission. */
public record BedrockFormResult(int formId, String username, String rawData, boolean closed) {

    public boolean cancelled() {
        return closed || rawData == null || "null".equals(rawData);
    }

    public int buttonIndex() {
        if (cancelled()) {
            return -1;
        }
        try {
            return Integer.parseInt(rawData.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
