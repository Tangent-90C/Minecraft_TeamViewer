package fun.prof_chen.teamviewer.main_code.network.protocol;

public interface MessageCodec {
	byte[] encode(Object packet);

	<T> T decode(byte[] payload, Class<T> packetType);
}
