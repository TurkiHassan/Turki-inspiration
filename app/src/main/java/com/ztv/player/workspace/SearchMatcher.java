package com.ztv.player.workspace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Title matching shared by typed and spoken search. Both sides are folded the same way
 * (case, Arabic letter variants and diacritics, Arabic-Indic digits, punctuation), then every
 * word of the query must appear somewhere in the title, in any order. Spoken number words
 * ("one", "واحد") also match their digits, filler words ("play", "قناة") are optional, and
 * known channel words match across Arabic and Latin script.
 */
public final class SearchMatcher {
    public static final int NO_MATCH = -1;
    private static final int EXACT = 0;
    private static final int PREFIX = 1;
    private static final int PHRASE = 2;
    private static final int WORDS = 3;
    private static final int CACHE_LIMIT = 80_000;

    private static final Map<String, String> NUMBERS = new HashMap<>();
    private static final Set<String> FILLERS = new HashSet<>(Arrays.asList(
            "play", "watch", "open", "find", "search", "show", "channel", "the", "movie", "film", "series", "tv",
            "شغل", "شغلي", "افتح", "ابحث", "عن", "اعرض", "قناه", "فيلم", "مسلسل"));
    private static final Map<String, String> FOLDED = new HashMap<>();

    static {
        String[][] numbers = {
                {"zero", "0"}, {"one", "1"}, {"two", "2"}, {"three", "3"}, {"four", "4"}, {"five", "5"},
                {"six", "6"}, {"seven", "7"}, {"eight", "8"}, {"nine", "9"}, {"ten", "10"},
                {"صفر", "0"}, {"واحد", "1"}, {"اثنين", "2"}, {"اثنان", "2"}, {"ثلاثه", "3"}, {"اربعه", "4"},
                {"خمسه", "5"}, {"سته", "6"}, {"سبعه", "7"}, {"ثمانيه", "8"}, {"تسعه", "9"}, {"عشره", "10"},
        };
        for (String[] pair : numbers) {
            NUMBERS.put(pair[0], pair[1]);
        }
    }

    /** Arabic spellings of Latin letter names ("ام بي سي" is M B C), folded. */
    private static final Map<String, String> LETTERS = new HashMap<>();
    /** Channel and genre words that are written in Latin letters in most playlists. */
    private static final Map<String, String> ARABIC_TO_LATIN = new HashMap<>();
    private static final Map<String, String> LATIN_TO_ARABIC = new HashMap<>();
    private static final int LONGEST_TERM = 3;

    static {
        String[][] letters = {
                {"ايه", "a"}, {"بي", "b"}, {"سي", "c"}, {"دي", "d"}, {"اي", "i"}, {"اف", "f"}, {"جي", "g"},
                {"اتش", "h"}, {"اش", "h"}, {"ايتش", "h"}, {"جاي", "j"}, {"كي", "k"}, {"كاي", "k"}, {"ال", "l"},
                {"ام", "m"}, {"ان", "n"}, {"او", "o"}, {"كيو", "q"}, {"ار", "r"}, {"اس", "s"}, {"تي", "t"},
                {"يو", "u"}, {"في", "v"}, {"دبليو", "w"}, {"اكس", "x"}, {"واي", "y"}, {"زد", "z"},
        };
        for (String[] pair : letters) {
            LETTERS.put(pair[0], pair[1]);
        }
        String[][] terms = {
                {"الجزيره", "jazeera"}, {"العربيه", "arabiya"}, {"روتانا", "rotana"}, {"الحدث", "hadath"},
                {"سكاي", "sky"}, {"نيوز", "news"}, {"سبورت", "sport"}, {"سبورتس", "sports"}, {"بي ان", "bein"},
                {"بين", "bein"}, {"شاهد", "shahid"}, {"نتفليكس", "netflix"}, {"الكاس", "alkass"}, {"دبي", "dubai"},
                {"ابوظبي", "abu dhabi"}, {"ابو ظبي", "abu dhabi"}, {"السعوديه", "saudi"}, {"الاخباريه", "ekhbariya"},
                {"روسيا اليوم", "rt"}, {"فرانس", "france"}, {"ناشونال جيوغرافيك", "national geographic"},
                {"ناشيونال جيوغرافيك", "national geographic"}, {"ديسكفري", "discovery"}, {"كرتون نتورك", "cartoon network"},
                {"سبيستون", "spacetoon"}, {"سبيس تون", "spacetoon"}, {"الشرق", "asharq"}, {"القاهره", "cairo"},
                {"النهار", "nahar"}, {"الحياه", "hayah"}, {"دريم", "dream"}, {"اكشن", "action"}, {"دراما", "drama"},
                {"سينما", "cinema"}, {"افلام", "aflam"}, {"كيدز", "kids"}, {"ماكس", "max"}, {"بوليوود", "bollywood"},
                {"مصر", "masr"}, {"العراق", "iraq"}, {"لايف", "live"}, {"فور كي", "4k"}, {"ستارز", "stars"},
                {"فوكس", "fox"}, {"ديزني", "disney"}, {"اوربت", "orbit"}, {"امازون", "amazon"}, {"القران", "quran"},
                {"المجد", "almajd"}, {"كوميدي", "comedy"}, {"فاميلي", "family"}, {"بلس", "plus"}, {"وان", "one"},
        };
        for (String[] pair : terms) {
            ARABIC_TO_LATIN.put(pair[0], pair[1]);
            if (!pair[1].contains(" ") && !LATIN_TO_ARABIC.containsKey(pair[1])) {
                LATIN_TO_ARABIC.put(pair[1], pair[0]);
            }
        }
    }

    private final String phrase;
    private final List<Pattern> patterns;

