package com.arafatakaid.banglishkeyboard;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.Locale;

public class BanglaIME extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    private static final int KEYCODE_GLOBE = -100;
    private static final int KEYCODE_VOICE = -101;

    private KeyboardView keyboardView;
    private Keyboard banglaKeyboard;
    private SpeechRecognizer speechRecognizer;

    @Override
    public View onCreateInputView() {

        View root = getLayoutInflater()
                .inflate(R.layout.bangla_keyboard_view, null);

        keyboardView =
                root.findViewById(R.id.bangla_keyboard_view);

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

            case KEYCODE_VOICE:

                startVoiceInput();

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

    private void startVoiceInput() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.M) {

            if (checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {

                return;
            }
        }

        stopVoiceInput();

        speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(this);

        speechRecognizer.setRecognitionListener(
                new RecognitionListener() {

                    @Override
                    public void onReadyForSpeech(
                            android.os.Bundle params) {
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                    }

                    @Override
                    public void onRmsChanged(float rmsdB) {
                    }

                    @Override
                    public void onBufferReceived(
                            byte[] buffer) {
                    }

                    @Override
                    public void onEndOfSpeech() {
                    }

                    @Override
                    public void onError(int error) {
                        stopVoiceInput();
                    }

                    @Override
                    public void onResults(
                            android.os.Bundle results) {

                        ArrayList<String> matches =
                                results.getStringArrayList(
                                        SpeechRecognizer.RESULTS_RECOGNITION
                                );

                        if (matches != null
                                && !matches.isEmpty()) {

                            InputConnection ic =
                                    getCurrentInputConnection();

                            if (ic != null) {

                                ic.commitText(
                                        matches.get(0),
                                        1
                                );
                            }
                        }

                        stopVoiceInput();
                    }

                    @Override
                    public void onPartialResults(
                            android.os.Bundle partialResults) {
                    }

                    @Override
                    public void onEvent(
                            int eventType,
                            android.os.Bundle params) {
                    }
                }
        );

        Intent intent =
                new Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "bn-BD"
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                "bn-BD"
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE,
                "bn-BD"
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                3
        );

        speechRecognizer.startListening(intent);
    }

    private void stopVoiceInput() {

        if (speechRecognizer != null) {

            speechRecognizer.stopListening();
            speechRecognizer.destroy();
            speechRecognizer = null;
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

        InputConnection ic =
                getCurrentInputConnection();

        if (ic != null
                && text != null
                && text.length() > 0) {

            ic.commitText(text, 1);
        }
    }

    @Override
    public void onDestroy() {

        stopVoiceInput();

        super.onDestroy();
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
