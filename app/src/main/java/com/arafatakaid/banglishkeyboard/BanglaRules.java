package com.arafatakaid.banglishkeyboard;

import java.util.HashMap;
import java.util.Map;

public class BanglaRules {

    public static final Map<String, String> RULES = new HashMap<>();

    static {

        // স্বরবর্ণ
        RULES.put("a", "অ");
        RULES.put("aa", "আ");
        RULES.put("i", "ই");
        RULES.put("ii", "ঈ");
        RULES.put("u", "উ");
        RULES.put("uu", "ঊ");
        RULES.put("e", "এ");
        RULES.put("oi", "ঐ");
        RULES.put("o", "ও");
        RULES.put("ou", "ঔ");

        // ব্যঞ্জনবর্ণ
        RULES.put("k", "ক");
        RULES.put("kh", "খ");
        RULES.put("g", "গ");
        RULES.put("gh", "ঘ");
        RULES.put("ng", "ঙ");

        RULES.put("c", "চ");
        RULES.put("ch", "ছ");
        RULES.put("j", "জ");
        RULES.put("jh", "ঝ");
        RULES.put("ny", "ঞ");

        RULES.put("t", "ত");
        RULES.put("th", "থ");
        RULES.put("d", "দ");
        RULES.put("dh", "ধ");
        RULES.put("n", "ন");

        RULES.put("p", "প");
        RULES.put("ph", "ফ");
        RULES.put("b", "ব");
        RULES.put("bh", "ভ");
        RULES.put("m", "ম");

        RULES.put("z", "য");
        RULES.put("r", "র");
        RULES.put("l", "ল");
        RULES.put("sh", "শ");
        RULES.put("ss", "ষ");
        RULES.put("s", "স");
        RULES.put("h", "হ");

        // সাধারণ শব্দ
        RULES.put("ami", "আমি");
        RULES.put("tumi", "তুমি");
        RULES.put("apni", "আপনি");
        RULES.put("valo", "ভালো");
        RULES.put("bhalo", "ভালো");
        RULES.put("kemon", "কেমন");
        RULES.put("ki", "কি");
        RULES.put("na", "না");
        RULES.put("hya", "হ্যাঁ");
        RULES.put("dhonnobad", "ধন্যবাদ");
    }
}
