package fun.prof_chen.teamviewer.multipleplayeresp.sync.api;

import fun.prof_chen.teamviewer.multipleplayeresp.model.RemotePlayerInfo;

import java.util.UUID;

public interface RemotePlayerRepository extends CrudRepository<UUID, RemotePlayerInfo> {
}