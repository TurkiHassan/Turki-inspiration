package com.ztv.player.ui;

/**
 * The interface language. Strings live next to the code that shows them as English/Arabic
 * pairs, which suits a UI built in Java without XML layouts.
 */
public final class Tr {
    private static boolean arabic;

    private Tr() {
    }

    public static void setArabic(boolean value) {
        arabic = value;
    }

    public static boolean arabic() {
        return arabic;
    }

    public static String t(String english, String arabicText) {
        return arabic ? arabicText : english;
    }
}
