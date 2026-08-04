package com.arafatakaid.banglishkeyboard;

import android.content.Context;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class BanglaToBanglishConverter {

    private static final Map<Character, String> CONSONANTS = new HashMap<>();
    private static final Map<Character, String> VOWELS = new HashMap<>();
    private static final Map<Character, String> VOWEL_SIGNS = new HashMap<>();
    
    // কাস্টম ডিকশনারি ম্যাপ (যেমন: বাংলা শব্দের বিপরীতে নির্দিষ্ট বাংলিশ রূপ)
    private static final Map<String, String> CUSTOM_DICTIONARY = new HashMap<>();

    static {
        // Byanjonborno (consonants)
        CONSONANTS.put('\u0995', "k");
        CONSONANTS.put('\u0996', "kh");
        CONSONANTS.put('\u0997', "g");
        CONSONANTS.put('\u0998', "gh");
        CONSONANTS.put('\u0999', "ng");
        CONSONANTS.put('\u099A', "ch");
        CONSONANTS.put('\u099B', "chh");
        CONSONANTS.put('\u099C', "j");
        CONSONANTS.put('\u099D', "jh");
        CONSONANTS.put('\u099E', "ng");
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
        CONSONANTS.put('\u09DC', "r");
        CONSONANTS.put('\u09DD', "rh");
        CONSONANTS.put('\u09DF', "y");
        CONSONANTS.put('\u09CE', "t");

        // Shorborno (independent vowels)
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

        // Kar (vowel signs)
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

    private static final char HASANT = '\u09CD';
    private static final char ANUSVARA = '\u0982';
    private static final char CHANDRABINDU = '\u0981';
    private static final char VISARGA = '\u0983';

    // assets ফোল্ডার থেকে JSON ডিক्रশনারি লোড করার মেথড
    public static void loadDictionaryFromAssets(Context context, String fileName) {
        try {
            InputStream is = context.getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            is.close();

            JSONObject jsonObject = new JSONObject(sb.toString());
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = jsonObject.getString(key);
                CUSTOM_DICTIONARY.put(key, value);
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    public static String convert(String banglaText) {
        StringBuilder result = new StringBuilder();
        String[] words = banglaText.trim().split("\\s+");

        for (int w = 0; w < words.length; w++) {
            result.append(convertWord(words[w]));
            if (w < words.length - 1) result.append(" ");
        }
        return result.toString();
    }

    private static String convertWord(String word) {
        // প্রথমে চেক করবে ডিকশনারিতে এই শব্দটি আছে কিনা, থাকলে সরাসরি ডিকশনারির রূপ রিটার্ন করবে
        if (CUSTOM_DICTIONARY.containsKey(word)) {
            return CUSTOM_DICTIONARY.get(word);
        }

        StringBuilder sb = new StringBuilder();
        int i = 0;
        int len = word.length();

        while (i < len) {
            char c = word.charAt(i);

            if (CONSONANTS.containsKey(c)) {
                sb.append(CONSONANTS.get(c));

                boolean hasNext = (i + 1) < len;
                char next = hasNext ? word.charAt(i + 1) : '\0';

                if (hasNext && next == HASANT) {
                    i += 2;
                    continue;
                } else if (hasNext && VOWEL_SIGNS.containsKey(next)) {
                    sb.append(VOWEL_SIGNS.get(next));
                    i += 2;
                    continue;
                } else if (hasNext && (next == ANUSVARA || next == CHANDRABINDU || next == VISARGA)) {
                    sb.append("o");
                    i += 1;
                    continue;
                } else {
                    sb.append("o");
                    i += 1;
                    continue;
                }
            } else if (VOWELS.containsKey(c)) {
                sb.append(VOWELS.get(c));
                i += 1;
            } else if (c == ANUSVARA) {
                sb.append("ng");
                i += 1;
            } else if (c == CHANDRABINDU) {
                i += 1;
            } else if (c == VISARGA) {
                sb.append("h");
                i += 1;
            } else if (c == HASANT) {
                i += 1;
            } else {
                sb.append(c);
                i += 1;
            }
        }
        return sb.toString();
    }
}
