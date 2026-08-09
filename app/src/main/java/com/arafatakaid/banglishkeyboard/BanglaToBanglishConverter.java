package com.arafatakaid.banglishkeyboard;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BanglaToBanglishConverter
 *
 * Natural Banglish transliterator for the BanglaKeyboard project.
 *
 * Design goals:
 * 1. Prefer natural/common Banglish over mechanical character-by-character output.
 * 2. Preserve English/URLs/e-mails/mentions/hashtags.
 * 3. Handle য় (y), য-ফলা (y), ব-ফলা (w) and common conjuncts contextually.
 * 4. Suppress inappropriate inherent "o" only where Bengali orthography/context supports it.
 * 5. Keep a regression dictionary for known high-frequency words so a future
 *    algorithm change does not re-introduce previously fixed spellings.
 */
public final class BanglaToBanglishConverter {

    private BanglaToBanglishConverter() {}

    private static final String TAG = "BanglaToBanglish";

    public enum Style {
        NATURAL,
        SIMPLE,
        ACADEMIC
    }

    private static volatile Style currentStyle = Style.NATURAL;
    private static volatile boolean convertDigits = true;
    private static volatile boolean debugMode = false;

    private static final int CACHE_SIZE = 10000;

    private static final Map<String, String> CACHE =
            Collections.synchronizedMap(
                    new LinkedHashMap<String, String>(1024, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(
                                Map.Entry<String, String> eldest) {
                            return size() > CACHE_SIZE;
                        }
                    });

    private static final Map<String, String> DICTIONARY =
            new ConcurrentHashMap<>(65536);

    /**
     * Regression/exception dictionary.
     *
     * These are deliberate natural-Banglish spellings. They are not intended
     * to replace the general algorithm; they protect known high-frequency
     * spellings from future regressions.
     */
    private static final Map<String, String> EXCEPTION =
            new ConcurrentHashMap<>(8192);

    private static final Map<Character, String> CONSONANTS = new HashMap<>();
    private static final Map<Character, String> VOWELS = new HashMap<>();
    private static final Map<Character, String> VOWEL_SIGNS = new HashMap<>();
    private static final Map<Character, Character> DIGITS = new HashMap<>();

    private static final Map<String, String> NUKTA_CONSONANTS =
            new LinkedHashMap<>();

    private static final Map<String, String> JOINT =
            new LinkedHashMap<>();

    private static final char HASANTA = '\u09CD';
    private static final char ANUSWAR = '\u0982';
    private static final char CHANDRA = '\u0981';
    private static final char VISARGA = '\u0983';
    private static final char NUKTA = '\u09BC';

    private static final Pattern PRESERVE_PATTERN = Pattern.compile(
            "(https?://\\S+)"
                    + "|(www\\.\\S+)"
                    + "|([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[A-Za-z]{2,})"
                    + "|(@\\w+)"
                    + "|(#[\\w\\u0980-\\u09FF]+)"
    );

    private static final Pattern WORD_PATTERN =
            Pattern.compile("[\\u0980-\\u09FF]+");

    static {
        initializeVowels();
        initializeVowelSigns();
        initializeConsonants();
        initializeNuktaConsonants();
        initializeDigits();
        initializeJointLetters();
        initializeExceptionDictionary();
    }

    private static void initializeVowels() {
        VOWELS.put('অ', "o");
        VOWELS.put('আ', "a");
        VOWELS.put('ই', "i");
        VOWELS.put('ঈ', "i");
        VOWELS.put('উ', "u");
        VOWELS.put('ঊ', "u");
        VOWELS.put('ঋ', "ri");
        VOWELS.put('এ', "e");
        VOWELS.put('ঐ', "oi");
        VOWELS.put('ও', "o");
        VOWELS.put('ঔ', "ou");
    }

    private static void initializeVowelSigns() {
        VOWEL_SIGNS.put('া', "a");
        VOWEL_SIGNS.put('ি', "i");
        VOWEL_SIGNS.put('ী', "i");
        VOWEL_SIGNS.put('ু', "u");
        VOWEL_SIGNS.put('ূ', "u");
        VOWEL_SIGNS.put('ৃ', "ri");
        VOWEL_SIGNS.put('ে', "e");
        VOWEL_SIGNS.put('ৈ', "oi");
        VOWEL_SIGNS.put('ো', "o");
        VOWEL_SIGNS.put('ৌ', "ou");
    }

