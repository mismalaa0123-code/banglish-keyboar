package com.arafatakaid.banglishkeyboard;

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BanglishIME extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private static final String TAG = "BanglishIME";

    private static final int KEYCODE_MIC = -100;
    private static final int KEYCODE_DELETE = -5;
    private static final int KEYCODE_SHIFT = -1;
    private static final int KEYCODE_COPY = -201;
    private static final int KEYCODE_PASTE = -202;

    private KeyboardView keyboardView;
    private Keyboard qwertyKeyboard;
    private TextView suggestionText;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        // DictionaryHelper ক্লাসের মাধ্যমে ডিকশনারি মেমোরিতে লোড করা হচ্ছে
        DictionaryHelper dh = DictionaryHelper.getInstance(this);
        // Step 3: load the same dictionary into the converter so full phrases
        // can be matched before individual-word fallback rules run.
        BanglaToBanglishConverter.loadDictionaryFromAssets(this, "dictionary.json");
        // Debug: লোড হওয়া এন্ট্রির সংখ্যা লোগ করা
        try {
            int count = dh.getWordCount();
            Log.d(TAG, "Dictionary loaded entries: " + count);
        } catch (Exception e) {
            Log.d(TAG, "Dictionary check error: " + e.getMessage());
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

        // প্রিভিউ বারে জমা থাকা বাংলা টেক্সটে ট্যাপ করলে তা ইনপুট করার লজিক
        if (suggestionText != null) {
            suggestionText.setOnClickListener(v -> {
                String text = suggestionText.getText().toString();
                if (!text.isEmpty() && !text.contains("Listening") && !text.contains("এখানে বাংলা")) {
                    processBanglaText(text, 0);
                }
            });
        }

        return root;
    }

    /**
     * Extract the last contiguous Bangla token from the given text.
     * Returns empty string if none found.
     */
    private static String extractLastBanglaToken(String text) {
        if (text == null || text.isEmpty()) return "";
        Pattern p = Pattern.compile("[\\u0980-\\u09FF]+");
        Matcher m = p.matcher(text);
        String last = "";
        while (m.find()) {
            last = m.group();
        }
        return last != null ? last : "";
    }

    private void convertCurrentText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        // প্রথমে কার্সরের আগের টেক্সট থেকে চেক করবে (সর্বোচ্চ 200 অক্ষর)
        CharSequence before = ic.getTextBeforeCursor(200, 0);
        if (before != null && before.length() > 0) {
            String fullBefore = before.toString();
            String lastToken = extractLastBanglaToken(fullBefore);
            if (lastToken != null && !lastToken.isEmpty()) {
                // শুধু শেষ বাংলা শব্দটাকেই প্রক্রিয়া কর এবং তার দৈর্ঘ্য সরবরাহ কর
                processBanglaText(lastToken, lastToken.length());
                return;
            } else {
                Toast.makeText(this, "কোনো বাংলা শব্দ পাওয়া যায়নি কার্সরের আগে", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // যদি কার্সরে কিছু না থাকে, কিন্তু প্রিভিউ বারে ভয়েস টেক্সট থেকে থাকে
        if (suggestionText != null) {
            String voiceText = suggestionText.getText().toString().trim();
            if (!voiceText.isEmpty() && !voiceText.contains("Listening") && !voiceText.contains("এখানে বাংলা")) {
                processBanglaText(voiceText, 0);
                return;
            }
        }

        Toast.makeText(this, "Kono Bangla text pawa jayni", Toast.LENGTH_SHORT).show();
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

    private void processBanglaText(String banglaText, int banglaLength) {
        String cleanText = banglaText != null ? banglaText.trim() : "";
        if (cleanText.isEmpty()) return;

        // ১. প্রথমে ডিকশনারি (dictionary.json) থেকে শব্দটি চেক করা হচ্ছে
        String banglishResult = DictionaryHelper.getInstance(this).lookup(cleanText);
        Log.d(TAG, "Lookup for '" + cleanText + "' -> " + (banglishResult != null ? banglishResult : "null"));

        // ২. ডিকশনারিতে না পাওয়া গেলে কনভার্টার দিয়ে রুলস অনুযায়ী পরিবর্তন করা হচ্ছে
        if (banglishResult == null || banglishResult.trim().isEmpty()) {
            banglishResult = BanglaToBanglishConverter.convert(cleanText);
            Log.d(TAG, "Converted via rules: '" + cleanText + "' -> '" + banglishResult + "'");
        }

        commitBanglish(banglishResult, banglaLength);
    }

    private void commitBanglish(String banglishText, int banglaLength) {
        if (banglishText == null) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        if (banglaLength > 0) {
            try {
                ic.deleteSurroundingText(banglaLength, 0);
            } catch (Exception e) {
                Log.w(TAG, "deleteSurroundingText failed: " + e.getMessage());
            }
        }
        ic.commitText(banglishText + " ", 1);
        if (suggestionText != null) {
            suggestionText.setText("এখানে বাংলা দেখা যাবে...");
        }
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (primaryCode) {
            case KEYCODE_MIC:
                startVoiceInput();
                break;
            case KEYCODE_DELETE:
                ic.deleteSurroundingText(1, 0);
                break;
            case KEYCODE_COPY:
                Toast.makeText(this, "Select text to copy", Toast.LENGTH_SHORT).show();
                break;
            case KEYCODE_PASTE:
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null && clipboard.hasPrimaryClip()) {
                    android.content.ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                    CharSequence pasteText = item.coerceToText(this);
                    if (pasteText != null) {
                        ic.commitText(pasteText, 1);
                    }
                }
                break;
            case KEYCODE_SHIFT:
                break;
            case 10:
                ic.commitText("\n", 1);
                break;
            default:
                char code = (char) primaryCode;
                ic.commitText(String.valueOf(code), 1);
        }
    }

    private void startVoiceInput() {
        if (suggestionText != null) {
            suggestionText.setText("🎤 Listening... বলুন...");
        }
        Toast.makeText(this, "Voice input shuru hocche...", Toast.LENGTH_SHORT).show();
        VoiceInputHelper.startListening(this, this);
    }

    @Override public void onPress(int primaryCode) {}
    @Override public void onRelease(int primaryCode) {}
    @Override public void onText(CharSequence text) {}
    @Override public void swipeLeft() {}
    @Override public void swipeRight() {}
    @Override public void swipeDown() {}
    @Override public void swipeUp() {}
}
