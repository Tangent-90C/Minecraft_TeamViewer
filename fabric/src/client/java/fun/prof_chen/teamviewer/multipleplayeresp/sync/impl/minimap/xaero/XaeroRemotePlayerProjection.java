package fun.prof_chen.teamviewer.multipleplayeresp.sync.impl.minimap.xaero;

import fun.prof_chen.teamviewer.multipleplayeresp.model.RemotePlayerInfo;
import fun.prof_chen.teamviewer.multipleplayeresp.sync.api.RemotePlayerProjection;

import java.util.Map;
import java.util.UUID;

public final class XaeroRemotePlayerProjection implements RemotePlayerProjection {
	@Override
	public String id() {
		return "xaero-worldmap";
	}

	@Override
	public boolean isAvailable() {
		return XaeroWorldMapBridgeImpl.isAvailable();
	}

	@Override
	public void sync(Map<UUID, RemotePlayerInfo> players, boolean enabled) {
		XaeroWorldMapBridgeImpl.tick(players, enabled);
	}
}