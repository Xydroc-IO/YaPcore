package com.yapcore.bedrockui.service;

import com.yapcore.bedrock.ui.BedrockFormResult;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Soft bridge to YaPcore {@code FormService} + {@code BedrockUiBridge} when the dual-stack
 * gateway runs in the same JVM (native Bedrock sessions).
 */
final class ChassisFormBridge {

    private static final Logger LOG = Logger.getLogger("YaP.BedrockUI");

    private Object formService;
    private Object uiBridge;
    private Object sessions;
    private Method sendSimple;
    private Method sendCustom;
    private Method sendModal;
    private Method pushActionBar;
    private Method pushSidebar;
    private Method sessionByName;
    private boolean resolved;

    boolean hasSession(String username) {
        resolve();
        if (sessionByName == null || sessions == null) {
            return false;
        }
        try {
            return sessionByName.invoke(sessions, username) != null;
        } catch (Exception e) {
            return false;
        }
    }

    boolean pushActionBar(String username, String text) {
        resolve();
        if (pushActionBar == null || uiBridge == null || !hasSession(username)) {
            return false;
        }
        try {
            pushActionBar.invoke(uiBridge, username, text);
            return true;
        } catch (Exception e) {
            LOG.fine("pushActionBar failed: " + e.getMessage());
            return false;
        }
    }

    boolean pushSidebar(String username, String objectiveId, String title, List<String> lines) {
        resolve();
        if (pushSidebar == null || uiBridge == null || !hasSession(username)) {
            return false;
        }
        try {
            pushSidebar.invoke(uiBridge, username, objectiveId, title, lines);
            return true;
        } catch (Exception e) {
            LOG.fine("pushSidebar failed: " + e.getMessage());
            return false;
        }
    }

    int sendSimple(String username, String title, String content,
                   Consumer<BedrockFormResult> onResult, String... buttons) {
        resolve();
        if (sendSimple == null || formService == null || !hasSession(username)) {
            return -1;
        }
        try {
            Object handler = onResult == null ? null : (Consumer<Object>) result ->
                    onResult.accept(mapResult(result));
            Object id = sendSimple.invoke(formService, username, title, content, handler, (Object) buttons);
            return id instanceof Integer i ? i : -1;
        } catch (Exception e) {
            LOG.fine("sendSimple failed: " + e.getMessage());
            return -1;
        }
    }

    int sendCustom(String username, String title, String json,
                   Consumer<BedrockFormResult> onResult) {
        resolve();
        if (sendCustom == null || formService == null || !hasSession(username)) {
            return -1;
        }
        try {
            Object handler = onResult == null ? null : (Consumer<Object>) result ->
                    onResult.accept(mapResult(result));
            Object id = sendCustom.invoke(formService, username, title, json, handler);
            return id instanceof Integer i ? i : -1;
        } catch (Exception e) {
            LOG.fine("sendCustom failed: " + e.getMessage());
            return -1;
        }
    }

    int sendModal(String username, String title, String content, String button1, String button2,
                  Consumer<BedrockFormResult> onResult) {
        resolve();
        if (sendModal == null || formService == null || !hasSession(username)) {
            return -1;
        }
        try {
            Object handler = onResult == null ? null : (Consumer<Object>) result ->
                    onResult.accept(mapResult(result));
            Object id = sendModal.invoke(formService, username, title, content, button1, button2, handler);
            return id instanceof Integer i ? i : -1;
        } catch (Exception e) {
            LOG.fine("sendModal failed: " + e.getMessage());
            return -1;
        }
    }

    private synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> gatewayClass = Class.forName("com.yapcore.protocol.DualStackGateway");
            Object gateway = findGatewayInstance(gatewayClass);
            if (gateway == null) {
                return;
            }
            formService = gatewayClass.getMethod("formService").invoke(gateway);
            sessions = gatewayClass.getMethod("bedrockSessions").invoke(gateway);
            Object bedrockBridge = gatewayClass.getMethod("bedrockBridge").invoke(gateway);
            uiBridge = bedrockBridge.getClass().getMethod("ui").invoke(bedrockBridge);
            Class<?> formClass = Class.forName("com.yapcore.crossplay.form.FormService");
            sendSimple = formClass.getMethod(
                    "sendSimple", String.class, String.class, String.class, Consumer.class, String[].class);
            sendCustom = formClass.getMethod(
                    "sendCustom", String.class, String.class, String.class, Consumer.class);
            sendModal = formClass.getMethod(
                    "sendModal", String.class, String.class, String.class, String.class, String.class, Consumer.class);
            Class<?> uiClass = Class.forName("com.yapcore.crossplay.bedrock.bridge.BedrockUiBridge");
            pushActionBar = uiClass.getMethod("pushActionBar", String.class, String.class);
            pushSidebar = uiClass.getMethod("pushSidebar", String.class, String.class, String.class, List.class);
            Class<?> sessionsClass = Class.forName("com.yapcore.crossplay.bedrock.BedrockSessionManager");
            sessionByName = sessionsClass.getMethod("byUsername", String.class);
        } catch (Exception e) {
            LOG.fine("Chassis Bedrock UI not available: " + e.getMessage());
        }
    }

    private static Object findGatewayInstance(Class<?> gatewayClass) {
        try {
            Class<?> holderClass = Class.forName("com.yapcore.crossplay.bedrock.BedrockUiGatewayHolder");
            Method get = holderClass.getMethod("gateway");
            return get.invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BedrockFormResult mapResult(Object result) {
        try {
            Class<?> resultClass = result.getClass();
            int formId = (int) resultClass.getMethod("formId").invoke(result);
            String username = (String) resultClass.getMethod("username").invoke(result);
            String raw = (String) resultClass.getMethod("rawData").invoke(result);
            boolean closed = (boolean) resultClass.getMethod("closed").invoke(result);
            return new BedrockFormResult(formId, username, raw, closed);
        } catch (Exception e) {
            return new BedrockFormResult(-1, "", null, true);
        }
    }
}
