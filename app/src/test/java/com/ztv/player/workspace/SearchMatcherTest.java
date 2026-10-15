package com.ztv.player.workspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchMatcherTest {
    @Test
    public void fold_unifiesArabicVariantsDiacriticsAndDigits() {
        assertEquals("الجزيره مباشر 2", SearchMatcher.fold("  الجَزِيرَة — مُباشِر ٢ "));
        assertEquals("اسلام", SearchMatcher.fold("إسلام"));
        assertEquals("مستشفي", SearchMatcher.fold("مستشفى"));
        assertEquals("ar mbc 1 hd", SearchMatcher.fold("AR| MBC 1 (HD)"));
    }

    @Test
    public void score_ranksExactThenPrefixThenPhraseThenWords() {
        SearchMatcher matcher = SearchMatcher.of("mbc 1");
        assertEquals(0, matcher.score("MBC 1"));
        assertEquals(1, matcher.score("MBC 1 HD"));
        assertEquals(2, matcher.score("AR| MBC 1 HD"));
        assertEquals(3, matcher.score("MBC Drama 1"));
        assertEquals(SearchMatcher.NO_MATCH, matcher.score("MBC 2"));
    }

    @Test
    public void spokenWordsMatchInAnyOrderAndAcrossSpacing() {
        assertTrue(SearchMatcher.of("sports bein").score("beIN SPORTS 1 HD") >= 0);
        assertTrue(SearchMatcher.of("mbc1").score("MBC 1 HD") >= 0);
        assertTrue(SearchMatcher.of("MBC 1").score("MBC1 HD") >= 0);
    }

    @Test
    public void numberWordsMatchDigitsButKeepTheirOwnMeaning() {
        assertTrue(SearchMatcher.of("MBC one").score("MBC 1 HD") >= 0);
        assertTrue(SearchMatcher.of("one piece").score("One Piece (1999)") >= 0);
        assertTrue(SearchMatcher.of("روتانا واحد").score("روتانا 1") >= 0);
    }

    @Test
    public void fillerWordsAreOptional() {
        assertTrue(SearchMatcher.of("play MBC drama").score("MBC Drama HD") >= 0);
        assertTrue(SearchMatcher.of("قناة الجزيرة").score("الجزيرة مباشر") >= 0);
        assertTrue(SearchMatcher.of("مسلسل الهيبة").score("الهيبه") >= 0);
        assertTrue(SearchMatcher.of("movie").score("The Movie Show") >= 0);
    }

    @Test
    public void emptyOrPunctuationOnlyQueriesNeverMatch() {
        assertEquals(0, SearchMatcher.of(" | ").length());
        assertEquals(SearchMatcher.NO_MATCH, SearchMatcher.of(" | ").score("Anything"));
    }

    @Test
    public void spelledOutLettersAndChannelWordsBecomeLatin() {
        assertEquals("mbc action", SearchMatcher.toLatin(SearchMatcher.fold("إم بي سي أكشن")));
        assertEquals("bein sport 1", SearchMatcher.toLatin(SearchMatcher.fold("بي ان سبورت ١")));
        assertEquals("osn", SearchMatcher.toLatin(SearchMatcher.fold("او اس ان")));
        assertEquals("فيلم في البيت", SearchMatcher.toLatin("فيلم في البيت"));
    }

    @Test
    public void arabicSpeechFindsLatinChannelNamesAndBack() {
        assertTrue(SearchMatcher.of("ام بي سي اكشن").score("MBC-Action-HD") >= 0);
        assertTrue(SearchMatcher.of("ام بي سي ٢").score("MBC-2-HD") >= 0);
        assertTrue(SearchMatcher.of("الجزيرة").score("AR| Al Jazeera HD") >= 0);
        assertTrue(SearchMatcher.of("بي ان سبورت").score("beIN SPORTS 1 HD") >= 0);
        assertTrue(SearchMatcher.of("jazeera").score("الجزيرة مباشر") >= 0);
        assertTrue(SearchMatcher.of("الجزيرة").score("الجزيرة 2 2014") >= 0);
        assertEquals(SearchMatcher.NO_MATCH, SearchMatcher.of("ام بي سي اكشن").score("MBC-Drama-HD"));
    }
}
