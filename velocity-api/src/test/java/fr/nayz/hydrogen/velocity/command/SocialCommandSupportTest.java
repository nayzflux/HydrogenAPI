package fr.nayz.hydrogen.velocity.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SocialCommandSupportTest {
    private final SocialCommandSupport support = new SocialCommandSupport(null, null, null, null);

    @Test
    void pagesDefaultToOneAndRejectInvalidValues() {
        assertEquals(1, support.page(new String[]{"list"}, 1));
        assertEquals(3, support.page(new String[]{"list", "3"}, 1));
        assertThrows(CommandFailure.class, () -> support.page(new String[]{"list", "0"}, 1));
        assertThrows(CommandFailure.class, () -> support.page(new String[]{"list", "abc"}, 1));
        assertThrows(CommandFailure.class, () -> support.page(new String[]{"list", "1", "extra"}, 1));
    }
}
