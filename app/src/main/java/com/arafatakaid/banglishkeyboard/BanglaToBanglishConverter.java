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
 * <p><b>Key Architecture Features:</b>
 * <ul>
 *   <li><b>Transliteration Styles:</b> Dynamic switching between {@link Style#NATURAL}, {@link Style#SIMPLE}, and {@link Style#ACADEMIC}.</li>
 *   <li><b>Advanced Phonetics:</b> Context-aware 'র' (Ref, Phola), Nasal sounds (ং, ঞ, ঙ, ঁ), and 'ক্ষ', 'জ্ঞ' phonetics.</li>
 *   <li><b>O(1) Dictionary System:</b> Supports 50,000+ high-priority exception word overrides via JSON/Map.</li>
 *   <li><b>LRU Cache Engine:</b> Thread-safe bounded memory cache for ultra-fast repeated transliteration.</li>
 *   <li><b>Preserve Special Content:</b> Zero-conversion pass-through for URLs, Emails, Mentions, Hashtags, Emojis, HTML Tags, Markdown, and Phone Numbers.</li>
 *   <li><b>Unicode Cleanliness:</b> NFC Normalization and stripping of ZWJ (\u200D), ZWNJ (\u200C), and Variation Selectors.</li>
 *   <li><b>Thread Safety:</b> Complete concurrent access handling via {@link ConcurrentHashMap}, volatile state, and {@link ReentrantReadWriteLock}.</li>
 *   <li><b>Benchmark & Test Suite:</b> Built-in verification engine with 1000+ regression test inputs.</li>
 * </ul>
 *
 * @author Arafat Akaid
 * @version 2.0.0
 */
public class BanglaToBanglishConverter {

    private static final String TAG = "BanglaToBanglishConv";

    /**
     * Transliteration Style Configurations.
     */
    public enum Style {
        /** Natural phonetic mapping (e.g., ভালো -> bhalo, ফ -> ph) */
        NATURAL,
        /** Simplified phonetic mapping (e.g., ভালো -> valo, ফ -> f) */
        SIMPLE,
        /** Academic ISO-based transliteration mapping */
        ACADEMIC
    }

    // --- Configuration State ---
    private static volatile Style currentStyle = Style.NATURAL;
    private static volatile boolean convertDigitsToEnglish = true;
    private static volatile boolean debugLoggingEnabled = false;

    // --- High-Performance Thread-Safe Caches & Dictionaries ---
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

    // --- Core Phonetic Maps ---
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

    // --- Unicode Constants ---
    private static final char HASANT = '\u09CD';
    private static final char ANUSVARA = '\u0982';
    private static final char CHANDRABINDU = '\u0981';
    private static final char VISARGA = '\u0983';
    private static final char NUKTA = '\u09BC';
    private static final char ZWJ = '\u200D';
    private static final char ZWNJ = '\u200C';

    // --- Special Content Regex Patterns ---
    private static final Pattern PRESERVE_PATTERN = Pattern.compile(
            "(?i)" +
            "(https?://\\S+|www\\.\\S+)|" +                           // URLs
            "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})|" +   // Emails
            "(<[^>]+>)|" +                                            // HTML Tags
            "(\\[[^\\]]*\\]\\([^\\)]+\\))|" +                          // Markdown Links
            "(@\\w+)|" +                                             // Mentions
            "(#[\u0980-\u09FF\\w]+)|" +                              // Hashtags
            "(\\+?\\d{1,4}[\\s-]?)?(?>(?:\\(\\d{1,3}\\)|\\d{1,3})[\\s-]?){2,}\\d{3,4}|" + // Phone numbers
            "(\\p{So}|\\p{Cn})"                                       // Emojis / Symbols
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

    // ============================================================================
    // CONFIGURATION & PUBLIC API METHODS
    // ============================================================================

    /**
     * Sets the transliteration style dynamically at runtime.
     *
     * @param style Target {@link Style} (NATURAL, SIMPLE, or ACADEMIC).
     */
    public static void setStyle(Style style) {
        if (style != null && currentStyle != style) {
            currentStyle = style;
            clearCache();
            logDebug("Transliteration style changed to: " + style);
        }
    }

    /**
     * Returns the active transliteration style.
     */
    public static Style getStyle() {
        return currentStyle;
    }

    /**
     * Enables or disables conversion of Bangla digits (০-৯) to English digits (0-9).
     */
    public static void setConvertDigitsToEnglish(boolean convert) {
        if (convertDigitsToEnglish != convert) {
            convertDigitsToEnglish = convert;
            clearCache();
        }
    }

    /**
     * Enables or disables internal debug logging.
     */
    public static void setDebugLoggingEnabled(boolean enabled) {
        debugLoggingEnabled = enabled;
    }

    /**
     * Clears the thread-safe LRU word translation cache.
     */
    public static void clearCache() {
        TRANS_CACHE.clear();
        logDebug("Transliteration LRU cache cleared.");
    }

    /**
     * Loads a custom JSON dictionary from Assets for O(1) high-priority lookup.
     * Supports up to 100,000+ entries safely.
     *
     * @param context Android context
     * @param fileName Path to asset file (e.g., "dictionary.json")
     */
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

    /**
     * Programmatically registers a single high-priority dictionary override entry.
     */
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
