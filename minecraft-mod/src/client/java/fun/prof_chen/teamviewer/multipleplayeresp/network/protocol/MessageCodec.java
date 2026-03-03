package fun.prof_chen.teamviewer.multipleplayeresp.network.protocol;

public interface MessageCodec {
	byte[] encode(Object packet);

	<T> T decode(byte[] payload, Class<T> packetType);
}
