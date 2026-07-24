package fun.prof_chen.teamviewer.main_code.network.protocol;

import fun.prof_chen.teamviewer.main_code.network.proto.WireEnvelope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtobufRuntimeCompatibilityTest {
    @Test
    void generatedMessagesLoadWithPackagedRuntime() {
        WireEnvelope envelope = assertDoesNotThrow(WireEnvelope::getDefaultInstance);
        assertEquals(WireEnvelope.PayloadCase.PAYLOAD_NOT_SET, envelope.getPayloadCase());
    }
}
