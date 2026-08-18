package fun.prof_chen.teamviewer.main_code.client.sdk;

import fun.prof_chen.teamviewer.api.PlayerRelation;
import fun.prof_chen.teamviewer.main_code.client.model.TabPlayerSnapshot;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Loader-neutral source of local player relationship decisions. */
public interface PlayerRelationClassifier {
    String id();

    default IntegrationSupportStatus supportStatus() {
        return IntegrationSupportStatus.AVAILABLE;
    }

    default String supportDetail() {
        return "";
    }

    /** Missing UUIDs mean that this classifier has no decision for those players. */
    Map<UUID, PlayerRelation> classify(List<TabPlayerSnapshot> players);
}
