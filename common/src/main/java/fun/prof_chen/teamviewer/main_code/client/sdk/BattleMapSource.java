package fun.prof_chen.teamviewer.main_code.client.sdk;

import java.util.Optional;

/**
 * Loader-neutral Java extension point used by native providers and Lua-backed adapters.
 * Implementations only capture source data; {@code BattleMapCoordinator} owns protocol conversion.
 */
public interface BattleMapSource {
    String id();
    default IntegrationSupportStatus supportStatus() { return IntegrationSupportStatus.AVAILABLE; }
    default String supportDetail() { return ""; }
    Optional<BattleMapSourceSnapshot> capture();
}
