package com.arafatakaid.banglishkeyboard;

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class BanglishIME extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private static final int KEYCODE_MIC = -100;
    private static final int KEYCODE_DELETE = -5;
    private static final int KEYCODE_SHIFT = -1;

    private KeyboardView keyboardView;
    private Keyboard qwertyKeyboard;
    private TextView suggestionText;

    private DictionaryHelper dictionaryHelper;
    private GeminiHelper geminiHelper;
    private String lastSuggestion = "";

    @Override
    public void onCreate() {
        super.onCreate();
        dictionaryHelper = DictionaryHelper.getInstance(this);
        geminiHelper = new GeminiHelper();
    }

    @Override
    public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout suggestionBar = new LinearLayout(this);
        suggestionBar.setOrientation(LinearLayout.HORIZONTAL);
        suggestionBar.setPadding(16, 16, 16, 16);
        suggestionBar.setBackgroundColor(0xFFEDEDED);

        suggestionText = new TextView(this);
        suggestionText.setText("Bangla likhun ba mic e bolun...");
        suggestionText.setTextSize(16);
        suggestionText.setTextColor(0xFF333333);
        suggestionText.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        suggestionText.setOnClickListener(v -> commitSuggestion());

        Button convertButton = new Button(this);
        convertButton.setText("Convert");
        convertButton.setOnClickListener(v -> convertCurrentText());

        suggestionBar.addView(suggestionText);
        suggestionBar.addView(convertButton);

        keyboardView = (KeyboardView) LayoutInflater.from(this)
                .inflate(R.layout.keyboard_view, null);
        qwertyKeyboard = new Keyboard(this, R.xml.qwerty);
        keyboardView.setKeyboard(qwertyKeyboard);
        keyboardView.setOnKeyboardActionListener(this);

        root.addView(suggestionBar);
        root.addView(keyboardView);
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
        processBanglaText(before.toString().trim());
    }

    public void handleVoiceResult(String recognizedBangla) {
        processBanglaText(recognizedBangla);
    }

    private void processBanglaText(String banglaText) {
        suggestionText.setText("Convert hocche...");

        String dictResult = dictionaryHelper.lookup(banglaText);
        if (dictResult != null) {
            showSuggestion(dictResult);
            return;
        }

        geminiHelper.convertToBanglish(banglaText, new GeminiHelper.ResultCallback() {
            @Override
            public void onResult(String banglishText) {
                showSuggestion(banglishText);
            }

            @Override
            public void onError(String message) {
                suggestionText.setText("Error: " + message);
            }
        });
    }

    private void showSuggestion(String banglishText) {
        lastSuggestion = banglishText;
        suggestionText.setText(banglishText + "  (tap korun bosate)");
    }

    private void commitSuggestion() {
        if (lastSuggestion.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        CharSequence before = ic.getTextBeforeCursor(200, 0);
        if (before != null && before.length() > 0) {
            ic.deleteSurroundingText(before.length(), 0);
        }
        ic.commitText(lastSuggestion + " ", 1);
        suggestionText.setText("Bangla likhun ba mic e bolun...");
        lastSuggestion = "";
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
