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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise-Grade & Production-Ready Bangla to Banglish Transliteration Converter.
 *
 * @author Arafat Akaid
 * @version 2.0.0
 */
public class BanglaToBanglishConverter {

    private static final String TAG = "BanglaToBanglishConv";

    public enum Style {
        NATURAL,
        SIMPLE,
        ACADEMIC
    }

    private static volatile Style currentStyle = Style.NATURAL;
    private static volatile boolean convertDigitsToEnglish = true;
    private static volatile boolean debugLoggingEnabled = false;

    private static final int MAX_CACHE_SIZE = 10000;
    private static final Map<String, String> TRANS_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(1000, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
    );

    private static final Map<String, String> CUSTOM_DICTIONARY = new ConcurrentHashMap<>(65536);
    private static final Map<String, String> EXCEPTION_DICTIONARY = new ConcurrentHashMap<>(4096);
    private static final ReentrantReadWriteLock DICTIONARY_LOCK = new ReentrantReadWriteLock();

    private static final Map<Character, String> CONSONANTS_NATURAL = new HashMap<>(64);
    private static final Map<Character, String> CONSONANTS_SIMPLE = new HashMap<>(64);
    private static final Map<Character, String> CONSONANTS_ACADEMIC = new HashMap<>(64);

    private static final Map<Character, String> VOWELS_NATURAL = new HashMap<>(16);
    private static final Map<Character, String> VOWELS_SIMPLE = new HashMap<>(16);
    private static final Map<Character, String> VOWELS_ACADEMIC = new HashMap<>(16);

    private static final Map<Character, String> VOWEL_SIGNS_NATURAL = new HashMap<>(16);
    private static final Map<Character, String> VOWEL_SIGNS_SIMPLE = new HashMap<>(16);
    private static final Map<Character, String> VOWEL_SIGNS_ACADEMIC = new HashMap<>(16);

    private static final Map<Character, Character> BANGLA_DIGITS = new HashMap<>(16);
    private static final Map<String, String> JOINT_LETTERS = new HashMap<>(1024);

    private static final Map<String, String> SUFFIXES = new HashMap<>(64);
    private static final Map<String, String> PREFIXES = new HashMap<>(64);

    private static final char HASANT = '\u09CD';
    private static final char ANUSVARA = '\u0982';
    private static final char CHANDRABINDU = '\u0981';
    private static final char VISARGA = '\u0983';
    private static final char NUKTA = '\u09BC';
    private static final char ZWJ = '\u200D';
    private static final char ZWNJ = '\u200C';

    private static final Pattern PRESERVE_PATTERN = Pattern.compile(
            "(?i)" +
            "(https?://\\S+|www\\.\\S+)|" +                           
            "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})|" +   
            "(<[^>]+>)|" +                                            
            "(\\[[^\\]]*\\]\\([^\\)]+\\))|" +                          
            "(@\\w+)|" +                                             
            "(#[\u0980-\u09FF\\w]+)|" +                              
            "(\\+?\\d{1,4}[\\s-]?)?(?>(?:\\(\\d{1,3}\\)|\\d{1,3})[\\s-]?){2,}\\d{3,4}|" + 
            "(\\p{So}|\\p{Cn})"                                       
    );

    static {
        initializeVowels();
        initializeVowelSigns();
        initializeConsonants();
        initializeDigits();
        initializeJointLetters();
        initializePrefixSuffix();
        initializeExceptionDictionary();
    }

    public static void setStyle(Style style) {
        if (style != null && currentStyle != style) {
            currentStyle = style;
            clearCache();
            logDebug("Transliteration style changed to: " + style);
        }
    }

    public static Style getStyle() {
        return currentStyle;
    }

    public static void setConvertDigitsToEnglish(boolean convert) {
        if (convertDigitsToEnglish != convert) {
            convertDigitsToEnglish = convert;
            clearCache();
        }
    }

    public static void setDebugLoggingEnabled(boolean enabled) {
        debugLoggingEnabled = enabled;
    }

    public static void clearCache() {
        TRANS_CACHE.clear();
        logDebug("Transliteration LRU cache cleared.");
    }

