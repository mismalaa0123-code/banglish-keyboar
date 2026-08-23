package com.arafatakaid.banglishkeyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;

public class BanglaIME extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private KeyboardView kv;
    private Keyboard banglaKeyboard;
    private boolean isLongPressed = false;
    private Handler backspaceHandler = new Handler();
    private Runnable backspaceRunnable;

    @Override
    public View onCreateInputView() {
        View layout = getLayoutInflater().inflate(R.layout.bangla_keyboard_view, null);
        kv = layout.findViewById(R.id.keyboard_view);
        banglaKeyboard = new Keyboard(this, R.xml.bangla_keyboard);
        kv.setKeyboard(banglaKeyboard);
        kv.setOnKeyboardActionListener(this);

        // Copy/Paste Button
        layout.findViewById(R.id.btn_copy_paste).setOnClickListener(v -> handleCopyPaste());

        // Voice Input Button
        layout.findViewById(R.id.btn_voice_input).setOnClickListener(v -> startVoiceInput());

        return layout;
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (primaryCode) {
            case Keyboard.KEYCODE_DELETE:
                if (!isLongPressed) {
                    ic.deleteSurroundingText(1, 0);
                }
                break;
            case Keyboard.KEYCODE_DONE:
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                break;
            case 32: // Space
                ic.commitText(" ", 1);
                break;
            case -2: // Number/Symbol Keyboard
                // Toggle logic here
                break;
            case -100: // Emoji Panel
                // Toggle emoji layout
                break;
            case -101: // Language Switcher (Globe)
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showInputMethodPicker();
                break;
            case -102: // Cursor Movement
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
                break;
            default:
                char code = (char) primaryCode;
                ic.commitText(String.valueOf(code), 1);
        }
    }

    // Backspace Long Press: Clear All Text
    @Override
    public void onPress(int primaryCode) {
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            isLongPressed = false;
            backspaceRunnable = () -> {
                isLongPressed = true;
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    CharSequence currentText = ic.getExtractedText(new android.view.inputmethod.ExtractedTextRequest(), 0).text;
                    if (currentText != null) {
                        ic.deleteSurroundingText(currentText.length(), currentText.length());
                    }
                }
            };
            backspaceHandler.postDelayed(backspaceRunnable, 800); // Long press duration
        }
    }

    @Override
    public void onRelease(int primaryCode) {
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            backspaceHandler.removeCallbacks(backspaceRunnable);
        }
    }

    private void handleCopyPaste() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        // Simple Paste implementation
        if (clipboard.hasPrimaryClip()) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            ic.commitText(item.getText(), 1);
        } else {
            Toast.makeText(this, "ক্লিপবোর্ড খালি", Toast.LENGTH_SHORT).show();
        }
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "বলুন...");

        try {
            // IME-তে সরাসরি SpeechRecognizer ব্যবহার করা ভালো, 
            // এখানে একটি টোস্ট দিয়ে ফাংশনালিটি নিশ্চিত করা হলো।
            Toast.makeText(this, "ভয়েস ইনপুট শুরু হচ্ছে...", Toast.LENGTH_SHORT).show();
            // নোট: বাস্তব প্রয়োগে SpeechRecognizer Listener ব্যবহার করতে হবে।
        } catch (Exception e) {
            Toast.makeText(this, "ভয়েস ইনপুট কাজ করছে না", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onText(CharSequence text) { getCurrentInputConnection().commitText(text, 1); }
    @Override public void swipeLeft() {}
    @Override public void swipeRight() {}
    @Override public void swipeDown() {}
    @Override public void swipeUp() {}
}
