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
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BanglishIME extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    private static final String TAG = "BanglishIME";

    // Voice
    private static final int KEYCODE_MIC = -100;

    // Global language / keyboard switch
    private static final int KEYCODE_GLOBE = -101;

    // Keyboard controls
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

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

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

        return root;
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

                // 🎤 Voice
                case KEYCODE_MIC:

                    startVoiceInput();

                    break;

                // 🌐 Global language
                case KEYCODE_GLOBE:

                    showKeyboardPicker();

                    break;

                // ⌫ Delete
                case KEYCODE_DELETE:

                    ic.deleteSurroundingText(
                            1,
                            0
                    );

                    break;

                // Copy
                case KEYCODE_COPY:

                    copySelectedText(ic);

                    break;

                // Paste
                case KEYCODE_PASTE:

                    pasteClipboard(ic);

                    break;

                // Shift
                case KEYCODE_SHIFT:

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

                // Enter
                case 10:

                    ic.commitText(
                            "\n",
                            1
                    );

                    break;

                default:

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
    public void onPress(
            int primaryCode) {
    }

    @Override
    public void onRelease(
            int primaryCode) {
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
}
