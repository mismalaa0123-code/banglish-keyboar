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
 * Version : 3.0
 * Author  : Arafat Akaid
 * =====================================================
 *
 * Features
 * ----------
 * ✔ Rule Based
 * ✔ Dictionary Based
 * ✔ Exception Dictionary
 * ✔ Prefix Rules
 * ✔ Suffix Rules
 * ✔ Joint Letter Rules
 * ✔ Unicode Normalize
 * ✔ LRU Cache
 * ✔ Thread Safe
 * ✔ Future AI Support
 *
 */

public final class BanglaToBanglishConverter {

    private BanglaToBanglishConverter(){}

    private static final String TAG =
            "BanglaToBanglish";

    /*
     * =============================
     * STYLE
     * =============================
     */

    public enum Style{

        NATURAL,

        SIMPLE,

        ACADEMIC

    }

    private static volatile Style currentStyle =
            Style.NATURAL;

    /*
     * =============================
     * SETTINGS
     * =============================
     */

    private static boolean
            convertDigits = true;

    private static boolean
            debugMode = false;

    /*
     * =============================
     * CACHE
     * =============================
     */

    private static final int
            CACHE_SIZE = 10000;

    private static final Map<String,String> CACHE =
            Collections.synchronizedMap(

                    new LinkedHashMap<String,String>(
                            1024,
                            0.75f,
                            true){

                        @Override
                        protected boolean removeEldestEntry(
                                Map.Entry<String,String> eldest){

                            return size()>CACHE_SIZE;

                        }

                    });

    /*
     * =============================
     * DICTIONARY
     * =============================
     */

    private static final Map<String,String>
            DICTIONARY =
            new ConcurrentHashMap<>(65536);

    private static final Map<String,String>
            EXCEPTION =
            new ConcurrentHashMap<>(4096);

    /*
     * =============================
     * CHARACTER MAP
     * =============================
     */

    private static final Map<Character,String>
            CONSONANTS =
            new HashMap<>();

    private static final Map<Character,String>
            VOWELS =
            new HashMap<>();

    private static final Map<Character,String>
            VOWEL_SIGNS =
            new HashMap<>();

    private static final Map<Character,Character>
            DIGITS =
            new HashMap<>();

    /*
     * =============================
     * ADVANCED RULES
     * =============================
     */

    private static final Map<String,String>
            JOINT =
            new HashMap<>();

    private static final Map<String,String>
            PREFIX =
            new HashMap<>();

    private static final Map<String,String>
            SUFFIX =
            new HashMap<>();

    /*
     * =============================
     * SPECIAL CHARACTER
     * =============================
     */

    private static final char HASANTA='্';
    private static final char ANUSWAR='ং';
    private static final char CHANDRA='ঁ';
    private static final char VISARGA='ঃ';
    private static final char NUKTA='়';

    /*
     * =============================
     * PRESERVE PATTERN
     * =============================
     */

    private static final Pattern
            PRESERVE_PATTERN =
            Pattern.compile(

                    "(https?://\\S+)"
                            +"|(www\\.\\S+)"
                            +"|([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[A-Za-z]{2,})"
                            +"|(@\\w+)"
                            +"|(#[\\w\\u0980-\\u09FF]+)"

            );

    /*
     * =============================
     * STATIC INITIALIZER
     * =============================
     */

    static{

        initializeVowels();

        initializeVowelSigns();

        initializeConsonants();

        initializeDigits();

        initializeJointLetters();

        initializePrefixRules();

        initializeSuffixRules();

        initializeExceptionDictionary();

    }
        /*
     * ======================================
     * INITIALIZE VOWELS
     * ======================================
     */

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

    /*
     * ======================================
     * INITIALIZE VOWEL SIGNS
     * ======================================
     */

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

    /*
     * ======================================
     * INITIALIZE DIGITS
     * ======================================
     */