    public static void loadDictionaryFromAssets(Context context, String fileName) {
        if (context == null || fileName == null) return;
        DICTIONARY_LOCK.writeLock().lock();
        try (InputStream is = context.getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JSONObject jsonObject = new JSONObject(sb.toString());
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = jsonObject.getString(key);
                CUSTOM_DICTIONARY.put(cleanUnicode(Normalizer.normalize(key, Normalizer.Form.NFC)).trim(), value.trim());
            }
            clearCache();
            logDebug("Successfully loaded " + CUSTOM_DICTIONARY.size() + " custom dictionary entries.");
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error loading custom dictionary from assets: " + fileName, e);
        } finally {
            DICTIONARY_LOCK.writeLock().unlock();
        }
    }

    public static void addDictionaryOverride(String banglaWord, String banglishWord) {
        if (banglaWord == null || banglishWord == null) return;
        DICTIONARY_LOCK.writeLock().lock();
        try {
            String clean = cleanUnicode(Normalizer.normalize(banglaWord, Normalizer.Form.NFC)).trim();
            CUSTOM_DICTIONARY.put(clean, banglishWord.trim());
            TRANS_CACHE.remove(clean);
        } finally {
            DICTIONARY_LOCK.writeLock().unlock();
        }
    }

    private static void initializeVowels() {
        VOWELS_NATURAL.put('\u0985', "o");
        VOWELS_NATURAL.put('\u0986', "a");
        VOWELS_NATURAL.put('\u0987', "i");
        VOWELS_NATURAL.put('\u0988', "i");
        VOWELS_NATURAL.put('\u0989', "u");
        VOWELS_NATURAL.put('\u098A', "u");
        VOWELS_NATURAL.put('\u098B', "ri");
        VOWELS_NATURAL.put('\u098F', "e");
        VOWELS_NATURAL.put('\u0990', "oi");
        VOWELS_NATURAL.put('\u0993', "o");
        VOWELS_NATURAL.put('\u0994', "ou");

        VOWELS_SIMPLE.putAll(VOWELS_NATURAL);

        VOWELS_ACADEMIC.put('\u0985', "a");
        VOWELS_ACADEMIC.put('\u0986', "ā");
        VOWELS_ACADEMIC.put('\u0987', "i");
        VOWELS_ACADEMIC.put('\u0988', "ī");
        VOWELS_ACADEMIC.put('\u0989', "u");
        VOWELS_ACADEMIC.put('\u098A', "ū");
        VOWELS_ACADEMIC.put('\u098B', "r̥");
        VOWELS_ACADEMIC.put('\u098F', "ē");
        VOWELS_ACADEMIC.put('\u0990', "ai");
        VOWELS_ACADEMIC.put('\u0993', "ō");
        VOWELS_ACADEMIC.put('\u0994', "au");
    }

    private static void initializeVowelSigns() {
        VOWEL_SIGNS_NATURAL.put('\u09BE', "a");
        VOWEL_SIGNS_NATURAL.put('\u09BF', "i");
        VOWEL_SIGNS_NATURAL.put('\u09C0', "i");
        VOWEL_SIGNS_NATURAL.put('\u09C1', "u");
        VOWEL_SIGNS_NATURAL.put('\u09C2', "u");
        VOWEL_SIGNS_NATURAL.put('\u09C3', "ri");
        VOWEL_SIGNS_NATURAL.put('\u09C7', "e");
        VOWEL_SIGNS_NATURAL.put('\u09C8', "oi");
        VOWEL_SIGNS_NATURAL.put('\u09CB', "o");
        VOWEL_SIGNS_NATURAL.put('\u09CC', "ou");

        VOWEL_SIGNS_SIMPLE.putAll(VOWEL_SIGNS_NATURAL);

        VOWEL_SIGNS_ACADEMIC.put('\u09BE', "ā");
        VOWEL_SIGNS_ACADEMIC.put('\u09BF', "i");
        VOWEL_SIGNS_ACADEMIC.put('\u09C0', "ī");
        VOWEL_SIGNS_ACADEMIC.put('\u09C1', "u");
        VOWEL_SIGNS_ACADEMIC.put('\u09C2', "ū");
        VOWEL_SIGNS_ACADEMIC.put('\u09C3', "r̥");
        VOWEL_SIGNS_ACADEMIC.put('\u09C7', "ē");
        VOWEL_SIGNS_ACADEMIC.put('\u09C8', "ai");
        VOWEL_SIGNS_ACADEMIC.put('\u09CB', "ō");
        VOWEL_SIGNS_ACADEMIC.put('\u09CC', "au");
    }

    private static void initializeConsonants() {
        CONSONANTS_NATURAL.put('\u0995', "k");
        CONSONANTS_NATURAL.put('\u0996', "kh");
        CONSONANTS_NATURAL.put('\u0997', "g");
        CONSONANTS_NATURAL.put('\u0998', "gh");
        CONSONANTS_NATURAL.put('\u0999', "ng");
        CONSONANTS_NATURAL.put('\u099A', "ch");
        CONSONANTS_NATURAL.put('\u099B', "chh");
        CONSONANTS_NATURAL.put('\u099C', "j");
        CONSONANTS_NATURAL.put('\u099D', "jh");
        CONSONANTS_NATURAL.put('\u099E', "ny");
        CONSONANTS_NATURAL.put('\u099F', "t");
        CONSONANTS_NATURAL.put('\u09A0', "th");
        CONSONANTS_NATURAL.put('\u09A1', "d");
        CONSONANTS_NATURAL.put('\u09A2', "dh");
        CONSONANTS_NATURAL.put('\u09A3', "n");
        CONSONANTS_NATURAL.put('\u09A4', "t");
        CONSONANTS_NATURAL.put('\u09A5', "th");
        CONSONANTS_NATURAL.put('\u09A6', "d");
        CONSONANTS_NATURAL.put('\u09A7', "dh");
        CONSONANTS_NATURAL.put('\u09A8', "n");
        CONSONANTS_NATURAL.put('\u09AA', "p");
        CONSONANTS_NATURAL.put('\u09AB', "ph");
        CONSONANTS_NATURAL.put('\u09AC', "b");
        CONSONANTS_NATURAL.put('\u09AD', "bh");
        CONSONANTS_NATURAL.put('\u09AE', "m");
        CONSONANTS_NATURAL.put('\u09AF', "j");
        CONSONANTS_NATURAL.put('\u09B0', "r");
        CONSONANTS_NATURAL.put('\u09B2', "l");
        CONSONANTS_NATURAL.put('\u09B6', "sh");
        CONSONANTS_NATURAL.put('\u09B7', "sh");
        CONSONANTS_NATURAL.put('\u09B8', "s");
        CONSONANTS_NATURAL.put('\u09B9', "h");
        CONSONANTS_NATURAL.put('\u09DC', "r");
        CONSONANTS_NATURAL.put('\u09DD', "rh");
        CONSONANTS_NATURAL.put('\u09DF', "y");
        CONSONANTS_NATURAL.put('\u09CE', "t");

        CONSONANTS_SIMPLE.putAll(CONSONANTS_NATURAL);
        CONSONANTS_SIMPLE.put('\u09AB', "f");
        CONSONANTS_SIMPLE.put('\u09AD', "v");
        CONSONANTS_SIMPLE.put('\u099B', "ch");

        CONSONANTS_ACADEMIC.putAll(CONSONANTS_NATURAL);
        CONSONANTS_ACADEMIC.put('\u099F', "ṭ");
        CONSONANTS_ACADEMIC.put('\u09A0', "ṭh");
        CONSONANTS_ACADEMIC.put('\u09A1', "ḍ");
        CONSONANTS_ACADEMIC.put('\u09A2', "ḍh");
        CONSONANTS_ACADEMIC.put('\u09A3', "ṇ");
        CONSONANTS_ACADEMIC.put('\u09B6', "ś");
        CONSONANTS_ACADEMIC.put('\u09B7', "ṣ");
        CONSONANTS_ACADEMIC.put('\u09DC', "ṛ");
        CONSONANTS_ACADEMIC.put('\u09DD', "ṛh");
    }

    private static void initializeDigits() {
        BANGLA_DIGITS.put('০', '0');
        BANGLA_DIGITS.put('১', '1');
        BANGLA_DIGITS.put('২', '2');
        BANGLA_DIGITS.put('৩', '3');
        BANGLA_DIGITS.put('৪', '4');
        BANGLA_DIGITS.put('৫', '5');
        BANGLA_DIGITS.put('৬', '6');
        BANGLA_DIGITS.put('৭', '7');
        BANGLA_DIGITS.put('৮', '8');
        BANGLA_DIGITS.put('৯', '9');
    }

    private static void initializePrefixSuffix() {
        SUFFIXES.put("গুলো", "gulo");
        SUFFIXES.put("গুলি", "guli");
        SUFFIXES.put("দের", "der");
        SUFFIXES.put("কে", "ke");
        SUFFIXES.put("তে", "te");
        SUFFIXES.put("ের", "er");
        SUFFIXES.put("র", "r");
        SUFFIXES.put("ভাবে", "bhabe");
        SUFFIXES.put("তম", "tomo");
        SUFFIXES.put("খানা", "khana");
        SUFFIXES.put("খানি", "khani");

        PREFIXES.put("অসং", "oshong");
        PREFIXES.put("অপ", "opo");
        PREFIXES.put("উপ", "upo");
        PREFIXES.put("প্রতি", "proti");
        PREFIXES.put("অনতি", "onoti");
        PREFIXES.put("সু", "shu");
        PREFIXES.put("কু", "ku");
        PREFIXES.put("বে", "be");
    }

    private static void initializeJointLetters() {
        JOINT_LETTERS.put("ক্ক", "kk");
        JOINT_LETTERS.put("ক্ত", "kt");
        JOINT_LETTERS.put("ক্ত্র", "ktr");
        JOINT_LETTERS.put("ক্ত্ব", "ktw");
        JOINT_LETTERS.put("ক্ল", "kl");
        JOINT_LETTERS.put("ক্ব", "kb");
        JOINT_LETTERS.put("ক্ষ", "kkh");
        JOINT_LETTERS.put("ক্ষ্ম", "kkhm");
        JOINT_LETTERS.put("ক্ষ্ণ", "kkhn");
        JOINT_LETTERS.put("ক্স", "ks");
        JOINT_LETTERS.put("ক্ষ্য", "kkhy");

        JOINT_LETTERS.put("গ্ধ", "gdh");
        JOINT_LETTERS.put("গ্ন", "gn");
        JOINT_LETTERS.put("গ্ন্য", "gny");
        JOINT_LETTERS.put("গ্ল", "gl");
        JOINT_LETTERS.put("গ্ব", "gb");
        JOINT_LETTERS.put("ঘ্ন", "ghn");

        JOINT_LETTERS.put("ঙ্ক", "nk");
        JOINT_LETTERS.put("ঙ্ক্ষ", "nksh");
        JOINT_LETTERS.put("ঙ্খ", "nkh");
        JOINT_LETTERS.put("ঙ্গ", "ng");
        JOINT_LETTERS.put("ঙ্গ্য", "ngy");
        JOINT_LETTERS.put("ঙ্ঘ", "ngh");

        JOINT_LETTERS.put("চ্চ", "cc");
        JOINT_LETTERS.put("চ্ছ", "cch");
        JOINT_LETTERS.put("চ্ছ্ব", "cchw");

        JOINT_LETTERS.put("জ্জ", "jj");
        JOINT_LETTERS.put("জ্জ্ব", "jjw");

        JOINT_LETTERS.put("ঞ্চ", "nch");
        JOINT_LETTERS.put("ঞ্ছ", "nchh");
        JOINT_LETTERS.put("ঞ্জ", "nj");
        JOINT_LETTERS.put("ঞ্ঝ", "njh");

        JOINT_LETTERS.put("ট্ট", "tt");
        JOINT_LETTERS.put("ড্ড", "dd");
        JOINT_LETTERS.put("ণ্ট", "nt");
        JOINT_LETTERS.put("ন্ঠ", "nth");
        JOINT_LETTERS.put("ণ্ড", "nd");
        JOINT_LETTERS.put("ত্ত", "tt");
        JOINT_LETTERS.put("ত্ত্ব", "ttw");
        JOINT_LETTERS.put("ত্ম", "tm");
        JOINT_LETTERS.put("ত্র", "tr");
        JOINT_LETTERS.put("থ্য", "thy");
        JOINT_LETTERS.put("দ্ভ", "dbh");
        JOINT_LETTERS.put("দ্ম", "dm");
        JOINT_LETTERS.put("দ্য", "dy");
        JOINT_LETTERS.put("দ্র", "dr");
        JOINT_LETTERS.put("দ্ধ", "ddh");
        JOINT_LETTERS.put("দ্ব", "db");
        JOINT_LETTERS.put("ধ্ব", "dhw");

        JOINT_LETTERS.put("ন্ত", "nt");
        JOINT_LETTERS.put("ন্ত্র", "ntr");
        JOINT_LETTERS.put("ন্থ", "nth");
        JOINT_LETTERS.put("ন্দ", "nd");
        JOINT_LETTERS.put("ন্ধ", "ndh");
        JOINT_LETTERS.put("ন্ন", "nn");
        JOINT_LETTERS.put("ন্ম", "nm");

        JOINT_LETTERS.put("প্ত", "pt");
        JOINT_LETTERS.put("প্ল", "pl");
        JOINT_LETTERS.put("ব্দ", "bd");
        JOINT_LETTERS.put("ব্ধ", "bdh");
        JOINT_LETTERS.put("ব্র", "br");
        JOINT_LETTERS.put("ব্য", "by");
        JOINT_LETTERS.put("ভ্র", "bhr");
        JOINT_LETTERS.put("ম্প", "mp");
        JOINT_LETTERS.put("ম্ব", "mb");
        JOINT_LETTERS.put("ম্ভ", "mbh");
        JOINT_LETTERS.put("ম্ম", "mm");
        JOINT_LETTERS.put("ম্য", "my");

        JOINT_LETTERS.put("ল্ক", "lk");
        JOINT_LETTERS.put("ল্প", "lp");
        JOINT_LETTERS.put("ল্ল", "ll");
        JOINT_LETTERS.put("শ্চ", "shc");
        JOINT_LETTERS.put("শ্ছ", "shchh");
        JOINT_LETTERS.put("শ্ন", "shn");
        JOINT_LETTERS.put("শ্ব", "shw");
        JOINT_LETTERS.put("ষ্ট", "sht");
        JOINT_LETTERS.put("স্ট", "st");
        JOINT_LETTERS.put("স্ত", "st");
        JOINT_LETTERS.put("স্থ", "sth");
        JOINT_LETTERS.put("স্প", "sp");
        JOINT_LETTERS.put("স্ফ", "sf");
        JOINT_LETTERS.put("স্ক", "sk");
        JOINT_LETTERS.put("স্ম", "sm");
        JOINT_LETTERS.put("স্ব", "sw");
        JOINT_LETTERS.put("হ্ন", "hn");
        JOINT_LETTERS.put("হ্ম", "hm");
        JOINT_LETTERS.put("হ্য", "hy");
    }

    private static void initializeExceptionDictionary() {
        EXCEPTION_DICTIONARY.put("আমি", "ami");
        EXCEPTION_DICTIONARY.put("তুমি", "tumi");
        EXCEPTION_DICTIONARY.put("বাংলা", "bangla");
        EXCEPTION_DICTIONARY.put("শিক্ষা", "shikkha");
        EXCEPTION_DICTIONARY.put("রক্ষা", "rokkha");
        EXCEPTION_DICTIONARY.put("লক্ষ্য", "lokkho");
        EXCEPTION_DICTIONARY.put("কক্ষ", "kokkho");
        EXCEPTION_DICTIONARY.put("বিজ্ঞান", "biggan");
        EXCEPTION_DICTIONARY.put("অজ্ঞ", "oggo");
        EXCEPTION_DICTIONARY.put("জ্ঞান", "gyan");
        EXCEPTION_DICTIONARY.put("স্কুল", "school");
        EXCEPTION_DICTIONARY.put("রাষ্ট্র", "rashtro");
        EXCEPTION_DICTIONARY.put("কমল", "komol");
        EXCEPTION_DICTIONARY.put("কলম", "kolom");
        EXCEPTION_DICTIONARY.put("বরফ", "borof");
        EXCEPTION_DICTIONARY.put("সরল", "sorol");
        EXCEPTION_DICTIONARY.put("বন্ধু", "bondhu");
        EXCEPTION_DICTIONARY.put("করছি", "korchi");
        EXCEPTION_DICTIONARY.put("যাচ্ছি", "jacchi");
        EXCEPTION_DICTIONARY.put("খাচ্ছি", "khacchi");
        EXCEPTION_DICTIONARY.put("পছন্দ", "pochondo");
        EXCEPTION_DICTIONARY.put("প্রথম", "prothom");
        EXCEPTION_DICTIONARY.put("ভালো", "bhalo");
        EXCEPTION_DICTIONARY.put("মন", "mon");
        EXCEPTION_DICTIONARY.put("দেশ", "desh");
        EXCEPTION_DICTIONARY.put("চা", "cha");
        EXCEPTION_DICTIONARY.put("ছাতা", "chhata");
        EXCEPTION_DICTIONARY.put("ছাত্র", "chhatro");
        EXCEPTION_DICTIONARY.put("চামচ", "chamoc");
        EXCEPTION_DICTIONARY.put("ছবি", "chhobi");
        EXCEPTION_DICTIONARY.put("ছোট", "choto");
        EXCEPTION_DICTIONARY.put("ছেলে", "chele");
        EXCEPTION_DICTIONARY.put("মেয়ে", "meye");
        EXCEPTION_DICTIONARY.put("ক্ষতি", "khoti");
        EXCEPTION_DICTIONARY.put("শিক্ষক", "shikkhok");
        EXCEPTION_DICTIONARY.put("অঞ্চল", "onchol");
        EXCEPTION_DICTIONARY.put("ব্যঞ্জন", "byanjon");
        EXCEPTION_DICTIONARY.put("জঙ্গল", "jongol");
        EXCEPTION_DICTIONARY.put("চঞ্চল", "chonchol");
        EXCEPTION_DICTIONARY.put("সত্য", "shotto");
        EXCEPTION_DICTIONARY.put("বিশ্ব", "bisho");
        EXCEPTION_DICTIONARY.put("পদ্ম", "poddo");
        EXCEPTION_DICTIONARY.put("স্বাস্থ্য", "shastho");
        EXCEPTION_DICTIONARY.put("প্রাকটিকেল", "practical");
        EXCEPTION_DICTIONARY.put("কর্ম", "kormo");
        EXCEPTION_DICTIONARY.put("ধর্ম", "dhormo");
        EXCEPTION_DICTIONARY.put("গ্রাম", "gram");
        EXCEPTION_DICTIONARY.put("প্রকৃতি", "prokriti");
        EXCEPTION_DICTIONARY.put("প্রয়োজন", "proyojon");
        EXCEPTION_DICTIONARY.put("প্রয়োজন", "proyojon");
        EXCEPTION_DICTIONARY.put("সংগীত", "sangeet");
        EXCEPTION_DICTIONARY.put("সংখ্যা", "shonkha");
        EXCEPTION_DICTIONARY.put("সংযোগ", "shongjog");
        EXCEPTION_DICTIONARY.put("অঙ্ক", "onko");
        EXCEPTION_DICTIONARY.put("অঙ্গ", "ongo");
    }

    public static String convert(String banglaText) {
        if (banglaText == null || banglaText.isEmpty()) return "";

        String cleanInput = cleanUnicode(Normalizer.normalize(banglaText, Normalizer.Form.NFC));

        String dictMatch = lookupDictionary(cleanInput.trim());
        if (dictMatch != null) {
            return dictMatch;
        }

        StringBuilder finalResult = new StringBuilder();
        Matcher matcher = PRESERVE_PATTERN.matcher(cleanInput);
        int lastIndex = 0;

        while (matcher.find()) {
            if (matcher.start() > lastIndex) {
                String normalText = cleanInput.substring(lastIndex, matcher.start());
                finalResult.append(processSentence(normalText));
            }
            finalResult.append(matcher.group());
            lastIndex = matcher.end();
        }

        if (lastIndex < cleanInput.length()) {
            String normalText = cleanInput.substring(lastIndex);
            finalResult.append(processSentence(normalText));
        }

        return finalResult.toString();
    }

    private static String processSentence(String text) {
        if (text == null || text.isEmpty()) return "";

        if (TRANS_CACHE.containsKey(text)) {
            return TRANS_CACHE.get(text);
        }

        StringBuilder sb = new StringBuilder();
        StringBuilder wordBuffer = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (isBanglaChar(c) || isBanglaDigit(c)) {
                wordBuffer.append(c);
            } else {
                if (wordBuffer.length() > 0) {
                    sb.append(processWord(wordBuffer.toString()));
                    wordBuffer.setLength(0);
                }
                sb.append(c);
            }
        }

        if (wordBuffer.length() > 0) {
            sb.append(processWord(wordBuffer.toString()));
        }

        String result = sb.toString();
        TRANS_CACHE.put(text, result);
        return result;
    }

    private static String processWord(String word) {
        if (word == null || word.isEmpty()) return "";

        String dictVal = lookupDictionary(word);
        if (dictVal != null) return dictVal;

        String expVal = EXCEPTION_DICTIONARY.get(word);
        if (expVal != null) return expVal;

        for (Map.Entry<String, String> entry : SUFFIXES.entrySet()) {
            String suffix = entry.getKey();
            if (word.endsWith(suffix) && word.length() > suffix.length()) {
                String root = word.substring(0, word.length() - suffix.length());
                String rootTrans = processWord(root);
                return rootTrans + entry.getValue();
            }
        }

        for (Map.Entry<String, String> entry : PREFIXES.entrySet()) {
            String prefix = entry.getKey();
            if (word.startsWith(prefix) && word.length() > prefix.length()) {
                String rest = word.substring(prefix.length());
                String restTrans = processWord(rest);
                return entry.getValue() + restTrans;
            }
        }

        return transliteratePhonetic(word);
    }

    private static String transliteratePhonetic(String word) {
        StringBuilder sb = new StringBuilder();
        int len = word.length();

        Map<Character, String> vowels = currentStyle == Style.ACADEMIC ? VOWELS_ACADEMIC :
                (currentStyle == Style.SIMPLE ? VOWELS_SIMPLE : VOWELS_NATURAL);

        Map<Character, String> vowelSigns = currentStyle == Style.ACADEMIC ? VOWEL_SIGNS_ACADEMIC :
                (currentStyle == Style.SIMPLE ? VOWEL_SIGNS_SIMPLE : VOWEL_SIGNS_NATURAL);

        Map<Character, String> consonants = currentStyle == Style.ACADEMIC ? CONSONANTS_ACADEMIC :
                (currentStyle == Style.SIMPLE ? CONSONANTS_SIMPLE : CONSONANTS_NATURAL);

        for (int i = 0; i < len; i++) {
            char c = word.charAt(i);

            if (isBanglaDigit(c)) {
                if (convertDigitsToEnglish) {
                    sb.append(BANGLA_DIGITS.get(c));
                } else {
                    sb.append(c);
                }
                continue;
            }

            if (i + 2 < len) {
                String sub = word.substring(i, i + 3);
                if (JOINT_LETTERS.containsKey(sub)) {
                    sb.append(JOINT_LETTERS.get(sub));
                    i += 2;
                    continue;
                }
            }

            if (i + 1 < len) {
                String sub = word.substring(i, i + 2);
                if (JOINT_LETTERS.containsKey(sub)) {
                    sb.append(JOINT_LETTERS.get(sub));
                    i += 1;
                    continue;
                }
            }

            if (vowels.containsKey(c)) {
                sb.append(vowels.get(c));
                continue;
            }

            if (consonants.containsKey(c)) {
                sb.append(consonants.get(c));

                boolean hasFollowingVowelSign = false;
                boolean hasHasant = false;

                if (i + 1 < len) {
                    char next = word.charAt(i + 1);
                    if (vowelSigns.containsKey(next)) {
                        sb.append(vowelSigns.get(next));
                        hasFollowingVowelSign = true;
                        i++;
                    } else if (next == HASANT) {
                        hasHasant = true;
                        i++;
                    }
                }

                if (!hasFollowingVowelSign && !hasHasant) {
                    if (i == len - 1) {
                        if (currentStyle == Style.ACADEMIC) {
                            sb.append("a");
                        }
                    } else {
                        char next = word.charAt(i + 1);
                        if (consonants.containsKey(next) || vowels.containsKey(next)) {
                            sb.append("o");
                        }
                    }
                }
                continue;
            }

            if (c == ANUSVARA) {
                sb.append("ng");
            } else if (c == CHANDRABINDU) {
                sb.append("n");
            } else if (c == VISARGA) {
                sb.append("h");
            } else if (c != HASANT && c != NUKTA && c != ZWJ && c != ZWNJ) {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    private static String lookupDictionary(String word) {
        DICTIONARY_LOCK.readLock().lock();
        try {
            return CUSTOM_DICTIONARY.get(word);
        } finally {
            DICTIONARY_LOCK.readLock().unlock();
        }
    }

    private static boolean isBanglaChar(char c) {
        return c >= '\u0980' && c <= '\u09FF';
    }

    private static boolean isBanglaDigit(char c) {
        return BANGLA_DIGITS.containsKey(c);
    }

    private static String cleanUnicode(String text) {
        if (text == null) return "";
        return text.replace("\u200B", "").replace("\uFEFF", "");
    }

    private static void logDebug(String message) {
        if (debugLoggingEnabled) {
            Log.d(TAG, message);
        }
    }
}
