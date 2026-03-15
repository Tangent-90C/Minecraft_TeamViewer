package fun.prof_chen.teamviewer.main_code.model;

import java.util.UUID;

public record RemotePlayerInfo(UUID uuid, Position3D position, String dimension, String name) {
}