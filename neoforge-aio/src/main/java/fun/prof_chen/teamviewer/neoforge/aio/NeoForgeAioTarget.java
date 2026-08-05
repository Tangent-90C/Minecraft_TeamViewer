package fun.prof_chen.teamviewer.neoforge.aio;

import java.util.List;

record NeoForgeAioTarget(
        String minecraft,
        String minecraftRange,
        String neoForgeRange,
        String adapter,
        int javaRelease,
        String entrypoint,
        String factory,
        List<String> mixins
) { }
