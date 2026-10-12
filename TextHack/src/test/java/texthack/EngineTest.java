package texthack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class EngineTest {
    @Test
    void allEngineChecksPass() {
        assertEquals(0, SelfTest.runAll());
    }
}
