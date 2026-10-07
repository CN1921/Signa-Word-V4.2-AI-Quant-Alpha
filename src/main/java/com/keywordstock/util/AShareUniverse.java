package com.keywordstock.util;

/** A-share target universe: Main Board + ChiNext + STAR. */
public final class AShareUniverse {
    private AShareUniverse() {}

    public static boolean isTarget(String code) {
        if (code == null) return false;
        String c = code.trim();
        if (c.length() != 6 || !c.chars().allMatch(Character::isDigit)) return false;
        return c.startsWith("000") || c.startsWith("001") || c.startsWith("002") || c.startsWith("003")
                || c.startsWith("600") || c.startsWith("601") || c.startsWith("603") || c.startsWith("605")
                || c.startsWith("300") || c.startsWith("688");
    }
}
