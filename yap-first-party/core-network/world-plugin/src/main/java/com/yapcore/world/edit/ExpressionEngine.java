package com.yapcore.world.edit;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Practical FAWE/WorldEdit expression subset for {@code //generate} and {@code //deform}.
 * Supports: numbers, {@code x y z}, {@code noise}, {@code rand}, {@code + - * /},
 * comparisons {@code > < >= <= ==}, and {@code && ||}, plus unary {@code -}.
 * Variables are normalized 0..1 relative to selection ({@code x,y,z}) plus absolute
 * {@code rx,ry,rz} and height {@code h}.
 */
public final class ExpressionEngine {

    private ExpressionEngine() {
    }

    public static double eval(String expression, double x, double y, double z,
                              double rx, double ry, double rz, double h) {
        if (expression == null || expression.isBlank()) {
            return 0;
        }
        String expr = expression.trim().toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("&&", "&")
                .replace("||", "|");
        try {
            return new Parser(expr, x, y, z, rx, ry, rz, h).parseExpr();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** True when expression evaluates &gt; 0.5 (boolean fill). */
    public static boolean test(String expression, double x, double y, double z,
                               double rx, double ry, double rz, double h) {
        return eval(expression, x, y, z, rx, ry, rz, h) > 0.5;
    }

    private static final class Parser {
        private final String s;
        private int i;
        private final double x, y, z, rx, ry, rz, h;

        Parser(String s, double x, double y, double z, double rx, double ry, double rz, double h) {
            this.s = s;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.h = h;
        }

        double parseExpr() {
            double v = parseOr();
            if (i < s.length()) {
                throw new IllegalArgumentException("trailing at " + i);
            }
            return v;
        }

        private double parseOr() {
            double v = parseAnd();
            while (peek('|')) {
                i++;
                double r = parseAnd();
                v = (v > 0.5 || r > 0.5) ? 1 : 0;
            }
            return v;
        }

        private double parseAnd() {
            double v = parseCmp();
            while (peek('&')) {
                i++;
                double r = parseCmp();
                v = (v > 0.5 && r > 0.5) ? 1 : 0;
            }
            return v;
        }

        private double parseCmp() {
            double v = parseAdd();
            while (true) {
                if (match(">=")) {
                    v = v >= parseAdd() ? 1 : 0;
                } else if (match("<=")) {
                    v = v <= parseAdd() ? 1 : 0;
                } else if (match("==")) {
                    v = Math.abs(v - parseAdd()) < 1e-9 ? 1 : 0;
                } else if (peek('>')) {
                    i++;
                    v = v > parseAdd() ? 1 : 0;
                } else if (peek('<')) {
                    i++;
                    v = v < parseAdd() ? 1 : 0;
                } else {
                    break;
                }
            }
            return v;
        }

        private double parseAdd() {
            double v = parseMul();
            while (true) {
                if (peek('+')) {
                    i++;
                    v += parseMul();
                } else if (peek('-')) {
                    i++;
                    v -= parseMul();
                } else {
                    break;
                }
            }
            return v;
        }

        private double parseMul() {
            double v = parseUnary();
            while (true) {
                if (peek('*')) {
                    i++;
                    v *= parseUnary();
                } else if (peek('/')) {
                    i++;
                    double d = parseUnary();
                    v = d == 0 ? 0 : v / d;
                } else {
                    break;
                }
            }
            return v;
        }

        private double parseUnary() {
            if (peek('-')) {
                i++;
                return -parseUnary();
            }
            if (peek('+')) {
                i++;
                return parseUnary();
            }
            return parsePrimary();
        }

        private double parsePrimary() {
            if (peek('(')) {
                i++;
                double v = parseOr();
                expect(')');
                return v;
            }
            if (Character.isDigit(peekChar()) || peek('.')) {
                return parseNumber();
            }
            if (Character.isLetter(peekChar())) {
                String id = parseIdent();
                return switch (id) {
                    case "x" -> x;
                    case "y" -> y;
                    case "z" -> z;
                    case "rx" -> rx;
                    case "ry" -> ry;
                    case "rz" -> rz;
                    case "h", "height" -> h;
                    case "noise" -> noise(rx * 0.07, ry * 0.07, rz * 0.07);
                    case "rand", "random" -> ThreadLocalRandom.current().nextDouble();
                    case "true" -> 1;
                    case "false" -> 0;
                    default -> 0;
                };
            }
            throw new IllegalArgumentException("bad token at " + i);
        }

        private double parseNumber() {
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
                i++;
            }
            return Double.parseDouble(s.substring(start, i));
        }

        private String parseIdent() {
            int start = i;
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) {
                i++;
            }
            return s.substring(start, i);
        }

        private boolean match(String m) {
            if (s.startsWith(m, i)) {
                i += m.length();
                return true;
            }
            return false;
        }

        private boolean peek(char c) {
            return i < s.length() && s.charAt(i) == c;
        }

        private char peekChar() {
            return i < s.length() ? s.charAt(i) : '\0';
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw new IllegalArgumentException("expected " + c);
            }
            i++;
        }
    }

    /** Simple value noise in [-1, 1]. */
    public static double noise(double x, double y, double z) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        double fx = x - xi;
        double fy = y - yi;
        double fz = z - zi;
        double n000 = hash(xi, yi, zi);
        double n100 = hash(xi + 1, yi, zi);
        double n010 = hash(xi, yi + 1, zi);
        double n110 = hash(xi + 1, yi + 1, zi);
        double n001 = hash(xi, yi, zi + 1);
        double n101 = hash(xi + 1, yi, zi + 1);
        double n011 = hash(xi, yi + 1, zi + 1);
        double n111 = hash(xi + 1, yi + 1, zi + 1);
        double nx00 = lerp(n000, n100, smooth(fx));
        double nx10 = lerp(n010, n110, smooth(fx));
        double nx01 = lerp(n001, n101, smooth(fx));
        double nx11 = lerp(n011, n111, smooth(fx));
        double nxy0 = lerp(nx00, nx10, smooth(fy));
        double nxy1 = lerp(nx01, nx11, smooth(fy));
        return lerp(nxy0, nxy1, smooth(fz));
    }

    private static double hash(int x, int y, int z) {
        int n = x * 374761393 + y * 668265263 + z * 1274126177;
        n = (n ^ (n >> 13)) * 1274126177;
        n = n ^ (n >> 16);
        return ((n & 0xffff) / 32768.0) - 1.0;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }
}
