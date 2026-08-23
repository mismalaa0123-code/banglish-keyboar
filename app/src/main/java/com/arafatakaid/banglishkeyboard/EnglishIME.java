package com.arafatakaid.banglishkeyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnglishIME extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    /*
     * english_qwerty.xml-এর Custom Key Code
     */
    private static final int KEYCODE_GLOBE = -100;
    private static final int KEYCODE_NUMBER = -200;
    private static final int KEYCODE_EMOJI = -201;

    /*
     * Emoji key-গুলোর জন্য আলাদা code range।
     */
    private static final int EMOJI_CODE_START = -1000;

    /*
     * Backspace long press সময়।
     */
    private static final long BACKSPACE_LONG_PRESS_TIME = 700L;

    private KeyboardView keyboardView;
    private Keyboard englishKeyboard;

    private final Handler handler = new Handler();

    private boolean isNumberMode = false;
    private boolean isEmojiMode = false;
    private boolean isLongPressDelete = false;
    private boolean deleteKeyPressed = false;

    private Runnable clearAllRunnable;

    /*
     * Original keyboard letters সংরক্ষণ করবে,
     * যাতে Number/Emoji mode থেকে আবার English mode-এ ফেরা যায়।
     */
    private final List<Keyboard.Key> letterKeys = new ArrayList<>();
    private final Map<Keyboard.Key, Integer> originalCodes = new HashMap<>();
    private final Map<Keyboard.Key, CharSequence> originalLabels = new HashMap<>();

    /*
     * Emoji code অনুযায়ী কোন emoji insert হবে।
     */
    private final Map<Integer, String> emojiMap = new HashMap<>();

    @Override
    public View onCreateInputView() {

        View root = LayoutInflater.from(this).inflate(
                R.layout.english_keyboard_view,
                null
        );

        keyboardView = root.findViewById(
                R.id.english_keyboard_view
        );

        /*
         * Start.io Banner
         * এই অংশ অপরিবর্তিত রাখা হয়েছে।
         */
        FrameLayout bannerContainer = root.findViewById(
                R.id.startio_banner_container
        );

        StartIoBannerHelper.attach(
                this,
                bannerContainer
        );

        /*
         * English Keyboard
         */
        englishKeyboard = new Keyboard(
                this,
                R.xml.english_qwerty
        );

        keyboardView.setKeyboard(
                englishKeyboard
        );

        keyboardView.setOnKeyboardActionListener(
                this
        );

        keyboardView.setPreviewEnabled(
                false
        );

        saveOriginalLetterKeys();

        /*
         * Copy / Paste toolbar button
         */
        View copyPasteButton = root.findViewById(
                R.id.btn_english_copy_paste
        );

        if (copyPasteButton != null) {
            copyPasteButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            handleCopyOrPaste();
                        }
                    }
            );
        }

        return root;
    }

    /*
     * English letter key-গুলোর original code এবং label save করবে।
     */
    private void saveOriginalLetterKeys() {

        letterKeys.clear();
        originalCodes.clear();
        originalLabels.clear();

        List<Keyboard.Key> keys = englishKeyboard.getKeys();

        for (Keyboard.Key key : keys) {

            if (key.codes == null || key.codes.length == 0) {
                continue;
            }

            int code = key.codes[0];

            if (code >= 'a' && code <= 'z') {
                letterKeys.add(key);
                originalCodes.put(key, code);
                originalLabels.put(key, key.label);
            }
        }
    }

    @Override
    public void onKey(
            int primaryCode,
            int[] keyCodes
    ) {

        InputConnection inputConnection =
                getCurrentInputConnection();

        if (inputConnection == null) {
            return;
        }

        try {
            switch (primaryCode) {

                case Keyboard.KEYCODE_DELETE:

                    if (!isLongPressDelete) {
                        inputConnection.deleteSurroundingText(
                                1,
                                0
                        );
                    }

                    break;

                case Keyboard.KEYCODE_SHIFT:

                    /*
                     * Number অথবা Emoji mode-এ Shift কাজ করবে না।
                     */
                    if (!isNumberMode && !isEmojiMode) {
                        englishKeyboard.setShifted(
                                !englishKeyboard.isShifted()
                        );

                        keyboardView.invalidateAllKeys();
                    }

                    break;

                case Keyboard.KEYCODE_DONE:

                    sendEnter(inputConnection);

                    break;

                case 10:

                    sendEnter(inputConnection);

                    break;

                case KEYCODE_GLOBE:

                    showKeyboardPicker();

                    break;

                case KEYCODE_NUMBER:

                    toggleNumberMode();

                    break;

                case KEYCODE_EMOJI:

                    toggleEmojiMode();

                    break;

                default:

                    /*
                     * Emoji mode থেকে emoji নির্বাচন।
                     */
                    if (emojiMap.containsKey(primaryCode)) {
                        String emoji = emojiMap.get(primaryCode);

                        if (emoji != null) {
                            inputConnection.commitText(
                                    emoji,
                                    1
                            );
                        }

                        return;
                    }

                    /*
                     * সাধারণ letter / number / symbol input।
                     */
                    if (primaryCode >= 0
                            && primaryCode <= Character.MAX_VALUE) {

                        char character = (char) primaryCode;

                        if (!isNumberMode
                                && !isEmojiMode
                                && englishKeyboard.isShifted()
                                && Character.isLetter(character)) {

                            character = Character.toUpperCase(
                                    character
                            );
                        }

                        inputConnection.commitText(
                                String.valueOf(character),
                                1
                        );

                        /*
                         * একবার Shift দিয়ে letter লেখার পর
                         * lowercase-এ ফিরে যাবে।
                         */
                        if (!isNumberMode
                                && !isEmojiMode
                                && englishKeyboard.isShifted()
                                && Character.isLetter(character)) {

                            englishKeyboard.setShifted(false);
                            keyboardView.invalidateAllKeys();
                        }
                    }

                    break;
            }

        } catch (Exception ignored) {
            /*
             * Keyboard crash বন্ধ রাখার জন্য।
             */
        }
    }

    /*
     * Enter পাঠানো।
     */
    private void sendEnter(
            InputConnection inputConnection
    ) {
        try {
            inputConnection.sendKeyEvent(
                    new KeyEvent(
                            KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_ENTER
                    )
            );

            inputConnection.sendKeyEvent(
                    new KeyEvent(
                            KeyEvent.ACTION_UP,
                            KeyEvent.KEYCODE_ENTER
                    )
            );

        } catch (Exception ignored) {
            inputConnection.commitText("\n", 1);
        }
    }

    /*
     * Number/Symbol mode চালু বা বন্ধ।
     */
    private void toggleNumberMode() {

        if (isNumberMode) {
            restoreEnglishKeys();
            return;
        }

        isNumberMode = true;
        isEmojiMode = false;

        englishKeyboard.setShifted(false);

        /*
         * 26টি letter key-এর জায়গায় Number/Symbol বসানো হবে।
         */
        String[] symbols = {
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
                "@", "#", "$", "%", "&", "-", "+", "(", ")", "/",
                "=", "*", "\"", "'", ":", ";"
        };

        for (int i = 0; i < letterKeys.size(); i++) {

            Keyboard.Key key = letterKeys.get(i);

            if (i < symbols.length) {
                String symbol = symbols[i];

                key.label = symbol;
                key.codes[0] = symbol.charAt(0);
            }
        }

        keyboardView.invalidateAllKeys();
    }

    /*
     * Emoji mode চালু বা বন্ধ।
     */
    private void toggleEmojiMode() {

        if (isEmojiMode) {
            restoreEnglishKeys();
            return;
        }

        isEmojiMode = true;
        isNumberMode = false;

        englishKeyboard.setShifted(false);

        emojiMap.clear();

        String[] emojis = {
                "😀", "😁", "😂", "🤣", "😊", "😍", "😘", "😎", "😭", "🔥",
                "👍", "👎", "❤️", "🙏", "🎉", "👏", "🤔", "😢", "😡", "😴",
                "😇", "🥰", "🤩", "🤗", "😜", "💯"
        };

        for (int i = 0; i < letterKeys.size(); i++) {

            Keyboard.Key key = letterKeys.get(i);

            if (i < emojis.length) {

                int emojiCode = EMOJI_CODE_START - i;

                key.label = emojis[i];
                key.codes[0] = emojiCode;

                emojiMap.put(
                        emojiCode,
                        emojis[i]
                );
            }
        }

        keyboardView.invalidateAllKeys();
    }

    /*
     * Number অথবা Emoji mode থেকে আবার QWERTY English layout ফিরিয়ে আনে।
     */
    private void restoreEnglishKeys() {

        isNumberMode = false;
        isEmojiMode = false;

        emojiMap.clear();

        for (Keyboard.Key key : letterKeys) {

            Integer originalCode = originalCodes.get(key);
            CharSequence originalLabel = originalLabels.get(key);

            if (originalCode != null) {
                key.codes[0] = originalCode;
            }

            key.label = originalLabel;
        }

        englishKeyboard.setShifted(false);

        keyboardView.invalidateAllKeys();
    }

    /*
     * Globe button চাপলে সব keyboard-এর list আসবে।
     */
    private void showKeyboardPicker() {

        try {
            InputMethodManager inputMethodManager =
                    (InputMethodManager) getSystemService(
                            Context.INPUT_METHOD_SERVICE
                    );

            if (inputMethodManager != null) {
                inputMethodManager.showInputMethodPicker();
            }

        } catch (Exception ignored) {
        }
    }

    /*
     * Copy/Paste:
     *
     * Selected text থাকলে Copy।
     * Selection না থাকলে Clipboard থেকে Paste।
     */
    private void handleCopyOrPaste() {

        InputConnection inputConnection =
                getCurrentInputConnection();

        if (inputConnection == null) {
            return;
        }

        ClipboardManager clipboardManager =
                (ClipboardManager) getSystemService(
                        Context.CLIPBOARD_SERVICE
                );

        if (clipboardManager == null) {
            return;
        }

        CharSequence selectedText = null;

        try {
            if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.LOLLIPOP) {

                selectedText = inputConnection.getSelectedText(0);
            }
        } catch (Exception ignored) {
        }

        /*
         * Text selected থাকলে Copy করবে।
         */
        if (selectedText != null
                && selectedText.length() > 0) {

            ClipData clipData = ClipData.newPlainText(
                    "English Keyboard Text",
                    selectedText
            );

            clipboardManager.setPrimaryClip(
                    clipData
            );

            Toast.makeText(
                    this,
                    "Copied",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        /*
         * Selected text না থাকলে Paste করবে।
         */
        try {
            if (clipboardManager.hasPrimaryClip()
                    && clipboardManager.getPrimaryClip() != null
                    && clipboardManager.getPrimaryClip()
                    .getItemCount() > 0) {

                ClipData.Item item =
                        clipboardManager.getPrimaryClip()
                                .getItemAt(0);

                CharSequence pasteText =
                        item.coerceToText(this);

                if (pasteText != null
                        && pasteText.length() > 0) {

                    inputConnection.commitText(
                            pasteText,
                            1
                    );

                    Toast.makeText(
                            this,
                            "Pasted",
                            Toast.LENGTH_SHORT
                    ).show();
                }

            } else {
                Toast.makeText(
                        this,
                        "Clipboard is empty",
                        Toast.LENGTH_SHORT
                ).show();
            }

        } catch (Exception ignored) {
            Toast.makeText(
                    this,
                    "Paste failed",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    /*
     * Backspace চেপে ধরলে সম্পূর্ণ current text clear করবে।
     */
    private void clearAllText() {

        InputConnection inputConnection =
                getCurrentInputConnection();

        if (inputConnection == null) {
            return;
        }

        try {
            inputConnection.beginBatchEdit();

            inputConnection.deleteSurroundingText(
                    100000,
                    100000
            );

            inputConnection.endBatchEdit();

        } catch (Exception ignored) {
        }
    }

    @Override
    public void onPress(int primaryCode) {

        if (primaryCode == Keyboard.KEYCODE_DELETE) {

            deleteKeyPressed = true;
            isLongPressDelete = false;

            clearAllRunnable = new Runnable() {
                @Override
                public void run() {

                    if (deleteKeyPressed) {
                        isLongPressDelete = true;
                        clearAllText();
                    }
                }
            };

            handler.postDelayed(
                    clearAllRunnable,
                    BACKSPACE_LONG_PRESS_TIME
            );
        }
    }

    @Override
    public void onRelease(int primaryCode) {

        if (primaryCode == Keyboard.KEYCODE_DELETE) {

            deleteKeyPressed = false;

            if (clearAllRunnable != null) {
                handler.removeCallbacks(
                        clearAllRunnable
                );
            }
        }
    }

    @Override
    public void onText(CharSequence text) {

        if (text == null || text.length() == 0) {
            return;
        }

        InputConnection inputConnection =
                getCurrentInputConnection();

        if (inputConnection != null) {
            inputConnection.commitText(
                    text,
                    1
            );
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
    public void onDestroy() {

        if (clearAllRunnable != null) {
            handler.removeCallbacks(
                    clearAllRunnable
            );
        }

        super.onDestroy();
    }
}
