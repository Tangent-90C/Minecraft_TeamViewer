package fun.prof_chen.teamviewer.main_code.network.protocol;

public interface MessageCodec {
	byte[] encode(Object packet);

	ProtocolPackets.DecodedInboundMessage decode(byte[] payload);
}
