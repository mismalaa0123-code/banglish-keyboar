package com.arafatakaid.banglishkeyboard;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

public class BanglaIME extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    private static final int KEYCODE_GLOBE = -100;

    private KeyboardView keyboardView;
    private Keyboard banglaKeyboard;

    @Override
    public View onCreateInputView() {

        View root = getLayoutInflater()
                .inflate(R.layout.bangla_keyboard_view, null);

        keyboardView = root.findViewById(R.id.bangla_keyboard_view);

        banglaKeyboard = new Keyboard(
                this,
                R.xml.bangla_keyboard
        );

        keyboardView.setKeyboard(banglaKeyboard);
        keyboardView.setOnKeyboardActionListener(this);
        keyboardView.setPreviewEnabled(false);

        return root;
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {

        InputConnection ic = getCurrentInputConnection();

        if (ic == null) {
            return;
        }

        switch (primaryCode) {

            case Keyboard.KEYCODE_DELETE:

                ic.deleteSurroundingText(1, 0);

                break;

            case Keyboard.KEYCODE_SHIFT:

                banglaKeyboard.setShifted(
                        !banglaKeyboard.isShifted()
                );

                keyboardView.invalidateAllKeys();

                break;

            case Keyboard.KEYCODE_DONE:

                ic.commitText("\n", 1);

                break;

            case KEYCODE_GLOBE:

                showKeyboardPicker();

                break;

            default:

                if (primaryCode != 0) {

                    String text =
                            String.valueOf((char) primaryCode);

                    ic.commitText(text, 1);
                }

                break;
        }
    }

    private void showKeyboardPicker() {

        InputMethodManager imm =
                (InputMethodManager)
                        getSystemService(
                                Context.INPUT_METHOD_SERVICE
                        );

        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }

    @Override
    public void onText(CharSequence text) {

        InputConnection ic = getCurrentInputConnection();

        if (ic != null && text != null) {
            ic.commitText(text, 1);
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
    public void onPress(int primaryCode) {
    }

    @Override
    public void onRelease(int primaryCode) {
    }
}
