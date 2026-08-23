package com.arafatakaid.banglishkeyboard;

public class BanglaRules {
    
    // কার বা combining characters চেক করা
    public static boolean isBengaliCombiningChar(char c) {
        return (c >= '\u09BE' && c <= '\u09CC') || c == '\u09CD' || c == '\u09D7';
    }

    // হসন্ত হ্যান্ডলিং লজিক
    public static boolean isHasanta(char c) {
        return c == '\u09CD';
    }

    // বিশেষ অক্ষর হ্যান্ডলিং (যেমন ড়, ঢ়, য়)
    public static String handleSpecialChars(int code) {
        return String.valueOf((char) code);
    }
}
