package fun.prof_chen.teamviewer.neoforge.adapter.client;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgeTickEventCompat {
    private NeoForgeTickEventCompat() { }

    static void register(Runnable callback) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post ignored) -> callback.run());
    }
}
