package com.arafatakaid.banglishkeyboard;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * =====================================================
 * BanglaToBanglishConverter
 * Version : 4.0 STEP-1 (Natural Banglish Core)
 * Author  : Arafat Akaid
 * =====================================================
 */

public final class BanglaToBanglishConverter {

    private BanglaToBanglishConverter(){}

    private static final String TAG = "BanglaToBanglish";

    public enum Style{
        NATURAL,
        SIMPLE,
        ACADEMIC
    }

    private static volatile Style currentStyle = Style.NATURAL;

    private static boolean convertDigits = true;
    private static boolean debugMode = false;

    private static final int CACHE_SIZE = 10000;

    private static final Map<String,String> CACHE =
            Collections.synchronizedMap(
                    new LinkedHashMap<String,String>(1024, 0.75f, true){
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String,String> eldest){
                            return size() > CACHE_SIZE;
                        }
                    });

    private static final Map<String,String> DICTIONARY = new ConcurrentHashMap<>(65536);
    private static final Map<String,String> EXCEPTION = new ConcurrentHashMap<>(4096);

    // Multi-word dictionary entries, e.g. "ভালো আছি". Kept separate so
    // sentence conversion can match phrases before converting individual words.
    private static final Map<String,String> PHRASE_DICTIONARY = new ConcurrentHashMap<>(2048);
    private static volatile int MAX_PHRASE_WORDS = 1;

    // Single-codepoint consonants (safe as char literals)
    private static final Map<Character,String> CONSONANTS = new HashMap<>();

    // Consonants that are actually TWO Unicode codepoints (base + nukta),
    // e.g. ড় (ড + ়), ঢ় (ঢ + ়). These CANNOT be char literals in Java,
    // so they are stored as Strings and matched like joint letters.
    private static final Map<String,String> NUKTA_CONSONANTS = new LinkedHashMap<>();

    private static final Map<Character,String> VOWELS = new HashMap<>();
    private static final Map<Character,String> VOWEL_SIGNS = new HashMap<>();
    private static final Map<Character,Character> DIGITS = new HashMap<>();

    private static final Map<String,String> JOINT = new HashMap<>();
    private static final Map<String,String> PREFIX = new HashMap<>();
    private static final Map<String,String> SUFFIX = new HashMap<>();

    private static final char HASANTA = '\u09CD';
    private static final char ANUSWAR = '\u0982';
    private static final char CHANDRA = '\u0981';
    private static final char VISARGA = '\u0903';
    private static final char NUKTA = '\u09BC';

    private static final Pattern PRESERVE_PATTERN = Pattern.compile(
            "(https?://\\S+)"
                    + "|(www\\.\\S+)"
                    + "|([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[A-Za-z]{2,})"
                    + "|(@\\w+)"
                    + "|(#[\\w\\u0980-\\u09FF]+)"
    );

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\u0980-\\u09FF]+");

    static {
        initializeVowels();
        initializeVowelSigns();
        initializeConsonants();
        initializeNuktaConsonants();
        initializeDigits();
        initializeJointLetters();
        initializePrefixRules();
        initializeSuffixRules();
        initializeExceptionDictionary();
    }

    /* ====================================== INITIALIZE VOWELS ====================================== */
    private static void initializeVowels() {
        VOWELS.put('\u0985', "o");
        VOWELS.put('\u0986', "a");
        VOWELS.put('\u0987', "i");
        VOWELS.put('\u0988', "i");
        VOWELS.put('\u0989', "u");
        VOWELS.put('\u098A', "u");
        VOWELS.put('\u098B', "ri");
        VOWELS.put('\u098F', "e");
        VOWELS.put('\u0990', "oi");
        VOWELS.put('\u0993', "o");
        VOWELS.put('\u0994', "ou");
    }

    /* ====================================== INITIALIZE VOWEL SIGNS ====================================== */
    private static void initializeVowelSigns() {
        VOWEL_SIGNS.put('\u09BE', "a");
        VOWEL_SIGNS.put('\u09BF', "i");
        VOWEL_SIGNS.put('\u09C0', "i");
        VOWEL_SIGNS.put('\u09C1', "u");
        VOWEL_SIGNS.put('\u09C2', "u");
        VOWEL_SIGNS.put('\u09C3', "ri");
        VOWEL_SIGNS.put('\u09C7', "e");
        VOWEL_SIGNS.put('\u09C8', "oi");
        VOWEL_SIGNS.put('\u09CB', "o");
        VOWEL_SIGNS.put('\u09CC', "ou");
    }

    /* ====================================== INITIALIZE DIGITS ====================================== */
    private static void initializeDigits() {
        DIGITS.put('\u09E6','0');
        DIGITS.put('\u09E7','1');
        DIGITS.put('\u09E8','2');
        DIGITS.put('\u09E9','3');
        DIGITS.put('\u09EA','4');
        DIGITS.put('\u09EB','5');
        DIGITS.put('\u09EC','6');
        DIGITS.put('\u09ED','7');
        DIGITS.put('\u09EE','8');
        DIGITS.put('\u09EF','9');
    }

    /* ====================================== INITIALIZE CONSONANTS ======================================
     * NOTE: 'ড়' and 'ঢ়' are NOT included here because they are two-codepoint
     * sequences (base consonant + nukta U+09BC) and are not valid single
     * `char` literals in Java. They are handled in NUKTA_CONSONANTS instead.
     * 'য়' (U+09DF, YYA) IS a single precomposed codepoint, so it stays here.
     */
    private static void initializeConsonants() {
        CONSONANTS.put('\u0995', "k");
        CONSONANTS.put('\u0996', "kh");
        CONSONANTS.put('\u0997', "g");
        CONSONANTS.put('\u0998', "gh");
        CONSONANTS.put('\u0999', "ng");

        CONSONANTS.put('\u099A', "ch");
        CONSONANTS.put('\u099B', "chh");
        CONSONANTS.put('\u099C', "j");
        CONSONANTS.put('\u099D', "jh");
        CONSONANTS.put('\u099E', "ny");

        CONSONANTS.put('\u099F', "t");
        CONSONANTS.put('\u09A0', "th");
        CONSONANTS.put('\u09A1', "d");
        CONSONANTS.put('\u09A2', "dh");
        CONSONANTS.put('\u09A3', "n");

        CONSONANTS.put('\u09A4', "t");
        CONSONANTS.put('\u09A5', "th");
        CONSONANTS.put('\u09A6', "d");
        CONSONANTS.put('\u09A7', "dh");
        CONSONANTS.put('\u09A8', "n");

        CONSONANTS.put('\u09AA', "p");
        CONSONANTS.put('\u09AB', "ph");
        CONSONANTS.put('\u09AC', "b");
        CONSONANTS.put('\u09AD', "bh");
        CONSONANTS.put('\u09AE', "m");

        CONSONANTS.put('\u09AF', "j");
        CONSONANTS.put('\u09B0', "r");
        CONSONANTS.put('\u09B2', "l");

        CONSONANTS.put('\u09B6', "sh");
        CONSONANTS.put('\u09B7', "sh");
        CONSONANTS.put('\u09B8', "s");
        CONSONANTS.put('\u09B9', "h");

        CONSONANTS.put('\u09DF', "y");
        CONSONANTS.put('\u09CE', "t");
    }

    /* ====================================== INITIALIZE NUKTA CONSONANTS ======================================
     * These are String-keyed (base consonant codepoint + U+09BC nukta codepoint)
     * because they cannot legally be represented as a single Java `char`.
     */
    private static void initializeNuktaConsonants() {
        NUKTA_CONSONANTS.put("\u09A1\u09BC", "r");  // ড়
        NUKTA_CONSONANTS.put("\u09A2\u09BC", "rh");  // ঢ়
    }

    /* ====================================== INITIALIZE JOINT LETTERS ====================================== */
    private static void initializeJointLetters() {
        JOINT.put("ক্ক", "kk");
        JOINT.put("ক্ত", "kt");
        JOINT.put("ক্ত্র", "ktr");
        JOINT.put("ক্ত্ব", "ktto");
        JOINT.put("ক্ল", "kl");
        JOINT.put("ক্ব", "kb");
        JOINT.put("ক্ষ", "kkh");
        JOINT.put("ক্ষ্ম", "kkhm");
        JOINT.put("ক্ষ্ণ", "kkhn");
        JOINT.put("ক্ষ্য", "kkhy");
        JOINT.put("ক্স", "ks");

        JOINT.put("গ্ধ", "gdh");
        JOINT.put("গ্ন", "gn");
        JOINT.put("গ্ন্য", "gny");
        JOINT.put("গ্ল", "gl");
        JOINT.put("গ্ব", "gb");
        JOINT.put("ঘ্ন", "ghn");

        JOINT.put("ঙ্ক", "nk");
        JOINT.put("ঙ্খ", "nkh");
        JOINT.put("ঙ্গ", "ng");
        JOINT.put("ঙ্গ্য", "ngy");
        JOINT.put("ঙ্ঘ", "ngh");
        JOINT.put("ঙ্ক্ষ", "nksh");

        JOINT.put("চ্চ", "cc");
        JOINT.put("চ্ছ", "cch");
        JOINT.put("চ্ছ্ব", "cchw");

        JOINT.put("জ্জ", "jj");
        JOINT.put("জ্জ্ব", "jjw");

        JOINT.put("ঞ্চ", "nch");
        JOINT.put("ঞ্ছ", "nchh");
        JOINT.put("ঞ্জ", "nj");
        JOINT.put("ঞ্ঝ", "njh");

        JOINT.put("ট্ট", "tt");
        JOINT.put("ড্ড", "dd");
        JOINT.put("ণ্ট", "nt");
        JOINT.put("ন্ঠ", "nth");
        JOINT.put("ণ্ড", "nd");

        JOINT.put("ত্ত", "tt");
        JOINT.put("ত্ত্ব", "ttw");
        JOINT.put("ত্ম", "tm");
        JOINT.put("ত্র", "tr");
        JOINT.put("থ্য", "thy");

        JOINT.put("দ্ভ", "dbh");
        JOINT.put("দ্ম", "dm");
        JOINT.put("দ্য", "dy");
        JOINT.put("দ্র", "dr");
        JOINT.put("দ্ধ", "ddh");
        JOINT.put("দ্ব", "db");

        JOINT.put("ধ্ব", "dhw");

        JOINT.put("ন্ত", "nt");
        JOINT.put("ন্ত্র", "ntr");
        JOINT.put("ন্থ", "nth");
        JOINT.put("ন্দ", "nd");
        JOINT.put("ন্ধ", "ndh");
        JOINT.put("ন্ন", "nn");
        JOINT.put("ন্ম", "nm");

        JOINT.put("প্ত", "pt");
        JOINT.put("প্ল", "pl");
        JOINT.put("ব্দ", "bd");
        JOINT.put("ব্ধ", "bdh");
        JOINT.put("ব্র", "br");
        JOINT.put("ব্য", "by");
        JOINT.put("ভ্র", "bhr");
        JOINT.put("ম্প", "mp");
        JOINT.put("ম্ব", "mb");
        JOINT.put("ম্ভ", "mbh");
        JOINT.put("ম্ম", "mm");
        JOINT.put("ম্য", "my");

        JOINT.put("ল্ক", "lk");
        JOINT.put("ল্প", "lp");
        JOINT.put("ল্ল", "ll");
        JOINT.put("শ্চ", "shc");
        JOINT.put("শ্ছ", "shchh");
        JOINT.put("শ্ন", "shn");
        JOINT.put("শ্ব", "shw");
        JOINT.put("ষ্ট", "sht");
        JOINT.put("স্ট", "st");
        JOINT.put("স্ত", "st");
        JOINT.put("স্থ", "sth");
        JOINT.put("স্প", "sp");
        JOINT.put("স্ফ", "sf");
        JOINT.put("স্ক", "sk");
        JOINT.put("স্ম", "sm");
        JOINT.put("স্ব", "sw");
        JOINT.put("হ্ন", "hn");
        JOINT.put("হ্ম", "hm");
        JOINT.put("হ্য", "hy");
    }

    /* ====================================== INITIALIZE PREFIX RULES ====================================== */
    private static void initializePrefixRules() {
        PREFIX.put("অধি", "odhi");
        PREFIX.put("অতি", "oti");
        PREFIX.put("অপ", "opo");
        PREFIX.put("অন", "on");
        PREFIX.put("অ", "o");

        PREFIX.put("উপ", "upo");
        PREFIX.put("উৎ", "ut");

        PREFIX.put("প্রতি", "proti");
        PREFIX.put("প্র", "pro");

        PREFIX.put("বি:", "bi");
        PREFIX.put("বি", "bi");

        PREFIX.put("সু", "su");
        PREFIX.put("কু", "ku");
        PREFIX.put("মহা", "moha");
        PREFIX.put("সহ", "soho");
        PREFIX.put("নির", "nir");
        PREFIX.put("দুর", "dur");
        PREFIX.put("পরা", "pora");
        PREFIX.put("আন্ত", "anto");
        PREFIX.put("সর্ব", "shorbo");
    }

    /* ====================================== INITIALIZE SUFFIX RULES ====================================== */
    private static void initializeSuffixRules() {
        SUFFIX.put("গুলো", "gulo");
        SUFFIX.put("গুলি", "guli");
        SUFFIX.put("দের", "der");
        SUFFIX.put("য়ের", "yer");
        SUFFIX.put("ভাবে", "bhabe");
        SUFFIX.put("খানা", "khana");
        SUFFIX.put("খানি", "khani");
        SUFFIX.put("জনক", "jonok");
        SUFFIX.put("কারী", "kari");
        SUFFIX.put("শীল", "shil");
        SUFFIX.put("পূর্ণ", "purno");
        SUFFIX.put("টা", "ta");
        SUFFIX.put("টি", "ti");
        SUFFIX.put("কে", "ke");
        SUFFIX.put("তে", "te");
        SUFFIX.put("এর", "er");
        SUFFIX.put("জন", "jon");
        SUFFIX.put("ত্ব", "tto");
        SUFFIX.put("ময়", "moy");
        SUFFIX.put("র", "r");
    }

    /* ====================================== INITIALIZE EXCEPTION DICTIONARY ====================================== */
    private static void initializeExceptionDictionary() {
        EXCEPTION.put("আমি", "ami");
        EXCEPTION.put("আমরা", "amra");
        EXCEPTION.put("তুমি", "tumi");
        EXCEPTION.put("তোমরা", "tomra");
        EXCEPTION.put("সে", "she");
        EXCEPTION.put("তিনি", "tini");
        EXCEPTION.put("আপনি", "apni");
        EXCEPTION.put("আপনার", "apnar");

        EXCEPTION.put("বাংলা", "bangla");
        EXCEPTION.put("বাংলাদেশ", "bangladesh");
        EXCEPTION.put("ভালো", "bhalo");
        EXCEPTION.put("খারাপ", "kharap");
        EXCEPTION.put("বন্ধু", "bondhu");
        EXCEPTION.put("দেশ", "desh");
        EXCEPTION.put("মানুষ", "manush");
        EXCEPTION.put("পৃথিবী", "prithibi");

        EXCEPTION.put("স্বাধীনতা", "swadhinota");
        EXCEPTION.put("ঔষধ", "oshudh");
        EXCEPTION.put("ওষুধ", "oshudh");
        EXCEPTION.put("দায়িত্ব", "dayitto");
        EXCEPTION.put("বিজ্ঞান", "biggan");
        EXCEPTION.put("জ্ঞান", "gyan");
        EXCEPTION.put("অজ্ঞ", "oggo");
        EXCEPTION.put("রাষ্ট্র", "rashtro");
        EXCEPTION.put("স্বাস্থ্য", "shastho");
        EXCEPTION.put("বিশ্ব", "bishsho");
        EXCEPTION.put("লক্ষ্য", "lokkho");
        EXCEPTION.put("রক্ষা", "rokkha");
        EXCEPTION.put("শিক্ষা", "shikkha");
        EXCEPTION.put("শিক্ষক", "shikkhok");

        EXCEPTION.put("করছি", "korchi");
        EXCEPTION.put("করেছিল", "korechilo");
        EXCEPTION.put("করবে", "korbe");
        EXCEPTION.put("করেছি", "korechi");
        EXCEPTION.put("খাচ্ছি", "khacchi");
        EXCEPTION.put("যাচ্ছি", "jacchi");

        EXCEPTION.put("বিজয়নগর", "bijoynogor");
        EXCEPTION.put("ব্রাহ্মণবাড়িয়া", "brahmanbaria");
        EXCEPTION.put("ঢাকা", "dhaka");
        EXCEPTION.put("চট্টগ্রাম", "chattogram");
        EXCEPTION.put("রাজশাহী", "rajshahi");
        EXCEPTION.put("খুলনা", "khulna");
        EXCEPTION.put("সিলেট", "sylhet");
        EXCEPTION.put("বরিশাল", "barishal");
        EXCEPTION.put("রংপুর", "rangpur");
        EXCEPTION.put("ময়মনসিংহ", "mymensingh");
    }

    /* ====================================== SETTINGS ====================================== */
    public static void setStyle(Style style) {
        if (style != null) {
            currentStyle = style;
            CACHE.clear();
        }
    }

    public static Style getStyle() { return currentStyle; }

    public static void setDebugMode(boolean enable) { debugMode = enable; }
    public static boolean isDebugMode() { return debugMode; }

    public static void setConvertDigits(boolean enable) { convertDigits = enable; }
    public static boolean isConvertDigits() { return convertDigits; }

    public static void clearCache() { CACHE.clear(); }

    private static void log(String message) {
        if (debugMode) Log.d(TAG, message);
    }

    /* ====================================== DICTIONARY ====================================== */
    public static void addDictionaryWord(String bangla, String banglish) {
        if (bangla == null || banglish == null) return;
        bangla = cleanUnicode(Normalizer.normalize(bangla, Normalizer.Form.NFC));
        DICTIONARY.put(bangla.trim(), banglish.trim());
        rebuildPhraseEntry(bangla.trim(), banglish.trim());
        CACHE.remove(bangla);
    }

    public static void removeDictionaryWord(String bangla) {
        if (bangla == null) return;
        DICTIONARY.remove(cleanUnicode(bangla));
        CACHE.remove(bangla);
    }

    public static void clearDictionary() {
        DICTIONARY.clear();
        PHRASE_DICTIONARY.clear();
        MAX_PHRASE_WORDS = 1;
        CACHE.clear();
    }

    /* ====================================== LOAD DICTIONARY ====================================== */
    public static void loadDictionaryFromAssets(Context context, String fileName) {
        if (context == null || fileName == null) return;

        try {
            InputStream is = context.getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            reader.close();

            JSONObject object = new JSONObject(builder.toString());
            Iterator<String> keys = object.keys();

            while (keys.hasNext()) {
                String key = keys.next();
                String normalizedKey = cleanUnicode(key);
                String value = object.getString(key);
                DICTIONARY.put(normalizedKey, value);
                rebuildPhraseEntry(normalizedKey, value);
            }

            CACHE.clear();

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Dictionary Load Failed", e);
        }
    }

    /* ====================================== CLEAN UNICODE ====================================== */
    private static String cleanUnicode(String text) {
        if (text == null) return "";
        text = Normalizer.normalize(text, Normalizer.Form.NFC);
        text = text.replace("\u200C", "");
        text = text.replace("\u200D", "");
        text = text.replace("\uFEFF", "");
        return text.trim();
    }

    /* ====================================== LOOKUP DICTIONARY ====================================== */
    private static String lookupDictionary(String word) {
        if (word == null || word.isEmpty()) return null;

        word = cleanUnicode(word);

        String cache = CACHE.get(word);
        if (cache != null) return cache;

        String value = DICTIONARY.get(word);
        if (value == null) value = EXCEPTION.get(word);

        if (value != null) CACHE.put(word, value);

        return value;
    }

    /* ====================================== PHRASE DICTIONARY ====================================== */
    private static void rebuildPhraseEntry(String key, String value) {
        if (key == null || value == null) return;
        String normalized = cleanUnicode(key);
        if (normalized.indexOf(' ') < 0) return;
        if (!containsBangla(normalized)) return;

        PHRASE_DICTIONARY.put(normalized, value.trim());
        int words = countBanglaWords(normalized);
        if (words > MAX_PHRASE_WORDS) MAX_PHRASE_WORDS = words;
    }

    private static boolean containsBangla(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0980' && c <= '\u09FF') return true;
        }
        return false;
    }

    private static int countBanglaWords(String text) {
        Matcher m = WORD_PATTERN.matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    /**
     * Converts a gap while giving exact multi-word dictionary phrases priority.
     * Only whitespace-separated Bengali words are joined; punctuation and
     * English text remain untouched.
     */
    private static String processGapWithPhrases(String gap) {
        if (gap == null || gap.isEmpty() || PHRASE_DICTIONARY.isEmpty()) {
            return processGapWordsOnly(gap);
        }

        Matcher m = WORD_PATTERN.matcher(gap);
        java.util.ArrayList<String> words = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> starts = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> ends = new java.util.ArrayList<>();

        while (m.find()) {
            words.add(m.group());
            starts.add(m.start());
            ends.add(m.end());
        }
        if (words.isEmpty()) return gap;

        StringBuilder out = new StringBuilder();
        int cursor = 0;
        int i = 0;

        while (i < words.size()) {
            out.append(gap, cursor, starts.get(i));

            String bestValue = null;
            int bestEnd = i + 1;
            int maxWords = Math.min(MAX_PHRASE_WORDS, words.size() - i);

            // Longest exact Bengali phrase wins. Separators must be whitespace only.
            for (int count = maxWords; count >= 2; count--) {
                boolean whitespaceOnly = true;
                for (int w = i; w < i + count - 1; w++) {
                    String sep = gap.substring(ends.get(w), starts.get(w + 1));
                    if (!sep.matches("\\s+")) {
                        whitespaceOnly = false;
                        break;
                    }
                }
                if (!whitespaceOnly) continue;

                StringBuilder phrase = new StringBuilder();
                for (int w = i; w < i + count; w++) {
                    if (w > i) phrase.append(' ');
                    phrase.append(cleanUnicode(words.get(w)));
                }

                String value = PHRASE_DICTIONARY.get(phrase.toString());
                if (value != null) {
                    bestValue = value;
                    bestEnd = i + count;
                    break;
                }
            }

            if (bestValue != null) {
                out.append(bestValue);
                cursor = ends.get(bestEnd - 1);
                i = bestEnd;
            } else {
                out.append(processWord(words.get(i)));
                cursor = ends.get(i);
                i++;
            }
        }

        out.append(gap.substring(cursor));
        return out.toString();
    }

    private static String processGapWordsOnly(String gap) {
        if (gap == null || gap.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        Matcher wordMatcher = WORD_PATTERN.matcher(gap);
        int last = 0;
        while (wordMatcher.find()) {
            sb.append(gap, last, wordMatcher.start());
            sb.append(processWord(wordMatcher.group()));
            last = wordMatcher.end();
        }
        sb.append(gap.substring(last));
        return sb.toString();
    }

    /* ====================================== LONGEST JOINT MATCH ====================================== */
    private static String findLongestJoint(String word, int start) {
        int maxLen = Math.min(8, word.length() - start);
        for (int len = maxLen; len >= 2; len--) {
            String sub = word.substring(start, start + len);
            if (JOINT.containsKey(sub)) return sub;
        }
        return null;
    }

    /* ====================================== NUKTA CONSONANT MATCH (ড়, ঢ়) ====================================== */
    private static String findNuktaConsonant(String word, int start) {
        if (start + 1 < word.length()) {
            String pair = word.substring(start, start + 2);
            if (NUKTA_CONSONANTS.containsKey(pair)) return pair;
        }
        return null;
    }

    /* ====================================== CONTEXT HELPERS ====================================== */

    /**
     * Returns true when the current consonant is the final pronounced consonant
     * of the word/segment. A final inherent vowel is normally NOT written in
     * natural Banglish: কর -> kor, মন -> mon, দেশ -> desh.
     */
    private static boolean isWordFinalConsonant(String word, int nextIndex) {
        if (nextIndex >= word.length()) return true;

        char next = word.charAt(nextIndex);
        // A vowel sign supplies the vowel explicitly.
        if (VOWEL_SIGNS.containsKey(next)) return false;
        // Hasanta means the consonant is closed and the next cluster continues.
        if (next == HASANTA) return false;
        // Nukta/combining marks belong to the current consonant.
        if (next == NUKTA || next == CHANDRA) return false;
        return false;
    }


    /**
     * Handles common Bengali phala forms when a conjunct is not explicitly listed
     * in JOINT. This is a fallback, not a replacement for word-level exceptions.
     */
    private static String genericFola(String word, int start) {
        if (start + 2 >= word.length()) return null;
        char base = word.charAt(start);
        if (!CONSONANTS.containsKey(base) || word.charAt(start + 1) != HASANTA) return null;

        char fola = word.charAt(start + 2);
        String baseRoman = CONSONANTS.get(base);
        if (fola == '\u09B0') return baseRoman + "r"; // র-ফলা
        if (fola == '\u09AF') return baseRoman + "y"; // য-ফলা
        if (fola == '\u09AC') return baseRoman + "w"; // ব-ফলা
        if (fola == '\u09AE') return baseRoman + "m"; // ম-ফলা
        return null;
    }

    /** Consume a generic fola and return the index after it, or -1. */
    private static int consumeGenericFola(String word, int start, StringBuilder sb) {
        String roman = genericFola(word, start);
        if (roman == null) return -1;
        sb.append(roman);
        return start + 3;
    }

    /** Common pronunciation aliases for difficult signs in natural mode. */
    private static String naturalSign(String value) {
        if (currentStyle == Style.NATURAL) {
            if ("ph".equals(value)) return "f";
            if ("sh".equals(value)) return "sh";
        }
        return value;
    }

    /* ====================================== CORE TRANSLITERATION ====================================== */
    private static String transliterateCore(String word) {
        if (word == null || word.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        int i = 0;
        final int len = word.length();

        while (i < len) {
            // 1) Longest explicit conjunct first.
            String jointMatch = findLongestJoint(word, i);
            if (jointMatch != null) {
                sb.append(JOINT.get(jointMatch));
                i += jointMatch.length();

                if (i < len && VOWEL_SIGNS.containsKey(word.charAt(i))) {
                    sb.append(naturalSign(VOWEL_SIGNS.get(word.charAt(i))));
                    i++;
                } else if (i < len && word.charAt(i) == HASANTA) {
                    i++;
                } else {
                    // A closed conjunct at the end of a Bengali word often
                    // carries a pronounced inherent vowel: প্রশ্ন -> proshno,
                    // স্বপ্ন -> shopno, শক্ত -> shokto.
                    sb.append("o");
                }
                continue;
            }

            // 2) Generic fola fallback: C + hasanta + র/য/ব/ম.
            int folaEnd = consumeGenericFola(word, i, sb);
            if (folaEnd >= 0) {
                i = folaEnd;
                if (i < len && VOWEL_SIGNS.containsKey(word.charAt(i))) {
                    sb.append(naturalSign(VOWEL_SIGNS.get(word.charAt(i))));
                    i++;
                } else if (i < len && word.charAt(i) == HASANTA) {
                    i++;
                } else if (i < len) {
                    sb.append("o");
                }
                continue;
            }

            // 3) Nukta consonants: ড় / ঢ়.
            String nuktaMatch = findNuktaConsonant(word, i);
            if (nuktaMatch != null) {
                sb.append(NUKTA_CONSONANTS.get(nuktaMatch));
                i += nuktaMatch.length();

                if (i < len && word.charAt(i) == HASANTA) {
                    i++;
                } else if (i < len && VOWEL_SIGNS.containsKey(word.charAt(i))) {
                    sb.append(naturalSign(VOWEL_SIGNS.get(word.charAt(i))));
                    i++;
                } else if (i < len) {
                    sb.append("o");
                }
                continue;
            }

            char c = word.charAt(i);

            // 4) Reph: র্ before another consonant.
            if (c == '\u09B0' && i + 1 < len && word.charAt(i + 1) == HASANTA) {
                if (i + 2 < len) {
                    sb.append("r");
                    i += 2;
                    continue;
                }
            }

            // 5) Hasanta itself: no vowel.
            if (c == HASANTA) {
                i++;
                continue;
            }

            // 6) Consonants.
            if (CONSONANTS.containsKey(c)) {
                String roman = CONSONANTS.get(c);

                // Generic two-consonant conjunct fallback. This covers clusters
                // not explicitly listed in JOINT, e.g. স্বপ্ন -> swopno.
                if (i + 2 < len && word.charAt(i + 1) == HASANTA) {
                    char nextConsonant = word.charAt(i + 2);
                    if (CONSONANTS.containsKey(nextConsonant)) {
                        String nextRoman = CONSONANTS.get(nextConsonant);
                        if (nextConsonant == '\u09AF') nextRoman = "y";
                        else if (nextConsonant == '\u09AC') nextRoman = "w";
                        else if (nextConsonant == '\u09AE') nextRoman = "m";
                        else if (nextConsonant == '\u09B0') nextRoman = "r";
                        sb.append(naturalSign(roman)).append(naturalSign(nextRoman));
                        i += 3;
                        if (i < len && VOWEL_SIGNS.containsKey(word.charAt(i))) {
                            sb.append(naturalSign(VOWEL_SIGNS.get(word.charAt(i))));
                            i++;
                        } else {
                            sb.append("o");
                        }
                        continue;
                    }
                }
                // য is usually typed as "j" initially (যে -> je), but after a
                // vowel it behaves like the semivowel "y" (ক্রিয়া -> kriya).
                if (c == '\u09AF' && i > 0) {
                    char prev = word.charAt(i - 1);
                    if (VOWEL_SIGNS.containsKey(prev) || VOWELS.containsKey(prev)) {
                        roman = "y";
                    }
                }
                sb.append(naturalSign(roman));
                i++;

                if (i < len && word.charAt(i) == NUKTA) {
                    i++;
                }

                if (i < len && word.charAt(i) == HASANTA) {
                    // If the following character is a known fola, let the next
                    // iteration consume the full fola; otherwise simply close it.
                    if (i + 1 < len) {
                        char next = word.charAt(i + 1);
                        if (next == '\u09B0' || next == '\u09AF' || next == '\u09AC' || next == '\u09AE') {
                            // Keep hasanta for genericFola by rewinding one unit.
                            i--;
                            continue;
                        }
                    }
                    i++;
                } else if (i < len && VOWEL_SIGNS.containsKey(word.charAt(i))) {
                    sb.append(naturalSign(VOWEL_SIGNS.get(word.charAt(i))));
                    i++;
                } else if (i < len) {
                    sb.append("o");
                }
                continue;
            }

            // 7) Independent vowels.
            if (VOWELS.containsKey(c)) {
                sb.append(VOWELS.get(c));
                i++;
                continue;
            }

            // 8) Standalone vowel signs (defensive fallback).
            if (VOWEL_SIGNS.containsKey(c)) {
                sb.append(naturalSign(VOWEL_SIGNS.get(c)));
                i++;
                continue;
            }

            // 9) Nasal/visarga signs.
            if (c == ANUSWAR) {
                sb.append("ng");
                i++;
                continue;
            }
            if (c == CHANDRA) {
                sb.append("n");
                i++;
                continue;
            }
            if (c == VISARGA) {
                sb.append("h");
                i++;
                continue;
            }

            // 10) Nukta combining mark on its own: ignore.
            if (c == NUKTA) {
                i++;
                continue;
            }

            // 11) Bengali digits.
            if (DIGITS.containsKey(c)) {
                if (convertDigits) sb.append(DIGITS.get(c));
                else sb.append(c);
                i++;
                continue;
            }

            // 12) Any other character is preserved.
            sb.append(c);
            i++;
        }

        return sb.toString();
    }

    /* ====================================== PROCESS WORD ====================================== */
    private static String processWord(String word) {
        if (word == null || word.isEmpty()) return word;

        String cleaned = cleanUnicode(word);

        // Exact dictionary/exception always wins. This is the safest path for
        // natural Banglish spellings and irregular pronunciations.
        String direct = lookupDictionary(cleaned);
        if (direct != null) return direct;

        String remaining = cleaned;
        StringBuilder prefixOut = new StringBuilder();
        String suffixOut = "";

        // Prefix matching is conservative: only use it when a meaningful core
        // remains, otherwise it can corrupt short/common words.
        String prefixMatch = null;
        for (Map.Entry<String, String> e : PREFIX.entrySet()) {
            String key = e.getKey();
            if (remaining.startsWith(key) && remaining.length() - key.length() >= 2) {
                if (prefixMatch == null || key.length() > prefixMatch.length()) {
                    prefixMatch = key;
                }
            }
        }
        if (prefixMatch != null) {
            prefixOut.append(PREFIX.get(prefixMatch));
            remaining = remaining.substring(prefixMatch.length());
        }

        // Suffix matching is also conservative. Very short cores are left to
        // the transliteration engine instead of forcing a morphological split.
        String suffixMatch = null;
        for (Map.Entry<String, String> e : SUFFIX.entrySet()) {
            String key = e.getKey();
            if (remaining.endsWith(key) && remaining.length() - key.length() >= 2) {
                if (suffixMatch == null || key.length() > suffixMatch.length()) {
                    suffixMatch = key;
                }
            }
        }
        if (suffixMatch != null) {
            suffixOut = SUFFIX.get(suffixMatch);
            remaining = remaining.substring(0, remaining.length() - suffixMatch.length());
        }

        String core = transliterateCore(remaining);
        String result = prefixOut.toString() + core + suffixOut;

        if (result.isEmpty()) result = transliterateCore(cleaned);

        CACHE.put(cleaned, result);
        return result;
    }

    /* ====================================== PROCESS GAP (non-preserved text) ====================================== */
    private static String processGap(String gap) {
        return processGapWithPhrases(gap);
    }

    /* ====================================== PROCESS SENTENCE ====================================== */
    public static String processSentence(String text) {
        if (text == null || text.isEmpty()) return "";

        StringBuilder result = new StringBuilder();
        Matcher preserveMatcher = PRESERVE_PATTERN.matcher(text);
        int lastEnd = 0;

        while (preserveMatcher.find()) {
            String gap = text.substring(lastEnd, preserveMatcher.start());
            result.append(processGap(gap));
            result.append(preserveMatcher.group());
            lastEnd = preserveMatcher.end();
        }

        result.append(processGap(text.substring(lastEnd)));
        return result.toString();
    }

    /* ====================================== CONVERT (MAIN ENTRY) ====================================== */
    public static String convert(String text) {
        if (text == null || text.isEmpty()) return "";

        String cleaned = cleanUnicode(text);
        log("Converting: " + cleaned);

        String result = processSentence(cleaned);
        log("Result: " + result);

        return result;
    }
}
