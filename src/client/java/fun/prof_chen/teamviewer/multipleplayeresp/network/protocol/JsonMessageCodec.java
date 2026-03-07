package fun.prof_chen.teamviewer.multipleplayeresp.network.protocol;

import com.google.gson.Gson;

public final class JsonMessageCodec implements MessageCodec {
	private final Gson gson;

	public JsonMessageCodec(Gson gson) {
		this.gson = gson;
	}

	@Override
	public byte[] encode(Object packet) {
		return gson.toJson(packet).getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	@Override
	public <T> T decode(byte[] payload, Class<T> packetType) {
		return gson.fromJson(new String(payload, java.nio.charset.StandardCharsets.UTF_8), packetType);
	}
}
