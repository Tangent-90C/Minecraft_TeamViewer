package fun.prof_chen.teamviewer.main_code.network.bridge;

import net.minecraft.SharedConstants;

final class NeoForgeRuntimeCompat {
    private NeoForgeRuntimeCompat() { }

    static String minecraftVersion() {
        return SharedConstants.getCurrentVersion().name();
    }
}
