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
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;

public class BanglaIME extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    private static final int KEYCODE_GLOBE = -100;

    private KeyboardView keyboardView;
    private Keyboard banglaKeyboard;

    private SpeechRecognizer speechRecognizer;

    private Button voiceButton;

    private boolean isListening = false;


    // =========================================================
    // CREATE KEYBOARD VIEW
    // =========================================================

    @Override
    public View onCreateInputView() {

        View root = getLayoutInflater()
                .inflate(R.layout.bangla_keyboard_view, null);


        // -----------------------------------------------------
        // বাংলা Keyboard View
        // -----------------------------------------------------

        keyboardView =
                root.findViewById(
                        R.id.bangla_keyboard_view
                );


        // -----------------------------------------------------
        // Voice Button
        // -----------------------------------------------------

        voiceButton =
                root.findViewById(
                        R.id.bangla_voice_button
                );


        // -----------------------------------------------------
        // বাংলা Keyboard
        // -----------------------------------------------------

        banglaKeyboard =
                new Keyboard(
                        this,
                        R.xml.bangla_keyboard
                );


        keyboardView.setKeyboard(
                banglaKeyboard
        );


        keyboardView.setOnKeyboardActionListener(
                this
        );


        keyboardView.setPreviewEnabled(
                false
        );


        // -----------------------------------------------------
        // Voice Button Click
        // -----------------------------------------------------

        if (voiceButton != null) {

            voiceButton.setOnClickListener(
                    new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {

                            if (isListening) {

                                stopVoiceInput();

                            } else {

                                startVoiceInput();
                            }
                        }
                    }
            );
        }


        return root;
    }


    // =========================================================
    // KEYBOARD KEY
    // =========================================================

    @Override
    public void onKey(
            int primaryCode,
            int[] keyCodes) {


        InputConnection ic =
                getCurrentInputConnection();


        if (ic == null) {
            return;
        }


        switch (primaryCode) {


            // -------------------------------------------------
            // DELETE
            // -------------------------------------------------

            case Keyboard.KEYCODE_DELETE:

                ic.deleteSurroundingText(
                        1,
                        0
                );

                break;


            // -------------------------------------------------
            // SHIFT
            // -------------------------------------------------

            case Keyboard.KEYCODE_SHIFT:

                if (banglaKeyboard != null) {

                    banglaKeyboard.setShifted(
                            !banglaKeyboard.isShifted()
                    );

                    if (keyboardView != null) {

                        keyboardView.invalidateAllKeys();
                    }
                }

                break;


            // -------------------------------------------------
            // ENTER
            // -------------------------------------------------

            case Keyboard.KEYCODE_DONE:

                ic.commitText(
                        "\n",
                        1
                );

                break;


            // -------------------------------------------------
            // GLOBE
            // -------------------------------------------------

            case KEYCODE_GLOBE:

                showKeyboardPicker();

                break;


            // -------------------------------------------------
            // NORMAL BANGLA CHARACTER
            // -------------------------------------------------

            default:

                if (primaryCode != 0) {

                    String text =
                            String.valueOf(
                                    (char) primaryCode
                            );

                    ic.commitText(
                            text,
                            1
                    );
                }

                break;
        }
    }


    // =========================================================
    // START VOICE INPUT
    // =========================================================

    private void startVoiceInput() {


        // -----------------------------------------------------
        // Speech Recognition Available?
        // -----------------------------------------------------

        if (!SpeechRecognizer
                .isRecognitionAvailable(this)) {

            Toast.makeText(
                    this,
                    "এই ফোনে Voice Recognition পাওয়া যাচ্ছে না",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }


        // -----------------------------------------------------
        // Microphone Permission
        // -----------------------------------------------------

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.M) {


            if (checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {


                Toast.makeText(
                        this,
                        "Microphone Permission প্রয়োজন",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }
        }


        // -----------------------------------------------------
        // Stop Previous Recognition
        // -----------------------------------------------------

        stopVoiceInput();


        try {


            speechRecognizer =
                    SpeechRecognizer
                            .createSpeechRecognizer(
                                    this
                            );


            speechRecognizer
                    .setRecognitionListener(
                            new RecognitionListener() {


                                // ---------------------------------
                                // READY
                                // ---------------------------------

                                @Override
                                public void onReadyForSpeech(
                                        Bundle params) {

                                    isListening = true;

                                    setVoiceButtonText(
                                            "🎤 শুনছি..."
                                    );
                                }


                                // ---------------------------------
                                // BEGINNING OF SPEECH
                                // ---------------------------------

                                @Override
                                public void onBeginningOfSpeech() {

                                    isListening = true;

                                    setVoiceButtonText(
                                            "🎤 বলুন..."
                                    );
                                }


                                // ---------------------------------
                                // VOLUME
                                // ---------------------------------

                                @Override
                                public void onRmsChanged(
                                        float rmsdB) {
                                }


                                // ---------------------------------
                                // BUFFER
                                // ---------------------------------

                                @Override
                                public void onBufferReceived(
                                        byte[] buffer) {
                                }


                                // ---------------------------------
                                // END SPEECH
                                // ---------------------------------

                                @Override
                                public void onEndOfSpeech() {

                                    isListening = false;

                                    setVoiceButtonText(
                                            "🎤 লেখা তৈরি হচ্ছে..."
                                    );
                                }


                                // ---------------------------------
                                // ERROR
                                // ---------------------------------

                                @Override
                                public void onError(
                                        int error) {

                                    isListening = false;

                                    String message;


                                    switch (error) {


                                        case SpeechRecognizer
                                                .ERROR_AUDIO:

                                            message =
                                                    "Microphone-এ সমস্যা হয়েছে";

                                            break;


                                        case SpeechRecognizer
                                                .ERROR_NETWORK:

                                            message =
                                                    "Network সমস্যা হয়েছে";

                                            break;


                                        case SpeechRecognizer
                                                .ERROR_NETWORK_TIMEOUT:

                                            message =
                                                    "Network Timeout";

                                            break;


                                        case SpeechRecognizer
                                                .ERROR_NO_MATCH:

                                            message =
                                                    "কোনো কথা বোঝা যায়নি";

                                            break;


                                        case SpeechRecognizer
                                                .ERROR_RECOGNIZER_BUSY:

                                            message =
                                                    "Voice Recognition ব্যস্ত আছে";

                                            break;


                                        case SpeechRecognizer
                                                .ERROR_SPEECH_TIMEOUT:

                                            message =
                                                    "কোনো কথা শোনা যায়নি";

                                            break;


                                        case SpeechRecognizer
                                                .ERROR_INSUFFICIENT_PERMISSIONS:

                                            message =
                                                    "Microphone Permission নেই";

                                            break;


                                        default:

                                            message =
                                                    "Voice Error: "
                                                            + error;

                                            break;
                                    }


                                    setVoiceButtonText(
                                            "🎤 ভয়েস দিয়ে লিখুন"
                                    );


                                    Toast.makeText(
                                            BanglaIME.this,
                                            message,
                                            Toast.LENGTH_SHORT
                                    ).show();


                                    stopVoiceInput();
                                }


                                // ---------------------------------
                                // FINAL RESULT
                                // ---------------------------------

                                @Override
                                public void onResults(
                                        Bundle results) {


                                    isListening = false;


                                    ArrayList<String> matches =
                                            results.getStringArrayList(
                                                    SpeechRecognizer
                                                            .RESULTS_RECOGNITION
                                            );


                                    if (matches != null
                                            && !matches.isEmpty()) {


                                        String finalText =
                                                matches.get(0);


                                        if (finalText != null
                                                && !finalText
                                                .trim()
                                                .isEmpty()) {


                                            InputConnection ic =
                                                    getCurrentInputConnection();


                                            if (ic != null) {

                                                ic.commitText(
                                                        finalText,
                                                        1
                                                );
                                            }
                                        }
                                    }


                                    setVoiceButtonText(
                                            "🎤 ভয়েস দিয়ে লিখুন"
                                    );


                                    stopVoiceInput();
                                }


                                // ---------------------------------
                                // LIVE PARTIAL RESULT
                                // ---------------------------------

                                @Override
                                public void onPartialResults(
                                        Bundle partialResults) {


                                    ArrayList<String> partialMatches =
                                            partialResults
                                                    .getStringArrayList(
                                                            SpeechRecognizer
                                                                    .RESULTS_RECOGNITION
                                                    );


                                    if (partialMatches != null
                                            && !partialMatches
                                            .isEmpty()) {


                                        String liveText =
                                                partialMatches
                                                        .get(0);


                                        if (liveText != null
                                                && !liveText
                                                .trim()
                                                .isEmpty()) {


                                            setVoiceButtonText(
                                                    "🎤 "
                                                            + liveText
                                            );
                                        }
                                    }
                                }


                                // ---------------------------------
                                // EVENT
                                // ---------------------------------

                                @Override
                                public void onEvent(
                                        int eventType,
                                        Bundle params) {
                                }
                            }
                    );


            // -----------------------------------------------------
            // SPEECH INTENT
            // -----------------------------------------------------

            Intent intent =
                    new Intent(
                            RecognizerIntent
                                    .ACTION_RECOGNIZE_SPEECH
                    );


            // Free form speech

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent
                            .LANGUAGE_MODEL_FREE_FORM
            );


            // বাংলা - বাংলাদেশ

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "bn-BD"
            );


            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "bn-BD"
            );


            // Maximum results

            intent.putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
            );


            // IMPORTANT:
            // Live result ON

            intent.putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
            );


            // -----------------------------------------------------
            // Start Listening
            // -----------------------------------------------------

            speechRecognizer.startListening(
                    intent
            );


            isListening = true;


            setVoiceButtonText(
                    "🎤 শুনছি..."
            );


        } catch (Exception e) {


            isListening = false;


            setVoiceButtonText(
                    "🎤 ভয়েস দিয়ে লিখুন"
            );


            Toast.makeText(
                    this,
                    "Voice চালু করতে সমস্যা হয়েছে",
                    Toast.LENGTH_LONG
            ).show();


            stopVoiceInput();
        }
    }


    // =========================================================
    // CHANGE VOICE BUTTON TEXT
    // =========================================================

    private void setVoiceButtonText(
            final String text) {


        if (voiceButton != null) {


            voiceButton.post(
                    new Runnable() {

                        @Override
                        public void run() {

                            voiceButton.setText(
                                    text
                            );
                        }
                    }
            );
        }
    }


    // =========================================================
    // STOP VOICE INPUT
    // =========================================================

    private void stopVoiceInput() {


        isListening = false;


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


        if (voiceButton != null) {

            setVoiceButtonText(
                    "🎤 ভয়েস দিয়ে লিখুন"
            );
        }
    }


    // =========================================================
    // KEYBOARD PICKER
    // =========================================================

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


    // =========================================================
    // ON TEXT
    // =========================================================

    @Override
    public void onText(
            CharSequence text) {


        InputConnection ic =
                getCurrentInputConnection();


        if (ic != null
                && text != null
                && text.length() > 0) {


            ic.commitText(
                    text,
                    1
            );
        }
    }


    // =========================================================
    // DESTROY
    // =========================================================

    @Override
    public void onDestroy() {


        stopVoiceInput();


        super.onDestroy();
    }


    // =========================================================
    // UNUSED SWIPE METHODS
    // =========================================================

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
