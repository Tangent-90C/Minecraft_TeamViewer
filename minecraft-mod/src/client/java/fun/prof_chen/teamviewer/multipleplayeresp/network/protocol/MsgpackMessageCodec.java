package fun.prof_chen.teamviewer.multipleplayeresp.network.protocol;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public final class MsgpackMessageCodec implements MessageCodec {
	private final ObjectMapper objectMapper;

	public MsgpackMessageCodec() {
		this.objectMapper = new ObjectMapper(new MessagePackFactory());
		this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	@Override
	public byte[] encode(Object packet) {
		try {
			return objectMapper.writeValueAsBytes(packet);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to encode msgpack payload", e);
		}
	}

	@Override
	public <T> T decode(byte[] payload, Class<T> packetType) {
		try {
			return objectMapper.readValue(payload, packetType);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to decode msgpack payload", e);
		}
	}
}
