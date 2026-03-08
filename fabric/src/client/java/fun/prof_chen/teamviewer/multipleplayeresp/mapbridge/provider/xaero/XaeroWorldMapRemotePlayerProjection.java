package fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.provider.xaero;

import fun.prof_chen.teamviewer.multipleplayeresp.mapbridge.implementor.RemotePlayerProjection;
import fun.prof_chen.teamviewer.multipleplayeresp.model.RemotePlayerInfo;

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
	public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) {
		XaeroWorldMapBridge.tick(players, enabled);
	}
}