package fun.prof_chen.teamviewer.main_code.client.sdk;

import java.util.Optional;

/** Loader-neutral, plugin-addressable battle-map observation source. */
public interface BattleMapSource {
    String id();
    default IntegrationSupportStatus supportStatus() { return IntegrationSupportStatus.AVAILABLE; }
    default String supportDetail() { return ""; }
    Optional<BattleMapSourceSnapshot> capture();
}
