package fun.prof_chen.teamviewer.main_code.client.entity;

import fun.prof_chen.teamviewer.main_code.config.Config;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityUploadFilterTest {
    @Test
    void denyRulesOverrideTypeOrNameAllowRules() {
        EntityUploadFilter filter = new EntityUploadFilter(
                Set.of("minecraft:zombie"),
                Set.of("minecraft:skeleton"),
                Set.of("Boss"),
                Set.of("Blocked"),
                1L);

        assertTrue(filter.allows("MINECRAFT:ZOMBIE", null));
        assertTrue(filter.allows("minecraft:cow", "Boss"));
        assertFalse(filter.allows("minecraft:skeleton", "Boss"), "type deny must override name allow");
        assertFalse(filter.allows("minecraft:zombie", "Blocked"), "name deny must override type allow");
        assertFalse(filter.allows("minecraft:cow", "boss"), "custom names are case-sensitive");
        assertFalse(filter.allows("minecraft:cow", null));
    }

    @Test
    void emptyAllowListsPermitEverythingExceptDeniedEntries() {
        EntityUploadFilter filter = new EntityUploadFilter(
                Set.of(), Set.of("minecraft:item"), Set.of(), Set.of(), 2L);
        assertTrue(filter.allows("minecraft:zombie", null));
        assertFalse(filter.allows("minecraft:item", null));
        assertFalse(filter.needsNameForDecision());
    }

    @Test
    void configNormalizesTypesAndBoundsRules() {
        Config config = new Config();
        assertTrue(config.addEntityFilterRule(Config.ENTITY_FILTER_ALLOW_TYPE, " Minecraft:Zombie "));
        assertFalse(config.addEntityFilterRule(Config.ENTITY_FILTER_ALLOW_TYPE, "minecraft:zombie"));
        assertFalse(config.addEntityFilterRule(Config.ENTITY_FILTER_ALLOW_TYPE, "missing_namespace"));
        assertTrue(config.addEntityFilterRule(Config.ENTITY_FILTER_DENY_NAME, "Boss"));
        assertTrue(config.getEntityUploadFilter().allows("minecraft:zombie", null));
        assertFalse(config.getEntityUploadFilter().allows("minecraft:zombie", "Boss"));
    }
}
