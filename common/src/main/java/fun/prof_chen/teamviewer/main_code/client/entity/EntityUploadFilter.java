package fun.prof_chen.teamviewer.main_code.client.entity;

import java.util.Locale;
import java.util.Set;

/** Immutable, precompiled exact-match filter used directly by the entity capture hot path. */
public final class EntityUploadFilter {
    public static final EntityUploadFilter ALLOW_ALL =
            new EntityUploadFilter(Set.of(), Set.of(), Set.of(), Set.of(), 0L);

    private final Set<String> allowedTypes;
    private final Set<String> deniedTypes;
    private final Set<String> allowedNames;
    private final Set<String> deniedNames;
    private final boolean hasAllowRules;
    private final boolean needsNameForDecision;
    private final long revision;

    public EntityUploadFilter(
            Set<String> allowedTypes,
            Set<String> deniedTypes,
            Set<String> allowedNames,
            Set<String> deniedNames,
            long revision) {
        this.allowedTypes = Set.copyOf(allowedTypes);
        this.deniedTypes = Set.copyOf(deniedTypes);
        this.allowedNames = Set.copyOf(allowedNames);
        this.deniedNames = Set.copyOf(deniedNames);
        this.hasAllowRules = !this.allowedTypes.isEmpty() || !this.allowedNames.isEmpty();
        this.needsNameForDecision = !this.allowedNames.isEmpty() || !this.deniedNames.isEmpty();
        this.revision = revision;
    }

    public boolean needsNameForDecision() {
        return needsNameForDecision;
    }

    public long revision() {
        return revision;
    }

    public boolean allows(String entityType, String customName) {
        return allowsStableType(normalizeType(entityType), customName);
    }

    /**
     * Fast path for registry IDs supplied by official adapters. The caller guarantees a lowercase,
     * namespaced ID, avoiding normalization and its second per-entity string scan.
     */
    public boolean allowsStableType(String stableEntityType, String customName) {
        if (deniedTypes.contains(stableEntityType)
                || customName != null && deniedNames.contains(customName)) {
            return false;
        }
        return !hasAllowRules
                || allowedTypes.contains(stableEntityType)
                || customName != null && allowedNames.contains(customName);
    }

    public static String normalizeType(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