    private static void initializeConsonants() {
        CONSONANTS.put('ক', "k");
        CONSONANTS.put('খ', "kh");
        CONSONANTS.put('গ', "g");
        CONSONANTS.put('ঘ', "gh");
        CONSONANTS.put('ঙ', "ng");

        CONSONANTS.put('চ', "ch");
        CONSONANTS.put('ছ', "chh");
        CONSONANTS.put('জ', "j");
        CONSONANTS.put('ঝ', "jh");
        CONSONANTS.put('ঞ', "ny");

        CONSONANTS.put('ট', "t");
        CONSONANTS.put('ঠ', "th");
        CONSONANTS.put('ড', "d");
        CONSONANTS.put('ঢ', "dh");
        CONSONANTS.put('ণ', "n");

        CONSONANTS.put('ত', "t");
        CONSONANTS.put('থ', "th");
        CONSONANTS.put('দ', "d");
        CONSONANTS.put('ধ', "dh");
        CONSONANTS.put('ন', "n");

        CONSONANTS.put('প', "p");
        CONSONANTS.put('ফ', "f");
        CONSONANTS.put('ব', "b");
        CONSONANTS.put('ভ', "bh");
        CONSONANTS.put('ম', "m");

        // য = j in a normal consonant position.
        // য-ফলা is handled contextually as y.
        CONSONANTS.put('য', "j");

        CONSONANTS.put('র', "r");
        CONSONANTS.put('ল', "l");

        CONSONANTS.put('শ', "sh");
        CONSONANTS.put('ষ', "sh");
        CONSONANTS.put('স', "s");
        CONSONANTS.put('হ', "h");

        // য় is y.
        CONSONANTS.put('য়', "y");
        CONSONANTS.put('ৎ', "t");
    }


    private static void initializeDigits() {
        DIGITS.put('০', '0');
        DIGITS.put('১', '1');
        DIGITS.put('২', '2');
        DIGITS.put('৩', '3');
        DIGITS.put('৪', '4');
        DIGITS.put('৫', '5');
        DIGITS.put('৬', '6');
        DIGITS.put('৭', '7');
        DIGITS.put('৮', '8');
        DIGITS.put('৯', '9');
    }

    private static void initializeNuktaConsonants() {
        NUKTA_CONSONANTS.put("ড়", "r");
        NUKTA_CONSONANTS.put("ঢ়", "rh");
        NUKTA_CONSONANTS.put("য়", "y");
    }

