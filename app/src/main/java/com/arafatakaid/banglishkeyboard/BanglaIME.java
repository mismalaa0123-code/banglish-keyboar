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
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class BanglaIME extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private static final int CODE_SYMBOLS = -2;
    private static final int CODE_EMOJI = -100;
    private static final int CODE_LANGUAGE = -101;
    private static final int CODE_CURSOR = -102;

    private KeyboardView keyboardView;
    private Keyboard banglaKeyboard;

    private LinearLayout rootLayout;
    private ScrollView panelContainer;
    private GridLayout panelGrid;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable backspaceLongRunnable;
    private boolean backspaceLongDone = false;

    private SpeechRecognizer speechRecognizer;

    @Override
    public View onCreateInputView() {
        View view = getLayoutInflater().inflate(R.layout.bangla_keyboard_view, null);

        rootLayout = (LinearLayout) view;

        keyboardView = view.findViewById(R.id.keyboard_view);
        banglaKeyboard = new Keyboard(this, R.xml.bangla_keyboard);

        keyboardView.setKeyboard(banglaKeyboard);
        keyboardView.setOnKeyboardActionListener(this);

        View copyPasteButton = view.findViewById(R.id.btn_copy_paste);
        if (copyPasteButton != null) {
            copyPasteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleCopyPaste();
                }
            });
        }

        View voiceButton = view.findViewById(R.id.btn_voice_input);
        if (voiceButton != null) {
            voiceButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startVoiceInput();
                }
            });
        }

        createDynamicPanel();

        return view;
    }

    private void createDynamicPanel() {
        panelContainer = new ScrollView(this);
        panelContainer.setFillViewport(false);
        panelContainer.setBackgroundColor(0xFFFFFFFF);
        panelContainer.setVisibility(View.GONE);

        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(245)
        );
        panelContainer.setLayoutParams(containerParams);

        panelGrid = new GridLayout(this);
        panelGrid.setColumnCount(7);
        panelGrid.setPadding(dp(6), dp(6), dp(6), dp(6));

        panelContainer.addView(panelGrid);

        rootLayout.addView(panelContainer);
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (primaryCode) {

            case Keyboard.KEYCODE_DELETE:
                /*
                 * Backspace tap এখানে delete করা হবে না।
                 * Tap delete হবে onRelease() থেকে।
                 * এতে long press আর tap একসাথে trigger হবে না।
                 */
                break;

            case CODE_SYMBOLS:
                showNumberSymbolPanel();
                break;

            case CODE_EMOJI:
                showEmojiPanel();
                break;

            case CODE_LANGUAGE:
                showLanguagePicker();
                break;

            case CODE_CURSOR:
                moveCursorLeft();
                break;

            case Keyboard.KEYCODE_DONE:
            case 10:
                sendEnter();
                break;

            case 32:
                ic.commitText(" ", 1);
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
    public void onPress(int primaryCode) {
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            backspaceLongDone = false;

            if (backspaceLongRunnable != null) {
                handler.removeCallbacks(backspaceLongRunnable);
            }

            backspaceLongRunnable = new Runnable() {
                @Override
                public void run() {
                    backspaceLongDone = true;
                    clearCurrentText();
                }
            };

            handler.postDelayed(backspaceLongRunnable, 650);
        }
    }

    @Override
    public void onRelease(int primaryCode) {
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            if (backspaceLongRunnable != null) {
                handler.removeCallbacks(backspaceLongRunnable);
            }

            if (!backspaceLongDone) {
                deleteOneBanglaCharacter();
            }
        }
    }

    private void deleteOneBanglaCharacter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        CharSequence beforeCursor = ic.getTextBeforeCursor(12, 0);

        if (beforeCursor == null || beforeCursor.length() == 0) {
            ic.deleteSurroundingText(1, 0);
            return;
        }

        int length = beforeCursor.length();
        char last = beforeCursor.charAt(length - 1);

        int deleteCount = 1;

        /*
         * Emoji / surrogate pair হলে ২ char unit delete
         */
        if (Character.isLowSurrogate(last) && length >= 2) {
            char prev = beforeCursor.charAt(length - 2);
            if (Character.isHighSurrogate(prev)) {
                deleteCount = 2;
            }
        }

        /*
         * বাংলা যুক্তবর্ণের ক্ষেত্রে:
         * যদি শেষ অক্ষরের আগে হসন্ত থাকে, তাহলে হসন্ত + শেষ অক্ষর একসাথে delete
         * উদাহরণ: ক্ + ত -> backspace দিলে ত সহ হসন্ত সরবে
         */
        if (deleteCount == 1 && length >= 2) {
            char prev = beforeCursor.charAt(length - 2);
            if (prev == '\u09CD' && isBanglaLetter(last)) {
                deleteCount = 2;
            }
        }

        ic.deleteSurroundingText(deleteCount, 0);
    }

    private boolean isBanglaLetter(char c) {
        return c >= '\u0980' && c <= '\u09FF';
    }

    private void clearCurrentText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        try {
            ExtractedTextRequest request = new ExtractedTextRequest();
            ExtractedText extractedText = ic.getExtractedText(request, 0);

            if (extractedText != null && extractedText.text != null) {
                int textLength = extractedText.text.length();

                ic.beginBatchEdit();
                ic.setSelection(0, textLength);
                ic.commitText("", 1);
                ic.endBatchEdit();
            } else {
                ic.deleteSurroundingText(10000, 10000);
            }
        } catch (Exception e) {
            ic.deleteSurroundingText(10000, 10000);
        }
    }

    private void handleCopyPaste() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard == null) return;

        CharSequence selectedText = ic.getSelectedText(0);

        if (selectedText != null && selectedText.length() > 0) {
            ClipData clip = ClipData.newPlainText("bangla_selected_text", selectedText);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "কপি হয়েছে", Toast.LENGTH_SHORT).show();
        } else {
            if (clipboard.hasPrimaryClip()
                    && clipboard.getPrimaryClip() != null
                    && clipboard.getPrimaryClip().getItemCount() > 0) {

                CharSequence pasteText =
                        clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);

                if (pasteText != null && pasteText.length() > 0) {
                    ic.commitText(pasteText, 1);
                } else {
                    Toast.makeText(this, "পেস্ট করার মতো টেক্সট নেই", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "ক্লিপবোর্ড খালি", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startVoiceInput() {
        hidePanelOnly();

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "ভয়েস ইনপুট সাপোর্ট করছে না", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "ভয়েসের জন্য Microphone permission প্রয়োজন", Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Toast.makeText(BanglaIME.this, "বলুন...", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                }

                @Override
                public void onError(int error) {
                    Toast.makeText(BanglaIME.this, "ভয়েস ইনপুট পাওয়া যায়নি", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches =
                            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                    if (matches != null && matches.size() > 0) {
                        String text = matches.get(0);
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null && text != null && text.length() > 0) {
                            ic.commitText(text, 1);
                        }
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);

        try {
            speechRecognizer.cancel();
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            Toast.makeText(this, "ভয়েস ইনপুট চালু করা যায়নি", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLanguagePicker() {
        hidePanelOnly();

        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }

    private void moveCursorLeft() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT));
    }

    private void sendEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    }

    private void showNumberSymbolPanel() {
        String[] symbols = new String[]{
                "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০",
                "@", "#", "৳", "%", "&", "-", "+", "(", ")",
                "=", "*", "“", "”", "'", ":", ";", "!", "?",
                "ABC", ",", "।", "/", "⌫"
        };

        showPanel(symbols, 10);
    }

    private void showEmojiPanel() {
        String[] emojis = new String[]{
                "😀", "😃", "😄", "😁", "😆", "😅", "😂",
                "🤣", "😊", "😇", "🙂", "😉", "😍", "😘",
                "😗", "😙", "😚", "😋", "😎", "🤔", "😐",
                "😑", "😶", "🙄", "😏", "😣", "😥", "😮",
                "🤐", "😯", "😪", "😫", "😴", "😌", "😛",
                "😜", "😝", "🤤", "😒", "😓", "😔", "😕",
                "🙃", "🤑", "😲", "☹", "🙁", "😖", "😞",
                "😟", "😤", "😢", "😭", "😦", "😧", "😨",
                "😩", "🤯", "😬", "😰", "😱", "🥵", "🥶",
                "😳", "🤪", "😵", "😡", "😠", "🤬", "😷",
                "🤒", "🤕", "🤢", "🤮", "🤧", "😇", "🥳",
                "👍", "👎", "🙏", "👏", "❤️", "🔥", "🎉",
                "ABC", "⌫"
        };

        showPanel(emojis, 7);
    }

    private void showPanel(String[] items, int columns) {
        if (panelContainer == null || panelGrid == null || keyboardView == null) return;

        keyboardView.setVisibility(View.GONE);
        panelContainer.setVisibility(View.VISIBLE);

        panelGrid.removeAllViews();
        panelGrid.setColumnCount(columns);

        for (int i = 0; i < items.length; i++) {
            final String value = items[i];

            TextView key = new TextView(this);
            key.setText(value);
            key.setTextColor(0xFF000000);
            key.setTextSize(value.equals("ABC") ? 14 : 22);
            key.setGravity(Gravity.CENTER);
            key.setBackgroundResource(R.drawable.keyboard_key_background);
            key.setPadding(dp(2), dp(2), dp(2), dp(2));

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(42);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            key.setLayoutParams(params);

            key.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handlePanelKey(value);
                }
            });

            panelGrid.addView(key);
        }
    }

    private void handlePanelKey(String value) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        if ("ABC".equals(value)) {
            hideDynamicPanel();
        } else if ("⌫".equals(value)) {
            deleteOneBanglaCharacter();
        } else {
            ic.commitText(value, 1);
        }
    }

    private void hideDynamicPanel() {
        if (panelContainer != null) {
            panelContainer.setVisibility(View.GONE);
        }

        if (keyboardView != null) {
            keyboardView.setVisibility(View.VISIBLE);
        }
    }

    private void hidePanelOnly() {
        if (panelContainer != null) {
            panelContainer.setVisibility(View.GONE);
        }

        if (keyboardView != null) {
            keyboardView.setVisibility(View.VISIBLE);
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
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
        moveCursorLeft();
    }

    @Override
    public void swipeRight() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT));
        }
    }

    @Override
    public void swipeDown() {
        requestHideSelf(0);
    }

    @Override
    public void swipeUp() {
    }

    @Override
    public void onDestroy() {
        if (backspaceLongRunnable != null) {
            handler.removeCallbacks(backspaceLongRunnable);
        }

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }

        super.onDestroy();
    }
}
