package fun.prof_chen.teamviewer.main_code.client.model;

import fun.prof_chen.teamviewer.api.PlayerRelation;

import java.util.Objects;

/** Effective relationship plus the configured presentation color. */
public record PlayerRelationView(PlayerRelation relation, int color, boolean resolved) {
    public PlayerRelationView {
        relation = Objects.requireNonNullElse(relation, PlayerRelation.NEUTRAL);
    }
}
