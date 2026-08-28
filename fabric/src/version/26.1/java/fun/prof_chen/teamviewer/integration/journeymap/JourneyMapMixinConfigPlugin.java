package fun.prof_chen.teamviewer.integration.journeymap;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

/** Applies the optional renderer hooks only when JourneyMap exposes the tested bytecode shape. */
public final class JourneyMapMixinConfigPlugin implements IMixinConfigPlugin {
    private static final String BEACON_HANDLER = "journeymap.client.event.handlers.WaypointBeaconHandler";
    private static final String UI_MANAGER = "journeymap.client.ui.UIManager";
    private static final String WAYPOINT_RENDERER = "journeymap.client.render.ingame.WaypointRenderer";
    private static final String BEACON_RENDERER = "journeymap.client.render.ingame.WaypointBeaconRenderer";
    private static final String DECORATION_RENDERER = "journeymap.client.render.ingame.WaypointDecorationRenderer";
    private static final String DRAW_WAYPOINT_STEP = "journeymap.client.render.draw.DrawWayPointStep";
    private boolean compatible;

    @Override
    public void onLoad(String mixinPackage) {
        compatible = FabricLoader.getInstance().getModContainer("minecraft")
                .map(container -> "26.1.2".equals(container.getMetadata().getVersion().getFriendlyString()))
                .orElse(false)
                && FabricLoader.getInstance().isModLoaded("journeymap")
                && hasSupportedShape();
        JourneyMapGroupPolicyBridge.setSupported(compatible);
    }

    private boolean hasSupportedShape() {
        try {
            return hasMethodWithFields(BEACON_HANDLER, "onRenderWaypoints",
                    "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lcom/mojang/blaze3d/vertex/PoseStack;Z)V",
                    "renderWaypointsWorld")
                    && hasMethodWithFields(UI_MANAGER, "drawWaypointDecorations",
                    "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    "renderWaypointsWorld")
                    && hasMethodWithFields(WAYPOINT_RENDERER, "renderWaypoint",
                    "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljourneymap/client/waypoint/ClientWaypointImpl;"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;Ljourneymap/client/render/draw/DrawStep$Pass;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;)V", "maxDistance")
                    && hasMethodWithFields(WAYPOINT_RENDERER, "canDrawWaypoint",
                    "(Ljourneymap/client/waypoint/ClientWaypointImpl;Ljava/lang/String;)Z", "beaconEnabled")
                    && hasMethodWithFields(BEACON_RENDERER, "render",
                    "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
                    "showRotatingBeam", "showStaticBeam")
                    && hasMethodWithFields(BEACON_RENDERER, "render",
                    "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Ljourneymap/client/render/draw/DrawStep$Pass;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                            + "Ljourneymap/client/waypoint/ClientWaypointImpl;FJ[FFDDDLnet/minecraft/world/phys/Vec3;"
                            + "Lnet/minecraft/world/phys/Vec3;DDD)V", "showRotatingBeam", "showStaticBeam")
                    && hasMethodWithFields(DECORATION_RENDERER, "waypointsToDraw",
                    "(Ljava/util/Collection;)Ljava/util/List;", "showRotatingBeam", "showStaticBeam")
                    && hasMethodWithFields(DRAW_WAYPOINT_STEP, "drawOffscreen",
                    "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/awt/geom/Point2D;D)V");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasMethodWithFields(String className, String name, String descriptor, String... fields)
            throws Exception {
        ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(className, false);
        MethodNode method = node.methods.stream()
                .filter(value -> name.equals(value.name) && descriptor.equals(value.desc))
                .findFirst().orElse(null);
        if (method == null) return false;
        Set<String> required = Set.of(fields);
        java.util.HashSet<String> found = new java.util.HashSet<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && required.contains(field.name)) found.add(field.name);
        }
        return found.containsAll(required);
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return compatible; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
