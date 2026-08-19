package fun.prof_chen.teamviewer.main_code.sync.api;

import fun.prof_chen.teamviewer.main_code.model.LastSeenPlayerInfo;

import java.util.UUID;

public interface LastSeenPlayerRepository extends CrudRepository<UUID, LastSeenPlayerInfo> {
}
