package fun.prof_chen.teamviewer.main_code.network.protocol;

import java.util.UUID;

public interface MessageCodec {
	byte[] encode(Object packet);

	byte[] encodeEntityPatch(UUID submitPlayerId, EntityPatchView patch);

	ProtocolPackets.DecodedInboundMessage decode(byte[] payload);
}
