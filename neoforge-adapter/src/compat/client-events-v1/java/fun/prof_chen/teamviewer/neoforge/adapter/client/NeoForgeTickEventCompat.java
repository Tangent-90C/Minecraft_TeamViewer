package fun.prof_chen.teamviewer.neoforge.adapter.client;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;

final class NeoForgeTickEventCompat {
    private NeoForgeTickEventCompat() { }

    static void register(Runnable callback) {
        NeoForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) callback.run();
        });
    }
}
