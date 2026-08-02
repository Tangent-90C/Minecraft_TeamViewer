package fun.prof_chen.teamviewer.main_code.network.transport;

import org.junit.jupiter.api.Test;

import java.net.ProtocolException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OkHttpTransportProcessTest {
    @Test
    void acceptsSupportedServerWindowBits() throws ProtocolException {
        assertEquals(8, OkHttpTransportProcess.parseServerMaxWindowBits("8"));
        assertEquals(15, OkHttpTransportProcess.parseServerMaxWindowBits("15"));
    }

    @Test
    void rejectsNonNumericServerWindowBitsAsProtocolError() {
        ProtocolException error = assertThrows(ProtocolException.class,
                () -> OkHttpTransportProcess.parseServerMaxWindowBits("invalid"));

        assertTrue(error.getMessage().contains("invalid"));
        assertInstanceOf(NumberFormatException.class, error.getCause());
    }

    @Test
    void rejectsUnsupportedServerWindowBits() {
        ProtocolException tooSmall = assertThrows(ProtocolException.class,
                () -> OkHttpTransportProcess.parseServerMaxWindowBits("7"));
        ProtocolException tooLarge = assertThrows(ProtocolException.class,
                () -> OkHttpTransportProcess.parseServerMaxWindowBits("16"));

        assertTrue(tooSmall.getMessage().contains("7"));
        assertTrue(tooLarge.getMessage().contains("16"));
    }
}
