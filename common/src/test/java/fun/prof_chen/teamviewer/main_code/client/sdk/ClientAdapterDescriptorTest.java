package fun.prof_chen.teamviewer.main_code.client.sdk;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientAdapterDescriptorTest {
    @Test
    void requiresEverySdkFeature() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientAdapterDescriptor("incomplete", EnumSet.of(ClientFeature.CONNECTION_LIFECYCLE)));
        assertEquals(EnumSet.allOf(ClientFeature.class), ClientAdapterDescriptor.complete("1.21.8").features());
    }
}
