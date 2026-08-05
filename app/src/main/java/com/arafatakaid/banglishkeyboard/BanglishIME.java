package com.arafatakaid.banglishkeyboard;

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class BanglishIME extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private static final int KEYCODE_MIC = -100;
    private static final int KEYCODE_DELETE = -5;
    private static final int KEYCODE_SHIFT = -1;
    private static final int KEYCODE_COPY = -201;
    private static final int KEYCODE_PASTE = -202;

    private KeyboardView keyboardView;
    private Keyboard qwertyKeyboard;
    private TextView suggestionText;

    private DictionaryHelper dictionaryHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        dictionaryHelper = DictionaryHelper.getInstance(this);
    }

    @Override
    public View onCreateInputView() {
        View root = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null);

        suggestionText = root.findViewById(R.id.preview_text_view);
        Button convertButton = root.findViewById(R.id.btn_convert);
        Button micButton = root.findViewById(R.id.btn_mic); // ভয়েস বাটন বাইন্ড করা হলো
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

        return root;
    }

    private void convertCurrentText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        CharSequence before = ic.getTextBeforeCursor(200, 0);
        if (before == null || before.length() == 0) {
            Toast.makeText(this, "Kono Bangla text pawa jayni", Toast.LENGTH_SHORT).show();
            return;
        }
        String banglaText = before.toString().trim();
        int banglaLength = before.length();
        processBanglaText(banglaText, banglaLength);
    }

    public void handleVoiceResult(String recognizedBangla) {
        processBanglaText(recognizedBangla, -1);
    }

    private void processBanglaText(String banglaText, int banglaLength) {
        // ডিকশনারি সার্চ নিখুঁত করার জন্য অতিরিক্ত স্পেস ট্রিম করা হলো
        String cleanText = banglaText != null ? banglaText.trim() : "";
        
        String dictResult = dictionaryHelper.lookup(cleanText);
        String banglishResult;

        if (dictResult != null && !dictResult.isEmpty()) {
            banglishResult = dictResult;
        } else {
            banglishResult = BanglaToBanglishConverter.convert(cleanText);
        }

        commitBanglish(banglishResult, banglaLength);
    }

    private void commitBanglish(String banglishText, int banglaLength) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        if (banglaLength > 0) {
            ic.deleteSurroundingText(banglaLength, 0);
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
