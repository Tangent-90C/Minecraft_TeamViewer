package fun.prof_chen.teamviewer.api;

public record PlayerInteractionState(
        PlayerRelation relation,
        boolean resolved,
        PlayerRelationOrigin origin,
        Attackability attackability) {
    public PlayerInteractionState {
        relation = relation == null ? PlayerRelation.NEUTRAL : relation;
        origin = origin == null ? PlayerRelationOrigin.UNRESOLVED : origin;
        attackability = attackability == null ? Attackability.UNKNOWN : attackability;
    }

    public static PlayerInteractionState unresolved() {
        return new PlayerInteractionState(PlayerRelation.NEUTRAL, false,
                PlayerRelationOrigin.UNRESOLVED, Attackability.UNKNOWN);
    }
}