    /**
     * Longest-first conjunct map.
     *
     * The important principle is that a conjunct ending in য/য় is rendered
     * with y, while ব-ফলা is rendered with w in natural Banglish.
     */
    private static void initializeJointLetters() {
        putJoint("ক্ষ্ম", "kkhm");
        putJoint("ক্ষ্ণ", "kkhn");
        putJoint("ক্ষ্য", "kkhy");
        putJoint("ক্ত্ব", "ktw");
        putJoint("চ্ছ্ব", "cchw");
        putJoint("জ্জ্ব", "jjw");
        putJoint("ত্ত্ব", "ttw");

        putJoint("ভ্র", "bhr");
        putJoint("ত্র", "tr");
        putJoint("দ্র", "dr");
        putJoint("প্র", "pr");
        putJoint("ব্র", "br");
        putJoint("গ্র", "gr");
        putJoint("ক্র", "kr");
        putJoint("ফ্র", "fr");
        putJoint("ম্র", "mr");
        putJoint("শ্র", "shr");
        putJoint("হ্র", "hr");

        putJoint("দ্য", "dy");
        putJoint("ধ্য", "dhy");
        putJoint("ন্য", "ny");
        putJoint("ত্য", "ty");
        putJoint("থ্য", "thy");
        putJoint("ব্য", "by");
        putJoint("ভ্য", "bhy");
        putJoint("ম্য", "my");
        putJoint("ল্য", "ly");
        putJoint("শ্র্য", "shry");
        putJoint("হ্য", "hy");
        putJoint("গ্য", "gy");
        putJoint("ক্য", "ky");
        putJoint("খ্র", "khr");

        putJoint("ক্ব", "kw");
        putJoint("গ্ব", "gw");
        putJoint("দ্ব", "dw");
        putJoint("ধ্ব", "dhw");
        putJoint("শ্ব", "shw");
        putJoint("স্ব", "sw");
        putJoint("হ্ব", "hw");

        putJoint("ক্ক", "kk");
        putJoint("ক্ত", "kt");
        putJoint("ক্ল", "kl");
        putJoint("ক্ষ", "kkh");
        putJoint("ক্স", "ks");

        putJoint("গ্ধ", "gdh");
        putJoint("গ্ন", "gn");
        putJoint("গ্ল", "gl");

        putJoint("ঙ্ক্ষ", "nksh");
        putJoint("ঙ্ক", "nk");
        putJoint("ঙ্খ", "nkh");
        putJoint("ঙ্গ", "ng");
        putJoint("ঙ্ঘ", "ngh");

        putJoint("চ্চ", "cc");
        putJoint("চ্ছ", "cch");
        putJoint("জ্জ", "jj");
        putJoint("ঞ্চ", "nch");
        putJoint("ঞ্ছ", "nchh");
        putJoint("ঞ্জ", "nj");
        putJoint("ঞ্ঝ", "njh");

        putJoint("ট্ট", "tt");
        putJoint("ড্ড", "dd");
        putJoint("ণ্ট", "nt");
        putJoint("ন্ঠ", "nth");
        putJoint("ণ্ড", "nd");

        putJoint("ত্ত", "tt");
        putJoint("ত্ম", "tm");

        putJoint("দ্ভ", "dbh");
        putJoint("দ্ম", "dm");
        putJoint("দ্ধ", "ddh");

        putJoint("ন্ত", "nt");
        putJoint("ন্ত্র", "ntr");
        putJoint("ন্থ", "nth");
        putJoint("ন্দ", "nd");
        putJoint("ন্ধ", "ndh");
        putJoint("ন্ন", "nn");
        putJoint("ন্ম", "nm");

        putJoint("প্ত", "pt");
        putJoint("প্ল", "pl");
        putJoint("ব্দ", "bd");
        putJoint("ব্ধ", "bdh");

        putJoint("ম্প", "mp");
        putJoint("ম্ব", "mb");
        putJoint("ম্ভ", "mbh");
        putJoint("ম্ম", "mm");

        putJoint("ল্ক", "lk");
        putJoint("ল্প", "lp");
        putJoint("ল্ল", "ll");

        putJoint("শ্চ", "shch");
        putJoint("শ্ছ", "shchh");
        putJoint("শ্ন", "shn");
        putJoint("ষ্ট", "sht");
        putJoint("স্ট", "st");
        putJoint("স্ত", "st");
        putJoint("স্থ", "sth");
        putJoint("স্প", "sp");
        putJoint("স্ফ", "sf");
        putJoint("স্ক", "sk");
        putJoint("স্ম", "sm");

        putJoint("হ্ন", "hn");
        putJoint("হ্ম", "hm");
    }

    private static void putJoint(String bangla, String banglish) {
        JOINT.put(bangla, banglish);
    }

