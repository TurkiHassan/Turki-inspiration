package com.ztv.player.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KidsPolicyTest {
    private static final XtreamContentType LIVE = XtreamContentType.LIVE;

    private static XtreamMediaItem channel(int id, String categoryId) {
        return new XtreamMediaItem(LIVE, "Channel " + id, id, categoryId, "", "", "ts");
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static KidsPolicy policy(Set<String> categories, Set<String> allowed, Set<String> blocked) {
        return new KidsPolicy(categories, allowed, blocked, KidsPolicy.UNLIMITED, false, 0, 0);
    }

    @Test
    public void onlyAllowedCategoriesShow_andNewCategoriesStayHidden() {
        KidsPolicy kids = policy(set(KidsPolicy.categoryKey(LIVE, "7", "Kids")), set(), set());
        assertTrue(kids.allows(channel(1, "7"), "Kids"));
        assertFalse(kids.allows(channel(2, "8"), "Sports"));
        assertFalse(kids.allows(channel(3, "99"), "Brand new category"));
    }

    @Test
    public void singleItemsCanBeAddedOrBlocked() {
        KidsPolicy kids = policy(
                set(KidsPolicy.categoryKey(LIVE, "7", "Kids")),
                set(KidsPolicy.itemKey(LIVE, 50)),
                set(KidsPolicy.itemKey(LIVE, 2)));
        assertTrue(kids.allows(channel(1, "7"), "Kids"));
        assertFalse(kids.allows(channel(2, "7"), "Kids"));
        assertTrue(kids.allows(channel(50, "8"), "Documentaries"));
    }

    @Test
    public void categoriesWithoutAnIdFallBackToTheirTitle() {
        String key = KidsPolicy.categoryKey(LIVE, "", "Uncategorized");
        assertNotEquals(key, KidsPolicy.categoryKey(LIVE, "", "Other"));
        assertTrue(policy(set(key), set(), set()).allows(channel(1, ""), "Uncategorized"));
    }

    @Test
    public void sameIdInAnotherTypeIsADifferentCategory() {
        assertNotEquals(KidsPolicy.categoryKey(LIVE, "7", "Kids"),
                KidsPolicy.categoryKey(XtreamContentType.SERIES, "7", "Kids"));
    }

    @Test
    public void filterDropsEmptySectionsAndKeepsOrder() {
        KidsPolicy kids = policy(set(KidsPolicy.categoryKey(LIVE, "7", "Kids")), set(KidsPolicy.itemKey(LIVE, 9)), set());
        List<XtreamMediaSection> sections = Arrays.asList(
                new XtreamMediaSection("Sports", Arrays.asList(channel(8, "8"), channel(9, "8"))),
                new XtreamMediaSection("News", Collections.singletonList(channel(10, "10"))),
                new XtreamMediaSection("Kids", Arrays.asList(channel(1, "7"), channel(2, "7"))));
        List<XtreamMediaSection> kept = kids.filter(sections);
        assertEquals(2, kept.size());
        assertEquals("Sports", kept.get(0).title());
        assertEquals(1, kept.get(0).items().size());
        assertEquals(9, kept.get(0).items().get(0).id());
        assertEquals(2, kept.get(1).items().size());
    }

    @Test
    public void bedtimeWindowCanCrossMidnight() {
        KidsPolicy night = new KidsPolicy(set(), set(), set(), 0, true, 21 * 60, 7 * 60);
        assertTrue(night.isBedtime(22 * 60));
        assertTrue(night.isBedtime(60));
        assertFalse(night.isBedtime(7 * 60));
        assertFalse(night.isBedtime(12 * 60));
        KidsPolicy nap = new KidsPolicy(set(), set(), set(), 0, true, 13 * 60, 15 * 60);
        assertTrue(nap.isBedtime(14 * 60));
        assertFalse(nap.isBedtime(16 * 60));
        KidsPolicy off = new KidsPolicy(set(), set(), set(), 0, false, 21 * 60, 7 * 60);
        assertFalse(off.isBedtime(23 * 60));
    }

    @Test
    public void pinHashIsSaltedAndStable() {
        assertEquals(KidsStore.hash("salt", "1234"), KidsStore.hash("salt", "1234"));
        assertNotEquals(KidsStore.hash("salt", "1234"), KidsStore.hash("other", "1234"));
        assertNotEquals(KidsStore.hash("salt", "1234"), KidsStore.hash("salt", "1235"));
    }

    private static Calendar at(int day, int hour, int minute) {
        Calendar time = Calendar.getInstance();
        time.clear();
        time.set(2026, Calendar.OCTOBER, day, hour, minute);
        return time;
    }

    @Test
    public void dailyLimitBlocksOnceUsedUp_andBonusMinutesExtendIt() {
        KidsPolicy hour = new KidsPolicy(set(), set(), set(), 60, false, 0, 0);
        Calendar noon = at(15, 12, 0);
        assertNull(hour.block(noon, 59 * 60_000L, 0, 0));
        assertEquals(KidsPolicy.Block.TIME_UP, hour.block(noon, 60 * 60_000L, 0, 0));
        assertNull(hour.block(noon, 60 * 60_000L, 30, 0));
        assertEquals(30 * 60_000L, hour.remainingMs(60 * 60_000L, 30));
        assertEquals(Long.MAX_VALUE, policy(set(), set(), set()).remainingMs(999_999L, 0));
    }

    @Test
    public void bedtimeBlocks_andTonightsPassCoversAfterMidnight() {
        KidsPolicy night = new KidsPolicy(set(), set(), set(), 0, true, 21 * 60, 7 * 60);
        Calendar evening = at(15, 22, 0);
        Calendar lateNight = at(16, 1, 30);
        Calendar nextEvening = at(16, 21, 30);
        assertEquals(KidsPolicy.Block.BEDTIME, night.block(evening, 0, 0, 0));
        int pass = night.bedtimeNight(evening);
        assertEquals(KidsStore.dayKey(at(15, 0, 0)), pass);
        assertNull(night.block(evening, 0, 0, pass));
        assertNull(night.block(lateNight, 0, 0, pass));
        assertEquals(KidsPolicy.Block.BEDTIME, night.block(nextEvening, 0, 0, pass));
        assertNull(night.block(at(16, 12, 0), 0, 0, 0));
    }

    @Test
    public void bedtimeWinsOverTimeLeft() {
        KidsPolicy both = new KidsPolicy(set(), set(), set(), 120, true, 21 * 60, 7 * 60);
        assertEquals(KidsPolicy.Block.BEDTIME, both.block(at(15, 23, 0), 0, 0, 0));
    }
}
