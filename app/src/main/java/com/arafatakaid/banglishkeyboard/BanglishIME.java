package com.arafatakaid.banglishkeyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BanglishIME extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    private static final String TAG = "BanglishIME";

    private static final int KEYCODE_MIC = -100;
    private static final int KEYCODE_DELETE = -5;
    private static final int KEYCODE_SHIFT = -1;
    private static final int KEYCODE_COPY = -201;
    private static final int KEYCODE_PASTE = -202;

    /*
     * Bangla Unicode block.
     * This pattern deliberately accepts combining marks as part of a Bengali
     * token so কার/হসন্ত/ফলা ভেঙে না যায়।
     */
    private static final String BANGLA_CHAR_CLASS = "\\u0980-\\u09FF";
    private static final Pattern LAST_BANGLA_TOKEN =
            Pattern.compile("[" + BANGLA_CHAR_CLASS + "]+");

    /*
     * A Bengali run may contain spaces between words. It is used only when
     * the user explicitly presses Convert. This allows phrase dictionary
     * entries such as "আমি জানি না" to be considered together.
     */
    private static final Pattern LAST_BANGLA_RUN =
            Pattern.compile("[" + BANGLA_CHAR_CLASS + "]+(?:\\s+[" +
                    BANGLA_CHAR_CLASS + "]+)*");

    private KeyboardView keyboardView;
    private Keyboard qwertyKeyboard;
    private TextView suggestionText;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            /*
             * Load the shared dictionary once when the IME service starts.
             * BanglaToBanglishConverter keeps its own cached representation,
             * so this does not mean the JSON is parsed on every key press.
             */
            DictionaryHelper dh = DictionaryHelper.getInstance(this);
            BanglaToBanglishConverter.loadDictionaryFromAssets(this, "dictionary.json");

            try {
                Log.d(TAG, "Dictionary loaded entries: " + dh.getWordCount());
            } catch (Exception e) {
                Log.w(TAG, "Dictionary count unavailable: " + e.getMessage());
            }
        } catch (Exception e) {
            /*
             * A dictionary problem must not crash the keyboard service.
             * The converter's rule fallback can still be used.
             */
            Log.e(TAG, "Dictionary initialization failed", e);
        }
    }

    @Override
    public View onCreateInputView() {
        View root = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null);

        suggestionText = root.findViewById(R.id.preview_text_view);
        Button convertButton = root.findViewById(R.id.btn_convert);
        Button micButton = root.findViewById(R.id.btn_mic);
        keyboardView = root.findViewById(R.id.keyboard_view);

        qwertyKeyboard = new Keyboard(this, R.xml.qwerty);
        keyboardView.setKeyboard(qwertyKeyboard);
        keyboardView.setOnKeyboardActionListener(this);

        if (convertButton != null) {
            convertButton.setOnClickListener(v -> convertCurrentText());
        }

        if (micButton != null) {
            micButton.setOnClickListener(v -> startVoiceInput());
        }

        if (suggestionText != null) {
            suggestionText.setOnClickListener(v -> {
                String text = suggestionText.getText() != null
                        ? suggestionText.getText().toString().trim()
                        : "";

                if (isUsableVoiceOrPreviewText(text)) {
                    processBanglaText(text, 0);
                }
            });
        }

        return root;
    }

    private static boolean isUsableVoiceOrPreviewText(String text) {
        if (text == null || text.trim().isEmpty()) return false;

        String t = text.trim();
        return !t.contains("Listening")
                && !t.contains("এখানে বাংলা")
                && !t.contains("Voice input");
    }

    /**
     * Returns the last Bengali-only token.
     * Kept for safe single-word conversion when the cursor is at the end.
     */
    private static String extractLastBanglaToken(String text) {
        if (text == null || text.isEmpty()) return "";

        Matcher m = LAST_BANGLA_TOKEN.matcher(text);
        String last = "";

        while (m.find()) {
            last = m.group();
        }

        return last;
    }

    /**
     * Returns the last contiguous Bengali phrase before the cursor.
     *
     * Example:
     * "আমি আজ ভালো আছি" -> "আমি আজ ভালো আছি"
     *
     * This is intentionally limited to Bengali words separated by whitespace.
     * It will not consume an English word, URL, email, or punctuation block.
     */
    private static String extractLastBanglaRun(String text) {
        if (text == null || text.isEmpty()) return "";

        Matcher m = LAST_BANGLA_RUN.matcher(text);
        String last = "";

        while (m.find()) {
            last = m.group();
        }

        return last != null ? last.trim() : "";
    }

    /**
     * Returns the UTF-16 length of the exact run that was found immediately
     * before the cursor. This is important because deleteSurroundingText()
     * works in Java UTF-16 code units, not Unicode code points.
     */
    private static int findLastBanglaRunLength(String text, String run) {
        if (text == null || run == null || run.isEmpty()) return 0;

        int end = text.length();

        // Remove only trailing whitespace from the inspected text.
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }

        int start = end;

        while (start > 0) {
            char c = text.charAt(start - 1);

            if (isBanglaChar(c)) {
                start--;
            } else if (Character.isWhitespace(c)) {
                // Continue across spaces only if Bengali text exists before it.
                int j = start - 1;
                while (j >= 0 && Character.isWhitespace(text.charAt(j))) {
                    j--;
                }

                if (j >= 0 && isBanglaChar(text.charAt(j))) {
                    start = j + 1;
                    // Continue scanning the Bengali word before the space.
                    while (start > 0 && isBanglaChar(text.charAt(start - 1))) {
                        start--;
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        return Math.max(0, end - start);
    }

    private static boolean isBanglaChar(char c) {
        return c >= '\u0980' && c <= '\u09FF';
    }

    /**
     * Convert the Bengali phrase immediately before the cursor.
     *
     * Phrase matching is useful here because the converter supports
     * multi-word dictionary entries.
     */
    private void convertCurrentText() {
        InputConnection ic = getCurrentInputConnection();

        if (ic == null) {
            showToast("Input connection পাওয়া যায়নি");
            return;
        }

        CharSequence before = ic.getTextBeforeCursor(1000, 0);

        if (before != null && before.length() > 0) {
            String fullBefore = before.toString();

            // First try a Bengali phrase, not just the final word.
            String lastRun = extractLastBanglaRun(fullBefore);

            if (!lastRun.isEmpty()) {
                int runLength = findLastBanglaRunLength(fullBefore, lastRun);

                if (runLength > 0) {
                    processBanglaText(lastRun, runLength);
                    return;
                }
            }

            // Fallback: final Bengali token.
            String lastToken = extractLastBanglaToken(fullBefore);

            if (!lastToken.isEmpty()) {
                processBanglaText(lastToken, lastToken.length());
                return;
            }

            showToast("কার্সরের আগে কোনো বাংলা লেখা পাওয়া যায়নি");
            return;
        }

        // Voice preview fallback.
        if (suggestionText != null) {
            String voiceText = suggestionText.getText() != null
                    ? suggestionText.getText().toString().trim()
                    : "";

            if (isUsableVoiceOrPreviewText(voiceText)) {
                processBanglaText(voiceText, 0);
                return;
            }
        }

        showToast("কোনো বাংলা text পাওয়া যায়নি");
    }

    public void handleVoiceResult(String recognizedBangla) {
        mainHandler.post(() -> {
            if (suggestionText != null && recognizedBangla != null) {
                suggestionText.setText(recognizedBangla);
            }
        });
    }

    public void handleVoicePartialResult(String partialText) {
        mainHandler.post(() -> {
            if (suggestionText != null && partialText != null) {
                suggestionText.setText(partialText);
            }
        });
    }

    /**
     * Dictionary-first processing.
     *
     * The IME does not attempt fuzzy correction here. The converter itself
     * owns phrase/rule/exception fallback logic.
     */
    private void processBanglaText(String banglaText, int banglaLength) {
        String cleanText = banglaText != null ? banglaText.trim() : "";

        if (cleanText.isEmpty()) return;

        String banglishResult = null;

        try {
            banglishResult = DictionaryHelper.getInstance(this).lookup(cleanText);
        } catch (Exception e) {
            Log.w(TAG, "Dictionary lookup failed; using converter", e);
        }

        Log.d(TAG, "Lookup for '" + cleanText + "' -> "
                + (banglishResult != null ? banglishResult : "null"));

        if (banglishResult == null || banglishResult.trim().isEmpty()) {
            try {
                banglishResult = BanglaToBanglishConverter.convert(cleanText);
            } catch (Exception e) {
                Log.e(TAG, "Converter failed for: " + cleanText, e);

                // Never crash the IME. Keep the original text as a last resort.
                banglishResult = cleanText;
            }

            Log.d(TAG, "Converted via rules: '" + cleanText
                    + "' -> '" + banglishResult + "'");
        }

        if (banglishResult == null || banglishResult.trim().isEmpty()) {
            banglishResult = cleanText;
        }

        commitBanglish(banglishResult, banglaLength);
    }

    /**
     * Replaces exactly the requested UTF-16 length immediately before cursor.
     * A single trailing space is added for converted text unless the result
     * already ends with whitespace.
     */
    private void commitBanglish(String banglishText, int banglaLength) {
        if (banglishText == null) return;

        InputConnection ic = getCurrentInputConnection();

        if (ic == null) return;

        String result = banglishText.trim();

        if (result.isEmpty()) return;

        if (banglaLength > 0) {
            try {
                boolean deleted = ic.deleteSurroundingText(banglaLength, 0);

                if (!deleted) {
                    Log.w(TAG, "deleteSurroundingText returned false");
                }
            } catch (Exception e) {
                Log.w(TAG, "deleteSurroundingText failed", e);
                return;
            }
        }

        try {
            ic.commitText(result + " ", 1);
        } catch (Exception e) {
            Log.e(TAG, "commitText failed", e);
            return;
        }

        if (suggestionText != null) {
            suggestionText.setText("এখানে বাংলা দেখা যাবে...");
        }
    }

    private void showToast(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.w(TAG, "Toast failed: " + e.getMessage());
        }
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        InputConnection ic = getCurrentInputConnection();

        if (ic == null) return;

        try {
            switch (primaryCode) {
                case KEYCODE_MIC:
                    startVoiceInput();
                    break;

                case KEYCODE_DELETE:
                    ic.deleteSurroundingText(1, 0);
                    break;

                case KEYCODE_COPY:
                    /*
                     * Copy requires an actual selection in the target app.
                     * We do not silently copy unrelated clipboard data.
                     */
                    showToast("Copy করতে text select করুন");
                    break;

                case KEYCODE_PASTE:
                    pasteClipboard(ic);
                    break;

                case KEYCODE_SHIFT:
                    // Shift state is handled by the keyboard XML/view.
                    break;

                case 10:
                    ic.commitText("\n", 1);
                    break;

                default:
                    /*
                     * R.xml.qwerty provides normal printable key codes.
                     * Ignore invalid negative codes rather than inserting
                     * unexpected Unicode characters.
                     */
                    if (primaryCode >= 0 && primaryCode <= Character.MAX_VALUE) {
                        char code = (char) primaryCode;
                        ic.commitText(String.valueOf(code), 1);
                    } else {
                        Log.d(TAG, "Ignoring unsupported key code: " + primaryCode);
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Keyboard key handling failed: " + primaryCode, e);
        }
    }

    private void pasteClipboard(InputConnection ic) {
        try {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                showToast("Clipboard খালি");
                return;
            }

            ClipData clip = clipboard.getPrimaryClip();

            if (clip == null || clip.getItemCount() == 0) {
                showToast("Clipboard খালি");
                return;
            }

            ClipData.Item item = clip.getItemAt(0);
            CharSequence pasteText = item != null ? item.coerceToText(this) : null;

            if (pasteText != null && pasteText.length() > 0) {
                // Paste is intentionally lossless: do not silently transliterate
                // clipboard content. User can press Convert afterward.
                ic.commitText(pasteText, 1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Paste failed", e);
        }
    }

    private void startVoiceInput() {
        if (suggestionText != null) {
            suggestionText.setText("🎤 Listening... বলুন...");
        }

        showToast("Voice input shuru hocche...");

        try {
            VoiceInputHelper.startListening(this, this);
        } catch (Exception e) {
            Log.e(TAG, "Voice input failed", e);

            if (suggestionText != null) {
                suggestionText.setText("Voice input চালু করা যায়নি");
            }
        }
    }

    @Override
    public void onPress(int primaryCode) {
        // No-op.
    }

    @Override
    public void onRelease(int primaryCode) {
        // No-op.
    }

    @Override
    public void onText(CharSequence text) {
        if (text == null || text.length() == 0) return;

        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        try {
            ic.commitText(text, 1);
        } catch (Exception e) {
            Log.e(TAG, "onText commit failed", e);
        }
    }

    @Override
    public void swipeLeft() {
        // No-op.
    }

    @Override
    public void swipeRight() {
        // No-op.
    }

    @Override
    public void swipeDown() {
        // No-op.
    }

    @Override
    public void swipeUp() {
        // No-op.
    }
}
