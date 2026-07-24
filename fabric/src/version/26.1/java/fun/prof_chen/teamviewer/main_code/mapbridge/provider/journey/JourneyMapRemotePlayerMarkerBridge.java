package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

import com.mojang.blaze3d.platform.NativeImage;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;
import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.model.MapImage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class JourneyMapRemotePlayerMarkerBridge {
    private static final String PREFIX = "player-marker:";
    private static final Map<String, MarkerOverlay> MARKERS = new ConcurrentHashMap<>();
    private static final NativeImage ICON = createIconImage();

    private JourneyMapRemotePlayerMarkerBridge() {
    }

    static void tick(Map<UUID, RemotePlayerInfo> players, boolean enabled) {
        if (!enabled) {
            clear();
            return;
        }
        if (!JourneyMapClientPlugin.isAvailable()
                || !JourneyMapClientPlugin.clientApi().playerAccepts(
                        JourneyMapClientPlugin.TEAMVIEWER_MOD_ID, DisplayType.Marker)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }
        UUID localId = client.player.getUUID();
        String dimension = client.level.dimension().identifier().toString();
        Set<String> active = ConcurrentHashMap.newKeySet();
        for (RemotePlayerInfo player : players == null ? Collections.<RemotePlayerInfo>emptyList() : players.values()) {
            if (player == null || player.position() == null || player.uuid() == null || player.uuid().equals(localId)
                    || (player.dimension() != null && !player.dimension().equals(dimension))) {
                continue;
            }
            String id = PREFIX + player.uuid();
            active.add(id);
            upsert(id, player, client);
        }
        for (String id : Set.copyOf(MARKERS.keySet())) {
            if (!active.contains(id)) {
                remove(id);
            }
        }
    }

    static void clear() {
        for (String id : Set.copyOf(MARKERS.keySet())) {
            remove(id);
        }
    }

    private static void upsert(String id, RemotePlayerInfo player, Minecraft client) {
        BlockPos position = new BlockPos(
                (int) Math.floor(player.position().x()),
                (int) Math.floor(player.position().y()),
                (int) Math.floor(player.position().z()));
        String name = player.name() == null || player.name().isBlank() ? "Player" : player.name();
        MarkerOverlay marker = MARKERS.get(id);
        boolean changed = false;
        if (marker == null) {
            marker = new MarkerOverlay(JourneyMapClientPlugin.TEAMVIEWER_MOD_ID, position, createIcon());
            marker.setActiveUIs(Context.UI.all());
            marker.setDisplayOrder(1000);
            marker.setOverlayGroupName("TeamViewRelay");
            MARKERS.put(id, marker);
            changed = true;
        }
        if (!Objects.equals(marker.getPoint(), position)) {
            marker.setPoint(position);
            changed = true;
        }
        if (!Objects.equals(marker.getLabel(), name)) {
            marker.setLabel(name);
            marker.setTitle(name);
            changed = true;
        }
        if (!Objects.equals(marker.getDimension(), client.level.dimension())) {
            marker.setDimension(client.level.dimension());
            changed = true;
        }
        if (changed) {
            try {
                JourneyMapClientPlugin.clientApi().show(marker);
            } catch (Exception exception) {
                MARKERS.remove(id);
            }
        }
    }

    private static void remove(String id) {
        MarkerOverlay marker = MARKERS.remove(id);
        if (marker != null && JourneyMapClientPlugin.isAvailable()) {
            JourneyMapClientPlugin.clientApi().remove(marker);
        }
    }

    private static MapImage createIcon() {
        return new MapImage(ICON).centerAnchors().setColor(0xFF5555).setOpacity(0.95F).setBlur(true);
    }

    private static NativeImage createIconImage() {
        NativeImage image = new NativeImage(9, 9, true);
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                int dx = x - 4;
                int dy = y - 4;
                image.setPixel(x, y, dx * dx + dy * dy <= 12 ? 0xFFFFFFFF : 0x00000000);
            }
        }
        return image;
    }
}
