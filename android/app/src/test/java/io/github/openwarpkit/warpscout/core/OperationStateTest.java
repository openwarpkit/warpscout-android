package io.github.openwarpkit.warpscout.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class OperationStateTest {
    @Test
    public void emptyStateHasZeroProgress() {
        assertEquals(0.0f, new OperationState().getProgress(), 0.0f);
    }
}
