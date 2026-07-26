package fun.prof_chen.teamviewer.main_code.network.protocol;

import fun.prof_chen.teamviewer.main_code.network.proto.WireEnvelope;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtobufRuntimeCompatibilityTest {
    @Test
    void generatedMessagesLoadWithPackagedRuntime() {
        WireEnvelope envelope = assertDoesNotThrow(WireEnvelope::getDefaultInstance);
        assertEquals(WireEnvelope.PayloadCase.PAYLOAD_NOT_SET, envelope.getPayloadCase());
    }

    @Test
    void typedEntityPatchWritesOnlyMaskedFields() throws Exception {
        UUID submit = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        EntityPatchView patch = new EntityPatchView() {
            public int upsertCount() { return 1; }
            public UUID upsertId(int index) { return entity; }
            public int fieldMask(int index) { return X | DIMENSION | TYPE | WIDTH; }
            public double x(int index) { return 12.5; }
            public double y(int index) { return 99; }
            public double z(int index) { return 0; }
            public double vx(int index) { return 0; }
            public double vy(int index) { return 0; }
            public double vz(int index) { return 0; }
            public String dimension(int index) { return "minecraft:overworld"; }
            public String entityType(int index) { return "minecraft:zombie"; }
            public String entityName(int index) { return "ignored"; }
            public float width(int index) { return 0.6f; }
            public float height(int index) { return 1.95f; }
            public int deleteCount() { return 0; }
            public UUID deleteId(int index) { throw new IndexOutOfBoundsException(index); }
        };

        WireEnvelope envelope = WireEnvelope.parseFrom(
                new ProtobufMessageCodec().encodeEntityPatch(submit, patch));
        var bundle = envelope.getPlayerReportBundle();
        var upsert = bundle.getEntitiesPatch().getUpsert(0);
        assertEquals(submit.toString(), bundle.getSubmitPlayerId());
        assertEquals(entity.toString(), upsert.getId());
        assertEquals(12.5, upsert.getData().getX());
        assertTrue(upsert.getData().hasX());
        assertFalse(upsert.getData().hasY());
        assertEquals("minecraft:overworld", upsert.getData().getDimension());
        assertEquals("minecraft:zombie", upsert.getData().getEntityType());
        assertFalse(upsert.getData().hasEntityName());
        assertTrue(upsert.getData().hasWidth());
        assertFalse(upsert.getData().hasHeight());
    }
}
