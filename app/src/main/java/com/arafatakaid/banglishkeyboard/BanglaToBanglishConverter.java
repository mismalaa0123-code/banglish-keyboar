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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * =====================================================
 * BanglaToBanglishConverter
 * Version : 4.0 STEP-13 (Performance + Safety + Final Self Test)
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

    // Common Bengali verb roots used for productive inflection fallback.
    // Exact dictionary/exception matches still have higher priority.
    private static final Map<String,String> VERB_ROOTS = new ConcurrentHashMap<>(256);
    private static final Map<String,String> VERB_SUFFIXES = new LinkedHashMap<>();

    // Multi-word dictionary entries, e.g. "ভালো আছি". Kept separate so
    // sentence conversion can match phrases before converting individual words.
    private static final Map<String,String> PHRASE_DICTIONARY = new ConcurrentHashMap<>(2048);
    private static volatile int MAX_PHRASE_WORDS = 1;

    // STEP-11: dictionary loading state. The converter may be called on every
    // keystroke, so the same asset must never be parsed repeatedly. Explicit
    // calls to loadDictionaryFromAssets() can still reload/replace the data.
    private static final Object DICTIONARY_LOCK = new Object();
    private static final AtomicBoolean DICTIONARY_LOADING = new AtomicBoolean(false);
    private static volatile String LOADED_DICTIONARY_FILE = null;

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
    private static final Map<String,String> FOLA = new LinkedHashMap<>();
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
                    + "|(#[A-Za-z0-9_]+)"
    );

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\u0980-\\u09FF]+");

    static {
        initializeVowels();
        initializeVowelSigns();
        initializeConsonants();
        initializeNuktaConsonants();
        initializeDigits();
        initializeJointLetters();
        initializeFolaRules();
        initializePrefixRules();
        initializeSuffixRules();
        initializeExceptionDictionary();
        initializeVerbRules();
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
        // Additional high-frequency conjuncts
        JOINT.put("ক্ট", "kt");
        JOINT.put("ক্ন", "kn");
        JOINT.put("ক্ম", "km");
        JOINT.put("ক্র", "kr");
        JOINT.put("খ্র", "khr");
        JOINT.put("গ্র", "gr");
        JOINT.put("ঘ্র", "ghr");
        JOINT.put("চ্র", "chr");
        JOINT.put("জ্জ", "jj");
        JOINT.put("জ্র", "jr");
        JOINT.put("ঝ্র", "jhr");
        JOINT.put("ট্র", "tr");
        JOINT.put("ড্র", "dr");
        JOINT.put("ঢ্র", "dhr");
        JOINT.put("ণ্ঠ", "nth");
        JOINT.put("ণ্ঢ", "ndh");
        JOINT.put("ণ্ম", "nm");
        JOINT.put("ত্য", "ty");
        JOINT.put("দ্গ", "dg");
        JOINT.put("দ্ঘ", "dgh");
        JOINT.put("দ্ব", "dw");
        JOINT.put("দ্ভ্র", "dbhr");
        JOINT.put("ধ্র", "dhr");
        JOINT.put("ন্ব", "nw");
        JOINT.put("ন্র", "nr");
        JOINT.put("প্ট", "pt");
        JOINT.put("প্স", "ps");
        JOINT.put("প্র", "pr");
        JOINT.put("ফ্র", "fr");
        JOINT.put("ব্জ", "bj");
        JOINT.put("ব্র", "br");
        JOINT.put("ভ্য", "bhy");
        JOINT.put("ম্র", "mr");
        JOINT.put("ল্ক", "lk");
        JOINT.put("ল্গ", "lg");
        JOINT.put("ল্ম", "lm");
        JOINT.put("শ্র", "shr");
        JOINT.put("ষ্ক", "shk");
        JOINT.put("ষ্প", "shp");
        JOINT.put("ষ্ম", "shm");
        JOINT.put("স্ক্র", "skr");
        JOINT.put("স্ট্র", "str");
        JOINT.put("স্ন", "sn");
        JOINT.put("স্ন্য", "sny");
        JOINT.put("স্র", "sr");
        JOINT.put("হ্র", "hr");
    }

    /* ====================================== INITIALIZE FOLA RULES ====================================== */
    /**
     * Common Bengali phala clusters. These are kept separate from the generic
     * conjunct table because the second consonant changes shape/pronunciation
     * when used as a phala. Values target Bangladesh-style natural typing.
     */
    private static void initializeFolaRules() {
        // র-ফলা
        String[] rFola = {
                "ক্র:k r", "গ্র:g r", "প্র:p r", "ব্র:b r", "ভ্র:bh r",
                "দ্র:d r", "ধ্র:dh r", "ত্র:t r", "থ্র:th r", "শ্র:sh r",
                "স্র:s r", "হ্র:h r", "ম্র:m r", "ন্র:n r", "ফ্র:f r",
                "ভ্র:bh r", "র্র:r r", "ট্র:t r", "ড্র:d r", "চ্র:ch r",
                "জ্র:j r", "প্ল:p l", "ক্ল:k l", "গ্ল:g l", "ফ্ল:f l",
                "ব্ল:b l", "ভ্ল:bh l", "শ্ল:sh l", "স্ল:s l", "হ্ল:h l"
        };
        // The table above is normalized below to avoid spaces in generated output.
        for (String item : rFola) {
            int colon = item.indexOf(':');
            if (colon > 0) FOLA.put(item.substring(0, colon), item.substring(colon + 1).replace(" ", ""));
        }

        // য-ফলা
        String[] yFola = {
                "ক্য:ky", "খ্য:khy", "গ্য:gy", "ঘ্য:ghy", "চ্য:chy", "ছ্য:chhy",
                "জ্য:jy", "ঝ্য:jhy", "ট্য:ty", "ঠ্য:thy", "ড্য:dy", "ঢ্য:dhy",
                "ত্য:ty", "থ্য:thy", "দ্য:dy", "ধ্য:dhy", "ন্য:ny", "প্য:py",
                "ফ্য:phy", "ব্য:by", "ভ্য:bhy", "ম্য:my", "ল্য:ly", "শ্য:shy",
                "ষ্য:shy", "স্য:sy", "হ্য:hy"
        };
        for (String item : yFola) {
            int colon = item.indexOf(':');
            if (colon > 0) FOLA.put(item.substring(0, colon), item.substring(colon + 1));
        }

        // ব-ফলা
        String[] wFola = {
                "ক্ব:kw", "গ্ব:gw", "ঘ্ব:ghw", "চ্ব:chw", "জ্ব:jw",
                "ত্ব:tw", "থ্ব:thw", "দ্ব:dw", "ধ্ব:dhw", "ন্ব:nw",
                "প্ব:pw", "ফ্ব:fw", "ব্ব:bw", "ভ্ব:bhw", "ম্ব:mw",
                "ল্ব:lw", "শ্ব:shw", "ষ্ব:shw", "স্ব:sw", "হ্ব:hw"
        };
        for (String item : wFola) {
            int colon = item.indexOf(':');
            if (colon > 0) FOLA.put(item.substring(0, colon), item.substring(colon + 1));
        }

        // ম-ফলা
        String[] mFola = {
                "ক্ম:km", "গ্ম:gm", "ঘ্ম:ghm", "ত্ম:tm", "দ্ম:dm",
                "ধ্ম:dhm", "ন্ম:nm", "প্ম:pm", "ব্ম:bm", "ভ্ম:bhm",
                "ল্ম:lm", "শ্ম:shm", "ষ্ম:shm", "স্ম:sm", "হ্ম:hm"
        };
        for (String item : mFola) {
            int colon = item.indexOf(':');
            if (colon > 0) FOLA.put(item.substring(0, colon), item.substring(colon + 1));
        }
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

    /* ====================================== COMMON VERB RULES (STEP-6) ====================================== */
    private static void initializeVerbRules() {
        // High-frequency roots. Values are the natural Banglish stem.
        String[][] roots = {
                {"কর","kor"},{"হও","ho"},{"যা","ja"},{"আস","as"},{"খা","kha"},
                {"দে","de"},{"নে","ne"},{"পা","pa"},{"দেখ","dekh"},{"শুন","shun"},
                {"বল","bol"},{"লিখ","likh"},{"পড়","por"},{"পড়","por"},{"খেল","khel"},
                {"গেল","gel"},{"যাচ","jac"},{"থাক","thak"},{"রাখ","rakh"},{"নেও","neo"},
                {"দাও","dao"},{"পড়া","pora"},{"পড়া","pora"},{"শেখ","shekh"},{"শিখ","shikh"},
                {"জান","jan"},{"মান","man"},{"চিন","chin"},{"ভালবাস","bhalobash"},{"বাস","bash"},
                {"চল","chol"},{"ফির","fir"},{"উঠ","uth"},{"বস","bosh"},{"ঘুমা","ghuma"},
                {"হাস","hash"},{"কাঁদ","kand"},{"কাঁদা","kanda"},{"নাচ","nach"},{"গান গা","gan ga"},
                {"জিজ্ঞাসা কর","jiggasha kor"},{"বোঝ","bojh"},{"বুঝ","bujh"},{"ভুল","bhul"},{"শুরু কর","shuru kor"},
                {"শেষ কর","shesh kor"},{"চাই","chai"},{"পছন্দ কর","pochondo kor"},{"ভয় পা","bhoy pa"},
                {"মারা","mara"},{"মর","mor"},{"বাঁচ","bach"},{"বাঁচা","bacha"},{"জিত","jit"},
                {"হার","har"},{"কিন","kin"},{"বেচ","bech"},{"পাঠা","patha"},{"পড়া","pora"},
                {"শো","sho"},{"দৌড়া","doura"},{"দৌড়া","doura"},{"উড়","ur"},{"উড়া","ura"},
                {"ধর","dhor"},{"ছাড়","chhar"},{"ছাড়","chhar"},{"ছুঁ","chhu"},{"ছু","chhu"},
                {"খুঁজ","khoj"},{"খোজ","khoj"},{"তুল","tul"},{"ফেল","fel"},{"দাঁড়া","dara"},
                {"দাঁড়া","dara"},{"লাগ","lag"},{"জ্বাল","jwal"},{"নিভ","nibh"},{"খোল","khol"},
                {"বন্ধ কর","bondho kor"},{"চালু কর","chalu kor"},{"তৈরি কর","toiri kor"},{"ব্যবহার কর","byabohar kor"}
        };
        for (String[] r : roots) VERB_ROOTS.put(r[0], r[1]);

        // Productive endings. These are applied only when the remaining stem
        // is a known verb root, so ordinary nouns are not rewritten accidentally.
        VERB_SUFFIXES.put("চ্ছিলাম", "chchilam");
        VERB_SUFFIXES.put("চ্ছিলে", "chchile");
        VERB_SUFFIXES.put("চ্ছিলেন", "chchilen");
        VERB_SUFFIXES.put("চ্ছিল", "chchhil");
        VERB_SUFFIXES.put("চ্ছি", "cchi");
        VERB_SUFFIXES.put("চ্ছিস", "cchis");
        VERB_SUFFIXES.put("চ্ছেন", "cchen");
        VERB_SUFFIXES.put("ছিলাম", "chilam");
        VERB_SUFFIXES.put("ছিলে", "chile");
        VERB_SUFFIXES.put("ছিলেন", "chilen");
        VERB_SUFFIXES.put("ছিল", "chhil");
        VERB_SUFFIXES.put("ছি", "chi");
        VERB_SUFFIXES.put("ছিস", "chis");
        VERB_SUFFIXES.put("ছেন", "chen");
        VERB_SUFFIXES.put("েছিলাম", "echilam");
        VERB_SUFFIXES.put("েছিলে", "echile");
        VERB_SUFFIXES.put("েছিলেন", "echilen");
        VERB_SUFFIXES.put("েছিল", "echhil");
        VERB_SUFFIXES.put("েছি", "echi");
        VERB_SUFFIXES.put("েছিস", "echis");
        VERB_SUFFIXES.put("েছেন", "echen");
        VERB_SUFFIXES.put("েবে", "ebe");
        VERB_SUFFIXES.put("েবেন", "eben");
        VERB_SUFFIXES.put("বে", "be");
        VERB_SUFFIXES.put("বেন", "ben");
        VERB_SUFFIXES.put("ব", "bo");
        VERB_SUFFIXES.put("বি", "bi");
        VERB_SUFFIXES.put("বো", "bo");
        VERB_SUFFIXES.put("লাম", "lam");
        VERB_SUFFIXES.put("লে", "le");
        VERB_SUFFIXES.put("লেন", "len");
        VERB_SUFFIXES.put("ল", "l");
        VERB_SUFFIXES.put("তে", "te");
        VERB_SUFFIXES.put("িতে", "ite");
        VERB_SUFFIXES.put("াতে", "ate");
    }

    private static String applyVerbRule(String word) {
        if (word == null || word.isEmpty()) return null;

        // Handle productive forms where Bengali orthography changes the root
        // before -ছি/-ছিল- (e.g. কর + ছি -> করছি, খা + চ্ছি -> খাচ্ছি).
        String[][] special = {
                {"করছি","korchi"},{"করছিস","korchis"},{"করছেন","korchen"},
                {"করছিলাম","korchilam"},{"করছিলে","korchile"},{"করছিলেন","korchilen"},{"করছিল","korchhil"},
                {"খাচ্ছি","khacchi"},{"খাচ্ছিস","khacchis"},{"খাচ্ছেন","khacchen"},
                {"খাচ্ছিলাম","khacchilam"},{"খাচ্ছিলে","khacchile"},{"খাচ্ছিলেন","khacchilen"},{"খাচ্ছিল","khacchhil"},
                {"যাচ্ছি","jacchi"},{"যাচ্ছিস","jacchis"},{"যাচ্ছেন","jacchen"},
                {"যাচ্ছিলাম","jacchilam"},{"যাচ্ছিলে","jacchile"},{"যাচ্ছিলেন","jacchilen"},{"যাচ্ছিল","jacchhil"},
                {"আসছি","aschi"},{"আসছিস","aschis"},{"আসছেন","aschen"},{"আসছিলাম","aschilam"},{"আসছিল","aschhil"},
                {"দিচ্ছি","dicchi"},{"দিচ্ছিস","dicchis"},{"দিচ্ছেন","dicchen"},{"দিচ্ছিলাম","dicchilam"},{"দিচ্ছিল","dicchhil"},
                {"নিচ্ছি","nicchi"},{"নিচ্ছিস","nicchis"},{"নিচ্ছেন","nicchen"},{"নিচ্ছিলাম","nicchilam"},{"নিচ্ছিল","nicchhil"},
                {"দেখছি","dekhchi"},{"দেখছিস","dekhchis"},{"দেখছেন","dekhchen"},{"দেখছিলাম","dekhchilam"},{"দেখছিল","dekhchhil"},
                {"বলছি","bolchi"},{"বলছিস","bolchis"},{"বলছেন","bolchen"},{"বলছিলাম","bolchilam"},{"বলছিল","bolchhil"},
                {"লিখছি","likhchi"},{"লিখছিস","likhchis"},{"লিখছেন","likhchen"},{"লিখছিলাম","likhchilam"},{"লিখছিল","likhchhil"}
        };
        for (String[] x : special) if (word.equals(x[0])) return x[1];

        // Safe root + ending fallback for known roots only.
        for (Map.Entry<String,String> root : VERB_ROOTS.entrySet()) {
            if (!word.startsWith(root.getKey())) continue;
            String tail = word.substring(root.getKey().length());
            String tailOut = VERB_SUFFIXES.get(tail);
            if (tailOut != null) return root.getValue() + tailOut;
        }
        return null;
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

    /* ====================================== UNICODE SAFETY HELPERS ====================================== */

    /** Returns true when the string contains at least one Bengali code point. */
    private static boolean hasBengaliCodePoint(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            if (cp >= 0x0980 && cp <= 0x09FF) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    /**
     * Removes isolated Unicode surrogate code units from malformed pasted text.
     * Valid supplementary characters are preserved. Bengali itself is BMP, so
     * this only protects the scanner from broken UTF-16 input.
     */
    private static String removeIsolatedSurrogates(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    out.append(c).append(text.charAt(++i));
                }
            } else if (Character.isLowSurrogate(c)) {
                // Ignore an unmatched low surrogate.
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /* ====================================== DICTIONARY ====================================== */
    public static void addDictionaryWord(String bangla, String banglish) {
        if (bangla == null || banglish == null) return;
        bangla = cleanUnicode(Normalizer.normalize(bangla, Normalizer.Form.NFC));
        String key = bangla.trim();
        String value = banglish.trim();
        DICTIONARY.put(key, value);
        rebuildPhraseEntry(key, value);
        CACHE.remove(key);
    }

    public static void removeDictionaryWord(String bangla) {
        if (bangla == null) return;
        String key = cleanUnicode(bangla);
        DICTIONARY.remove(key);
        PHRASE_DICTIONARY.remove(key);
        CACHE.remove(key);
    }

    public static void clearDictionary() {
        DICTIONARY.clear();
        PHRASE_DICTIONARY.clear();
        MAX_PHRASE_WORDS = 1;
        LOADED_DICTIONARY_FILE = null;
        CACHE.clear();
    }

    /* ====================================== LOAD DICTIONARY ====================================== */
    /**
     * Loads an asset dictionary once for a given filename. Calling this method
     * repeatedly is safe: after a successful load, subsequent calls for the
     * same filename return immediately. A different filename intentionally
     * reloads the dictionary.
     */
    public static void loadDictionaryFromAssets(Context context, String fileName) {
        if (context == null || fileName == null || fileName.trim().isEmpty()) return;

        String requested = fileName.trim();
        if (requested.equals(LOADED_DICTIONARY_FILE) && !DICTIONARY.isEmpty()) return;

        synchronized (DICTIONARY_LOCK) {
            if (requested.equals(LOADED_DICTIONARY_FILE) && !DICTIONARY.isEmpty()) return;
            if (!DICTIONARY_LOADING.compareAndSet(false, true)) return;

            try (InputStream is = context.getAssets().open(requested);
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(is, StandardCharsets.UTF_8))) {

                StringBuilder builder = new StringBuilder(32768);
                String line;
                while ((line = reader.readLine()) != null) builder.append(line);

                JSONObject object = new JSONObject(builder.toString());

                // Build into temporary maps first. This prevents a failed JSON
                // parse from leaving the live dictionary half-populated.
                Map<String,String> loaded = new HashMap<>(Math.max(16, object.length() * 2));
                Iterator<String> keys = object.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = object.optString(key, null);
                    if (value == null) continue;
                    String normalizedKey = cleanUnicode(key).trim();
                    if (normalizedKey.isEmpty()) continue;
                    loaded.put(normalizedKey, value.trim());
                }

                DICTIONARY.clear();
                PHRASE_DICTIONARY.clear();
                MAX_PHRASE_WORDS = 1;
                DICTIONARY.putAll(loaded);
                for (Map.Entry<String,String> e : loaded.entrySet()) {
                    rebuildPhraseEntry(e.getKey(), e.getValue());
                }

                CACHE.clear();
                LOADED_DICTIONARY_FILE = requested;
                log("Dictionary loaded once: " + requested + " (" + loaded.size() + " entries)");

            } catch (IOException | JSONException | RuntimeException e) {
                Log.e(TAG, "Dictionary Load Failed: " + requested, e);
            } finally {
                DICTIONARY_LOADING.set(false);
            }
        }
    }

    /* ====================================== CLEAN UNICODE ====================================== */
    /**
     * STEP-7 Unicode normalizer.
     *
     * Goals:
     *  - normalize decomposed Bengali sequences to NFC;
     *  - remove invisible formatting characters that can break dictionary
     *    matching (ZWJ/ZWNJ/BOM/word-joiner);
     *  - remove variation selectors and other zero-width format marks;
     *  - collapse accidental duplicate Bengali hasanta/sign marks where it is
     *    safe to do so;
     *  - preserve normal spaces/newlines/punctuation.
     *
     * This is deliberately conservative: it does NOT try to "correct" Bengali
     * spelling. Linguistic correction remains the job of the dictionary/rules.
     */
    private static String cleanUnicode(String text) {
        if (text == null) return "";

        String safe = removeIsolatedSurrogates(text);
        String s = Normalizer.normalize(safe, Normalizer.Form.NFC);

        // Invisible characters which have no useful role in our transliteration
        // engine. They frequently appear after copy/paste from web pages/apps.
        s = s.replace("\uFEFF", ""); // BOM / zero-width no-break space
        s = s.replace("\u200B", ""); // zero-width space
        s = s.replace("\u200C", ""); // ZWNJ
        s = s.replace("\u200D", ""); // ZWJ
        s = s.replace("\u2060", ""); // word joiner
        s = s.replace("\u2061", ""); // function application format mark
        s = s.replace("\u2062", "");
        s = s.replace("\u2063", "");
        s = s.replace("\u2064", "");
        s = s.replace("\u2066", "");
        s = s.replace("\u2067", "");
        s = s.replace("\u2068", "");
        s = s.replace("\u2069", "");
        s = s.replace("\uFE00", ""); // variation selector 1
        s = s.replace("\uFE01", "");
        s = s.replace("\uFE02", "");
        s = s.replace("\uFE03", "");
        s = s.replace("\uFE04", "");
        s = s.replace("\uFE05", "");
        s = s.replace("\uFE06", "");
        s = s.replace("\uFE07", "");
        s = s.replace("\uFE08", "");
        s = s.replace("\uFE09", "");
        s = s.replace("\uFE0A", "");
        s = s.replace("\uFE0B", "");
        s = s.replace("\uFE0C", "");
        s = s.replace("\uFE0D", "");
        s = s.replace("\uFE0E", "");
        s = s.replace("\uFE0F", ""); // variation selector 16

        // Normalize again after removing format characters so decomposed pairs
        // exposed by cleanup are composed when Unicode has a canonical form.
        s = Normalizer.normalize(s, Normalizer.Form.NFC);

        // Safe cleanup of repeated hasanta/sign characters often introduced by
        // IME composition or pasted text. We intentionally do not collapse
        // arbitrary repeated letters because that would change user spelling.
        s = s.replaceAll("\\u09CD{2,}", "\u09CD");
        s = s.replaceAll("[\\u09BF\\u09C0]{2,}", "\u09BF");
        s = s.replaceAll("[\\u09C1\\u09C2]{2,}", "\u09C1");

        // Remove stray combining marks only when they are at the beginning of
        // the string or immediately after whitespace/punctuation. Valid marks
        // attached to a Bengali consonant/vowel are preserved.
        s = s.replaceAll("(^|[\\s\\p{Punct}])(?:\\u09BC|\\u09CD|\\u09BE|\\u09BF|\\u09C0|\\u09C1|\\u09C2|\\u09C3|\\u09C7|\\u09C8|\\u09CB|\\u09CC|\\u0981|\\u0982|\\u0983)+", "$1");

        return s.trim();
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
    /** Convert Bengali digits in ordinary text while leaving English text and punctuation unchanged. */
    private static String convertBengaliDigitsInGap(String text) {
        if (text == null || text.isEmpty() || !convertDigits) return text;
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Character mapped = DIGITS.get(c);
            out.append(mapped != null ? mapped : c);
        }
        return out.toString();
    }

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
        return convertBengaliDigitsInGap(out.toString());
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
        return convertBengaliDigitsInGap(sb.toString());
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

    private static boolean isBengaliConsonantAt(String word, int index) {
        if (index < 0 || index >= word.length()) return false;
        String nukta = findNuktaConsonant(word, index);
        if (nukta != null) return true;
        return CONSONANTS.containsKey(word.charAt(index));
    }



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


    private static String findLongestFola(String word, int start) {
        int maxLen = Math.min(3, word.length() - start);
        for (int len = maxLen; len >= 3; len--) {
            String sub = word.substring(start, start + len);
            if (FOLA.containsKey(sub)) return sub;
        }
        return null;
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

    /* ====================================== VOWEL / INHERENT VOWEL HELPERS ====================================== */

    /**
     * Returns the natural Banglish vowel for a Bengali vowel sign.
     * The mapping is kept explicit so future style-specific pronunciation
     * rules can be added without changing the core scanner.
     */
    private static String naturalVowelSign(char sign) {
        String value = VOWEL_SIGNS.get(sign);
        if (value == null) return null;
        if (currentStyle == Style.NATURAL) {
            // Bangladesh chat-style typing normally uses i/u for the long
            // Bengali vowel signs as well.
            if (sign == '\u09C0') return "i";
            if (sign == '\u09C2') return "u";
        }
        return value;
    }

    /** True when the next Bengali code point starts another consonant. */
    private static boolean hasFollowingConsonant(String word, int index) {
        return index >= 0 && index < word.length() && isBengaliConsonantAt(word, index);
    }

    /**
     * Whether a bare consonant should receive its inherent vowel. This is
     * deliberately conservative: final consonants do not receive an extra
     * "o", while a consonant followed by another consonant does.
     */
    private static boolean needsInherentO(String word, int nextIndex) {
        return hasFollowingConsonant(word, nextIndex);
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
                    sb.append(naturalSign(naturalVowelSign(word.charAt(i))));
                    i++;
                } else if (i < len && word.charAt(i) == HASANTA) {
                    i++;
                } else if (i < len && isBengaliConsonantAt(word, i)) {
                    // A conjunct followed by another consonant normally carries
                    // the Bengali inherent vowel: প্র + থ -> proth...,
                    // প্র + শ্ন -> proshno.
                    sb.append("o");
                } else if (i >= len) {
                    // A final conjunct commonly carries the spoken inherent
                    // vowel in natural Banglish: শক্ত -> shokto, প্রশ্ন -> proshno.
                    sb.append("o");
                }
                continue;
            }

            // 2) Specialized phala rules before generic fallback.
            String folaMatch = findLongestFola(word, i);
            if (folaMatch != null) {
                sb.append(FOLA.get(folaMatch));
                i += folaMatch.length();
                if (i < len && VOWEL_SIGNS.containsKey(word.charAt(i))) {
                    sb.append(naturalSign(naturalVowelSign(word.charAt(i))));
                    i++;
                } else if (i < len && word.charAt(i) == HASANTA) {
                    i++;
                } else if (i < len && isBengaliConsonantAt(word, i)) {
                    // Another consonant follows, so the cluster gets its
                    // inherent vowel before that next consonant.
                    sb.append("o");
                }
                continue;
            }

            // Generic fola fallback: C + hasanta + র/য/ব/ম.
            int folaEnd = consumeGenericFola(word, i, sb);
            if (folaEnd >= 0) {
                i = folaEnd;
                if (i < len && VOWEL_SIGNS.containsKey(word.charAt(i))) {
                    sb.append(naturalSign(naturalVowelSign(word.charAt(i))));
                    i++;
                } else if (i < len && word.charAt(i) == HASANTA) {
                    i++;
                } else if (i < len && isBengaliConsonantAt(word, i)) {
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
                    sb.append(naturalSign(naturalVowelSign(word.charAt(i))));
                    i++;
                } else if (i < len && isBengaliConsonantAt(word, i)) {
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
                            sb.append(naturalSign(naturalVowelSign(word.charAt(i))));
                            i++;
                        } else if (i < len && isBengaliConsonantAt(word, i)) {
                            sb.append("o");
                        } else if (i >= len) {
                            // A two-consonant conjunct at word end commonly
                            // carries the spoken inherent vowel: স্বপ্ন -> swopno,
                            // শক্ত -> shokto.
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
                    sb.append(naturalSign(naturalVowelSign(word.charAt(i))));
                    i++;
                } else if (i < len && isBengaliConsonantAt(word, i)) {
                    // A bare consonant before another consonant normally has
                    // the inherent vowel.
                    sb.append("o");
                } else if (i >= len && i >= 2 && word.charAt(i - 1) == HASANTA) {
                    // The final member of a conjunct often retains the spoken
                    // inherent vowel in natural Banglish: প্রশ্ন -> proshno.
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
                sb.append(naturalSign(naturalVowelSign(c)));
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

    /* ====================================== RULE PRIORITY (STEP-9) ====================================== */

    /**
     * Step-9 rule priority is intentionally centralized here so that a weaker
     * morphological rule can never overwrite a stronger exact match.
     *
     * Priority:
     *   1) exact dictionary
     *   2) exact exception
     *   3) known verb rule
     *   4) prefix/suffix morphology
     *   5) conjunct/fola/vowel core transliteration
     *   6) final general fallback
     *
     * The helper is kept small because phrase matching is already handled
     * before processWord().
     */
    private static String lookupExactDictionaryOnly(String word) {
        if (word == null || word.isEmpty()) return null;
        String value = DICTIONARY.get(word);
        if (value != null) {
            CACHE.put(word, value);
            return value;
        }
        return null;
    }

    private static String lookupExactExceptionOnly(String word) {
        if (word == null || word.isEmpty()) return null;
        String value = EXCEPTION.get(word);
        if (value != null) {
            CACHE.put(word, value);
            return value;
        }
        return null;
    }

    /** Returns true when a word contains characters that should never be sent
     * through Bengali morphological prefix/suffix rules. */
    private static boolean isProtectedWord(String word) {
        if (word == null || word.isEmpty()) return true;
        // English letters, digits, email/URL-like punctuation, or mixed-script
        // tokens should not be morphologically split. Bengali-only words are
        // safe candidates for the linguistic rules.
        for (int i = 0; i < word.length();) {
            int cp = word.codePointAt(i);
            if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z') ||
                    (cp >= '0' && cp <= '9')) return true;
            i += Character.charCount(cp);
        }
        return false;
    }


    /* ====================================== WRONG-CORRECTION PROTECTION (STEP-10) ====================================== */

    /**
     * Returns true when the token is entirely Bengali (including Bengali combining
     * marks). Morphological rules are only allowed on such tokens.
     */
    private static boolean isBengaliOnlyToken(String word) {
        if (word == null || word.isEmpty()) return false;
        boolean hasBengali = false;
        for (int i = 0; i < word.length();) {
            int cp = word.codePointAt(i);
            if (cp >= 0x0980 && cp <= 0x09FF) {
                hasBengali = true;
            } else if (Character.isWhitespace(cp) || Character.getType(cp) == Character.NON_SPACING_MARK
                    || Character.getType(cp) == Character.COMBINING_SPACING_MARK
                    || Character.getType(cp) == Character.ENCLOSING_MARK) {
                // Allowed only as part of a Bengali token; processWord normally has
                // no whitespace, while combining marks may occur in Bengali text.
            } else {
                return false;
            }
            i += Character.charCount(cp);
        }
        return hasBengali;
    }

    /**
     * Rejects a morphology result when it looks like the rule accidentally
     * deleted most of the user's token. Exact dictionary/exception results are
     * never passed through this check.
     */
    private static boolean isSafeCorrection(String original, String candidate, String raw) {
        if (original == null || original.isEmpty()) return false;
        if (candidate == null || candidate.isEmpty()) return false;
        if (raw == null || raw.isEmpty()) return false;

        // Never allow a morphology rule to erase the token completely.
        if (candidate.length() < 2 && original.length() >= 4) return false;

        // A productive correction should not be dramatically shorter than the
        // ordinary transliteration unless it is an explicit dictionary/exception.
        // This catches accidental prefix/suffix over-stripping.
        int minAllowed = Math.max(2, raw.length() / 3);
        if (candidate.length() < minAllowed) return false;

        return true;
    }

    /**
     * Protects user text from repeated linguistic rewriting. Banglish output
     * contains no Bengali code points, so passing an already-converted token
     * through this converter should leave it unchanged.
     */
    private static boolean isAlreadyBanglishToken(String word) {
        if (word == null || word.isEmpty()) return false;
        return !hasBengaliCodePoint(word);
    }

    /* ====================================== PROCESS WORD ====================================== */

    private static String processWord(String word) {
        if (word == null || word.isEmpty()) return word;

        String cleaned = cleanUnicode(word);
        if (cleaned.isEmpty()) return cleaned;

        // STEP 10.1 — Never rewrite an already non-Bengali token. This makes the
        // converter safe for mixed text and repeated conversion.
        if (isAlreadyBanglishToken(cleaned)) {
            return cleaned;
        }

        // STEP 10.2 — Cache only complete tokens.
        String cached = CACHE.get(cleaned);
        if (cached != null) return cached;

        // STEP 10.3 — Exact dictionary and exception remain authoritative.
        String directDictionary = lookupExactDictionaryOnly(cleaned);
        if (directDictionary != null) return directDictionary;

        String directException = lookupExactExceptionOnly(cleaned);
        if (directException != null) return directException;

        // STEP 10.4 — Morphological rules are allowed only for Bengali-only
        // tokens. Mixed/foreign tokens go directly to the safe scanner.
        boolean bengaliOnly = isBengaliOnlyToken(cleaned);
        boolean protectedToken = isProtectedWord(cleaned) || !bengaliOnly;

        // STEP 10.5 — Verb fallback is deliberately conservative.
        if (!protectedToken) {
            String verb = applyVerbRule(cleaned);
            if (verb != null && !verb.isEmpty()) {
                CACHE.put(cleaned, verb);
                return verb;
            }
        }

        // STEP 10.6 — Always compute a baseline transliteration first. If a
        // prefix/suffix rule later produces a suspicious result, we can safely
        // return this baseline instead of deleting/changing user text.
        String rawBaseline = transliterateCore(cleaned);
        if (rawBaseline.isEmpty()) rawBaseline = cleaned;

        String remaining = cleaned;
        StringBuilder prefixOut = new StringBuilder();
        String suffixOut = "";

        if (!protectedToken) {
            // Longest prefix wins.
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
                String mapped = PREFIX.get(prefixMatch);
                if (mapped != null && !mapped.isEmpty()) {
                    prefixOut.append(mapped);
                    remaining = remaining.substring(prefixMatch.length());
                }
            }

            // Longest suffix wins.
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
                String mapped = SUFFIX.get(suffixMatch);
                if (mapped != null && !mapped.isEmpty()) {
                    suffixOut = mapped;
                    remaining = remaining.substring(0, remaining.length() - suffixMatch.length());
                }
            }
        }

        String core = transliterateCore(remaining);
        String result = prefixOut.toString() + core + suffixOut;

        // STEP 10.7 — Wrong-correction protection. If morphology produced an
        // empty or suspiciously destructive result, use the untouched baseline.
        if (!isSafeCorrection(cleaned, result, rawBaseline)) {
            result = rawBaseline;
        }

        // STEP 10.8 — Absolute fallback: never delete non-empty user input.
        if (result == null || result.isEmpty()) {
            result = rawBaseline;
        }
        if (result == null || result.isEmpty()) {
            result = cleaned;
        }

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

        StringBuilder result = new StringBuilder(text.length() + 16);
        Matcher preserveMatcher = PRESERVE_PATTERN.matcher(text);
        int lastEnd = 0;

        while (preserveMatcher.find()) {
            if (preserveMatcher.start() > lastEnd) {
                result.append(processGap(text.substring(lastEnd, preserveMatcher.start())));
            }
            result.append(preserveMatcher.group());
            lastEnd = preserveMatcher.end();
        }

        if (lastEnd < text.length()) result.append(processGap(text.substring(lastEnd)));
        return result.toString();
    }

    /* ====================================== CONVERT (MAIN ENTRY) ====================================== */
    public static String convert(String text) {
        if (text == null || text.isEmpty()) return "";

        try {
            String cleaned = cleanUnicode(text);
            if (cleaned.isEmpty()) return "";

            // STEP-11: fast path for text that contains no Bengali. This avoids
            // regex/token work when the keyboard receives English-only input.
            if (!hasBengaliCodePoint(cleaned)) {
                return convertBengaliDigitsInGap(cleaned);
            }

            String result = processSentence(cleaned);
            if (result == null || result.isEmpty()) return cleaned;

            log("Result: " + result);
            return result;
        } catch (RuntimeException e) {
            // STEP-12: the keyboard must never crash because of a malformed
            // token or an unexpected rule interaction. Returning normalized
            // input is safer than losing the user's text.
            Log.e(TAG, "Conversion failed; returning safe input", e);
            return cleanUnicode(text);
        }
    }

    /* ====================================== STEP-11/12 STATUS ====================================== */
    public static boolean isDictionaryLoaded() {
        return LOADED_DICTIONARY_FILE != null && !DICTIONARY.isEmpty();
    }

    public static String getLoadedDictionaryFile() {
        return LOADED_DICTIONARY_FILE;
    }

    public static int getDictionarySize() {
        return DICTIONARY.size();
    }

    public static int getCacheSize() {
        synchronized (CACHE) {
            return CACHE.size();
        }
    }

    /* ====================================== STEP-13 FINAL SELF TEST ====================================== */
    /**
     * Lightweight production-safe self test. It does not change dictionary data
     * and is never executed automatically. Call it manually from a debug screen
     * or test harness when you want a quick health check.
     *
     * Format: PASS/FAIL lines followed by a summary.
     */
    public static String runSelfTest() {
        StringBuilder report = new StringBuilder(2048);
        int total = 0;
        int passed = 0;

        String[][] exact = {
                {"আমি", "ami"},
                {"বাংলা", "bangla"},
                {"বাংলাদেশ", "bangladesh"},
                {"স্বাধীনতা", "swadhinota"},
                {"ঔষধ", "oshudh"},
                {"ওষুধ", "oshudh"},
                {"দায়িত্ব", "dayitto"},
                {"বিজ্ঞান", "biggan"},
                {"জ্ঞান", "gyan"},
                {"বিজয়নগর", "bijoynogor"},
                {"ঢাকা", "dhaka"}
        };

        for (String[] test : exact) {
            total++;
            String actual = convert(test[0]);
            boolean ok = test[1].equals(actual);
            if (ok) passed++;
            report.append(ok ? "PASS" : "FAIL")
                    .append(" | ").append(test[0])
                    .append(" -> ").append(actual)
                    .append(" | expected=").append(test[1]).append('\n');
        }

        String[][] safety = {
                {"Hello world 123", "Hello world 123"},
                {"https://example.com", "https://example.com"},
                {"test@example.com", "test@example.com"},
                {"@username", "@username"},
                {"#Bangladesh", "#Bangladesh"},
                {"১২৩৪৫", "12345"}
        };

        for (String[] test : safety) {
            total++;
            String actual = convert(test[0]);
            boolean ok = test[1].equals(actual);
            if (ok) passed++;
            report.append(ok ? "PASS" : "FAIL")
                    .append(" | ").append(test[0])
                    .append(" -> ").append(actual)
                    .append(" | expected=").append(test[1]).append('\n');
        }

        String nullResult = convert(null);
        total++;
        boolean nullOk = "".equals(nullResult);
        if (nullOk) passed++;
        report.append(nullOk ? "PASS" : "FAIL")
                .append(" | null -> ").append(nullResult).append('\n');

        String stableInput = "স্বাধীনতা";
        String once = convert(stableInput);
        String twice = convert(once);
        total++;
        boolean stableOk = once.equals(twice);
        if (stableOk) passed++;
        report.append(stableOk ? "PASS" : "FAIL")
                .append(" | idempotence | ").append(once)
                .append(" -> ").append(twice).append('\n');

        report.append("SUMMARY: ").append(passed).append('/').append(total)
                .append(" tests passed");
        return report.toString();
    }
}
