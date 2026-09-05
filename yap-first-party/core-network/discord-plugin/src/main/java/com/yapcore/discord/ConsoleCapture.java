package com.yapcore.discord;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs a console command while capturing {@code sendMessage*} output when possible.
 * Must be invoked on the global region ({@code YapSched.global}).
 */
public final class ConsoleCapture {

    private ConsoleCapture() {
    }

    /**
     * @return captured lines joined, or a fallback acknowledgement when nothing was captured
     */
    public static String dispatchCapturing(String command, int maxChars) {
        if (command == null || command.isBlank()) {
            return "Empty command.";
        }
        String cmd = command.trim();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        List<String> lines = new ArrayList<>();
        ConsoleCommandSender real = Bukkit.getConsoleSender();
        CommandSender capture = capturingProxy(real, lines);
        boolean ok;
        try {
            ok = Bukkit.dispatchCommand(capture, cmd);
        } catch (Throwable t) {
            return TextCommandParser.truncate("Command error: " + t.getMessage(), maxChars);
        }
        String joined = String.join("\n", lines).trim();
        if (joined.isBlank()) {
            joined = ok ? "Dispatched: `" + cmd + "`" : "Command failed or unknown: `" + cmd + "`";
        }
        return TextCommandParser.truncate(joined, maxChars);
    }

    private static CommandSender capturingProxy(ConsoleCommandSender real, List<String> lines) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if (name.startsWith("sendMessage") || name.equals("sendRichMessage")
                    || name.equals("sendPlainMessage") || name.equals("sendActionBar")) {
                captureArgs(lines, args);
                return defaultReturn(method);
            }
            try {
                return method.invoke(real, args);
            } catch (ReflectiveOperationException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(cause);
            }
        };
        return (CommandSender) Proxy.newProxyInstance(
                ConsoleCapture.class.getClassLoader(),
                new Class<?>[]{CommandSender.class, ConsoleCommandSender.class},
                handler);
    }

    private static Object defaultReturn(Method method) {
        Class<?> rt = method.getReturnType();
        if (rt == void.class) {
            return null;
        }
        if (rt == boolean.class) {
            return false;
        }
        if (rt == int.class) {
            return 0;
        }
        return null;
    }

    private static void captureArgs(List<String> lines, Object[] args) {
        if (args == null) {
            return;
        }
        for (Object arg : args) {
            if (arg == null || arg instanceof UUID) {
                continue;
            }
            if (arg instanceof String s) {
                if (!s.isBlank()) {
                    lines.add(s);
                }
                continue;
            }
            if (arg instanceof String[] arr) {
                for (String s : arr) {
                    if (s != null && !s.isBlank()) {
                        lines.add(s);
                    }
                }
                continue;
            }
            String plain = adventurePlain(arg);
            if (plain != null && !plain.isBlank()) {
                lines.add(plain);
            }
        }
    }

    private static String adventurePlain(Object component) {
        try {
            Class<?> serializerClass = Class.forName(
                    "net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            Object serializer = serializerClass.getMethod("plainText").invoke(null);
            Object result = serializerClass.getMethod("serialize",
                    Class.forName("net.kyori.adventure.text.Component")).invoke(serializer, component);
            return result == null ? null : result.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
