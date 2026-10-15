package com.ztv.player.data;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a child may watch in Kids mode, as an allow-list: whole categories, plus single items
 * added from other categories, minus single items blocked inside an allowed category.
 * Anything the provider adds later stays hidden until a parent allows it.
 */
public final class KidsPolicy {
    public static final int UNLIMITED = 0;

    private final Set<String> categories;
    private final Set<String> allowedItems;
    private final Set<String> blockedItems;
    private final int dailyMinutes;
    private final boolean bedtime;
    private final int bedtimeStart;
    private final int bedtimeEnd;

    public KidsPolicy(Set<String> categories, Set<String> allowedItems, Set<String> blockedItems,
                      int dailyMinutes, boolean bedtime, int bedtimeStart, int bedtimeEnd) {
        this.categories = Collections.unmodifiableSet(new HashSet<>(categories));
        this.allowedItems = Collections.unmodifiableSet(new HashSet<>(allowedItems));
        this.blockedItems = Collections.unmodifiableSet(new HashSet<>(blockedItems));
        this.dailyMinutes = Math.max(0, dailyMinutes);
        this.bedtime = bedtime;
        this.bedtimeStart = bedtimeStart;
        this.bedtimeEnd = bedtimeEnd;
    }

    /** A category is identified by its panel id when it has one, else by its title. */
    public static String categoryKey(XtreamContentType type, String categoryId, String sectionTitle) {
        boolean hasId = categoryId != null && !categoryId.trim().isEmpty();
        return type.name() + "|" + (hasId ? "c:" + categoryId.trim() : "t:" + (sectionTitle == null ? "" : sectionTitle));
    }

    public static String categoryKey(XtreamMediaSection section, XtreamContentType type) {
        String id = section.items().isEmpty() ? null : section.items().get(0).categoryId();
        return categoryKey(type, id, section.title());
    }

    public static String itemKey(XtreamContentType type, int id) {
        return type.name() + "|" + id;
    }

    public Set<String> categories() {
        return categories;
    }

    public Set<String> allowedItems() {
        return allowedItems;
    }

    public Set<String> blockedItems() {
        return blockedItems;
    }

    public int dailyMinutes() {
        return dailyMinutes;
    }

    public boolean bedtimeEnabled() {
        return bedtime;
    }

    /** Minutes after midnight. */
    public int bedtimeStart() {
        return bedtimeStart;
    }

    /** Minutes after midnight; earlier than the start means the window crosses midnight. */
    public int bedtimeEnd() {
        return bedtimeEnd;
    }

    public boolean allowsCategory(String categoryKey) {
        return categories.contains(categoryKey);
    }

    public boolean allows(XtreamMediaItem item, String sectionTitle) {
        String key = itemKey(item.type(), item.id());
        if (allowedItems.contains(key)) {
            return true;
        }
        return categories.contains(categoryKey(item.type(), item.categoryId(), sectionTitle))
                && !blockedItems.contains(key);
    }

    /** Only allowed items, inside their sections; sections left empty disappear. */
    public List<XtreamMediaSection> filter(List<XtreamMediaSection> sections) {
        List<XtreamMediaSection> kept = new ArrayList<>();
        if (sections == null) {
            return kept;
        }
        for (XtreamMediaSection section : sections) {
            List<XtreamMediaItem> items = new ArrayList<>();
            for (XtreamMediaItem item : section.items()) {
                if (allows(item, section.title())) {
                    items.add(item);
                }
            }
            if (!items.isEmpty()) {
                kept.add(items.size() == section.items().size() ? section : new XtreamMediaSection(section.title(), items));
            }
        }
        return kept;
    }

    public boolean isBedtime(int minuteOfDay) {
        if (!bedtime || bedtimeStart == bedtimeEnd) {
            return false;
        }
        if (bedtimeStart < bedtimeEnd) {
            return minuteOfDay >= bedtimeStart && minuteOfDay < bedtimeEnd;
        }
        return minuteOfDay >= bedtimeStart || minuteOfDay < bedtimeEnd;
    }

    /** Why a child can't watch at {@code now}, or null when watching is fine. */
    public Block block(Calendar now, long watchedTodayMs, int bonusMinutes, int bedtimePassNight) {
        if (isBedtime(KidsStore.minuteOfDay(now)) && bedtimePassNight != bedtimeNight(now)) {
            return Block.BEDTIME;
        }
        if (dailyMinutes != UNLIMITED && watchedTodayMs >= (dailyMinutes + bonusMinutes) * 60_000L) {
            return Block.TIME_UP;
        }
        return null;
    }

    /** Milliseconds of watching left, or {@link Long#MAX_VALUE} without a daily limit. */
    public long remainingMs(long watchedTodayMs, int bonusMinutes) {
        if (dailyMinutes == UNLIMITED) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, (dailyMinutes + bonusMinutes) * 60_000L - watchedTodayMs);
    }

    /** The evening a bedtime window began on, so 01:00 belongs to the night before. */
    public int bedtimeNight(Calendar now) {
        if (bedtimeStart > bedtimeEnd && KidsStore.minuteOfDay(now) < bedtimeEnd) {
            Calendar yesterday = (Calendar) now.clone();
            yesterday.add(Calendar.DAY_OF_MONTH, -1);
            return KidsStore.dayKey(yesterday);
        }
        return KidsStore.dayKey(now);
    }

    public enum Block {
        BEDTIME, TIME_UP
    }
}
