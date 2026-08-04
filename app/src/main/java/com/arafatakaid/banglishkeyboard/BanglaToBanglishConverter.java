package com.arafatakaid.banglishkeyboard;

import java.util.HashMap;
import java.util.Map;

public class BanglaToBanglishConverter {

    private static final Map<Character, String> CONSONANTS = new HashMap<>();
    private static final Map<Character, String> VOWELS = new HashMap<>();
    private static final Map<Character, String> VOWEL_SIGNS = new HashMap<>();

    static {
        // Consonants (byanjonborno) - default/inherent vowel bade
        CONSONANTS.put('ক', "k");
        CONSONANTS.put('খ', "kh");
        CONSONANTS.put('গ', "g");
        CONSONANTS.put('ঘ', "gh");
        CONSONANTS.put('ঙ', "ng");
        CONSONANTS.put('চ', "ch");
        CONSONANTS.put('ছ', "chh");
        CONSONANTS.put('জ', "j");
        CONSONANTS.put('ঝ', "jh");
        CONSONANTS.put('ঞ', "ng");
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
        CONSONANTS.put('য', "j");
        CONSONANTS.put('র', "r");
        CONSONANTS.put('ল', "l");
        CONSONANTS.put('শ', "sh");
        CONSONANTS.put('ষ', "sh");
        CONSONANTS.put('স', "s");
        CONSONANTS.put('হ', "h");
        CONSONANTS.put('ড়', "r");
        CONSONANTS.put('ঢ়', "rh");
        CONSONANTS.put('য়', "y");
        CONSONANTS.put('ৎ', "t");

        // Shorborno (independent vowels)
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

        // Kar (vowel signs) - consonant er sathe jog hoy
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

    private static final char HASANT = '্';
    private static final char ANUSVARA = 'ং';
    private static final char CHANDRABINDU = 'ঁ';
    private static final char VISARGA = 'ঃ';

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
                    // Hasant thakle inherent vowel add hobe na, jukto okkhor
                    i += 2;
                    continue;
                } else if (hasNext && VOWEL_SIGNS.containsKey(next)) {
                    sb.append(VOWEL_SIGNS.get(next));
                    i += 2;
                    continue;
                } else if (hasNext && (next == ANUSVARA || next == CHANDRABINDU || next == VISARGA)) {
                    // Inherent vowel add hobe, tarpor ei chihno process hobe pore loop e
                    sb.append("o");
                    i += 1;
                    continue;
                } else {
                    // Kono kar/hasant nai - inherent vowel
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
                // Nasal sound - shব্দে shadharonoto baad deya hoy ba 'n' hisheve dhora hoy
                i += 1;
            } else if (c == VISARGA) {
                sb.append("h");
                i += 1;
            } else if (c == HASANT) {
                // Alada hasant (age consonant na thakle) - skip
                i += 1;
            } else {
                // Bangla noy emon character (number, punctuation) - joto toto rakhbo
                sb.append(c);
                i += 1;
            }
        }
        return sb.toString();
    }
                       }
