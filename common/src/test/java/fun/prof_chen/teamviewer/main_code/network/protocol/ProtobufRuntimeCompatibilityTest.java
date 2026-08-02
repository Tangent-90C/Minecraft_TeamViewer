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
            @Override
            public int upsertCount() { return 1; }
            @Override
            public UUID upsertId(int index) { return entity; }
            @Override
            public int fieldMask(int index) { return X | DIMENSION | TYPE | WIDTH; }
            @Override
            public double x(int index) { return 12.5; }
            @Override
            public double y(int index) { return 99; }
            @Override
            public double z(int index) { return 0; }
            @Override
            public double vx(int index) { return 0; }
            @Override
            public double vy(int index) { return 0; }
            @Override
            public double vz(int index) { return 0; }
            @Override
            public String dimension(int index) { return "minecraft:overworld"; }
            @Override
            public String entityType(int index) { return "minecraft:zombie"; }
            @Override
            public String entityName(int index) { return "ignored"; }
            @Override
            public float width(int index) { return 0.6f; }
            @Override
            public float height(int index) { return 1.95f; }
            @Override
            public int deleteCount() { return 0; }
            @Override
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
