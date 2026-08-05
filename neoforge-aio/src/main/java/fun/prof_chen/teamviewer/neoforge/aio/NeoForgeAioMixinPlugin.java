package fun.prof_chen.teamviewer.neoforge.aio;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Supplies only the selected adapter Mixin, so older ASM never reads newer adapter bytecode. */
public final class NeoForgeAioMixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE = "fun.prof_chen.teamviewer.internal.neoforge.mixin.";

    @Override
    public void onLoad(String mixinPackage) {
        if (NeoForgeAioSelector.isClient()) {
            NeoForgeAioSelector.ensureMixinCompatibility();
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return NeoForgeAioSelector.isClient()
                && NeoForgeAioSelector.currentTarget().mixins().contains(mixinClassName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }

    @Override
    public List<String> getMixins() {
        if (!NeoForgeAioSelector.isClient()) return List.of();
        return NeoForgeAioSelector.currentTarget().mixins().stream()
                .map(name -> {
                    if (!name.startsWith(MIXIN_PACKAGE)) {
                        throw new IllegalStateException("Mixin is outside the AIO namespace: " + name);
                    }
                    return name.substring(MIXIN_PACKAGE.length());
                })
                .toList();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) { }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) { }
}
