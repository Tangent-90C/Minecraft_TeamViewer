package fun.prof_chen.teamviewer.multipleplayeresp.network.protocol;

import com.google.gson.Gson;

public final class JsonMessageCodec implements MessageCodec {
	private final Gson gson;

	public JsonMessageCodec(Gson gson) {
		this.gson = gson;
	}

	@Override
	public String encode(Object packet) {
		return gson.toJson(packet);
	}

	@Override
	public <T> T decode(String payload, Class<T> packetType) {
		return gson.fromJson(payload, packetType);
	}
}
