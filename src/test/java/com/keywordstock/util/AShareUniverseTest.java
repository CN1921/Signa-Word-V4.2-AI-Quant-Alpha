package com.keywordstock.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AShareUniverseTest {
    @Test void targetBoardsOnly() {
        assertTrue(AShareUniverse.isTarget("000001"));
        assertTrue(AShareUniverse.isTarget("002594"));
        assertTrue(AShareUniverse.isTarget("300750"));
        assertTrue(AShareUniverse.isTarget("688981"));
        assertTrue(AShareUniverse.isTarget("600519"));
        assertFalse(AShareUniverse.isTarget("830799"));
        assertFalse(AShareUniverse.isTarget("920001"));
        assertFalse(AShareUniverse.isTarget("510300"));
    }
}
