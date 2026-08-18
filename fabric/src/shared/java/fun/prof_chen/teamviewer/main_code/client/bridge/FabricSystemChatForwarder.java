package fun.prof_chen.teamviewer.main_code.client.bridge;

import fun.prof_chen.teamviewer.main_code.client.model.SystemChatMessageSnapshot;
import fun.prof_chen.teamviewer.main_code.client.sdk.ClientEventHandler;

import java.lang.reflect.Method;

/** Converts pre-message-API Fabric game packets without linking a version-specific packet accessor. */
public final class FabricSystemChatForwarder {
    private static volatile ClientEventHandler<?, ?> handler;

    private FabricSystemChatForwarder() { }

    public static void setHandler(ClientEventHandler<?, ?> value) {
        handler = value;
    }

    public static void onGameMessage(Object packet) {
        ClientEventHandler<?, ?> current = handler;
        if (current == null || packet == null) return;
        try {
            Object type = first(packet, "getType");
            String typeName = type instanceof Enum<?> value ? value.name() : "";
            if ("CHAT".equals(typeName)) return;

            Object message = first(packet, "content", "getMessage");
            Object text = message == null ? null : invoke(message, "getString");
            if (!(text instanceof String plainText)) return;

            Object overlayValue = first(packet, "overlay");
            boolean overlay = overlayValue instanceof Boolean value
                    ? value : "GAME_INFO".equals(typeName);
            current.onSystemChatMessage(new SystemChatMessageSnapshot(plainText, overlay));
        } catch (ReflectiveOperationException ignored) {
            // Older packet accessors vary by Minecraft version; an unrecognized packet is ignored.
        }
    }

    private static Object first(Object target, String... methods) throws ReflectiveOperationException {
        for (String method : methods) {
            try {
                return invoke(target, method);
            } catch (NoSuchMethodException ignored) {
                // Try the accessor name used by the next Minecraft packet family.
            }
        }
        return null;
    }

    private static Object invoke(Object target, String method) throws ReflectiveOperationException {
        Method accessor = target.getClass().getMethod(method);
        return accessor.invoke(target);
    }
}