    private SearchMatcher(String phrase, List<Pattern> patterns) {
        this.phrase = phrase;
        this.patterns = patterns;
    }

    public static SearchMatcher of(String query) {
        String phrase = fold(query);
        List<Pattern> patterns = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String variant : new String[]{phrase, toLatin(phrase), toArabic(phrase)}) {
            if (!variant.isEmpty() && seen.add(variant)) {
                patterns.add(new Pattern(variant));
            }
        }
        return new SearchMatcher(phrase, patterns);
    }

    /** Folded query length, so "  a " doesn't count as a three-character search. */
    public int length() {
        return phrase.length();
    }

    /**
     * {@link #NO_MATCH}, or a rank where lower is better (exact, prefix, phrase, words). The
     * query also matches through its Latin/Arabic spelling, so "ام بي سي اكشن" finds
     * "MBC-Action-HD" and "jazeera" finds "الجزيرة".
     */
    public int score(String title) {
        if (patterns.isEmpty() || title == null) {
            return NO_MATCH;
        }
        String folded = foldCached(title);
        int best = NO_MATCH;
        for (Pattern pattern : patterns) {
            int score = pattern.score(folded);
            if (score != NO_MATCH && (best == NO_MATCH || score < best)) {
                best = score;
            }
        }
        return best;
    }

    /** Arabic-script channel words and spelled-out letters rewritten in Latin. */
    static String toLatin(String phrase) {
        if (phrase.isEmpty()) {
            return "";
        }
        String[] words = phrase.split(" ");
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < words.length) {
            String term = null;
            int used = 0;
            for (int n = Math.min(LONGEST_TERM, words.length - i); n >= 1 && term == null; n--) {
                term = ARABIC_TO_LATIN.get(join(words, i, n));
                used = n;
            }
            if (term != null) {
                out.add(term);
                i += used;
                continue;
            }
            int run = 0;
            StringBuilder acronym = new StringBuilder();
            while (i + run < words.length && LETTERS.containsKey(words[i + run])) {
                acronym.append(LETTERS.get(words[i + run]));
                run++;
            }
            if (run >= 2) {
                out.add(acronym.toString());
                i += run;
            } else {
                out.add(words[i]);
                i++;
            }
        }
        return String.join(" ", out);
    }

    /** Latin channel words rewritten the way Arabic playlists spell them. */
    static String toArabic(String phrase) {
        if (phrase.isEmpty()) {
            return "";
        }
        String[] words = phrase.split(" ");
        for (int i = 0; i < words.length; i++) {
            String term = LATIN_TO_ARABIC.get(words[i]);
            if (term != null) {
                words[i] = term;
            }
        }
        return String.join(" ", words);
    }

    private static String join(String[] words, int from, int count) {
        StringBuilder out = new StringBuilder(words[from]);
        for (int i = from + 1; i < from + count; i++) {
            out.append(' ').append(words[i]);
        }
        return out.toString();
    }

    /** One spelling of the query: the whole phrase plus the words it must contain. */
    private static final class Pattern {
        private final String phrase;
        private final String compact;
        private final List<String[]> required;

        Pattern(String phrase) {
            this.phrase = phrase;
            this.compact = phrase.replace(" ", "");
            List<String[]> words = new ArrayList<>();
            List<String[]> fillers = new ArrayList<>();
            for (String word : phrase.split(" ")) {
                String digit = NUMBERS.get(word);
                String[] forms = digit == null ? new String[]{word} : new String[]{word, digit};
                (FILLERS.contains(word) ? fillers : words).add(forms);
            }
            this.required = words.isEmpty() ? fillers : words;
        }

        int score(String folded) {
            if (folded.equals(phrase)) {
                return EXACT;
            }
            if (folded.startsWith(phrase)) {
                return PREFIX;
            }
            if (folded.contains(phrase) || (compact.length() >= 3 && folded.replace(" ", "").contains(compact))) {
                return PHRASE;
            }
            for (String[] forms : required) {
                if (!containsAny(folded, forms)) {
                    return NO_MATCH;
                }
            }
            return WORDS;
        }
    }

    private static boolean containsAny(String folded, String[] forms) {
        for (String form : forms) {
            if (folded.contains(form)) {
                return true;
            }
        }
        return false;
    }

    private static String foldCached(String title) {
        synchronized (FOLDED) {
            String folded = FOLDED.get(title);
            if (folded == null) {
                if (FOLDED.size() >= CACHE_LIMIT) {
                    FOLDED.clear();
                }
                folded = fold(title);
                FOLDED.put(title, folded);
            }
            return folded;
        }
    }

    /** Lower-case, unify Arabic letter variants, drop diacritics and punctuation, single spaces. */
    static String fold(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        boolean space = true;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'ً' && c <= 'ْ') || c == 'ٰ' || c == 'ـ') {
                continue;
            }
            switch (c) {
                case 'أ': case 'إ': case 'آ': case 'ٱ':
                    c = 'ا';
                    break;
                case 'ة':
                    c = 'ه';
                    break;
                case 'ى': case 'ئ':
                    c = 'ي';
                    break;
                case 'ؤ':
                    c = 'و';
                    break;
                default:
                    if (c >= '٠' && c <= '٩') {
                        c = (char) ('0' + (c - '٠'));
                    } else if (c >= '۰' && c <= '۹') {
                        c = (char) ('0' + (c - '۰'));
                    }
                    break;
            }
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
                space = false;
            } else if (!space) {
                out.append(' ');
                space = true;
            }
        }
        int end = out.length();
        if (end > 0 && out.charAt(end - 1) == ' ') {
            out.setLength(end - 1);
        }
        return out.toString();
    }
}
