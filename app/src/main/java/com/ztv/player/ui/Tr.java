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

    /**
     * A counted phrase. Arabic: one and two have their own words ("نتيجة واحدة", "نتيجتان"),
     * 3–10 take the plural ("5 نتائج"), 0 and 11+ the singular ("84 نتيجة").
     */
    public static String count(int n, String enOne, String enMany,
                               String arOne, String arTwo, String arFew, String arMany) {
        if (!arabic) {
            return n + " " + (n == 1 ? enOne : enMany);
        }
        if (n == 1) {
            return arOne;
        }
        if (n == 2) {
            return arTwo;
        }
        return n + " " + (n >= 3 && n <= 10 ? arFew : arMany);
    }
}
