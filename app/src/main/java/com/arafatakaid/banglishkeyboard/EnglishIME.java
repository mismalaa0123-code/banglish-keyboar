package com.arafatakaid.banglishkeyboard;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

public class EnglishIME extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    private static final int KEYCODE_GLOBE = -100;
    private static final int KEYCODE_DELETE = -5;
    private static final int KEYCODE_SHIFT = -1;

    private KeyboardView keyboardView;
    private Keyboard englishKeyboard;

    @Override
    public View onCreateInputView() {

        View root = LayoutInflater.from(this)
                .inflate(R.layout.english_keyboard_view, null);

        keyboardView = root.findViewById(R.id.english_keyboard_view);

        englishKeyboard = new Keyboard(this, R.xml.english_qwerty);

        keyboardView.setKeyboard(englishKeyboard);
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

        try {

            switch (primaryCode) {

                case KEYCODE_DELETE:
                    ic.deleteSurroundingText(1, 0);
                    break;

                case KEYCODE_SHIFT:

                    boolean newShiftState =
                            !englishKeyboard.isShifted();

                    englishKeyboard.setShifted(newShiftState);

                    keyboardView.invalidateAllKeys();

                    break;

                case 10:
                    ic.commitText("\n", 1);
                    break;

                case KEYCODE_GLOBE:
                    showKeyboardPicker();
                    break;

                default:

                    if (primaryCode >= 0 &&
                            primaryCode <= Character.MAX_VALUE) {

                        char character = (char) primaryCode;

                        if (englishKeyboard.isShifted() &&
                                Character.isLetter(character)) {

                            character =
                                    Character.toUpperCase(character);
                        }

                        ic.commitText(
                                String.valueOf(character),
                                1
                        );

                        /*
                         * Normal English keyboard behavior:
                         * after typing one uppercase letter,
                         * Shift automatically turns off.
                         */
                        if (englishKeyboard.isShifted()) {

                            englishKeyboard.setShifted(false);

                            keyboardView.invalidateAllKeys();
                        }
                    }

                    break;
            }

        } catch (Exception ignored) {
            // Keyboard must never crash the input method.
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

        } catch (Exception ignored) {
        }
    }

    @Override
    public void onText(CharSequence text) {

        if (text == null || text.length() == 0) {
            return;
        }

        InputConnection ic = getCurrentInputConnection();

        if (ic != null) {
            ic.commitText(text, 1);
        }
    }

    @Override
    public void onPress(int primaryCode) {
    }

    @Override
    public void onRelease(int primaryCode) {
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
