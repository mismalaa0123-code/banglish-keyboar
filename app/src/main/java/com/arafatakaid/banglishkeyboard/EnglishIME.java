package com.arafatakaid.banglishkeyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class EnglishIME extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    private static final int KEYCODE_GLOBE = -100;
    private static final int KEYCODE_SYMBOLS = -101;
    private static final int KEYCODE_EMOJI = -102;

    private KeyboardView keyboardView;
    private Keyboard englishKeyboard;

    private LinearLayout rootLayout;
    private ScrollView panelScroll;
    private LinearLayout panelContent;

    @Override
    public View onCreateInputView() {

        View root;

        try {
            root = LayoutInflater.from(this).inflate(
                    R.layout.english_keyboard_view,
                    null
            );
        } catch (Exception e) {
            return createFallbackKeyboardView();
        }

        if (root instanceof LinearLayout) {
            rootLayout = (LinearLayout) root;
        }

        keyboardView = root.findViewById(R.id.english_keyboard_view);

        if (keyboardView == null) {
            return createFallbackKeyboardView();
        }

        // ==========================================
        // Start.io Banner - untouched but safe
        // ==========================================

        try {
            FrameLayout bannerContainer =
                    root.findViewById(R.id.startio_banner_container);

            if (bannerContainer != null) {
                StartIoBannerHelper.attach(this, bannerContainer);
            }
        } catch (Exception ignored) {
            // Banner/monetization crash prevent
        }

        // ==========================================
        // English Keyboard Setup
        // ==========================================

        boolean keyboardLoaded = setupKeyboard();

        if (!keyboardLoaded) {
            Toast.makeText(
                    this,
                    "English keyboard layout load failed",
                    Toast.LENGTH_SHORT
            ).show();
        }

        // ==========================================
        // Copy/Paste Toolbar
        // ==========================================

        setupCopyPasteButton(root);

        createPanelIfNeeded();

        return root;
    }

    private View createFallbackKeyboardView() {

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(0xFFEAF5E4);

        TextView copyPaste = new TextView(this);
        copyPaste.setText("📋  Copy/Paste");
        copyPaste.setGravity(Gravity.CENTER);
        copyPaste.setTextColor(0xFF111111);
        copyPaste.setTextSize(13);
        copyPaste.setBackgroundColor(0xFFFFF1F2);
        copyPaste.setTypeface(null, android.graphics.Typeface.BOLD);

        rootLayout.addView(
                copyPaste,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(42)
                )
        );

        copyPaste.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleCopyPaste();
            }
        });

        keyboardView = new KeyboardView(this, null);
        keyboardView.setBackgroundColor(0xFFEAF5E4);
        keyboardView.setPreviewEnabled(false);

        rootLayout.addView(
                keyboardView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        setupKeyboard();
        createPanelIfNeeded();

        return rootLayout;
    }

    private boolean setupKeyboard() {

        try {
            englishKeyboard = new Keyboard(
                    this,
                    R.xml.english_qwerty
            );

            keyboardView.setKeyboard(englishKeyboard);
            keyboardView.setOnKeyboardActionListener(this);
            keyboardView.setPreviewEnabled(false);
            keyboardView.setVisibility(View.VISIBLE);

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private void setupCopyPasteButton(View root) {

        try {
            int copyPasteId = getResources().getIdentifier(
                    "btn_english_copy_paste",
                    "id",
                    getPackageName()
            );

            if (copyPasteId != 0) {
                View copyPasteButton = root.findViewById(copyPasteId);

                if (copyPasteButton != null) {
                    copyPasteButton.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    handleCopyPaste();
                                }
                            }
                    );
                }
            }
        } catch (Exception ignored) {
            // If toolbar id missing, keyboard still works
        }
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {

        InputConnection ic = getCurrentInputConnection();

        if (ic == null) {
            return;
        }

        try {

            switch (primaryCode) {

                case Keyboard.KEYCODE_DELETE:
                    hidePanel();
                    ic.deleteSurroundingText(1, 0);
                    break;

                case Keyboard.KEYCODE_SHIFT:
                    hidePanel();

                    if (englishKeyboard != null && keyboardView != null) {
                        englishKeyboard.setShifted(!englishKeyboard.isShifted());
                        keyboardView.invalidateAllKeys();
                    }
                    break;

                case Keyboard.KEYCODE_DONE:
                case 10:
                    hidePanel();
                    ic.commitText("\n", 1);
                    break;

                case KEYCODE_SYMBOLS:
                    showNumberSymbolPanel();
                    break;

                case KEYCODE_EMOJI:
                    showEmojiPanel();
                    break;

                case KEYCODE_GLOBE:
                    hidePanel();
                    showKeyboardPicker();
                    break;

                case 32:
                    hidePanel();
                    ic.commitText(" ", 1);
                    break;

                default:
                    hidePanel();

                    if (primaryCode >= 0 && primaryCode <= Character.MAX_VALUE) {

                        char character = (char) primaryCode;

                        if (englishKeyboard != null
                                && englishKeyboard.isShifted()
                                && Character.isLetter(character)) {

                            character = Character.toUpperCase(character);
                        }

                        ic.commitText(String.valueOf(character), 1);

                        if (englishKeyboard != null
                                && keyboardView != null
                                && englishKeyboard.isShifted()) {

                            englishKeyboard.setShifted(false);
                            keyboardView.invalidateAllKeys();
                        }
                    }
                    break;
            }

        } catch (Exception ignored) {
            // Prevent keyboard service crash
        }
    }

    private void handleCopyPaste() {

        try {
            InputConnection ic = getCurrentInputConnection();

            if (ic == null) {
                return;
            }

            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

            if (clipboard == null) {
                return;
            }

            CharSequence selectedText = ic.getSelectedText(0);

            if (selectedText != null && selectedText.length() > 0) {

                clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                                "english_selected_text",
                                selectedText
                        )
                );

                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
                return;
            }

            if (clipboard.hasPrimaryClip()
                    && clipboard.getPrimaryClip() != null
                    && clipboard.getPrimaryClip().getItemCount() > 0) {

                CharSequence pasteText =
                        clipboard.getPrimaryClip()
                                .getItemAt(0)
                                .coerceToText(this);

                if (pasteText != null && pasteText.length() > 0) {
                    ic.commitText(pasteText, 1);
                } else {
                    Toast.makeText(this, "Nothing to paste", Toast.LENGTH_SHORT).show();
                }

            } else {
                Toast.makeText(this, "Clipboard empty", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception ignored) {
            // Safe clipboard handling
        }
    }

    private void createPanelIfNeeded() {

        try {
            if (rootLayout == null) {
                return;
            }

            if (panelScroll != null) {
                return;
            }

            panelScroll = new ScrollView(this);
            panelScroll.setBackgroundColor(0xFFEAF5E4);
            panelScroll.setVisibility(View.GONE);

            panelContent = new LinearLayout(this);
            panelContent.setOrientation(LinearLayout.VERTICAL);
            panelContent.setPadding(
                    dp(5),
                    dp(5),
                    dp(5),
                    dp(5)
            );

            panelScroll.addView(
                    panelContent,
                    new ScrollView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );

            rootLayout.addView(
                    panelScroll,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(205)
                    )
            );

        } catch (Exception ignored) {
            // Prevent panel creation crash
        }
    }

    private void showNumberSymbolPanel() {

        String[] symbols = new String[]{
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
                "@", "#", "$", "%", "&", "-", "+", "(", ")", "/",
                "=", "*", "\"", "'", ":", ";", "!", "?", ".", ",",
                "ABC", "⌫"
        };

        showPanel(symbols, 10);
    }

    private void showEmojiPanel() {

        String[] emojis = new String[]{
                "😀", "😃", "😄", "😁", "😆", "😅", "😂",
                "🤣", "😊", "🙂", "😉", "😍", "😘", "😎",
                "🤔", "🙄", "😐", "😢", "😭", "😡", "😴",
                "👍", "👎", "🙏", "👏", "❤️", "🔥", "🎉",
                "🌹", "💯", "✅", "❌", "⭐", "✨", "💔",
                "ABC", "⌫"
        };

        showPanel(emojis, 7);
    }

    private void showPanel(String[] items, int columns) {

        try {
            createPanelIfNeeded();

            if (panelScroll == null || panelContent == null || keyboardView == null) {
                return;
            }

            keyboardView.setVisibility(View.GONE);
            panelScroll.setVisibility(View.VISIBLE);

            panelContent.removeAllViews();

            int index = 0;

            while (index < items.length) {

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);

                panelContent.addView(
                        row,
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                dp(40)
                        )
                );

                for (int i = 0; i < columns; i++) {

                    if (index < items.length) {

                        final String value = items[index];

                        TextView key = new TextView(this);
                        key.setText(value);
                        key.setTextColor(0xFF111111);
                        key.setGravity(Gravity.CENTER);
                        key.setTextSize(
                                "ABC".equals(value) || "⌫".equals(value) ? 14 : 20
                        );
                        key.setPadding(
                                dp(2),
                                dp(2),
                                dp(2),
                                dp(2)
                        );

                        try {
                            key.setBackgroundResource(R.drawable.keyboard_key_background);
                        } catch (Exception ignored) {
                            key.setBackgroundColor(0xFFFFFFFF);
                        }

                        key.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                handlePanelKey(value);
                            }
                        });

                        LinearLayout.LayoutParams keyParams =
                                new LinearLayout.LayoutParams(
                                        0,
                                        dp(36),
                                        1f
                                );

                        keyParams.setMargins(
                                dp(2),
                                dp(2),
                                dp(2),
                                dp(2)
                        );

                        row.addView(key, keyParams);

                        index++;

                    } else {

                        View empty = new View(this);

                        row.addView(
                                empty,
                                new LinearLayout.LayoutParams(
                                        0,
                                        dp(36),
                                        1f
                                )
                        );
                    }
                }
            }

        } catch (Exception ignored) {
            // Prevent panel crash
        }
    }

    private void handlePanelKey(String value) {

        InputConnection ic = getCurrentInputConnection();

        if (ic == null || value == null) {
            return;
        }

        if ("ABC".equals(value)) {
            hidePanel();
        } else if ("⌫".equals(value)) {
            ic.deleteSurroundingText(1, 0);
        } else {
            ic.commitText(value, 1);
        }
    }

    private void hidePanel() {

        try {
            if (panelScroll != null) {
                panelScroll.setVisibility(View.GONE);
            }

            if (keyboardView != null) {
                keyboardView.setVisibility(View.VISIBLE);
            }
        } catch (Exception ignored) {
        }
    }

    private void showKeyboardPicker() {

        try {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

            if (imm != null) {
                imm.showInputMethodPicker();
            }
        } catch (Exception ignored) {
        }
    }

    private int dp(int value) {

        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
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
