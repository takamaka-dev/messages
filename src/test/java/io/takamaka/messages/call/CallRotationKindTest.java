package io.takamaka.messages.call;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** [0.2] §6.5 {@code rotation} row (J-2/K-3): a commit kind, budgeted like {@code leave} and {@code backstop}. */
class CallRotationKindTest {

    @Test
    void rotationIsACommitKindAndBudgetedLikeLeaveAndBackstop() {
        assertEquals("rotation", CallConstants.KIND_ROTATION);
        assertEquals(java.util.Set.of("initial", "leave", "era", "backstop", "restart", "rotation"), CallConstants.COMMIT_KINDS);
        assertTrue(CallConstants.isBudgetedKind("rotation"));
        assertTrue(CallConstants.isBudgetedKind("leave"));
        assertTrue(CallConstants.isBudgetedKind("backstop"));
        assertFalse(CallConstants.isBudgetedKind("era"));
        assertFalse(CallConstants.isBudgetedKind("restart"));
        assertFalse(CallConstants.isBudgetedKind("initial"));
        assertFalse(CallConstants.isBudgetedKind(null));
    }
}
