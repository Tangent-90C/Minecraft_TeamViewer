package fun.prof_chen.teamviewer.main_code.bridge;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Cached compatibility access for the Minecraft 26.1-26.2 client UI API transition.
 *
 * <p>26.2 moved the active screen into {@code Gui}, renamed the camera accessor and moved
 * overlay messages into {@code Hud}. Keeping those four calls here lets the same native
 * adapter binary family cover all 26.1 patch releases while producing a dedicated 26.2 Jar.
 */
public final class MinecraftClientUiCompat {
    private static final Field LEGACY_SCREEN = optionalField(Minecraft.class, "screen");
    private static final Field GUI = requiredField(Minecraft.class, "gui");
    private static final Field GAME_RENDERER = requiredField(Minecraft.class, "gameRenderer");
    private static final Method GUI_SCREEN = optionalMethod(GUI.getType(), "screen");
    private static final Method CLIENT_SET_SCREEN = firstMethod(
            Minecraft.class, new String[]{"setScreen", "setScreenAndShow"}, Screen.class);
    private static final Method GUI_SET_SCREEN = optionalMethod(GUI.getType(), "setScreen", Screen.class);
    private static final Method MAIN_CAMERA = firstMethod(
            GAME_RENDERER.getType(), new String[]{"getMainCamera", "mainCamera"});
    private static final Method GUI_OVERLAY_MESSAGE =
            optionalMethod(GUI.getType(), "setOverlayMessage", Component.class, boolean.class);
    private static final Field HUD = optionalField(GUI.getType(), "hud");
    private static final Method HUD_OVERLAY_MESSAGE = HUD == null ? null
            : optionalMethod(HUD.getType(), "setOverlayMessage", Component.class, boolean.class);

    private MinecraftClientUiCompat() { }

    public static Screen currentScreen(Minecraft client) {
        if (client == null) return null;
        if (LEGACY_SCREEN != null) return (Screen) get(LEGACY_SCREEN, client);
        return (Screen) invoke(required(GUI_SCREEN, "Gui.screen()"), get(GUI, client));
    }

    public static void setScreen(Minecraft client, Screen screen) {
        if (client == null) return;
        if (CLIENT_SET_SCREEN != null) {
            invoke(CLIENT_SET_SCREEN, client, screen);
            return;
        }
        invoke(required(GUI_SET_SCREEN, "Gui.setScreen(Screen)"), get(GUI, client), screen);
    }

    public static Camera mainCamera(Minecraft client) {
        if (client == null) return null;
        return (Camera) invoke(MAIN_CAMERA, get(GAME_RENDERER, client));
    }

    public static void showActionBar(Minecraft client, Component message) {
        if (client == null || message == null) return;
        Object gui = get(GUI, client);
        if (GUI_OVERLAY_MESSAGE != null) {
            invoke(GUI_OVERLAY_MESSAGE, gui, message, false);
            return;
        }
        Object hud = get(required(HUD, "Gui.hud"), gui);
        invoke(required(HUD_OVERLAY_MESSAGE, "Hud.setOverlayMessage(Component, boolean)"),
                hud, message, false);
    }

    private static Field requiredField(Class<?> owner, String name) {
        Field field = optionalField(owner, name);
        return required(field, owner.getName() + "." + name);
    }

    private static Field optionalField(Class<?> owner, String name) {
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static Method firstMethod(Class<?> owner, String[] names, Class<?>... parameters) {
        for (String name : names) {
            Method method = optionalMethod(owner, name, parameters);
            if (method != null) return method;
        }
        throw new IllegalStateException("Missing compatible method on " + owner.getName()
                + ": " + String.join("/", names));
    }

    private static Method optionalMethod(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object get(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot read " + field, error);
        }
    }

    private static Object invoke(Method method, Object owner, Object... arguments) {
        try {
            return method.invoke(owner, arguments);
        } catch (IllegalAccessException | InvocationTargetException error) {
            Throwable cause = error instanceof InvocationTargetException invocation
                    ? invocation.getCause() : error;
            throw new IllegalStateException("Cannot invoke " + method, cause);
        }
    }

    private static <T> T required(T value, String description) {
        if (value == null) throw new IllegalStateException("Missing compatible Minecraft API: " + description);
        return value;
    }
}
