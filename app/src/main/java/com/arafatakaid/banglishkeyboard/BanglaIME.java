package com.arafatakaid.banglishkeyboard;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;

public class BanglaIME extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    private static final int KEYCODE_GLOBE       = -100;
    private static final int KEYCODE_NUMBER_MODE = -200;
    private static final int KEYCODE_EMOJI       = -201;
    private static final int KEYCODE_CURSOR      = -202;
    private static final int KEYCODE_ABC         = -203;

    private KeyboardView keyboardView;
    private Keyboard banglaKeyboard;
    private Keyboard numberKeyboard;

    private Button voiceButton;
    private Button emojiButton;
    private Button clipboardButton;

    private LinearLayout emojiPanel;

    private boolean isNumberMode = false;
    private boolean isEmojiPanelOpen = false;

    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    private static final String[] EMOJIS = {
            "\uD83D\uDE03", "\uD83D\uDE02", "\uD83D\uDE0D", "\uD83D\uDE24",
            "\u2764\uFE0F", "\uD83D\uDE2F", "\uD83D\uDE18", "\uD83D\uDE17",
            "\uD83D\uDE1A", "\uD83D\uDE29", "\uD83D\uDE0D", "\uD83D\uDE00",
            "\uD83D\uDE4F", "\uD83D\uDE25", "\uD83C\uDF39", "\uD83D\uDD25",
            "\uD83C\uDF86", "\uD83C\uDF89", "\uD83D\uDE4C", "\uD83D\uDC4C",
            "\uD83C\uDF39", "\uD83D\uDE33", "\uD83D\uDE0E", "\uD83E\uDD14",
            "\uD83D\uDE34", "\uD83E\uDD17", "\uD83D\uDE07", "\uD83E\uDD73",
            "\uD83D\uDCAA", "\uD83D\uDE4B", "\uD83D\uDC4B", "\u2728"
    };

    @Override
    public View onCreateInputView() {

        View root = getLayoutInflater()
                .inflate(R.layout.bangla_keyboard_view, null);

        keyboardView = root.findViewById(R.id.bangla_keyboard_view);
        voiceButton = root.findViewById(R.id.bangla_voice_button);
        emojiButton = root.findViewById(R.id.bangla_emoji_button);
        clipboardButton = root.findViewById(R.id.bangla_clipboard_button);
        emojiPanel = root.findViewById(R.id.bangla_emoji_panel);

        FrameLayout bannerContainer =
                root.findViewById(R.id.startio_banner_container);
        StartIoBannerHelper.attach(this, bannerContainer);

        banglaKeyboard = new Keyboard(this, R.xml.bangla_keyboard);
        numberKeyboard = new Keyboard(this, R.xml.bangla_number_keyboard);

        keyboardView.setKeyboard(banglaKeyboard);
        keyboardView.setOnKeyboardActionListener(this);
        keyboardView.setPreviewEnabled(false);

        if (voiceButton != null) {
            voiceButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isListening) {
                        stopVoiceInput();
                    } else {
                        startVoiceInput();
                    }
                }
            });
        }

        if (emojiButton != null) {
            emojiButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleEmojiPanel();
                }
            });
        }

        if (clipboardButton != null) {
            clipboardButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pasteFromClipboard();
                }
            });
        }

        buildEmojiPanel();

        return root;
    }

    private void buildEmojiPanel() {
        if (emojiPanel == null) return;
        emojiPanel.removeAllViews();

        int cols = 8;
        int density = getResources().getDisplayMetrics().density;
        int cellSize = (int) (48 * density);
        int gap = (int) (4 * density);

        for (int i = 0; i < EMOJIS.length; i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(gap, gap, gap, gap);

            LinearLayout.LayoutParams rowParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            row.setLayoutParams(rowParams);

            for (int j = 0; j < cols; j++) {
                if (i + j < EMOJIS.length) {
                    Button emojiBtn = new Button(this);
                    emojiBtn.setText(EMOJIS[i + j]);
                    emojiBtn.setTextSize(22);
                    emojiBtn.setMinWidth(cellSize);
                    emojiBtn.setMinHeight(cellSize);
                    emojiBtn.setPadding(0, 0, 0, 0);
                    emojiBtn.setBackgroundColor(0x00000000);
                    emojiBtn.setAllCaps(false);

                    LinearLayout.LayoutParams btnParams =
                            new LinearLayout.LayoutParams(
                                    0, cellSize, 1f);
                    btnParams.setMargins(gap / 2, gap / 2, gap / 2, gap / 2);
                    emojiBtn.setLayoutParams(btnParams);

                    final String emoji = EMOJIS[i + j];
                    emojiBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            InputConnection ic = getCurrentInputConnection();
                            if (ic != null) {
                                ic.commitText(emoji, 1);
                            }
                        }
                    });

                    row.addView(emojiBtn);
                }
            }
            emojiPanel.addView(row);
        }
    }

    private void toggleEmojiPanel() {
        if (emojiPanel == null || keyboardView == null) return;

        isEmojiPanelOpen = !isEmojiPanelOpen;

        if (isEmojiPanelOpen) {
            keyboardView.setVisibility(View.GONE);
            emojiPanel.setVisibility(View.VISIBLE);
        } else {
            emojiPanel.setVisibility(View.GONE);
            keyboardView.setVisibility(View.VISIBLE);
        }
    }

    private void pasteFromClipboard() {
        try {
            ClipboardManager cm =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    if (text != null) {
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) {
                            ic.commitText(text, 1);
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Clipboard খালি", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Clipboard error", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {

        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (primaryCode) {

            case Keyboard.KEYCODE_DELETE:
                ic.deleteSurroundingText(1, 0);
                break;

            case Keyboard.KEYCODE_DONE:
                handleEnter(ic);
                break;

            case KEYCODE_GLOBE:
                showKeyboardPicker();
                break;

            case KEYCODE_NUMBER_MODE:
                switchToNumberMode();
                break;

            case KEYCODE_ABC:
                switchToBengaliMode();
                break;

            case KEYCODE_EMOJI:
                toggleEmojiPanel();
                break;

            case KEYCODE_CURSOR:
                handleCursorControl(ic);
                break;

            default:
                if (primaryCode > 0) {
                    String text = String.valueOf((char) primaryCode);
                    ic.commitText(text, 1);
                }
                break;
        }
    }

    @Override
    public void onLongPress(int primaryCode) {
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.setComposingText("", 0);
                ic.deleteSurroundingText(Integer.MAX_VALUE, 0);
            }
        }
    }

    private void handleEnter(InputConnection ic) {
        EditorInfo editorInfo = getCurrentInputEditorInfo();

        if (editorInfo != null) {
            int imeAction = editorInfo.imeOptions &
                    EditorInfo.IME_MASK_ACTION;

            switch (imeAction) {
                case EditorInfo.IME_ACTION_GO:
                case EditorInfo.IME_ACTION_NEXT:
                case EditorInfo.IME_ACTION_SEARCH:
                case EditorInfo.IME_ACTION_SEND:
                case EditorInfo.IME_ACTION_DONE:
                    ic.performEditorAction(imeAction);
                    return;
            }
        }

        ic.commitText("\n", 1);
    }

    private void handleCursorControl(InputConnection ic) {
        try {
            ic.selectAll();
        } catch (Exception ignored) {
        }
    }

    private void switchToNumberMode() {
        if (keyboardView == null) return;
        isNumberMode = true;
        keyboardView.setKeyboard(numberKeyboard);
        keyboardView.invalidateAllKeys();
    }

    private void switchToBengaliMode() {
        if (keyboardView == null) return;
        isNumberMode = false;
        keyboardView.setKeyboard(banglaKeyboard);
        keyboardView.invalidateAllKeys();
    }

    private void showKeyboardPicker() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }

    private void startVoiceInput() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this,
                    "এই ফোনে Voice Recognition পাওয়া যাচ্ছে না",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,
                        "Microphone Permission প্রয়োজন",
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        stopVoiceInput();

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {

                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening = true;
                    setVoiceButtonText("🎤 শুনছি...");
                }

                @Override
                public void onBeginningOfSpeech() {
                    isListening = true;
                    setVoiceButtonText("🎤 বলুন...");
                }

                @Override
                public void onRmsChanged(float rmsdB) { }

                @Override
                public void onBufferReceived(byte[] buffer) { }

                @Override
                public void onEndOfSpeech() {
                    isListening = false;
                    setVoiceButtonText("🎤 লেখা তৈরি হচ্ছে...");
                }

                @Override
                public void onError(int error) {
                    isListening = false;
                    String message;
                    switch (error) {
                        case SpeechRecognizer.ERROR_AUDIO:
                            message = "Microphone-এ সমস্যা হয়েছে"; break;
                        case SpeechRecognizer.ERROR_NETWORK:
                            message = "Network সমস্যা হয়েছে"; break;
                        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                            message = "Network Timeout"; break;
                        case SpeechRecognizer.ERROR_NO_MATCH:
                            message = "কোনো কথা বোঝা যায়নি"; break;
                        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                            message = "Voice Recognition ব্যস্ত আছে"; break;
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            message = "কোনো কথা শোনা যায়নি"; break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            message = "Microphone Permission নেই"; break;
                        default:
                            message = "Voice Error: " + error; break;
                    }
                    setVoiceButtonText("🎤 ভয়েস দিয়ে লিখুন");
                    Toast.makeText(BanglaIME.this, message, Toast.LENGTH_SHORT).show();
                    stopVoiceInput();
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    ArrayList<String> matches = results.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String finalText = matches.get(0);
                        if (finalText != null && !finalText.trim().isEmpty()) {
                            InputConnection ic = getCurrentInputConnection();
                            if (ic != null) {
                                ic.commitText(finalText, 1);
                            }
                        }
                    }
                    setVoiceButtonText("🎤 ভয়েস দিয়ে লিখুন");
                    stopVoiceInput();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> partialMatches = partialResults
                            .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (partialMatches != null && !partialMatches.isEmpty()) {
                        String liveText = partialMatches.get(0);
                        if (liveText != null && !liveText.trim().isEmpty()) {
                            setVoiceButtonText("🎤 " + liveText);
                        }
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) { }
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

            speechRecognizer.startListening(intent);
            isListening = true;
            setVoiceButtonText("🎤 শুনছি...");

        } catch (Exception e) {
            isListening = false;
            setVoiceButtonText("🎤 ভয়েস দিয়ে লিখুন");
            Toast.makeText(this, "Voice চালু করতে সমস্যা হয়েছে",
                    Toast.LENGTH_LONG).show();
            stopVoiceInput();
        }
    }

    private void setVoiceButtonText(final String text) {
        if (voiceButton != null) {
            voiceButton.post(new Runnable() {
                @Override
                public void run() {
                    voiceButton.setText(text);
                }
            });
        }
    }

    private void stopVoiceInput() {
        isListening = false;
        if (speechRecognizer != null) {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) {}
            try { speechRecognizer.cancel(); } catch (Exception ignored) {}
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
            speechRecognizer = null;
        }
        if (voiceButton != null) {
            setVoiceButtonText("🎤");
        }
    }

    @Override
    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && text != null && text.length() > 0) {
            ic.commitText(text, 1);
        }
    }

    @Override
    public void onDestroy() {
        stopVoiceInput();
        super.onDestroy();
    }

    @Override public void swipeLeft() { }
    @Override public void swipeRight() { }
    @Override public void swipeDown() { }
    @Override public void swipeUp() { }
    @Override public void onPress(int primaryCode) { }
    @Override public void onRelease(int primaryCode) { }
}
