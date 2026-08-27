package com.arafatakaid.banglishkeyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BanglishIME extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    private static final String TAG = "BanglishIME";

    // আগের Voice code
    private static final int KEYCODE_MIC = -100;

    // আগের Globe code
    private static final int KEYCODE_GLOBE = -101;

    /*
     * Number / Emoji key codes.
     * সব পুরোনো ও নতুন code support করা হয়েছে,
     * তাই qwerty.xml-এ -301/-302 অথবা -102/-103 থাকলেও কাজ করবে।
     */
    private static final int KEYCODE_NUMBER_SYMBOL = -301;
    private static final int KEYCODE_EMOJI = -302;

    private static final int KEYCODE_NUMBER_SYMBOL_LEGACY = -102;
    private static final int KEYCODE_EMOJI_LEGACY = -103;

    // আগের keyboard controls
    private static final int KEYCODE_DELETE = -5;
    private static final int KEYCODE_SHIFT = -1;
    private static final int KEYCODE_COPY = -201;
    private static final int KEYCODE_PASTE = -202;

    private static final String BANGLA_CHAR_CLASS = "\\u0980-\\u09FF";

    private static final Pattern LAST_BANGLA_TOKEN =
            Pattern.compile("[" + BANGLA_CHAR_CLASS + "]+");

    private static final Pattern LAST_BANGLA_RUN =
            Pattern.compile(
                    "[" + BANGLA_CHAR_CLASS + "]+(?:\\s+["
                            + BANGLA_CHAR_CLASS + "]+)*"
            );

    private KeyboardView keyboardView;
    private Keyboard qwertyKeyboard;
    private TextView suggestionText;

    // Number / Emoji panel-এর জন্য
    private LinearLayout rootLayout;
    private ScrollView panelScroll;
    private LinearLayout panelContent;

    // Voice ও Backspace long press-এর জন্য
    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private Runnable backspaceLongPressRunnable;
    private boolean backspaceLongPressed = false;

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            DictionaryHelper dh =
                    DictionaryHelper.getInstance(this);

            BanglaToBanglishConverter.loadDictionaryFromAssets(
                    this,
                    "dictionary.json"
            );

            try {
                Log.d(
                        TAG,
                        "Dictionary loaded entries: "
                                + dh.getWordCount()
                );
            } catch (Exception e) {
                Log.w(
                        TAG,
                        "Dictionary count unavailable: "
                                + e.getMessage()
                );
            }

        } catch (Exception e) {
            Log.e(
                    TAG,
                    "Dictionary initialization failed",
                    e
            );
        }
    }

    @Override
    public View onCreateInputView() {

        View root = LayoutInflater.from(this)
                .inflate(R.layout.keyboard_view, null);

        if (root instanceof LinearLayout) {
            rootLayout = (LinearLayout) root;
        }

        suggestionText =
                root.findViewById(
                        R.id.preview_text_view
                );

        Button convertButton =
                root.findViewById(
                        R.id.btn_convert
                );

        Button micButton =
                root.findViewById(
                        R.id.btn_mic
                );

        keyboardView =
                root.findViewById(
                        R.id.keyboard_view
                );

        FrameLayout bannerContainer =
                root.findViewById(
                        R.id.startio_banner_container
                );

        // Start.io code অপরিবর্তিত
        StartIoBannerHelper.attach(
                this,
                bannerContainer
        );

        qwertyKeyboard =
                new Keyboard(
                        this,
                        R.xml.qwerty
                );

        keyboardView.setKeyboard(
                qwertyKeyboard
        );

        keyboardView.setOnKeyboardActionListener(
                this
        );

        keyboardView.setPreviewEnabled(false);

        if (convertButton != null) {
            convertButton.setOnClickListener(
                    v -> convertCurrentText()
            );
        }

        if (micButton != null) {
            micButton.setOnClickListener(
                    v -> startVoiceInput()
            );
        }

        if (suggestionText != null) {

            suggestionText.setOnClickListener(v -> {

                String text =
                        suggestionText.getText() != null
                                ? suggestionText
                                .getText()
                                .toString()
                                .trim()
                                : "";

                if (isUsableVoiceOrPreviewText(text)) {
                    processBanglaText(text, 0);
                }
            });
        }

        createPanelIfNeeded();

        return root;
    }

    /*
     * VoiceInputHelper.java এই দুটি method ব্যবহার করে।
     * এগুলো remove করবেন না।
     */

    public void handleVoiceResult(
            String recognizedBangla) {

        mainHandler.post(() -> {

            if (suggestionText != null &&
                    recognizedBangla != null) {

                suggestionText.setText(
                        recognizedBangla
                );
            }
        });
    }

    public void handleVoicePartialResult(
            String partialText) {

        mainHandler.post(() -> {

            if (suggestionText != null &&
                    partialText != null) {

                suggestionText.setText(
                        partialText
                );
            }
        });
    }

    private static boolean isUsableVoiceOrPreviewText(
            String text) {

        if (text == null ||
                text.trim().isEmpty()) {
            return false;
        }

        String t = text.trim();

        return !t.contains("Listening")
                && !t.contains("এখানে বাংলা")
                && !t.contains("Voice input");
    }

    private static String extractLastBanglaToken(
            String text) {

        if (text == null || text.isEmpty()) {
            return "";
        }

        Matcher matcher =
                LAST_BANGLA_TOKEN.matcher(text);

        String last = "";

        while (matcher.find()) {
            last = matcher.group();
        }

        return last;
    }

    private static String extractLastBanglaRun(
            String text) {

        if (text == null || text.isEmpty()) {
            return "";
        }

        Matcher matcher =
                LAST_BANGLA_RUN.matcher(text);

        String last = "";

        while (matcher.find()) {
            last = matcher.group();
        }

        return last != null
                ? last.trim()
                : "";
    }

    private static int findLastBanglaRunLength(
            String text,
            String run) {

        if (text == null ||
                run == null ||
                run.isEmpty()) {
            return 0;
        }

        int end = text.length();

        while (end > 0 &&
                Character.isWhitespace(
                        text.charAt(end - 1))) {
            end--;
        }

        int start = end;

        while (start > 0) {

            char c =
                    text.charAt(start - 1);

            if (isBanglaChar(c)) {

                start--;

            } else if (
                    Character.isWhitespace(c)) {

                int j = start - 1;

                while (j >= 0 &&
                        Character.isWhitespace(
                                text.charAt(j))) {
                    j--;
                }

                if (j >= 0 &&
                        isBanglaChar(
                                text.charAt(j))) {

                    start = j + 1;

                    while (start > 0 &&
                            isBanglaChar(
                                    text.charAt(
                                            start - 1))) {
                        start--;
                    }

                } else {
                    break;
                }

            } else {
                break;
            }
        }

        return Math.max(
                0,
                end - start
        );
    }

    private static boolean isBanglaChar(
            char c) {

        return c >= '\u0980'
                && c <= '\u09FF';
    }

    private void convertCurrentText() {

        InputConnection ic =
                getCurrentInputConnection();

        if (ic == null) {
            showToast(
                    "Input connection পাওয়া যায়নি"
            );
            return;
        }

        CharSequence before =
                ic.getTextBeforeCursor(
                        1000,
                        0
                );

        if (before != null &&
                before.length() > 0) {

            String fullBefore =
                    before.toString();

            String lastRun =
                    extractLastBanglaRun(
                            fullBefore
                    );

            if (!lastRun.isEmpty()) {

                int runLength =
                        findLastBanglaRunLength(
                                fullBefore,
                                lastRun
                        );

                if (runLength > 0) {

                    processBanglaText(
                            lastRun,
                            runLength
                    );

                    return;
                }
            }

            String lastToken =
                    extractLastBanglaToken(
                            fullBefore
                    );

            if (!lastToken.isEmpty()) {

                processBanglaText(
                        lastToken,
                        lastToken.length()
                );

                return;
            }

            showToast(
                    "কার্সরের আগে কোনো বাংলা লেখা পাওয়া যায়নি"
            );

            return;
        }

        if (suggestionText != null) {

            String voiceText =
                    suggestionText.getText() != null
                            ? suggestionText
                            .getText()
                            .toString()
                            .trim()
                            : "";

            if (isUsableVoiceOrPreviewText(
                    voiceText)) {

                processBanglaText(
                        voiceText,
                        0
                );

                return;
            }
        }

        showToast(
                "কোনো বাংলা text পাওয়া যায়নি"
        );
    }

    private void processBanglaText(
            String banglaText,
            int banglaLength) {

        String cleanText =
                banglaText != null
                        ? banglaText.trim()
                        : "";

        if (cleanText.isEmpty()) {
            return;
        }

        String banglishResult = null;

        try {

            banglishResult =
                    DictionaryHelper
                            .getInstance(this)
                            .lookup(cleanText);

        } catch (Exception e) {

            Log.w(
                    TAG,
                    "Dictionary lookup failed; using converter",
                    e
            );
        }

        if (banglishResult == null ||
                banglishResult.trim().isEmpty()) {

            try {

                banglishResult =
                        BanglaToBanglishConverter
                                .convert(cleanText);

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Converter failed for: "
                                + cleanText,
                        e
                );

                banglishResult =
                        cleanText;
            }
        }

        if (banglishResult == null ||
                banglishResult.trim().isEmpty()) {

            banglishResult =
                    cleanText;
        }

        commitBanglish(
                banglishResult,
                banglaLength
        );
    }

    private void commitBanglish(
            String banglishText,
            int banglaLength) {

        if (banglishText == null) {
            return;
        }

        InputConnection ic =
                getCurrentInputConnection();

        if (ic == null) {
            return;
        }

        String result =
                banglishText.trim();

        if (result.isEmpty()) {
            return;
        }

        if (banglaLength > 0) {

            try {

                ic.deleteSurroundingText(
                        banglaLength,
                        0
                );

            } catch (Exception e) {

                Log.w(
                        TAG,
                        "deleteSurroundingText failed",
                        e
                );

                return;
            }
        }

        try {

            ic.commitText(
                    result + " ",
                    1
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "commitText failed",
                    e
            );

            return;
        }

        if (suggestionText != null) {

            suggestionText.setText(
                    "এখানে বাংলা দেখা যাবে..."
            );
        }
    }

    private void showToast(
            String message) {

        try {

            Toast.makeText(
                    this,
                    message,
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {

            Log.w(
                    TAG,
                    "Toast failed",
                    e
            );
        }
    }

    @Override
    public void onKey(
            int primaryCode,
            int[] keyCodes) {

        InputConnection ic =
                getCurrentInputConnection();

        if (ic == null) {
            return;
        }

        try {

            switch (primaryCode) {

                /*
                 * Number/Symbol support:
                 * -301 = নতুন qwerty.xml
                 * -102 = পুরোনো qwerty.xml
                 * -2   = Android standard number mode
                 */
                case KEYCODE_NUMBER_SYMBOL:
                case KEYCODE_NUMBER_SYMBOL_LEGACY:
                case Keyboard.KEYCODE_MODE_CHANGE:

                    showNumberSymbolPanel();

                    break;

                /*
                 * Emoji support:
                 * -302 = নতুন qwerty.xml
                 * -103 = পুরোনো qwerty.xml
                 * -10  = অন্য keyboard layout compatibility
                 */
                case KEYCODE_EMOJI:
                case KEYCODE_EMOJI_LEGACY:
                case -10:

                    showEmojiPanel();

                    break;

                // আগের Voice
                case KEYCODE_MIC:

                    startVoiceInput();

                    break;

                // আগের Globe
                case KEYCODE_GLOBE:

                    hidePanel();
                    showKeyboardPicker();

                    break;

                /*
                 * Backspace:
                 * Tap = এক text unit delete
                 * Long press = সব text clear
                 */
                case KEYCODE_DELETE:

                    if (!backspaceLongPressed) {
                        deleteOneTextUnit(ic);
                    }

                    break;

                // আগের Copy
                case KEYCODE_COPY:

                    hidePanel();
                    copySelectedText(ic);

                    break;

                // আগের Paste
                case KEYCODE_PASTE:

                    hidePanel();
                    pasteClipboard(ic);

                    break;

                // আগের Shift
                case KEYCODE_SHIFT:

                    hidePanel();

                    if (qwertyKeyboard != null) {

                        qwertyKeyboard.setShifted(
                                !qwertyKeyboard.isShifted()
                        );

                        if (keyboardView != null) {

                            keyboardView
                                    .invalidateAllKeys();
                        }
                    }

                    break;

                // আগের Enter
                case 10:

                    hidePanel();

                    ic.commitText(
                            "\n",
                            1
                    );

                    break;

                default:

                    hidePanel();

                    if (primaryCode >= 0 &&
                            primaryCode <=
                                    Character.MAX_VALUE) {

                        char character =
                                (char) primaryCode;

                        if (qwertyKeyboard != null &&
                                qwertyKeyboard.isShifted() &&
                                Character.isLetter(
                                        character)) {

                            character =
                                    Character.toUpperCase(
                                            character
                                    );

                            qwertyKeyboard
                                    .setShifted(false);

                            if (keyboardView != null) {
                                keyboardView
                                        .invalidateAllKeys();
                            }
                        }

                        ic.commitText(
                                String.valueOf(
                                        character
                                ),
                                1
                        );
                    }

                    break;
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Keyboard key handling failed: "
                            + primaryCode,
                    e
            );
        }
    }

    /*
     * Backspace button press শুরু হলে 650ms অপেক্ষা করবে।
     * 650ms ধরে রাখলে পুরো text clear করবে।
     */

    @Override
    public void onPress(
            int primaryCode) {

        if (primaryCode != KEYCODE_DELETE) {
            return;
        }

        backspaceLongPressed = false;

        if (backspaceLongPressRunnable != null) {
            mainHandler.removeCallbacks(
                    backspaceLongPressRunnable
            );
        }

        backspaceLongPressRunnable =
                new Runnable() {
                    @Override
                    public void run() {

                        backspaceLongPressed = true;
                        clearAllCurrentText();
                    }
                };

        mainHandler.postDelayed(
                backspaceLongPressRunnable,
                650
        );
    }

    /*
     * Button ছেড়ে দিলে pending long press বন্ধ হবে।
     * Tap delete onKey() থেকে হয়।
     */

    @Override
    public void onRelease(
            int primaryCode) {

        if (primaryCode == KEYCODE_DELETE &&
                backspaceLongPressRunnable != null) {

            mainHandler.removeCallbacks(
                    backspaceLongPressRunnable
            );
        }
    }

    /*
     * একবার backspace চাপলে একটি Unicode typing unit delete:
     * - বাংলা অক্ষর + কার
     * - যুক্তবর্ণ
     * - হসন্ত
     * - emoji surrogate pair
     */

    private void deleteOneTextUnit(
            InputConnection ic) {

        try {

            CharSequence selected =
                    ic.getSelectedText(0);

            if (selected != null &&
                    selected.length() > 0) {

                ic.commitText("", 1);
                return;
            }

            CharSequence beforeCursor =
                    ic.getTextBeforeCursor(
                            32,
                            0
                    );

            int deleteLength =
                    getDeleteLength(beforeCursor);

            ic.deleteSurroundingText(
                    deleteLength,
                    0
            );

        } catch (Exception e) {

            try {
                ic.deleteSurroundingText(1, 0);
            } catch (Exception ignored) {
            }
        }
    }

    private int getDeleteLength(
            CharSequence beforeCursor) {

        if (beforeCursor == null ||
                beforeCursor.length() == 0) {
            return 1;
        }

        int length =
                beforeCursor.length();

        char last =
                beforeCursor.charAt(
                        length - 1
                );

        // Emoji / surrogate pair
        if (Character.isLowSurrogate(last) &&
                length >= 2) {

            char previous =
                    beforeCursor.charAt(
                            length - 2
                    );

            if (Character.isHighSurrogate(previous)) {
                return 2;
            }
        }

        // সাধারণ English/symbol character
        if (!isBanglaChar(last)) {
            return 1;
        }

        int index = length;

        // শেষের কার, হসন্ত, অনুস্বর ইত্যাদি বাদ দিচ্ছে
        while (index > 0 &&
                isBanglaMark(
                        beforeCursor.charAt(
                                index - 1))) {

            index--;
        }

        // কারের আগে থাকা মূল বাংলা অক্ষর
        if (index > 0 &&
                isBanglaBaseCharacter(
                        beforeCursor.charAt(
                                index - 1))) {

            index--;

            /*
             * যুক্তবর্ণ হলে:
             * ক + ্ + ষ
             * একবার backspace দিলে পুরো যুক্ত unit delete হবে।
             */
            while (index >= 2 &&
                    beforeCursor.charAt(
                            index - 1) == '\u09CD' &&
                    isBanglaBaseCharacter(
                            beforeCursor.charAt(
                                    index - 2))) {

                index -= 2;
            }

            return Math.max(
                    1,
                    length - index
            );
        }

        return 1;
    }

    private boolean isBanglaMark(
            char c) {

        return c == '\u0981'  // ঁ
                || c == '\u0982' // ং
                || c == '\u0983' // ঃ
                || c == '\u09BC' // ়
                || (c >= '\u09BE' && c <= '\u09CC')
                || c == '\u09CD' // ্
                || c == '\u09D7'; // ৗ
    }

    private boolean isBanglaBaseCharacter(
            char c) {

        return (c >= '\u0985' && c <= '\u09B9')
                || c == '\u09CE'
                || c == '\u09DC'
                || c == '\u09DD'
                || c == '\u09DF';
    }

    /*
     * Backspace long press:
     * পুরো current editor text clear করার চেষ্টা করবে।
     */

    private void clearAllCurrentText() {

        InputConnection ic =
                getCurrentInputConnection();

        if (ic == null) {
            return;
        }

        try {

            ExtractedTextRequest request =
                    new ExtractedTextRequest();

            ExtractedText extractedText =
                    ic.getExtractedText(
                            request,
                            0
                    );

            if (extractedText != null &&
                    extractedText.text != null) {

                int textLength =
                        extractedText.text.length();

                ic.beginBatchEdit();

                ic.setSelection(
                        0,
                        textLength
                );

                ic.commitText(
                        "",
                        1
                );

                ic.endBatchEdit();

            } else {

                ic.deleteSurroundingText(
                        10000,
                        10000
                );
            }

        } catch (Exception e) {

            try {
                ic.deleteSurroundingText(
                        10000,
                        10000
                );
            } catch (Exception ignored) {
            }
        }
    }

    private void showKeyboardPicker() {

        try {

            InputMethodManager imm =
                    (InputMethodManager)
                            getSystemService(
                                    Context.INPUT_METHOD_SERVICE
                            );

            if (imm != null) {

                imm.showInputMethodPicker();
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not show keyboard picker",
                    e
            );
        }
    }

    private void copySelectedText(
            InputConnection ic) {

        try {

            CharSequence selected =
                    ic.getSelectedText(0);

            if (selected == null ||
                    selected.length() == 0) {

                showToast(
                        "আগে text select করুন"
                );

                return;
            }

            ClipboardManager clipboard =
                    (ClipboardManager)
                            getSystemService(
                                    CLIPBOARD_SERVICE
                            );

            if (clipboard == null) {
                return;
            }

            ClipData clip =
                    ClipData.newPlainText(
                            "Banglish Keyboard",
                            selected
                    );

            clipboard.setPrimaryClip(clip);

            showToast("Copied");

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Copy failed",
                    e
            );
        }
    }

    private void pasteClipboard(
            InputConnection ic) {

        try {

            ClipboardManager clipboard =
                    (ClipboardManager)
                            getSystemService(
                                    CLIPBOARD_SERVICE
                            );

            if (clipboard == null ||
                    !clipboard.hasPrimaryClip()) {

                showToast(
                        "Clipboard খালি"
                );

                return;
            }

            ClipData clip =
                    clipboard.getPrimaryClip();

            if (clip == null ||
                    clip.getItemCount() == 0) {

                showToast(
                        "Clipboard খালি"
                );

                return;
            }

            ClipData.Item item =
                    clip.getItemAt(0);

            CharSequence pasteText =
                    item != null
                            ? item.coerceToText(this)
                            : null;

            if (pasteText != null &&
                    pasteText.length() > 0) {

                ic.commitText(
                        pasteText,
                        1
                );
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Paste failed",
                    e
            );
        }
    }

    private void startVoiceInput() {

        if (suggestionText != null) {

            suggestionText.setText(
                    "🎤 Listening... বলুন..."
            );
        }

        showToast(
                "Voice input shuru hocche..."
        );

        try {

            /*
             * আপনার original project-এর call।
             * VoiceInputHelper.java পরিবর্তন করা হয়নি।
             */
            VoiceInputHelper.startListening(
                    this,
                    this
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Voice input failed",
                    e
            );

            if (suggestionText != null) {

                suggestionText.setText(
                        "Voice input চালু করা যায়নি"
                );
            }
        }
    }

    /*
     * Number এবং Emoji panel methods
     */

    private void createPanelIfNeeded() {

        try {

            if (rootLayout == null ||
                    panelScroll != null) {
                return;
            }

            panelScroll =
                    new ScrollView(this);

            panelScroll.setBackgroundColor(
                    0xFFE2E5E9
            );

            panelScroll.setVisibility(
                    View.GONE
            );

            panelContent =
                    new LinearLayout(this);

            panelContent.setOrientation(
                    LinearLayout.VERTICAL
            );

            panelContent.setPadding(
                    dp(5),
                    dp(5),
                    dp(5),
                    dp(5)
            );

            panelScroll.addView(
                    panelContent,
                    new ScrollView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );

            rootLayout.addView(
                    panelScroll,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(220)
                    )
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Panel creation failed",
                    e
            );
        }
    }

    private void showNumberSymbolPanel() {

        String[] symbols = new String[]{
                "1", "2", "3", "4", "5",
                "6", "7", "8", "9", "0",

                "@", "#", "$", "%", "&",
                "-", "+", "(", ")", "/",

                "=", "*", "\"", "'", ":",
                ";", "!", "?", ".", ",",

                "ABC", "⌫"
        };

        showPanel(
                symbols,
                10
        );
    }

    private void showEmojiPanel() {

        String[] emojis = new String[]{
                "😀", "😃", "😄", "😁", "😆", "😅", "😂",
                "🤣", "😊", "🙂", "😉", "😍", "😘", "😎",
                "🤔", "🙄", "😐", "😢", "😭", "😡", "😴",
                "👍", "👎", "🙏", "👏", "❤️", "🔥", "🎉",
                "🌹", "💯", "✅", "❌", "⭐", "✨", "💔",
                "ABC", "⌫"
        };

        showPanel(
                emojis,
                7
        );
    }

    private void showPanel(
            String[] items,
            int columns) {

        try {

            createPanelIfNeeded();

            if (panelScroll == null ||
                    panelContent == null ||
                    keyboardView == null) {
                return;
            }

            panelContent.removeAllViews();

            keyboardView.setVisibility(
                    View.GONE
            );

            panelScroll.setVisibility(
                    View.VISIBLE
            );

            panelScroll.requestLayout();
            rootLayout.requestLayout();

            int index = 0;

            while (index < items.length) {

                LinearLayout row =
                        new LinearLayout(this);

                row.setOrientation(
                        LinearLayout.HORIZONTAL
                );

                panelContent.addView(
                        row,
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                dp(44)
                        )
                );

                for (int i = 0; i < columns; i++) {

                    if (index < items.length) {

                        final String value =
                                items[index];

                        TextView key =
                                new TextView(this);

                        key.setText(value);

                        key.setGravity(
                                Gravity.CENTER
                        );

                        key.setTextColor(
                                0xFF202124
                        );

                        if ("ABC".equals(value) ||
                                "⌫".equals(value)) {

                            key.setTextSize(14);

                        } else {

                            key.setTextSize(20);
                        }

                        key.setPadding(
                                dp(2),
                                dp(2),
                                dp(2),
                                dp(2)
                        );

                        key.setBackgroundResource(
                                R.drawable.keyboard_key_background
                        );

                        key.setOnClickListener(
                                v -> handlePanelKey(value)
                        );

                        LinearLayout.LayoutParams params =
                                new LinearLayout.LayoutParams(
                                        0,
                                        dp(39),
                                        1f
                                );

                        params.setMargins(
                                dp(2),
                                dp(2),
                                dp(2),
                                dp(2)
                        );

                        row.addView(
                                key,
                                params
                        );

                        index++;

                    } else {

                        View empty =
                                new View(this);

                        row.addView(
                                empty,
                                new LinearLayout.LayoutParams(
                                        0,
                                        dp(39),
                                        1f
                                )
                        );
                    }
                }
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Panel show failed",
                    e
            );
        }
    }

    private void handlePanelKey(
            String value) {

        InputConnection ic =
                getCurrentInputConnection();

        if (ic == null ||
                value == null) {
            return;
        }

        try {

            if ("ABC".equals(value)) {

                hidePanel();

            } else if ("⌫".equals(value)) {

                deleteOneTextUnit(ic);

            } else {

                ic.commitText(
                        value,
                        1
                );
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Panel key failed: " + value,
                    e
            );
        }
    }

    private void hidePanel() {

        try {

            if (panelScroll != null) {

                panelScroll.setVisibility(
                        View.GONE
                );
            }

            if (keyboardView != null) {

                keyboardView.setVisibility(
                        View.VISIBLE
                );
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Hide panel failed",
                    e
            );
        }
    }

    private int dp(
            int value) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return (int) (
                value * density + 0.5f
        );
    }

    @Override
    public void onText(
            CharSequence text) {

        if (text == null ||
                text.length() == 0) {
            return;
        }

        InputConnection ic =
                getCurrentInputConnection();

        if (ic == null) {
            return;
        }

        try {

            ic.commitText(
                    text,
                    1
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "onText commit failed",
                    e
            );
        }
    }

    @Override
    public void swipeLeft() {
    }

    @Override
    public void swipeRight() {
    }

    @Override
    public void swipeDown() {
    }

    @Override
    public void swipeUp() {
    }

    @Override
    public void onDestroy() {

        if (backspaceLongPressRunnable != null) {
            mainHandler.removeCallbacks(
                    backspaceLongPressRunnable
            );
        }

        super.onDestroy();
    }
}