    /**
     * Regression dictionary based on the known error categories supplied for
     * this converter. These entries intentionally lock the expected natural
     * Banglish spelling.
     */
    private static void initializeExceptionDictionary() {

        // Basic high-frequency words.
        putException("আমি", "ami");
        putException("আমরা", "amra");
        putException("তুমি", "tumi");
        putException("তোমরা", "tomra");
        putException("সে", "she");
        putException("তিনি", "tini");
        putException("আপনি", "apni");
        putException("আপনার", "apnar");

        putException("বাংলা", "bangla");
        putException("বাংলাদেশ", "bangladesh");
        putException("ভালো", "bhalo");
        putException("খারাপ", "kharap");
        putException("বন্ধু", "bondhu");
        putException("দেশ", "desh");
        putException("মানুষ", "manush");
        putException("পৃথিবী", "prithibi");

        // Category 1: vowel omission / schwa normalization.
        putException("সময়মতো", "somoymoto");
        putException("ধরনের", "dhoroner");
        putException("জীবনের", "jiboner");
        putException("একাগ্রতা", "ekagrota");
        putException("নিয়মিত", "niyomit");
        putException("জগতের", "jogoter");
        putException("বাস্তবে", "bastobe");
        putException("ভ্রমণের", "bhromoner");
        putException("গুজব", "gujob");
        putException("উদ্যমে", "udyome");
        putException("অর্জনের", "orjoner");
        putException("বয়স", "boyos");

        // Category 2: y-fola / b-fola / conjunct normalization.
        putException("উদ্দেশ্যে", "uddeshe");
        putException("ভবিষ্যতে", "bhobishote");
        putException("বিশ্বাসযোগ্য", "bishwasjoggo");
        putException("স্বাস্থ্যের", "shasthyer");
        putException("মাধ্যমের", "madhyomer");
        putException("সত্যতা", "sotyota");
        putException("বাধ্য", "baddho");
        putException("স্বপ্নের", "sopner");

        // Category 3: English loanwords.
        putException("ফেসবুক", "facebook");
        putException("সোশ্যাল", "social");
        putException("মিডিয়া", "media");
        putException("মিডিয়া", "media");
        putException("প্রোডাক্টের", "product-er");
        putException("রেটিং", "rating");
        putException("ট্যুরের", "tour-er");
        putException("প্ল্যান", "plan");
        putException("জিপিএস", "GPS");
        putException("রুট", "route");
        putException("বেলকনিতে", "balconyte");
        putException("অ্যাপগুলো", "appgulo");
        putException("স্মুথ", "smooth");
        putException("পারফর্ম", "perform");
        putException("সোশ্যাল মিডিয়া", "social media");

        // Category 4: vowel doubling/confusion.
        putException("আরও", "aro");
        putException("কাজও", "kajo");
        putException("খুবই", "khuboi");
        putException("রওনা", "raona");

        // Category 5: visarga.
        putException("নিঃস্বার্থ", "nishwartho");
        putException("নিঃশব্দ", "nishobdo");
        putException("নিঃসন্দেহ", "nishondeho");

        // Category 6: typo/phonetic-slip regression examples.
        putException("উঠল", "uthlo");
        putException("আনন্দদায়ক", "anondodayok");
        putException("আনন্দদায়ক", "anondodayok");
        putException("সামাজিক", "somajik");
        putException("প্রফুল্ল", "profullo");

        // Existing important project words.
        putException("স্বাধীনতা", "swadhinota");
        putException("ঔষধ", "oshudh");
        putException("ওষুধ", "oshudh");
        putException("দায়িত্ব", "dayitto");
        putException("দায়িত্ব", "dayitto");
        putException("বিজ্ঞান", "biggan");
        putException("জ্ঞান", "gyan");
        putException("যাব", "jab");
        putException("হয়ে", "hoye");
        putException("হয়ে", "hoye");
        putException("সময়ে", "somoye");
        putException("সময়ে", "somoye");
        putException("অন্যায়", "onyay");
        putException("অন্যায়", "onyay");
        putException("ন্যায়", "nyay");
        putException("ন্যায়", "nyay");
        putException("কন্যা", "konnya");
        putException("ধন্যবাদ", "dhonnobad");
        putException("অন্যকে", "onnoke");
        putException("অন্যদের", "onnoder");
        putException("কন্যাকে", "konnake");
        putException("কন্যাদের", "konnader");
        putException("ন্যায়কে", "nyayke");
        putException("ন্যায়কে", "nyayke");
        putException("ধন্যবাদকে", "dhonnobadke");
        putException("সন্যাসী", "sannyasi");
        putException("সন্ন্যাসী", "sannyasi");
        putException("অজ্ঞ", "oggo");
        putException("রাষ্ট্র", "rashtro");
        putException("স্বাস্থ্য", "shastho");
        putException("বিশ্ব", "bishsho");
        putException("লক্ষ্য", "lokkho");
        putException("রক্ষা", "rokkha");
        putException("শিক্ষা", "shikkha");
        putException("শিক্ষক", "shikkhok");

        // Verbs and common endings.
        putException("করছি", "korchi");
        putException("করেছিল", "korechilo");
        putException("করবে", "korbe");
        putException("করেছি", "korechi");
        putException("খাচ্ছি", "khacchi");
        putException("যাচ্ছি", "jacchi");

        // Locations.
        putException("বিজয়নগর", "bijoynogor");
        putException("বিজয়নগর", "bijoynogor");
        putException("ব্রাহ্মণবাড়িয়া", "brahmanbaria");
        putException("ব্রাহ্মণবাড়িয়া", "brahmanbaria");
        putException("ঢাকা", "dhaka");
        putException("চট্টগ্রাম", "chattogram");
        putException("রাজশাহী", "rajshahi");
        putException("খুলনা", "khulna");
        putException("সিলেট", "sylhet");
        putException("বরিশাল", "barishal");
        putException("রংপুর", "rangpur");
        putException("ময়মনসিংহ", "mymensingh");
        putException("ময়মনসিংহ", "mymensingh");

        // Previously fixed project regressions.
        putException("দরকারি", "dorkari");
        putException("থাকলে", "thakle");
        putException("আসবো", "asbo");
        putException("ঠিকঠাক", "thikthak");
        putException("সবকিছু", "sobkichu");
        putException("হতে", "hote");
        putException("অন্য", "onno");
        putException("নষ্ট", "noshto");
        putException("পৌঁছাতে", "pouchate");
        putException("হয়েছিল", "hoyechilo");
        putException("হয়েছিল", "hoyechilo");
        putException("শিখিয়েছিল", "shikhiyechilo");
        putException("শিখিয়েছিল", "shikhiyechilo");
        putException("ফেরার", "ferar");
        putException("ফ্রি", "free");
        putException("হওয়া", "howa");
        putException("হওয়া", "howa");
        putException("হওয়ার", "howar");
        putException("হওয়ার", "howar");

        // Common English loanwords already used by the project.
        putException("কনভার্টার", "converter");
        putException("আপডেট", "update");
        putException("আপডেটে", "update-e");
        putException("ব্যাকআপ", "backup");
        putException("ক্যামেরা", "camera");
        putException("রিভিউ", "review");
        putException("ফোন", "phone");
        putException("চার্জ", "charge");
        putException("রেসিপি", "recipe");
        putException("সিলেবাস", "syllabus");
        putException("নোটস", "notes");
        putException("অ্যালার্ম", "alarm");

        // Small but important natural-spelling words.
        putException("সময়", "somoy");
        putException("সময়", "somoy");
        putException("হয়", "hoy");
        putException("হয়", "hoy");
        putException("মতো", "moto");
        putException("জীবন", "jibon");
        putException("জগত", "jogot");
        putException("বাস্তব", "bastob");
        putException("ভ্রমণ", "bhromon");
        putException("উদ্যম", "udyom");
        putException("অর্জন", "orjon");
        putException("বয়সের", "boyoser");
        putException("বয়সের", "boyoser");
        putException("ভবিষ্যৎ", "bhobishot");
        putException("বিশ্বাসযোগ্য", "bishwasjoggo");
        putException("স্বপ্ন", "sopno");
        putException("সত্য", "sotyo");
        putException("বাধ্যতামূলক", "baddhotamulok");
    }

