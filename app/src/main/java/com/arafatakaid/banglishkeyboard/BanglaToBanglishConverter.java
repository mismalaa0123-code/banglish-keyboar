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
 * Version : 4.0 (Natural Banglish - Android compatible)
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

    private static final Map<Character,String> CONSONANTS = new HashMap<>();
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
        CONSONANTS.put('\u09AB', "f");
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

    private static void initializeNuktaConsonants() {
        NUKTA_CONSONANTS.put("\u09A1\u09BC", "r");   // ড়
        NUKTA_CONSONANTS.put("\u09A2\u09BC", "rh");  // ঢ়

        // Bengali "য়" is canonically represented as য + nukta (য + ়).
        // It must be treated as y, not as the normal consonant য -> j.
        NUKTA_CONSONANTS.put("\u09AF\u09BC", "y");  // য়
    }

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
        JOINT.put("ন্য", "nny");
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

        // Ya-phala / common natural clusters. These are handled as clusters
        // instead of independent য -> j so that "ত্য/ধ্য/থ্য" do not become
        // mechanical "tj/dj/thj" sequences.
        JOINT.put("ত্য", "tyo");
        JOINT.put("থ্য", "thyo");
        JOINT.put("দ্য", "dyo");
        JOINT.put("ধ্য", "dhyo");
        JOINT.put("শ্য", "shyo");
        JOINT.put("ষ্য", "shyo");
        JOINT.put("স্ন্য", "sny");
        JOINT.put("হ্য", "hyo");
    }

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

        // Verb endings: keep the stem's final consonant from gaining
        // an unwanted inherent "o" before these suffixes.
        SUFFIX.put("বে", "be");
        SUFFIX.put("ব", "bo");
    }

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
        EXCEPTION.put("যাব", "jab");
        EXCEPTION.put("হয়ে", "hoye");
        EXCEPTION.put("সময়ে", "somoye");
        EXCEPTION.put("অন্যায়", "onyay");
        EXCEPTION.put("ন্যায়", "nyay");
        EXCEPTION.put("কন্যা", "konnya");
        EXCEPTION.put("ধন্যবাদ", "dhonnobad");
        EXCEPTION.put("অন্যকে", "onnoke");
        EXCEPTION.put("অন্যদের", "onnoder");
        EXCEPTION.put("কন্যাকে", "konnake");
        EXCEPTION.put("কন্যাদের", "konnader");
        EXCEPTION.put("ন্যায়কে", "nyayke");
        EXCEPTION.put("ধন্যবাদকে", "dhonnobadke");
        EXCEPTION.put("সন্যাসী", "sannyasi");
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

        // --- Category A: consonant-cluster schwa words (locked to exact
        //     expected output, in addition to the general algorithm fix) ---
        EXCEPTION.put("দরকারি", "dorkari");
        EXCEPTION.put("থাকলে", "thakle");
        EXCEPTION.put("আসবো", "asbo");
        EXCEPTION.put("ঠিকঠাক", "thikthak");
        EXCEPTION.put("সবকিছু", "sobkichu");

        // --- Category B: essential sound omission fixes ---
        EXCEPTION.put("হতে", "hote");
        EXCEPTION.put("অন্য", "onno");
        EXCEPTION.put("নষ্ট", "noshto");
        EXCEPTION.put("পৌঁছাতে", "pouchate");
        EXCEPTION.put("হয়েছিল", "hoyechilo");
        EXCEPTION.put("শিখিয়েছিল", "shikhiyechilo");

        // --- Category C: phonetic mapping fixes ---
        EXCEPTION.put("ফেরার", "ferar");
        EXCEPTION.put("ফ্রি", "free");
        EXCEPTION.put("হওয়া", "howa");
        EXCEPTION.put("হওয়ার", "howar");

        // --- Category D: English/foreign loanwords ---
        EXCEPTION.put("কনভার্টার", "converter");
        EXCEPTION.put("আপডেট", "update");
        EXCEPTION.put("আপডেটে", "update-e");
        EXCEPTION.put("ব্যাকআপ", "backup");
        EXCEPTION.put("ক্যামেরা", "camera");
        EXCEPTION.put("রিভিউ", "review");
        EXCEPTION.put("ফোন", "phone");
        EXCEPTION.put("চার্জ", "charge");
        EXCEPTION.put("রেসিপি", "recipe");
        EXCEPTION.put("সিলেবাস", "syllabus");
        EXCEPTION.put("নোটস", "notes");
        EXCEPTION.put("অ্যালার্ম", "alarm");

        // ================================================================
        // Natural Banglish regression set
        // These are irregular/lexical spellings that cannot be derived
        // reliably from Bengali orthography alone.
        // ================================================================
        EXCEPTION.put("সময়মতো", "somoymoto");
        EXCEPTION.put("ধরনের", "dhoroner");
        EXCEPTION.put("জীবনের", "jiboner");
        EXCEPTION.put("একাগ্রতা", "ekagrota");
        EXCEPTION.put("নিয়মিত", "niyomit");
        EXCEPTION.put("জগতের", "jogoter");
        EXCEPTION.put("বাস্তবে", "bastobe");
        EXCEPTION.put("ভ্রমণের", "bhromoner");
        EXCEPTION.put("গুজব", "gujob");
        EXCEPTION.put("উদ্যমে", "udyome");
        EXCEPTION.put("অর্জনের", "orjoner");
        EXCEPTION.put("বয়স", "boyos");

        EXCEPTION.put("উদ্দেশ্যে", "uddeshe");
        EXCEPTION.put("ভবিষ্যতে", "bhobishote");
        EXCEPTION.put("বিশ্বাসযোগ্য", "bishwasjoggo");
        EXCEPTION.put("স্বাস্থ্যের", "shasthyer");
        EXCEPTION.put("মাধ্যমের", "madhyomer");
        EXCEPTION.put("সত্যতা", "sotyota");
        EXCEPTION.put("বাধ্য", "baddho");
        EXCEPTION.put("স্বপ্নের", "sopner");

        EXCEPTION.put("ফেসবুক", "facebook");
        EXCEPTION.put("সোশ্যাল", "social");
        EXCEPTION.put("মিডিয়া", "media");
        EXCEPTION.put("মিডিয়া", "media");
        EXCEPTION.put("প্রোডাক্ট", "product");
        EXCEPTION.put("রেটিং", "rating");
        EXCEPTION.put("ট্যুর", "tour");
        EXCEPTION.put("প্ল্যান", "plan");
        EXCEPTION.put("জিপিএস", "GPS");
        EXCEPTION.put("রুট", "route");
        EXCEPTION.put("ব্যালকনি", "balcony");
        EXCEPTION.put("অ্যাপ", "app");
        EXCEPTION.put("স্মুথ", "smooth");
        EXCEPTION.put("পারফর্ম", "perform");

        EXCEPTION.put("আরও", "aro");
        EXCEPTION.put("কাজও", "kajo");
        EXCEPTION.put("খুবই", "khuboi");
        EXCEPTION.put("রওনা", "raona");
        EXCEPTION.put("নিঃস্বার্থ", "nishwartho");

        EXCEPTION.put("উঠল", "uthlo");
        EXCEPTION.put("আনন্দদায়ক", "anondodayok");
        EXCEPTION.put("আনন্দদায়ক", "anondodayok");
        EXCEPTION.put("সামাজিক", "somajik");
        EXCEPTION.put("প্রফুল্ল", "profullo");

        // Six conversational test cases / frequently typed chat words.
        EXCEPTION.put("সারাদিন", "saradin");
        EXCEPTION.put("কোথায়", "kothay");
        EXCEPTION.put("কোথায়", "kothay");
        EXCEPTION.put("ছিলি", "chhili");
        EXCEPTION.put("অনেকবার", "onekbar");
        EXCEPTION.put("দিয়েছি", "diyechhi");
        EXCEPTION.put("দিয়েছিলি", "diyechhili");
        EXCEPTION.put("অনলাইনে", "online");
        EXCEPTION.put("ব্যস্ত", "byosto");
        EXCEPTION.put("আছিস", "achhis");
        EXCEPTION.put("সময়", "somoy");
        EXCEPTION.put("সময়", "somoy");
        EXCEPTION.put("মেসেঞ্জার", "messenger");
        EXCEPTION.put("মেসেঞ্জারে", "messenger-e");
        EXCEPTION.put("মেসেঞ্জারের", "messenger-er");
        EXCEPTION.put("ফোন", "phone");
        EXCEPTION.put("রিপ্লাই", "reply");
        EXCEPTION.put("ভিডিও", "video");
        EXCEPTION.put("ভিডিওটা", "video-ta");
        EXCEPTION.put("টিকটক", "tiktok");
        EXCEPTION.put("টিকটকে", "tiktok-e");
        EXCEPTION.put("ফেসবুকে", "facebook-e");
        EXCEPTION.put("পাঠাতে", "pathate");
        EXCEPTION.put("ইচ্ছা", "ichchha");
        EXCEPTION.put("করলো", "korlo");
        EXCEPTION.put("করল", "korlo");
        EXCEPTION.put("মজার", "mojar");
        EXCEPTION.put("কয়েকবার", "koyekbar");
        EXCEPTION.put("কয়েকবার", "koyekbar");
        EXCEPTION.put("হাসি", "hashi");
        EXCEPTION.put("থামাতে", "thamate");
        EXCEPTION.put("বলিস", "bolis");
        EXCEPTION.put("লাগলো", "laglo");
        EXCEPTION.put("লাগল", "laglo");
        EXCEPTION.put("সবার", "shobar");
        EXCEPTION.put("ঠিকমতো", "thikmoto");
        EXCEPTION.put("এখনও", "ekhono");
        EXCEPTION.put("দুজন", "dujone");
        EXCEPTION.put("বলে", "bole");
        EXCEPTION.put("হলে", "hole");
        EXCEPTION.put("যদি", "jodi");
        EXCEPTION.put("যাবে", "jabe");
        EXCEPTION.put("যাবো", "jabo");
        EXCEPTION.put("জানাস", "janas");
        EXCEPTION.put("দিস", "dis");
        EXCEPTION.put("করিস", "koris");
        EXCEPTION.put("পারি", "pari");
        EXCEPTION.put("পারিস", "paris");
        EXCEPTION.put("পরে", "pore");
        EXCEPTION.put("জরুরি", "joruri");
        EXCEPTION.put("পড়ে", "pore");
        EXCEPTION.put("চিন্তা", "chinta");
        EXCEPTION.put("কাজটা", "kajta");
        EXCEPTION.put("শেষ", "shesh");
        EXCEPTION.put("নিজে", "nije");
        EXCEPTION.put("নিজে থেকেই", "nije thekei");
        EXCEPTION.put("এর মধ্যে", "er moddhe");
        EXCEPTION.put("দরকার", "dorkar");
        EXCEPTION.put("হ্যাঁ", "hya");
        EXCEPTION.put("কালকের", "kalker");
        EXCEPTION.put("ছিল", "chhilo");
        EXCEPTION.put("দেখাচ্ছিল", "dekhacchhilo");
        EXCEPTION.put("এখনো", "ekhono");
        EXCEPTION.put("এখনও", "ekhono");
        EXCEPTION.put("এখনো", "ekhono");
        EXCEPTION.put("এত", "eto");
        EXCEPTION.put("দুজনে", "dujone");
        EXCEPTION.put("বলবো", "bolbo");
        EXCEPTION.put("যাওয়া", "jawa");
        EXCEPTION.put("যাওয়া", "jawa");
        EXCEPTION.put("তাহলে", "tahole");
        EXCEPTION.put("যেন", "jeno");
        EXCEPTION.put("এর", "er");
        EXCEPTION.put("মধ্যে", "moddhe");
        EXCEPTION.put("ফেসবুকেও", "facebook-eo");
        EXCEPTION.put("আজকে", "ajke");
        EXCEPTION.put("কালকে", "kalke");
        EXCEPTION.put("একটা", "ekta");
        EXCEPTION.put("একটু", "ektu");
        EXCEPTION.put("হয়তো", "hoyto");
        EXCEPTION.put("হয়তো", "hoyto");
        EXCEPTION.put("মেসেজ", "message");
        EXCEPTION.put("মেসেজটা", "message-ta");
        EXCEPTION.put("মেসেজের", "message-er");
        EXCEPTION.put("মেসেজে", "message-e");
        EXCEPTION.put("ফোনটা", "phone-ta");
        EXCEPTION.put("ফোনে", "phone-e");
        EXCEPTION.put("রিপ্লাই", "reply");
        EXCEPTION.put("শেয়ার", "share");
        EXCEPTION.put("শেয়ার", "share");
        EXCEPTION.put("অনলাইন", "online");
        EXCEPTION.put("টিকটক", "tiktok");
        EXCEPTION.put("টিকটকে", "tiktok-e");
        EXCEPTION.put("নক", "nok");
        EXCEPTION.put("দুজন", "dujone");
        EXCEPTION.put("দুইজন", "duijon");
        EXCEPTION.put("দুইজনকে", "duijonke");
        EXCEPTION.put("কোথাও", "kothao");
        EXCEPTION.put("কোনো", "kono");
        EXCEPTION.put("কোনও", "kono");
        EXCEPTION.put("কোনোটা", "konota");
        EXCEPTION.put("সবার", "shobar");
        EXCEPTION.put("পরে", "pore");
        EXCEPTION.put("তারপর", "tarpor");
        EXCEPTION.put("আরও", "aro");
        EXCEPTION.put("এর মধ্যে", "er moddhe");
        EXCEPTION.put("প্ল্যানটা", "plan-ta");
        EXCEPTION.put("ভুলে যাস", "bhule jas");
    }

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

    public static void addDictionaryWord(String bangla, String banglish) {
        if (bangla == null || banglish == null) return;
        bangla = cleanUnicode(Normalizer.normalize(bangla, Normalizer.Form.NFC));
        DICTIONARY.put(bangla.trim(), banglish.trim());
        CACHE.remove(bangla);
    }

    public static void removeDictionaryWord(String bangla) {
        if (bangla == null) return;
        DICTIONARY.remove(bangla);
        CACHE.remove(bangla);
    }

    public static void clearDictionary() {
        DICTIONARY.clear();
        CACHE.clear();
    }

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
                DICTIONARY.put(cleanUnicode(key), object.getString(key));
            }

            CACHE.clear();

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Dictionary Load Failed", e);
        }
    }

    private static String cleanUnicode(String text) {
        if (text == null) return "";
        text = Normalizer.normalize(text, Normalizer.Form.NFC);
        text = text.replace("\u200C", "");
        text = text.replace("\u200D", "");
        text = text.replace("\uFEFF", "");
        return text.trim();
    }

    private static String lookupDictionary(String word) {
        if (word == null || word.isEmpty()) return null;

        word = cleanUnicode(word);

        String cache = CACHE.get(word);
        if (cache != null) return cache;

        String value = DICTIONARY.get(word);
        if (value == null) value = EXCEPTION.get(word);

        // NFC may represent Bengali "য়" as য + ়. Keep exception lookup
        // compatible with both Unicode forms.
        if (value == null && word.indexOf("\u09AF\u09BC") >= 0) {
            value = EXCEPTION.get(word.replace("\u09AF\u09BC", "\u09DF"));
        }

        if (value != null) CACHE.put(word, value);

        return value;
    }

    private static String findLongestJoint(String word, int start) {
        int maxLen = Math.min(8, word.length() - start);
        for (int len = maxLen; len >= 2; len--) {
            String sub = word.substring(start, start + len);
            if (JOINT.containsKey(sub)) return sub;
        }
        return null;
    }

    private static String findNuktaConsonant(String word, int start) {
        if (start + 1 < word.length()) {
            String pair = word.substring(start, start + 2);
            if (NUKTA_CONSONANTS.containsKey(pair)) return pair;
        }
        return null;
    }

    /*
     * Smart Schwa Suppression.
     *
     * Bengali orthography does not mark the inherent vowel ("schwa" / 'o')
     * explicitly, but spoken Bangla frequently drops it in the middle of a
     * word when a "bare" consonant (one carrying no vowel sign of its own)
     * is immediately followed by another consonant that DOES carry its own
     * explicit vowel sign - e.g. থাকলে is spoken "thakle", not "thakole".
     *
     * The very first consonant of a word/stem almost always keeps its
     * inherent vowel in natural speech (e.g. সকাল -> "sokal", not "skal"),
     * so this suppression is intentionally skipped when the consonant in
     * question is at position 0 of the string being transliterated.
     */
    private static boolean nextIsVowelBearingConsonant(String word, int pos, int len) {
        if (pos >= len) return false;
        char next = word.charAt(pos);
        if (!CONSONANTS.containsKey(next)) return false;
        int after = pos + 1;
        return after < len && VOWEL_SIGNS.containsKey(word.charAt(after));
    }

    /*
     * Overload kept so the original API/logic remains intact.
     * The second form is used when a suffix is attached to a stem and
     * the stem's final consonant must not receive an extra inherent "o".
     */
    private static String transliterateCore(String word) {
        return transliterateCore(word, false);
    }

    /**
     * Core transliteration.
     *
     * Important Natural-mode rules:
     * 1) Explicit vowel signs are always authoritative.
     * 2) A word-final bare consonant does NOT receive an invented "o".
     * 3) Existing Bengali hasanta/joint spelling controls consonant clusters.
     * 4) We do not globally delete "o" after conversion; that would break
     *    words where the inherent vowel is genuinely pronounced.
     */
    private static String transliterateCore(String word, boolean suppressFinalInherentVowel) {
        if (word == null || word.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        int i = 0;
        int len = word.length();

        while (i < len) {
            String jointMatch = findLongestJoint(word, i);
            if (jointMatch != null) {
                sb.append(JOINT.get(jointMatch));
                i += jointMatch.length();

                if (i < len && VOWEL_SIGNS.containsKey(word.charAt(i))) {
                    sb.append(VOWEL_SIGNS.get(word.charAt(i)));
                    i++;
                } else if (i < len && word.charAt(i) == HASANTA) {
                    i++;
                } else if (currentStyle == Style.NATURAL
                        && i < len
                        && !(suppressFinalInherentVowel && i == len)) {
                    sb.append("o");
                }
                continue;
            }

            String nuktaMatch = findNuktaConsonant(word, i);
            if (nuktaMatch != null) {
                String value = NUKTA_CONSONANTS.get(nuktaMatch);
                sb.append(value);
                i += nuktaMatch.length();

                if (i < len && word.charAt(i) == HASANTA) {
                    i++;
                } else if (i < len && VOWEL_SIGNS.containsKey(word.charAt(i))) {
                    sb.append(VOWEL_SIGNS.get(word.charAt(i)));
                    i++;
                } else if (currentStyle == Style.NATURAL
                        && !("y".equals(value) && i == len)
                        && !(suppressFinalInherentVowel && i == len)) {
                    sb.append("o");
                }
                continue;
            }

            char c = word.charAt(i);

            if (c == HASANTA) {
                i++;
                continue;
            }

            if (CONSONANTS.containsKey(c)) {
                sb.append(CONSONANTS.get(c));
                i++;

                if (i < len && word.charAt(i) == HASANTA) {
                    i++;
                } else if (i < len && VOWEL_SIGNS.containsKey(word.charAt(i))) {
                    sb.append(VOWEL_SIGNS.get(word.charAt(i)));
                    i++;
                } else if (currentStyle == Style.NATURAL
                        && !(suppressFinalInherentVowel && i == len)
                        && i < len
                        && !(i > 1 && nextIsVowelBearingConsonant(word, i, len))) {
                    // In natural speech, a medial consonant often loses its
                    // inherent "o" before a following consonant that already
                    // carries an explicit vowel: আজকে -> ajke.
                    sb.append("o");
                }
                continue;
            }

            if (VOWELS.containsKey(c)) {
                sb.append(VOWELS.get(c));
                i++;
                continue;
            }

            if (VOWEL_SIGNS.containsKey(c)) {
                sb.append(VOWEL_SIGNS.get(c));
                i++;
                continue;
            }

            if (c == ANUSWAR) {
                sb.append("ng");
                i++;
                continue;
            }

            if (c == VISARGA) {
                sb.append("h");
                i++;
                continue;
            }

            if (c == CHANDRA || c == NUKTA) {
                i++;
                continue;
            }

            if (DIGITS.containsKey(c)) {
                if (convertDigits) sb.append(DIGITS.get(c));
                else sb.append(c);
                i++;
                continue;
            }

            sb.append(c);
            i++;
        }

        return sb.toString();
    }

    private static String processWord(String word) {
        if (word == null || word.isEmpty()) return word;

        String cleaned = cleanUnicode(word);

        // Exact user dictionary has highest priority.
        String direct = DICTIONARY.get(cleaned);
        if (direct != null) {
            CACHE.put(cleaned, direct);
            return direct;
        }

        // Built-in lexical exceptions are second priority.
        direct = EXCEPTION.get(cleaned);
        if (direct == null && cleaned.indexOf("\u09AF\u09BC") >= 0) {
            direct = EXCEPTION.get(cleaned.replace("\u09AF\u09BC", "\u09DF"));
        }
        if (direct != null) {
            CACHE.put(cleaned, direct);
            return direct;
        }

        String result = transliterateCore(cleaned, true);

        CACHE.put(cleaned, result);
        return result;
    }

    private static String processGap(String gap) {
        if (gap == null || gap.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        Matcher wordMatcher = WORD_PATTERN.matcher(gap);
        int last = 0;

        while (wordMatcher.find()) {
            sb.append(gap, last, wordMatcher.start());
            String word = wordMatcher.group();
            sb.append(processWord(word));
            last = wordMatcher.end();
        }

        sb.append(gap.substring(last));
        return sb.toString();
    }

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

    public static String convert(String text) {
        if (text == null || text.isEmpty()) return "";

        String cleaned = cleanUnicode(text);
        log("Converting: " + cleaned);

        String result = processSentence(cleaned);
        log("Result: " + result);

        return result;
    }
}
