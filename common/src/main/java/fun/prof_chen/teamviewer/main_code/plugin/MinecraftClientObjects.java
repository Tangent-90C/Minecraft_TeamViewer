package fun.prof_chen.teamviewer.main_code.plugin;

/**
 * Version-neutral factory for mapped Minecraft objects needed by integration plugins.
 * Lua must use this service instead of hard-coding development-namespace Minecraft classes.
 */
public interface MinecraftClientObjects {
    String SERVICE_ID = "minecraft.client_objects";

    Object blockPosition(int x, int y, int z);

    Object dimensionKey(String dimensionId);
}