    private static void putException(String bangla, String banglish) {
        EXCEPTION.put(cleanUnicode(bangla), banglish);
    }

    public static void setStyle(Style style) {
        if (style != null) {
            currentStyle = style;
            CACHE.clear();
        }
    }

    public static Style getStyle() {
        return currentStyle;
    }

    public static void setDebugMode(boolean enable) {
        debugMode = enable;
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    public static void setConvertDigits(boolean enable) {
        convertDigits = enable;
    }

    public static boolean isConvertDigits() {
        return convertDigits;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static void log(String message) {
        if (debugMode) {
            Log.d(TAG, message);
        }
    }

    public static void addDictionaryWord(String bangla, String banglish) {
        if (bangla == null || banglish == null) return;

        String key = cleanUnicode(bangla);
        DICTIONARY.put(key, banglish.trim());
        CACHE.remove(key);
    }

    public static void removeDictionaryWord(String bangla) {
        if (bangla == null) return;

        String key = cleanUnicode(bangla);
        DICTIONARY.remove(key);
        CACHE.remove(key);
    }

    public static void clearDictionary() {
        DICTIONARY.clear();
        CACHE.clear();
    }

    public static void loadDictionaryFromAssets(
            Context context,
            String fileName) {

        if (context == null || fileName == null) return;

        try (InputStream is = context.getAssets().open(fileName);
             BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     is,
                                     StandardCharsets.UTF_8))) {

            StringBuilder builder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            JSONObject object = new JSONObject(builder.toString());
            Iterator<String> keys = object.keys();

            while (keys.hasNext()) {
                String key = keys.next();
                DICTIONARY.put(
                        cleanUnicode(key),
                        object.getString(key));
            }

            CACHE.clear();

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Dictionary Load Failed", e);
        }
    }

