package com.arafatakaid.banglishkeyboard;

public final class BanglaRules {

    private static final char HASANTA = '\u09CD';       // ্
    private static final char ANUSVARA = '\u0982';      // ং
    private static final char VISARGA = '\u0983';       // ঃ
    private static final char CHANDRABINDU = '\u0981';  // ঁ
    private static final char KHANDA_TA = '\u09CE';     // ৎ
    private static final char AU_LENGTH_MARK = '\u09D7'; // ৗ

    private static final char ZERO_WIDTH_JOINER = '\u200D';
    private static final char ZERO_WIDTH_NON_JOINER = '\u200C';

    private BanglaRules() {
        // এই class থেকে object তৈরি করার প্রয়োজন নেই।
    }

    /*
     * Bengali Unicode character কি না পরীক্ষা করে।
     */
    public static boolean isBengaliCharacter(char character) {
        return character >= '\u0980' && character <= '\u09FF';
    }

    /*
     * বাংলা ব্যঞ্জনবর্ণ কি না পরীক্ষা করে।
     */
    public static boolean isConsonant(char character) {
        return (character >= '\u0995' && character <= '\u09B9')
                || character == '\u09DC'   // ড়
                || character == '\u09DD'   // ঢ়
                || character == '\u09DF'   // য়
                || character == KHANDA_TA; // ৎ
    }

    /*
     * বাংলা স্বরবর্ণ কি না পরীক্ষা করে।
     */
    public static boolean isVowel(char character) {
        return (character >= '\u0985' && character <= '\u0994')
                || character == '\u09E0'
                || character == '\u09E1';
    }

    /*
     * কার/মাত্রা কি না পরীক্ষা করে।
     *
     * া ি ী ু ূ ৃ ৄ ে ৈ ো ৌ এবং ৗ
     */
    public static boolean isKar(char character) {
        return (character >= '\u09BE' && character <= '\u09CC')
                || character == AU_LENGTH_MARK;
    }

    /*
     * হসন্ত (্) কি না পরীক্ষা করে।
     */
    public static boolean isHasanta(char character) {
        return character == HASANTA;
    }

    /*
     * বাংলা combining mark কি না পরীক্ষা করে।
     *
     * কার, হসন্ত, ং, ঃ, ঁ এবং কিছু Bengali Unicode mark এখানে ধরা হয়েছে।
     */
    public static boolean isBengaliCombiningCharacter(char character) {
        return isKar(character)
                || isHasanta(character)
                || character == ANUSVARA
                || character == VISARGA
                || character == CHANDRABINDU
                || character == '\u09BC'  // nukta
                || character == '\u09BD'  // avagraha
                || character == '\u09D7'; // au length mark
    }

    /*
     * ZWJ এবং ZWNJ চেক করে।
     * কিছু যুক্তবর্ণ/বিশেষ Unicode sequence-এ এগুলো থাকতে পারে।
     */
    public static boolean isJoiner(char character) {
        return character == ZERO_WIDTH_JOINER
                || character == ZERO_WIDTH_NON_JOINER;
    }

    /*
     * Backspace-এর জন্য কত character delete হবে সেটি হিসাব করে।
     *
     * উদাহরণ:
     *
     * ক      -> 1
     * কি     -> ক + ি একসাথে delete
     * কে     -> ক + ে একসাথে delete
     * ক্     -> ক + ্ একসাথে delete
     * ক্র    -> ক + ্ + র একসাথে delete
     * ক্ষ    -> ক + ্ + ষ একসাথে delete
     * ক্ত্র  -> ক + ্ + ত + ্ + র একসাথে delete
     *
     * ফলে বাংলা যুক্তবর্ণ এবং কার ভেঙে আলাদা অদ্ভুত character থাকার সম্ভাবনা কমে।
     */
    public static int getDeleteLength(String textBeforeCursor) {
        if (textBeforeCursor == null || textBeforeCursor.length() == 0) {
            return 1;
        }

        int index = textBeforeCursor.length() - 1;
        int deleteLength = 0;

        /*
         * Cursor-এর আগে যদি emoji বা surrogate pair থাকে,
         * তাহলে পুরো surrogate pair delete করার চেষ্টা করা হবে।
         */
        char lastCharacter = textBeforeCursor.charAt(index);

        if (Character.isLowSurrogate(lastCharacter)
                && index > 0
                && Character.isHighSurrogate(textBeforeCursor.charAt(index - 1))) {
            return 2;
        }

        /*
         * প্রথমে শেষের কার, হসন্ত, ং, ঃ, ঁ, joiner ইত্যাদি অন্তর্ভুক্ত করবে।
         */
        while (index >= 0) {
            char character = textBeforeCursor.charAt(index);

            if (isBengaliCombiningCharacter(character) || isJoiner(character)) {
                deleteLength++;
                index--;
            } else {
                break;
            }
        }

        /*
         * Combining mark-এর আগের মূল অক্ষরটি অন্তর্ভুক্ত করবে।
         */
        if (index >= 0) {
            deleteLength++;
            index--;
        }

        /*
         * যুক্তবর্ণ থাকলে:
         * পূর্বে হসন্ত থাকলে তার আগের ব্যঞ্জনবর্ণও একই ইউনিটের অংশ।
         *
         * যেমন:
         * ক্ + ষ = ক্ষ
         * ক্ + র = ক্র
         * ত্ + র = ত্র
         */
        while (index >= 0) {
            int checkIndex = index;

            /*
             * মাঝে ZWJ/ZWNJ থাকলে skip করবে।
             */
            while (checkIndex >= 0
                    && isJoiner(textBeforeCursor.charAt(checkIndex))) {
                deleteLength++;
                checkIndex--;
                index--;
            }

            if (checkIndex >= 0
                    && textBeforeCursor.charAt(checkIndex) == HASANTA) {

                deleteLength++;
                index--;
                checkIndex--;

                /*
                 * হসন্তের আগের ব্যঞ্জনবর্ণটি অন্তর্ভুক্ত করবে।
                 */
                if (checkIndex >= 0) {
                    char previousCharacter =
                            textBeforeCursor.charAt(checkIndex);

                    if (isConsonant(previousCharacter)
                            || isVowel(previousCharacter)
                            || isBengaliCharacter(previousCharacter)) {
                        deleteLength++;
                        index--;
                    }
                }
            } else {
                break;
            }
        }

        /*
         * কোনো কারণে 0 হলে অন্তত 1 character delete হবে।
         */
        return Math.max(deleteLength, 1);
    }

    /*
     * একটি character input দেওয়ার আগে সেটি Bengali combining mark কি না জানা যায়।
     */
    public static boolean needsPreviousBaseCharacter(char character) {
        return isKar(character)
                || isHasanta(character)
                || character == ANUSVARA
                || character == VISARGA
                || character == CHANDRABINDU;
    }

    /*
     * হসন্ত দিয়ে যুক্তবর্ণ তৈরির জন্য character sequence বৈধ কি না চেক করা যায়।
     */
    public static boolean canFormConjunct(
            char previousCharacter,
            char currentCharacter
    ) {
        return isConsonant(previousCharacter)
                && isConsonant(currentCharacter);
    }
}
