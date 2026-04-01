package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

import journeymap.api.v2.client.display.Context;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.model.MapImage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.math.BlockPos;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class JourneyMapRemotePlayerMarkerBridge {
	private static final String PLAYER_PREFIX = "player-marker:";
	private static final int PLAYER_COLOR = 0xFF5555;
	private static final Map<String, MarkerOverlay> MANAGED_MARKERS = new ConcurrentHashMap<>();
	private static final NativeImage PLAYER_ICON_IMAGE = createPlayerIconImage();

	private JourneyMapRemotePlayerMarkerBridge() {
	}

	static boolean isAvailable() {
		return JourneyMapWaypointAccess.isAvailable();
	}

	static void tick(Map<UUID, RemotePlayerInfo> players, boolean enabled) {
		if (!enabled) {
			clear();
			return;
		}
		if (!isAvailable()) {
			return;
		}
		if (!JourneyMapWaypointAccess.clientApi().playerAccepts(JourneyMapClientPlugin.TEAMVIEWER_MOD_ID, DisplayType.Marker)) {
			clear();
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) {
			return;
		}

		UUID localPlayerId = client.player.getUuid();
		String currentDimension = client.world.getRegistryKey().getValue().toString();
		Set<String> activeIds = ConcurrentHashMap.newKeySet();

		for (RemotePlayerInfo info : players == null ? Collections.<RemotePlayerInfo>emptyList() : players.values()) {
			if (info == null || info.uuid() == null || info.position() == null || info.uuid().equals(localPlayerId)) {
				continue;
			}
			if (info.dimension() != null && !info.dimension().isBlank() && !info.dimension().equals(currentDimension)) {
				continue;
			}

			String markerId = PLAYER_PREFIX + info.uuid();
			activeIds.add(markerId);
			upsertMarker(markerId, info, client);
		}

		for (String existingId : Set.copyOf(MANAGED_MARKERS.keySet())) {
			if (!activeIds.contains(existingId)) {
				removeMarker(existingId);
			}
		}
	}

	static void clear() {
		for (String id : Set.copyOf(MANAGED_MARKERS.keySet())) {
			removeMarker(id);
		}
	}

	private static void upsertMarker(String markerId, RemotePlayerInfo info, MinecraftClient client) {
		BlockPos blockPos = new BlockPos(
				(int) Math.floor(info.position().x()),
				(int) Math.floor(info.position().y()),
				(int) Math.floor(info.position().z()));
		String playerName = info.name() == null || info.name().isBlank() ? "Player" : info.name();

		MarkerOverlay overlay = MANAGED_MARKERS.get(markerId);
		boolean changed = false;
		if (overlay == null) {
			overlay = new MarkerOverlay(
					JourneyMapClientPlugin.TEAMVIEWER_MOD_ID,
					blockPos,
					createPlayerIcon());
			overlay.setActiveUIs(Context.UI.all());
			overlay.setDisplayOrder(1000);
			overlay.setOverlayGroupName("TeamViewRelay");
			MANAGED_MARKERS.put(markerId, overlay);
			changed = true;
		}

		if (!Objects.equals(overlay.getPoint(), blockPos)) {
			overlay.setPoint(blockPos);
			changed = true;
		}
		if (!Objects.equals(overlay.getLabel(), playerName)) {
			overlay.setLabel(playerName);
			changed = true;
		}
		if (!Objects.equals(overlay.getTitle(), playerName)) {
			overlay.setTitle(playerName);
			changed = true;
		}
		if (!Objects.equals(overlay.getDimension(), client.world.getRegistryKey())) {
			overlay.setDimension(client.world.getRegistryKey());
			changed = true;
		}

		if (!changed) {
			return;
		}

		try {
			JourneyMapWaypointAccess.clientApi().show(overlay);
		} catch (Exception e) {
			MANAGED_MARKERS.remove(markerId);
		}
	}

	private static void removeMarker(String markerId) {
		MarkerOverlay overlay = MANAGED_MARKERS.remove(markerId);
		if (overlay == null || !isAvailable()) {
			return;
		}
		JourneyMapWaypointAccess.clientApi().remove(overlay);
	}

	private static MapImage createPlayerIcon() {
		return new MapImage(PLAYER_ICON_IMAGE)
				.centerAnchors()
				.setColor(PLAYER_COLOR)
				.setOpacity(0.95F)
				.setBlur(true);
	}

	private static NativeImage createPlayerIconImage() {
		NativeImage image = new NativeImage(9, 9, true);
		for (int y = 0; y < 9; y++) {
			for (int x = 0; x < 9; x++) {
				int dx = x - 4;
				int dy = y - 4;
				int distanceSquared = dx * dx + dy * dy;
				if (distanceSquared <= 12) {
					image.setColorArgb(x, y, 0xFFFFFFFF);
				} else {
					image.setColorArgb(x, y, 0x00000000);
				}
			}
		}
		return image;
	}
}
