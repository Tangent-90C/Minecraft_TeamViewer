# TeamViewRelay Public API

Other client mods can read TeamViewRelay state through the loader-neutral classes in
`fun.prof_chen.teamviewer.api`.

```java
RemotePlayerViewBatch batch = TeamViewRelayApi.remotePlayerViews();
for (RemotePlayerView view : batch.players()) {
    RemotePlayerSnapshot player = view.player();
    PlayerInteractionState interaction = view.interaction();
}

PlayerInteractionState state = TeamViewRelayApi.playerInteraction(playerId);
```

`remotePlayers()` and `RemotePlayerBatch` remain available for API v1 consumers.
`remotePlayerViews()` adds the effective relationship, its origin, and a local attackability hint.
The hint uses `FRIENDLY -> BLOCKED`, `ENEMY -> ALLOWED`, and unresolved or neutral players ->
`UNKNOWN`. It does not represent server-side PvP authorization.

All calls fail closed during startup, disconnect, and shutdown. They return immutable empty or
unresolved values instead of throwing when TeamViewRelay is unavailable.
