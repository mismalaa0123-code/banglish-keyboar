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
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class BanglaIME extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private static final String TAG = "BanglaIME";

    private static final int CODE_SYMBOLS = -2;
    private static final int CODE_EMOJI = -100;
    private static final int CODE_LANGUAGE = -101;
    private static final int CODE_CURSOR = -102;

    private KeyboardView keyboardView;
    private Keyboard banglaKeyboard;

    private LinearLayout rootLayout;
    private ScrollView panelScroll;
    private LinearLayout panelContent;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable backspaceLongRunnable;
    private boolean backspaceLongDone = false;

    private SpeechRecognizer speechRecognizer;

    @Override
    public View onCreateInputView() {
        try {
            View view = getLayoutInflater().inflate(R.layout.bangla_keyboard_view, null);

            if (view instanceof LinearLayout) {
                rootLayout = (LinearLayout) view;
            }

            keyboardView = view.findViewById(R.id.keyboard_view);

            if (keyboardView == null) {
                Log.e(TAG, "keyboard_view id not found. Creating programmatic view.");
                return createProgrammaticKeyboardView();
            }

            setupKeyboardView();

            View copyPaste = view.findViewById(R.id.btn_copy_paste);
            if (copyPaste != null) {
                copyPaste.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        handleCopyPaste();
                    }
                });
            }

            View voice = view.findViewById(R.id.btn_voice_input);
            if (voice != null) {
                voice.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startVoiceInput();
                    }
                });
            }

            createPanelIfNeeded();

            return view;

        } catch (Throwable t) {
            Log.e(TAG, "onCreateInputView crash avoided", t);
            return createProgrammaticKeyboardView();
        }
    }

    private View createProgrammaticKeyboardView() {
        try {
            rootLayout = new LinearLayout(this);
            rootLayout.setOrientation(LinearLayout.VERTICAL);
            rootLayout.setBackgroundColor(0xFFF3F4F6);

            LinearLayout toolbar = new LinearLayout(this);
            toolbar.setOrientation(LinearLayout.HORIZONTAL);
            toolbar.setGravity(Gravity.CENTER);
            toolbar.setBackgroundColor(0xFFFFF0F0);
            toolbar.setPadding(dp(6), dp(4), dp(6), dp(4));

            LinearLayout.LayoutParams toolbarParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(52)
            );

            TextView copyPaste = createToolbarText("📋\nকপি/পেস্ট");
            TextView voice = createToolbarText("🎙\nভয়েস ইনপুট");

            toolbar.addView(copyPaste, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
            ));

            View divider = new View(this);
            divider.setBackgroundColor(0xFFD1D5DB);
            toolbar.addView(divider, new LinearLayout.LayoutParams(dp(1), dp(34)));

            toolbar.addView(voice, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
            ));

            rootLayout.addView(toolbar, toolbarParams);

            copyPaste.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleCopyPaste();
                }
            });

            voice.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startVoiceInput();
                }
            });

            keyboardView = new KeyboardView(this, null);
            setupKeyboardView();

            rootLayout.addView(keyboardView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            createPanelIfNeeded();

            return rootLayout;

        } catch (Throwable t) {
            Log.e(TAG, "Programmatic keyboard also failed", t);

            TextView errorView = new TextView(this);
            errorView.setText("বাংলা কিবোর্ড লোড হয়নি। Logcat error পাঠান।");
            errorView.setTextSize(18);
            errorView.setTextColor(0xFF000000);
            errorView.setGravity(Gravity.CENTER);
            errorView.setPadding(dp(12), dp(20), dp(12), dp(20));
            errorView.setBackgroundColor(0xFFFFFFFF);
            return errorView;
        }
    }

    private TextView createToolbarText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF000000);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(false);
        return tv;
    }

    private void setupKeyboardView() {
        banglaKeyboard = new Keyboard(this, R.xml.bangla_keyboard);
        keyboardView.setKeyboard(banglaKeyboard);
        keyboardView.setOnKeyboardActionListener(this);
        keyboardView.setPreviewEnabled(false);
        keyboardView.setBackgroundColor(0xFFF3F4F6);
    }

    private void createPanelIfNeeded() {
        if (rootLayout == null) return;
        if (panelScroll != null) return;

        panelScroll = new ScrollView(this);
        panelScroll.setBackgroundColor(0xFFFFFFFF);
        panelScroll.setVisibility(View.GONE);

        panelContent = new LinearLayout(this);
        panelContent.setOrientation(LinearLayout.VERTICAL);
        panelContent.setPadding(dp(6), dp(6), dp(6), dp(6));

        panelScroll.addView(panelContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        rootLayout.addView(panelScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(245)
        ));
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (primaryCode) {
            case Keyboard.KEYCODE_DELETE:
                // Delete onRelease() থেকে হবে, যাতে long press ও tap একসাথে trigger না হয়।
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

            case 10:
            case Keyboard.KEYCODE_DONE:
                sendEnter();
                break;

            case 32:
                hidePanel();
                ic.commitText(" ", 1);
                break;

            default:
                hidePanel();
                if (primaryCode > 0) {
                    ic.commitText(String.valueOf((char) primaryCode), 1);
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

        CharSequence beforeCursor = ic.getTextBeforeCursor(16, 0);
        int deleteLength = getBanglaDeleteLength(beforeCursor);

        ic.deleteSurroundingText(deleteLength, 0);
    }

    private int getBanglaDeleteLength(CharSequence beforeCursor) {
        if (beforeCursor == null || beforeCursor.length() == 0) {
            return 1;
        }

        int length = beforeCursor.length();
        char last = beforeCursor.charAt(length - 1);

        if (Character.isLowSurrogate(last) && length >= 2) {
            char prev = beforeCursor.charAt(length - 2);
            if (Character.isHighSurrogate(prev)) {
                return 2;
            }
        }

        if (isBanglaMark(last)) {
            return 1;
        }

        if (isBanglaLetter(last) && length >= 2) {
            char prev = beforeCursor.charAt(length - 2);
            if (prev == '\u09CD') {
                return 2;
            }
        }

        return 1;
    }

    private boolean isBanglaLetter(char c) {
        return c >= '\u0980' && c <= '\u09FF';
    }

    private boolean isBanglaMark(char c) {
        return c == '\u0981' || c == '\u0982' || c == '\u0983'
                || c == '\u09BC'
                || c == '\u09BE' || c == '\u09BF' || c == '\u09C0'
                || c == '\u09C1' || c == '\u09C2' || c == '\u09C3'
                || c == '\u09C7' || c == '\u09C8'
                || c == '\u09CB' || c == '\u09CC'
                || c == '\u09CD' || c == '\u09D7';
    }

    private void clearCurrentText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        try {
            ExtractedText extracted = ic.getExtractedText(new ExtractedTextRequest(), 0);

            if (extracted != null && extracted.text != null) {
                int totalLength = extracted.text.length();

                int selectionStart = extracted.selectionStart;
                int selectionEnd = extracted.selectionEnd;

                if (selectionStart < 0) selectionStart = totalLength;
                if (selectionEnd < 0) selectionEnd = totalLength;

                int before = Math.max(selectionStart, selectionEnd);
                int after = totalLength - Math.min(selectionStart, selectionEnd);

                ic.beginBatchEdit();
                ic.deleteSurroundingText(before, after);
                ic.endBatchEdit();
            } else {
                ic.deleteSurroundingText(10000, 10000);
            }
        } catch (Throwable t) {
            Log.e(TAG, "clearCurrentText failed", t);
            ic.deleteSurroundingText(10000, 10000);
        }
    }

    private void handleCopyPaste() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard == null) return;

        CharSequence selected = ic.getSelectedText(0);

        if (selected != null && selected.length() > 0) {
            clipboard.setPrimaryClip(ClipData.newPlainText("bangla_selected_text", selected));
            Toast.makeText(this, "কপি হয়েছে", Toast.LENGTH_SHORT).show();
            return;
        }

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

    private void startVoiceInput() {
        hidePanel();

        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Toast.makeText(this, "ভয়েস ইনপুট সাপোর্ট করছে না", Toast.LENGTH_SHORT).show();
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Microphone permission প্রয়োজন", Toast.LENGTH_LONG).show();
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

                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() {}

                    @Override
                    public void onError(int error) {
                        Toast.makeText(BanglaIME.this, "ভয়েস ইনপুট পাওয়া যায়নি", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onResults(Bundle results) {
                        ArrayList<String> matches =
                                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                        if (matches != null && matches.size() > 0) {
                            InputConnection ic = getCurrentInputConnection();
                            if (ic != null) {
                                ic.commitText(matches.get(0), 1);
                            }
                        }
                    }

                    @Override public void onPartialResults(Bundle partialResults) {}
                    @Override public void onEvent(int eventType, Bundle params) {}
                });
            }

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);

            speechRecognizer.cancel();
            speechRecognizer.startListening(intent);

        } catch (Throwable t) {
            Log.e(TAG, "Voice input failed", t);
            Toast.makeText(this, "ভয়েস ইনপুট চালু করা যায়নি", Toast.LENGTH_SHORT).show();
        }
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
                "😋", "😎", "🤔", "😐", "🙄", "😏", "😢",
                "😭", "😡", "😴", "🤲", "🙏", "👍", "👎",
                "👏", "❤️", "🔥", "🎉", "🌹", "💯", "✅",
                "ABC", "⌫"
        };

        showPanel(emojis, 7);
    }

    private void showPanel(String[] items, int columns) {
        createPanelIfNeeded();

        if (panelScroll == null || panelContent == null) {
            Toast.makeText(this, "Panel load হয়নি", Toast.LENGTH_SHORT).show();
            return;
        }

        if (keyboardView != null) {
            keyboardView.setVisibility(View.GONE);
        }

        panelScroll.setVisibility(View.VISIBLE);
        panelContent.removeAllViews();

        int index = 0;

        while (index < items.length) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            panelContent.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(46)
            ));

            for (int i = 0; i < columns; i++) {
                if (index < items.length) {
                    final String value = items[index];

                    TextView key = new TextView(this);
                    key.setText(value);
                    key.setTextColor(0xFF000000);
                    key.setTextSize("ABC".equals(value) ? 14 : 22);
                    key.setGravity(Gravity.CENTER);
                    key.setPadding(dp(2), dp(2), dp(2), dp(2));

                    try {
                        key.setBackgroundResource(R.drawable.keyboard_key_background);
                    } catch (Throwable t) {
                        key.setBackgroundColor(0xFFFFFFFF);
                    }

                    key.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            handlePanelKey(value);
                        }
                    });

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            0,
                            dp(42),
                            1f
                    );
                    lp.setMargins(dp(3), dp(3), dp(3), dp(3));

                    row.addView(key, lp);
                    index++;
                } else {
                    View empty = new View(this);
                    row.addView(empty, new LinearLayout.LayoutParams(
                            0,
                            dp(42),
                            1f
                    ));
                }
            }
        }
    }

    private void handlePanelKey(String value) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        if ("ABC".equals(value)) {
            hidePanel();
        } else if ("⌫".equals(value)) {
            deleteOneBanglaCharacter();
        } else {
            ic.commitText(value, 1);
        }
    }

    private void hidePanel() {
        if (panelScroll != null) {
            panelScroll.setVisibility(View.GONE);
        }

        if (keyboardView != null) {
            keyboardView.setVisibility(View.VISIBLE);
        }
    }

    private void showLanguagePicker() {
        hidePanel();

        try {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

            if (imm != null) {
                imm.showInputMethodPicker();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Language picker failed", t);
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