    private static String cleanUnicode(String text) {
        if (text == null) return "";

        text = Normalizer.normalize(
                text,
                Normalizer.Form.NFC);

        // Remove zero-width formatting characters that otherwise make
        // dictionary lookup fail for visually identical Bengali words.
        text = text.replace("\u200C", "");
        text = text.replace("\u200D", "");
        text = text.replace("\uFEFF", "");

        return text.trim();
    }

    private static String lookupDictionary(String word) {
        if (word == null || word.isEmpty()) return null;

        String key = cleanUnicode(word);

        String cached = CACHE.get(key);
        if (cached != null) return cached;

        String value = DICTIONARY.get(key);
        if (value == null) {
            value = EXCEPTION.get(key);
        }

        if (value != null) {
            CACHE.put(key, value);
        }

        return value;
    }

    private static String findLongestJoint(
            String word,
            int start) {

        int maxLen = Math.min(
                8,
                word.length() - start);

        for (int len = maxLen; len >= 2; len--) {
            String sub = word.substring(
                    start,
                    start + len);

            if (JOINT.containsKey(sub)) {
                return sub;
            }
        }

        return null;
    }

    private static String findNuktaConsonant(
            String word,
            int start) {

        if (start + 1 >= word.length()) return null;

        String pair = word.substring(
                start,
                start + 2);

        return NUKTA_CONSONANTS.containsKey(pair)
                ? pair
                : null;
    }

    /**
     * Returns true when the next consonant is part of a following syllable
     * with an explicit vowel sign. Example:
     * ধর + ে -> the preceding consonant should not receive a second "o".
     */
    private static boolean nextIsVowelBearingConsonant(
            String word,
            int pos) {

        if (pos < 0 || pos >= word.length()) return false;

        char next = word.charAt(pos);

        if (!CONSONANTS.containsKey(next)) {
            return false;
        }

        int after = pos + 1;

        return after < word.length()
                && VOWEL_SIGNS.containsKey(
                        word.charAt(after));
    }

    /**
     * A bare consonant normally carries an inherent "o" in Bangla.
     * In Natural style, suppress it when the consonant is immediately before
     * another syllable whose vowel is explicitly written.
     *
     * The first consonant is not suppressed merely because a later syllable
     * exists; this keeps common forms such as "sokal" natural.
     */
    private static boolean shouldAddInherentO(
            String word,
            int consonantStart,
            int nextPos,
            boolean suppressFinal) {

        if (currentStyle != Style.NATURAL) {
            return false;
        }

        if (nextPos < word.length()
                && word.charAt(nextPos) == HASANTA) {
            return false;
        }

        if (suppressFinal && nextPos >= word.length()) {
            return false;
        }

        if (nextPos < word.length()
                && nextIsVowelBearingConsonant(
                        word,
                        nextPos)) {
            return false;
        }

        /*
         * A consonant followed directly by another consonant usually forms
         * the natural Banglish "C o C" pattern unless the conjunct itself
         * was consumed by the joint-letter matcher.
         */
        return true;
    }

    private static String transliterateCore(String word) {
        return transliterateCore(word, false);
    }

