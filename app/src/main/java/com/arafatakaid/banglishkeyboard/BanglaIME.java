package com.arafatakaid.banglishkeyboard;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.Button;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;

public class BanglaIME extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    private static final int KEYCODE_GLOBE = -100;
    private static final int REQUEST_RECORD_AUDIO = 2001;

    private KeyboardView keyboardView;
    private Keyboard banglaKeyboard;
    private SpeechRecognizer speechRecognizer;

    private Button voiceButton;

    @Override
    public View onCreateInputView() {

        View root = getLayoutInflater()
                .inflate(R.layout.bangla_keyboard_view, null);

        // বাংলা KeyboardView
        keyboardView =
                root.findViewById(R.id.bangla_keyboard_view);

        // উপরের Voice Button
        voiceButton =
                root.findViewById(R.id.bangla_voice_button);

        // বাংলা Keyboard
        banglaKeyboard = new Keyboard(
                this,
                R.xml.bangla_keyboard
        );

        keyboardView.setKeyboard(banglaKeyboard);

        keyboardView.setOnKeyboardActionListener(this);

        keyboardView.setPreviewEnabled(false);

        // Voice Button click
        if (voiceButton != null) {

            voiceButton.setOnClickListener(
                    new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {

                            startVoiceInput();

                        }
                    }
            );
        }

        return root;
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {

        InputConnection ic =
                getCurrentInputConnection();

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

    /**
     * Voice Input শুরু
     */
    private void startVoiceInput() {

        // Speech recognition available কিনা
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {

            return;
        }

        // Android 6.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {

                // IME থেকে permission dialog সব device-এ
                // সরাসরি দেখানো সম্ভব নাও হতে পারে।
                // তাই permission না থাকলে Settings/App-এর
                // permission থেকে দিতে হবে।

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
                            Bundle params) {

                    }

                    @Override
                    public void onBeginningOfSpeech() {

                    }

                    @Override
                    public void onRmsChanged(
                            float rmsdB) {

                    }

                    @Override
                    public void onBufferReceived(
                            byte[] buffer) {

                    }

                    @Override
                    public void onEndOfSpeech() {

                    }

                    @Override
                    public void onError(
                            int error) {

                        stopVoiceInput();
                    }

                    @Override
                    public void onResults(
                            Bundle results) {

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
                            Bundle partialResults) {

                    }

                    @Override
                    public void onEvent(
                            int eventType,
                            Bundle params) {

                    }
                }
        );

        // Speech recognition intent
        Intent intent =
                new Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                );

        // বাংলা ভাষা
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "bn-BD"
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                "bn-BD"
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                3
        );

        // Partial result চাই
        intent.putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                false
        );

        speechRecognizer.startListening(intent);
    }

    /**
     * Voice Input বন্ধ
     */
    private void stopVoiceInput() {

        if (speechRecognizer != null) {

            try {

                speechRecognizer.stopListening();

            } catch (Exception ignored) {
            }

            try {

                speechRecognizer.cancel();

            } catch (Exception ignored) {
            }

            try {

                speechRecognizer.destroy();

            } catch (Exception ignored) {
            }

            speechRecognizer = null;
        }
    }

    /**
     * Keyboard Picker
     */
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

    /**
     * Keyboard text
     */
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

    /**
     * Service destroy
     */
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
    public void onPress(
            int primaryCode) {
    }

    @Override
    public void onRelease(
            int primaryCode) {
    }
        }
