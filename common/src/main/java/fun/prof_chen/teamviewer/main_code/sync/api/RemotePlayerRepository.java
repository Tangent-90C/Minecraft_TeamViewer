package fun.prof_chen.teamviewer.main_code.sync.api;

import fun.prof_chen.teamviewer.main_code.model.RemotePlayerInfo;

import java.util.UUID;

public interface RemotePlayerRepository extends CrudRepository<UUID, RemotePlayerInfo> {
}