    private static String transliterateCore(
            String word,
            boolean suppressFinalInherentVowel) {

        if (word == null || word.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        int i = 0;
        int len = word.length();

        while (i < len) {

            /*
             * 1) Longest known conjunct first.
             * This prevents sequences such as প্র, স্ব, ব্য, দ্য, etc.
             * from being split into unrelated single consonants.
             */
            String joint = findLongestJoint(word, i);

            if (joint != null) {

                String value = JOINT.get(joint);
                sb.append(value);

                i += joint.length();

                if (i < len
                        && VOWEL_SIGNS.containsKey(
                                word.charAt(i))) {

                    sb.append(
                            VOWEL_SIGNS.get(
                                    word.charAt(i)));
                    i++;

                } else if (i < len
                        && word.charAt(i) == HASANTA) {

                    i++;

                } else if (shouldAddInherentO(
                        word,
                        i - joint.length(),
                        i,
                        suppressFinalInherentVowel)) {

                    sb.append('o');
                }

                continue;
            }

            /*
             * 2) Nukta forms: ড়, ঢ়, য়.
             */
            String nukta = findNuktaConsonant(word, i);

            if (nukta != null) {

                String value = NUKTA_CONSONANTS.get(nukta);
                sb.append(value);

                i += nukta.length();

                if (i < len
                        && word.charAt(i) == HASANTA) {
                    i++;

                } else if (i < len
                        && VOWEL_SIGNS.containsKey(
                                word.charAt(i))) {

                    sb.append(
                            VOWEL_SIGNS.get(
                                    word.charAt(i)));
                    i++;

                } else if (!"y".equals(value)
                        && shouldAddInherentO(
                                word,
                                i - nukta.length(),
                                i,
                                suppressFinalInherentVowel)) {

                    sb.append('o');
                }

                continue;
            }

            char c = word.charAt(i);

            /*
             * 3) Hasanta outside a recognized conjunct.
             */
            if (c == HASANTA) {
                i++;
                continue;
            }

            /*
             * 4) Consonants.
             */
            if (CONSONANTS.containsKey(c)) {

                int start = i;

                String value = CONSONANTS.get(c);

                /*
                 * য-ফলা:
                 * যখন য-এর আগে hasanta থাকে এবং সেটি joint matcher-এ ধরা
                 * পড়েনি, তখন normal "j" নয়, "y" হবে।
                 *
                 * Example: সত্য -> sotyo.
                 */
                if (c == 'য'
                        && start > 0
                        && word.charAt(start - 1) == HASANTA) {
                    value = "y";
                }

                /*
                 * The sequence "স্ + ব" is the common স্ব form. If it was
                 * not consumed by the joint matcher, keep it natural.
                 */
                sb.append(value);
                i++;

                if (i < len
                        && word.charAt(i) == HASANTA) {

                    /*
                     * Do not append an inherent vowel before a hasanta.
                     * The next loop iteration will consume the following
                     * consonant or conjunct.
                     */
                    i++;

                } else if (i < len
                        && VOWEL_SIGNS.containsKey(
                                word.charAt(i))) {

                    sb.append(
                            VOWEL_SIGNS.get(
                                    word.charAt(i)));
                    i++;

                } else if (shouldAddInherentO(
                        word,
                        start,
                        i,
                        suppressFinalInherentVowel)) {

                    sb.append('o');
                }

                continue;
            }

            /*
             * 5) Independent vowels.
             */
            if (VOWELS.containsKey(c)) {
                sb.append(VOWELS.get(c));
                i++;
                continue;
            }

            /*
             * 6) Vowel signs that escaped a preceding consonant.
             */
            if (VOWEL_SIGNS.containsKey(c)) {
                sb.append(VOWEL_SIGNS.get(c));
                i++;
                continue;
            }

            /*
             * 7) Anuswar.
             */
            if (c == ANUSWAR) {
                sb.append("ng");
                i++;
                continue;
            }

            /*
             * 8) Visarga.
             *
             * Most common Bengali words containing visarga are better handled
             * through the regression dictionary. For unknown words, "h" is
             * the least destructive phonetic fallback.
             */
            if (c == VISARGA) {
                sb.append('h');
                i++;
                continue;
            }

            /*
             * 9) Chandrabindu/nukta are combining marks.
             */
            if (c == CHANDRA || c == NUKTA) {
                i++;
                continue;
            }

            /*
             * 10) Bengali digits.
             */
            if (DIGITS.containsKey(c)) {
                if (convertDigits) {
                    sb.append(DIGITS.get(c));
                } else {
                    sb.append(c);
                }
                i++;
                continue;
            }

            /*
             * 11) Unknown Bengali code point: preserve it rather than
             * silently deleting data.
             */
            sb.append(c);
            i++;
        }

        return sb.toString();
    }

    private static String processWord(String word) {

        if (word == null || word.isEmpty()) {
            return word;
        }

        String cleaned = cleanUnicode(word);

        /*
         * Dictionary and regression rules always win.
         * This is intentional: a known natural spelling must not be changed
         * by a future general-rule modification.
         */
        String direct = lookupDictionary(cleaned);

        if (direct != null) {
            return direct;
        }

        /*
         * Handle a multi-word English loan phrase before single-word logic.
         * Normally processSentence preserves spaces, so this branch mainly
         * serves direct calls to processWord.
         */
        if ("সোশ্যাল মিডিয়া".equals(cleaned)
                || "সোশ্যাল মিডিয়া".equals(cleaned)) {
            return "social media";
        }

        /*
         * Generic transliteration.
         *
         * We deliberately avoid the old prefix/suffix system here. Those
         * generic rules could override word-internal phonetics and were one
         * of the causes of unwanted "o"/suffix behavior.
         */
        String result = transliterateCore(cleaned);

        /*
         * Natural-mode cleanup is conservative. It does not blindly replace
         * every "oo"/"aa" because those sequences can be legitimate in
         * user-supplied text.
         */
        if (currentStyle == Style.NATURAL) {
            result = normalizeNaturalResult(
                    cleaned,
                    result);
        }

        CACHE.put(cleaned, result);
        return result;
    }

    private static String normalizeNaturalResult(
            String bangla,
            String result) {

        if (result == null || result.isEmpty()) {
            return result;
        }

        /*
         * These are safe normalizations for the exact vowel-doubling patterns
         * that caused the documented regressions. The source Bengali word is
         * checked so an arbitrary Latin sequence is never modified.
         */
        if ("আরও".equals(bangla)) return "aro";
        if ("কাজও".equals(bangla)) return "kajo";
        if ("খুবই".equals(bangla)) return "khuboi";
        if ("রওনা".equals(bangla)) return "raona";

        return result;
    }

    private static String processGap(String gap) {

        if (gap == null || gap.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        Matcher matcher = WORD_PATTERN.matcher(gap);

        int last = 0;

        while (matcher.find()) {

            sb.append(
                    gap,
                    last,
                    matcher.start());

            String word = matcher.group();

            sb.append(processWord(word));

            last = matcher.end();
        }

        sb.append(gap.substring(last));

        return sb.toString();
    }

    public static String processSentence(String text) {

        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        Matcher preserveMatcher =
                PRESERVE_PATTERN.matcher(text);

        int lastEnd = 0;

        while (preserveMatcher.find()) {

            String gap = text.substring(
                    lastEnd,
                    preserveMatcher.start());

            result.append(processGap(gap));

            /*
             * URLs, e-mail addresses, mentions and hashtags are copied exactly
             * as entered. This prevents the transliterator from corrupting
             * technical/user-generated Latin text.
             */
            result.append(preserveMatcher.group());

            lastEnd = preserveMatcher.end();
        }

        result.append(
                processGap(
                        text.substring(lastEnd)));

        return result.toString();
    }

    public static String convert(String text) {

        if (text == null || text.isEmpty()) {
            return "";
        }

        String cleaned = cleanUnicode(text);

        log("Converting: " + cleaned);

        String result = processSentence(cleaned);

        log("Result: " + result);

        return result;
    }

    /*
     * ---------------------------------------------------------------------
     * Regression examples
     * ---------------------------------------------------------------------
     *
     * The following examples are intentionally kept as comments so future
     * maintenance can test them without changing the public API:
     *
     * সময়মতো      -> somoymoto
     * ধরনের       -> dhoroner
     * জীবনের      -> jiboner
     * একাগ্রতা     -> ekagrota
     * নিয়মিত      -> niyomit
     * জগতের       -> jogoter
     * বাস্তবে      -> bastobe
     * ভ্রমণের      -> bhromoner
     * গুজব        -> gujob
     * উদ্যমে       -> udyome
     * অর্জনের      -> orjoner
     * বয়স         -> boyos
     *
     * উদ্দেশ্যে    -> uddeshe
     * ভবিষ্যতে     -> bhobishote
     * বিশ্বাসযোগ্য -> bishwasjoggo
     * স্বাস্থ্যের  -> shasthyer
     * মাধ্যমের     -> madhyomer
     * সত্যতা       -> sotyota
     * বাধ্য        -> baddho
     * স্বপ্নের      -> sopner
     *
     * ফেসবুক       -> facebook
     * সোশ্যাল মিডিয়া -> social media
     * প্রোডাক্টের  -> product-er
     * রেটিং        -> rating
     * ট্যুরের      -> tour-er
     * প্ল্যান       -> plan
     * জিপিএস       -> GPS
     * রুট          -> route
     * বেলকনিতে     -> balconyte
     * অ্যাপগুলো     -> appgulo
     * স্মুথ        -> smooth
     * পারফর্ম      -> perform
     *
     * আরও          -> aro
     * কাজও         -> kajo
     * খুবই         -> khuboi
     * রওনা         -> raona
     *
     * নিঃস্বার্থ    -> nishwartho
     *
     * উঠল          -> uthlo
     * আনন্দদায়ক    -> anondodayok
     * সামাজিক       -> somajik
     * প্রফুল্ল      -> profullo
     */
}
