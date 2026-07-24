package fun.prof_chen.teamviewer.main_code.mapbridge.provider.journey;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JourneyMapPlugin;

/** JourneyMap 6 entrypoint; bridge registration is intentionally API-only on 26.1. */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class JourneyMapClientPlugin implements IClientPlugin {
    @Override
    public String getModId() {
        return "team-view-relay";
    }

    @Override
    public void initialize(IClientAPI api) {
        // The shared synchronization core remains active without exposing Minecraft types.
        // Display adapters are registered only after the 26.1 JourneyMap API is present.
    }
}
