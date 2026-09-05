package com.yapcore.bedrockui.service;

import com.yapcore.bedrock.ui.BedrockFormResult;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Thin adapter to chassis {@code BedrockUiGatewayHolder} + {@code FormService}.
 * Uses a single reflective resolve of the gateway holder (chassis is not a compile
 * dependency of this plugin); subsequent calls use cached Method handles.
 */
final class ChassisFormAdapter {

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
            Object handler = wrap(onResult);
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
            Object handler = wrap(onResult);
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
            Object handler = wrap(onResult);
            Object id = sendModal.invoke(formService, username, title, content, button1, button2, handler);
            return id instanceof Integer i ? i : -1;
        } catch (Exception e) {
            LOG.fine("sendModal failed: " + e.getMessage());
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object wrap(Consumer<BedrockFormResult> onResult) {
        if (onResult == null) {
            return null;
        }
        return (Consumer<Object>) result -> onResult.accept(mapResult(result));
    }

    private synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            // Prefer BedrockUiGatewayHolder (stable plugin-facing entry) over DualStackGateway scan.
            Class<?> holderClass = Class.forName("com.yapcore.crossplay.bedrock.BedrockUiGatewayHolder");
            Object gateway = holderClass.getMethod("gateway").invoke(null);
            if (gateway == null) {
                return;
            }
            Class<?> gatewayClass = gateway.getClass();
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
