package fun.prof_chen.teamviewer.multipleplayeresp.model;

import java.util.UUID;

public record RemotePlayerInfo(UUID uuid, Position3D position, String dimension, String name) {
}