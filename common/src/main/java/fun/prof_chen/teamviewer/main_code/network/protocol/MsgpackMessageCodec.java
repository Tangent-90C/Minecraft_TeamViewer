package fun.prof_chen.teamviewer.main_code.network.protocol;

@Deprecated
public final class MsgpackMessageCodec implements MessageCodec {
	private final ProtobufMessageCodec delegate = new ProtobufMessageCodec();

	@Override
	public byte[] encode(Object packet) {
		return delegate.encode(packet);
	}

	@Override
	public <T> T decode(byte[] payload, Class<T> packetType) {
		return delegate.decode(payload, packetType);
	}
}
