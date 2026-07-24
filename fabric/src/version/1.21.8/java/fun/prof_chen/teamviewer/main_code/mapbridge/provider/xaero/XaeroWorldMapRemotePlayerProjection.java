package fun.prof_chen.teamviewer.main_code.mapbridge.provider.xaero;

import fun.prof_chen.teamviewer.main_code.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.main_code.client.sdk.IntegrationSupportStatus;
import net.fabricmc.loader.api.FabricLoader;
import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;

import java.util.Map;
import java.util.UUID;

public final class XaeroWorldMapRemotePlayerProjection implements RemotePlayerProjection {
	@Override
	public String id() {
		return "xaero-worldmap";
	}

	@Override
	public boolean isAvailable() {
		return XaeroWorldMapBridge.isAvailable();
	}

	@Override
	public IntegrationSupportStatus supportStatus() {
		return FabricLoader.getInstance().isModLoaded("xaeroworldmap")
				? IntegrationSupportStatus.AVAILABLE : IntegrationSupportStatus.MOD_NOT_INSTALLED;
	}

	@Override
	public Kind kind() { return Kind.XAERO_WORLD_MAP_MARKER; }

	@Override
	public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) {
		XaeroWorldMapBridge.tick(players, enabled);
	}
}
