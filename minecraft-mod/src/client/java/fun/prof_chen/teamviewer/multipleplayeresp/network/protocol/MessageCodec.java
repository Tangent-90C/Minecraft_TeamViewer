package fun.prof_chen.teamviewer.multipleplayeresp.network.protocol;

public interface MessageCodec {
	String encode(Object packet);

	<T> T decode(String payload, Class<T> packetType);
}
