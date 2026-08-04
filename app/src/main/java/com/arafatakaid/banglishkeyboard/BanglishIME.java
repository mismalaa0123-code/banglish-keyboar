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

    @Override
    public void onCreate() {
        super.onCreate();
        dictionaryHelper = DictionaryHelper.getInstance(this);
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
        suggestionText.setText("Bangla likhun, tarpor Convert chapun");
        suggestionText.setTextSize(16);
        suggestionText.setTextColor(0xFF333333);
        suggestionText.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button micButton = new Button(this);
        micButton.setText("🎤");
        micButton.setOnClickListener(v -> startVoiceInput());

        Button convertButton = new Button(this);
        convertButton.setText("Convert");
        convertButton.setOnClickListener(v -> convertCurrentText());

        suggestionBar.addView(suggestionText);
        suggestionBar.addView(micButton);
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
        String banglaText = before.toString().trim();
        int banglaLength = before.length();
        processBanglaText(banglaText, banglaLength);
    }

    public void handleVoiceResult(String recognizedBangla) {
        processBanglaText(recognizedBangla, -1);
    }

    private void processBanglaText(String banglaText, int banglaLength) {
        String dictResult = dictionaryHelper.lookup(banglaText);
        String banglishResult;

        if (dictResult != null) {
            banglishResult = dictResult;
        } else {
            banglishResult = BanglaToBanglishConverter.convert(banglaText);
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
        suggestionText.setText("Bangla likhun, tarpor Convert chapun");
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
                       
