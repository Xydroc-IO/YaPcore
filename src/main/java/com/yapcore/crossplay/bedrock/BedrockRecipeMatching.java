package com.yapcore.crossplay.bedrock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bukkit/Paper recipe matching helpers shared by {@link BedrockPaperRecipes}.
 */
public final class BedrockRecipeMatching {

    private BedrockRecipeMatching() {
    }

    public static Object matchMaterial(Class<?> matCl, String name) throws Exception {
        Object mat = matCl.getMethod("matchMaterial", String.class).invoke(null, name);
        if (mat != null) {
            return mat;
        }
        return matCl.getMethod("valueOf", String.class).invoke(null,
                name.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
    }

    public static boolean matchesCraftingRecipe(Object recipe, Object[] matrix,
                                                Class<?> matCl, Class<?> stackCl, ClassLoader cl)
            throws Exception {
        String simple = recipe.getClass().getSimpleName();
        if (simple.contains("Shaped")) {
            String[] rows = (String[]) recipe.getClass().getMethod("getShape").invoke(recipe);
            @SuppressWarnings("unchecked")
            java.util.Map<Character, Object> map = (java.util.Map<Character, Object>)
                    recipe.getClass().getMethod("getIngredientMap").invoke(recipe);
            if (rows == null || map == null) {
                return false;
            }
            Object[] expected = new Object[9];
            for (int r = 0; r < Math.min(3, rows.length); r++) {
                String row = rows[r];
                for (int c = 0; c < Math.min(3, row.length()); c++) {
                    char ch = row.charAt(c);
                    expected[r * 3 + c] = toStack(firstChoice(map.get(ch), matCl, stackCl), matCl, stackCl);
                }
            }
            return matrixMatches(expected, matrix);
        }
        if (simple.contains("Shapeless")) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) recipe.getClass().getMethod("getIngredientList").invoke(recipe);
            if (list == null) {
                return false;
            }
            List<Object> remaining = new ArrayList<>();
            for (Object m : matrix) {
                if (m != null) {
                    remaining.add(m);
                }
            }
            if (remaining.size() != list.size()) {
                return false;
            }
            for (Object ing : list) {
                Object need = toStack(firstChoice(ing, matCl, stackCl), matCl, stackCl);
                boolean found = false;
                for (int i = 0; i < remaining.size(); i++) {
                    if (sameType(remaining.get(i), need)) {
                        remaining.remove(i);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return remaining.isEmpty();
        }
        return false;
    }

    public static Object firstChoice(Object ingredient, Class<?> matCl, Class<?> stackCl) throws Exception {
        if (ingredient == null) {
            return null;
        }
        String n = ingredient.getClass().getName();
        if (n.contains("RecipeChoice") || n.contains("MaterialChoice") || n.contains("ExactChoice")) {
            try {
                return ingredient.getClass().getMethod("getItemStack").invoke(ingredient);
            } catch (NoSuchMethodException ignored) {
            }
            try {
                @SuppressWarnings("unchecked")
                List<Object> choices = (List<Object>) ingredient.getClass().getMethod("getChoices").invoke(ingredient);
                if (choices != null && !choices.isEmpty()) {
                    return choices.get(0);
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        return ingredient;
    }

    public static Object toStack(Object matOrStack, Class<?> matCl, Class<?> stackCl) throws Exception {
        if (matOrStack == null) {
            return null;
        }
        if (stackCl.isInstance(matOrStack)) {
            return matOrStack;
        }
        if (matCl.isInstance(matOrStack)) {
            return stackCl.getConstructor(matCl, int.class).newInstance(matOrStack, 1);
        }
        return null;
    }

    public static boolean matrixMatches(Object[] expected, Object[] matrix) throws Exception {
        for (int i = 0; i < 9; i++) {
            Object e = i < expected.length ? expected[i] : null;
            Object m = i < matrix.length ? matrix[i] : null;
            if (e == null && m == null) {
                continue;
            }
            if (e == null || m == null) {
                return false;
            }
            if (!sameType(e, m)) {
                return false;
            }
        }
        return true;
    }

    public static boolean sameType(Object a, Object b) throws Exception {
        if (a == null || b == null) {
            return a == b;
        }
        Object ta = a.getClass().getMethod("getType").invoke(a);
        Object tb = b.getClass().getMethod("getType").invoke(b);
        return ta != null && ta.equals(tb);
    }
}
