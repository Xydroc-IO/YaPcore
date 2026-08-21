package org.bukkit;

public enum ChatColor {
    BLACK('0'), DARK_BLUE('1'), DARK_GREEN('2'), DARK_AQUA('3'),
    DARK_RED('4'), DARK_PURPLE('5'), GOLD('6'), GRAY('7'),
    DARK_GRAY('8'), BLUE('9'), GREEN('a'), AQUA('b'),
    RED('c'), LIGHT_PURPLE('d'), YELLOW('e'), WHITE('f'),
    MAGIC('k'), BOLD('l'), STRIKETHROUGH('m'), UNDERLINE('n'),
    ITALIC('o'), RESET('r');

    public static final char COLOR_CHAR = '\u00A7';
    private final char code;

    ChatColor(char code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return COLOR_CHAR + "" + code;
    }

    public static String translateAlternateColorCodes(char alt, String text) {
        if (text == null) {
            return null;
        }
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == alt && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(chars[i + 1]) > -1) {
                chars[i] = COLOR_CHAR;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    public static String stripColor(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll(COLOR_CHAR + ".", "");
    }
}