    private static void initializeDigits() {

        DIGITS.put('০','0');
        DIGITS.put('১','1');
        DIGITS.put('২','2');
        DIGITS.put('৩','3');
        DIGITS.put('৪','4');

        DIGITS.put('৫','5');
        DIGITS.put('৬','6');
        DIGITS.put('৭','7');
        DIGITS.put('৮','8');
        DIGITS.put('৯','9');

    }
        /*
     * ======================================
     * INITIALIZE CONSONANTS
     * ======================================
     */

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
        CONSONANTS.put('ফ', "ph");
        CONSONANTS.put('ব', "b");
        CONSONANTS.put('ভ', "bh");
        CONSONANTS.put('ম', "m");

        CONSONANTS.put('য', "j");
        CONSONANTS.put('র', "r");
        CONSONANTS.put('ল', "l");

        CONSONANTS.put('শ', "sh");
        CONSONANTS.put('ষ', "sh");
        CONSONANTS.put('স', "s");
        CONSONANTS.put('হ', "h");

        CONSONANTS.put('ড়', "r");
        CONSONANTS.put('ঢ়', "rh");
        CONSONANTS.put('য়', "y");
        CONSONANTS.put('ৎ', "t");

            }
        /*
     * ======================================
     * INITIALIZE JOINT LETTERS
     * ======================================
     */

    private static void initializeJointLetters() {

        // ক Series
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

        // গ Series
        JOINT.put("গ্ধ", "gdh");
        JOINT.put("গ্ন", "gn");
        JOINT.put("গ্ন্য", "gny");
        JOINT.put("গ্ল", "gl");
        JOINT.put("গ্ব", "gb");
        JOINT.put("ঘ্ন", "ghn");

        // ঙ Series
        JOINT.put("ঙ্ক", "nk");
        JOINT.put("ঙ্খ", "nkh");
        JOINT.put("ঙ্গ", "ng");
        JOINT.put("ঙ্গ্য", "ngy");
        JOINT.put("ঙ্ঘ", "ngh");
        JOINT.put("ঙ্ক্ষ", "nksh");

        // চ Series
        JOINT.put("চ্চ", "cc");
        JOINT.put("চ্ছ", "cch");
        JOINT.put("চ্ছ্ব", "cchw");

        // জ Series
        JOINT.put("জ্জ", "jj");
        JOINT.put("জ্জ্ব", "jjw");

        // ঞ Series
        JOINT.put("ঞ্চ", "nch");
        JOINT.put("ঞ্ছ", "nchh");
        JOINT.put("ঞ্জ", "nj");
        JOINT.put("ঞ্ঝ", "njh");

        // ট/ড Series
        JOINT.put("ট্ট", "tt");
        JOINT.put("ড্ড", "dd");
        JOINT.put("ণ্ট", "nt");
        JOINT.put("ন্ঠ", "nth");
        JOINT.put("ণ্ড", "nd");

        // ত Series
        JOINT.put("ত্ত", "tt");
        JOINT.put("ত্ত্ব", "ttw");
        JOINT.put("ত্ম", "tm");
        JOINT.put("ত্র", "tr");
        JOINT.put("থ্য", "thy");

        // দ Series
        JOINT.put("দ্ভ", "dbh");
        JOINT.put("দ্ম", "dm");
        JOINT.put("দ্য", "dy");
        JOINT.put("দ্র", "dr");
        JOINT.put("দ্ধ", "ddh");
        JOINT.put("দ্ব", "db");

        // ধ Series
        JOINT.put("ধ্ব", "dhw");

        // ন Series
        JOINT.put("ন্ত", "nt");
        JOINT.put("ন্ত্র", "ntr");
        JOINT.put("ন্থ", "nth");
        JOINT.put("ন্দ", "nd");
        JOINT.put("ন্ধ", "ndh");
        JOINT.put("ন্ন", "nn");
        JOINT.put("ন্ম", "nm");

        // প/ব/ম Series
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

        // ল/শ/স/হ Series
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
        /*
     * ======================================
     * INITIALIZE PREFIX RULES
     * ======================================
     */

    private static void initializePrefixRules() {

        PREFIX.put("অ", "o");
        PREFIX.put("অন", "on");
        PREFIX.put("অপ", "opo");
        PREFIX.put("অধি", "odhi");
        PREFIX.put("অতি", "oti");

        PREFIX.put("উপ", "upo");
        PREFIX.put("উৎ", "ut");

        PREFIX.put("প্র", "pro");
        PREFIX.put("প্রতি", "proti");

        PREFIX.put("বি", "bi");
        PREFIX.put("বি:", "bi");

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

    /*
     * ======================================
     * INITIALIZE SUFFIX RULES
     * ======================================
     */

    private static void initializeSuffixRules() {

        SUFFIX.put("টা", "ta");
        SUFFIX.put("টি", "ti");
        SUFFIX.put("গুলো", "gulo");
        SUFFIX.put("গুলি", "guli");
        SUFFIX.put("দের", "der");
        SUFFIX.put("কে", "ke");
        SUFFIX.put("তে", "te");
        SUFFIX.put("য়ের", "yer");
        SUFFIX.put("এর", "er");
        SUFFIX.put("র", "r");

        SUFFIX.put("ভাবে", "bhabe");
        SUFFIX.put("খানা", "khana");
        SUFFIX.put("খানি", "khani");
        SUFFIX.put("জন", "jon");
        SUFFIX.put("জনক", "jonok");
        SUFFIX.put("কারী", "kari");
        SUFFIX.put("ত্ব", "tto");
        SUFFIX.put("ময়", "moy");
        SUFFIX.put("শীল", "shil");
        SUFFIX.put("পূর্ণ", "purno");

    }
        /*
     * ======================================
     * INITIALIZE EXCEPTION DICTIONARY
     * ======================================
     */

    private static void initializeExceptionDictionary() {

        // Pronouns
        EXCEPTION.put("আমি", "ami");
        EXCEPTION.put("আমরা", "amra");
        EXCEPTION.put("তুমি", "tumi");
        EXCEPTION.put("তোমরা", "tomra");
        EXCEPTION.put("সে", "she");
        EXCEPTION.put("তিনি", "tini");
        EXCEPTION.put("আপনি", "apni");
        EXCEPTION.put("আপনার", "apnar");

        // Common Words
        EXCEPTION.put("বাংলা", "bangla");
        EXCEPTION.put("বাংলাদেশ", "bangladesh");
        EXCEPTION.put("ভালো", "bhalo");
        EXCEPTION.put("খারাপ", "kharap");
        EXCEPTION.put("বন্ধু", "bondhu");
        EXCEPTION.put("দেশ", "desh");
        EXCEPTION.put("মানুষ", "manush");
        EXCEPTION.put("পৃথিবী", "prithibi");

        // Difficult Words
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

        // Verb
        EXCEPTION.put("করছি", "korchi");
        EXCEPTION.put("করেছিল", "korechilo");
        EXCEPTION.put("করবে", "korbe");
        EXCEPTION.put("করেছি", "korechi");
        EXCEPTION.put("খাচ্ছি", "khacchi");
        EXCEPTION.put("যাচ্ছি", "jacchi");

        // Places
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
        /*
     * ======================================
     * SETTINGS
     * ======================================
     */

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

    /*
     * ======================================
     * CACHE
     * ======================================
     */

    public static void clearCache() {

        CACHE.clear();

    }

    private static void log(String message) {

        if (debugMode) {

            Log.d(TAG, message);

        }

    }

    /*
     * ======================================
     * DICTIONARY
     * ======================================
     */

    public static void addDictionaryWord(
            String bangla,
            String banglish) {

        if (bangla == null || banglish == null)
            return;

        bangla = cleanUnicode(
                Normalizer.normalize(
                        bangla,
                        Normalizer.Form.NFC));

        DICTIONARY.put(
                bangla.trim(),
                banglish.trim());

        CACHE.remove(bangla);

    }

    public static void removeDictionaryWord(
            String bangla) {

        if (bangla == null)
            return;

        DICTIONARY.remove(bangla);

        CACHE.remove(bangla);

    }

    public static void clearDictionary() {

        DICTIONARY.clear();

        CACHE.clear();

            }
    
    
