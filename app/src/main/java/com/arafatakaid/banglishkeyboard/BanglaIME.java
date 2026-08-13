package com.arafatakaid.banglishkeyboard;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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

    @Override
    public View onCreateInputView() {

        View root = getLayoutInflater()
                .inflate(R.layout.bangla_keyboard_view, null);

        // বাংলা KeyboardView
        keyboardView = root.findViewById(
                R.id.bangla_keyboard_view
        );

        // Voice Button
        voiceButton = root.findViewById(
                R.id.bangla_voice_button
        );

        // বাংলা Keyboard
        banglaKeyboard = new Keyboard(
                this,
                R.xml.bangla_keyboard
        );

        keyboardView.setKeyboard(banglaKeyboard);

        keyboardView.setOnKeyboardActionListener(this);

        keyboardView.setPreviewEnabled(false);

        // Voice Button
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

    // =========================================================
    // Keyboard Key
    // =========================================================

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {

        InputConnection ic =
                getCurrentInputConnection();

        if (ic == null) {
            return;
        }

        switch (primaryCode) {

            // Delete
            case Keyboard.KEYCODE_DELETE:

                ic.deleteSurroundingText(1, 0);

                break;

            // Shift
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

            // Enter
            case Keyboard.KEYCODE_DONE:

                ic.commitText("\n", 1);

                break;

            // Globe
            case KEYCODE_GLOBE:

                showKeyboardPicker();

                break;

            // Normal Bangla character
            default:

                if (primaryCode != 0) {

                    String text =
                            String.valueOf((char) primaryCode);

                    ic.commitText(text, 1);
                }

                break;
        }
    }

    // =========================================================
    // VOICE INPUT
    // =========================================================

    private void startVoiceInput() {

        // যদি আগের voice recognition চলতে থাকে
        if (isListening) {

            stopVoiceInput();

            return;
        }

        // Speech Recognition আছে কিনা
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {

            Toast.makeText(
                    this,
                    "এই ফোনে Voice Recognition পাওয়া যাচ্ছে না",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        // Microphone Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {

                Toast.makeText(
                        this,
                        "Microphone Permission দিতে হবে",
                        Toast.LENGTH_LONG
                ).show();

                openAppPermissionSettings();

                return;
            }
        }

        // আগের recognizer বন্ধ
        stopVoiceInput();

        try {

            speechRecognizer =
                    SpeechRecognizer.createSpeechRecognizer(
                            getApplicationContext()
                    );

            speechRecognizer.setRecognitionListener(
                    new RecognitionListener() {

                        @Override
                        public void onReadyForSpeech(
                                Bundle params) {

                            isListening = true;

                            Toast.makeText(
                                    BanglaIME.this,
                                    "বলুন...",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }

                        @Override
                        public void onBeginningOfSpeech() {

                            isListening = true;
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

                            isListening = false;
                        }

                        @Override
                        public void onError(
                                int error) {

                            isListening = false;

                            String message;

                            switch (error) {

                                case SpeechRecognizer.ERROR_AUDIO:
                                    message = "Microphone-এ সমস্যা হয়েছে";
                                    break;

                                case SpeechRecognizer.ERROR_CLIENT:
                                    message = "Voice recognition শুরু করা যায়নি";
                                    break;

                                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                                    message = "Microphone Permission নেই";
                                    break;

                                case SpeechRecognizer.ERROR_NETWORK:
                                    message = "Network সমস্যা হয়েছে";
                                    break;

                                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                                    message = "Network Timeout";
                                    break;

                                case SpeechRecognizer.ERROR_NO_MATCH:
                                    message = "কোনো কথা বোঝা যায়নি";
                                    break;

                                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                                    message = "Voice Recognition ব্যস্ত আছে";
                                    break;

                                case SpeechRecognizer.ERROR_SERVER:
                                    message = "Voice Server সমস্যা";
                                    break;

                                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                                    message = "কোনো কথা শোনা যায়নি";
                                    break;

                                default:
                                    message =
                                            "Voice Error: " + error;
                                    break;
                            }

                            Toast.makeText(
                                    BanglaIME.this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();

                            stopVoiceInput();
                        }

                        @Override
                        public void onResults(
                                Bundle results) {

                            isListening = false;

                            if (results == null) {

                                stopVoiceInput();

                                return;
                            }

                            ArrayList<String> matches =
                                    results.getStringArrayList(
                                            SpeechRecognizer
                                                    .RESULTS_RECOGNITION
                                    );

                            if (matches != null
                                    && !matches.isEmpty()) {

                                String recognizedText =
                                        matches.get(0);

                                if (recognizedText != null
                                        && !recognizedText.trim()
                                        .isEmpty()) {

                                    InputConnection ic =
                                            getCurrentInputConnection();

                                    if (ic != null) {

                                        ic.commitText(
                                                recognizedText,
                                                1
                                        );
                                    }
                                }
                            }

                            stopVoiceInput();
                        }

                        @Override
                        public void onPartialResults(
                                Bundle partialResults) {

                            // এখানে partial text commit করছি না।
                            // করলে একই লেখা বারবার টাইপ হয়ে যেতে পারে।
                        }

                        @Override
                        public void onEvent(
                                int eventType,
                                Bundle params) {
                        }
                    }
            );

            // =================================================
            // Speech Intent
            // =================================================

            Intent intent =
                    new Intent(
                            RecognizerIntent
                                    .ACTION_RECOGNIZE_SPEECH
                    );

            // Free-form speech
            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            );

            // বাংলা বাংলাদেশ
            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "bn-BD"
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "bn-BD"
            );

            // সর্বোচ্চ result
            intent.putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
            );

            // Partial result চালু
            intent.putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
            );

            // Calling package
            intent.putExtra(
                    RecognizerIntent.EXTRA_CALLING_PACKAGE,
                    getPackageName()
            );

            // Voice recognition শুরু
            speechRecognizer.startListening(intent);

            isListening = true;

        } catch (Exception e) {

            isListening = false;

            Toast.makeText(
                    this,
                    "Voice চালু করতে সমস্যা: "
                            + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

            stopVoiceInput();
        }
    }

    // =========================================================
    // STOP VOICE
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
    }

    // =========================================================
    // MICROPHONE PERMISSION SETTINGS
    // =========================================================

    private void openAppPermissionSettings() {

        try {

            Intent intent =
                    new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    );

            Uri uri =
                    Uri.fromParts(
                            "package",
                            getPackageName(),
                            null
                    );

            intent.setData(uri);

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
            );

            startActivity(intent);

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "App Settings খুলতে পারিনি",
                    Toast.LENGTH_SHORT
            ).show();
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
    // onText
    // =========================================================

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

    // =========================================================
    // SERVICE DESTROY
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
    public void swipeUp() {
    }

    @Override
    public void swipeDown() {
    }

    @Override
    public void onPress(int primaryCode) {
    }

    @Override
    public void onRelease(int primaryCode) {
    }
                            }
