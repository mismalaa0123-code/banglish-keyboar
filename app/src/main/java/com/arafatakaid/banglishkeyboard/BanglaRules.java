public class BanglaRules {
    // এখানে আপনি স্পেশাল যুক্তবর্ণের লজিক বা হসন্ত লজিক দিতে পারেন
    public static boolean isVowelSign(char c) {
        return (c >= '\u09BE' && c <= '\u09CC') || c == '\u09D7';
    }
}
