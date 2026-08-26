package com.arafatakaid.banglishkeyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// এখানে VoiceResultListener যোগ করা হয়েছে এরর কাটানোর জন্য
public class BanglishIME extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener, VoiceInputHelper.VoiceResultListener {

    private static final String TAG = "BanglishIME";

    private static final int KEYCODE_MIC = -100;
    private static final int KEYCODE_GLOBE = -101;
    private static final int KEYCODE_SYMBOLS = -102; 
    private static final int KEYCODE_EMOJI = -103;   
    private static final int KEYCODE_DELETE = -5;
    private static final int KEYCODE_SHIFT = -1;
    private static final int KEYCODE_COPY = -201;
    private static final int KEYCODE_PASTE = -202;

    private static final String BANGLA_CHAR_CLASS = "\\u0980-\\u09FF";
    private static final Pattern LAST_BANGLA_TOKEN = Pattern.compile("[" + BANGLA_CHAR_CLASS + "]+");
    private static final Pattern LAST_BANGLA_RUN = Pattern.compile("[" + BANGLA_CHAR_CLASS + "]+(?:\\s+[" + BANGLA_CHAR_CLASS + "]+)*");

    private KeyboardView keyboardView;
    private Keyboard qwertyKeyboard;
    private TextView suggestionText;
    
    private LinearLayout rootLayout;
    private ScrollView panelScroll;
    private LinearLayout panelContent;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            DictionaryHelper.getInstance(this);
            BanglaToBanglishConverter.loadDictionaryFromAssets(this, "dictionary.json");
        } catch (Exception e) {
            Log.e(TAG, "Dictionary initialization failed", e);
        }
    }

    @Override
    public View onCreateInputView() {
        View root = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null);
        if (root instanceof LinearLayout) {
            rootLayout = (LinearLayout) root;
        }

        suggestionText = root.findViewById(R.id.preview_text_view);
        Button convertButton = root.findViewById(R.id.btn_convert);
        Button micButton = root.findViewById(R.id.btn_mic);
        keyboardView = root.findViewById(R.id.keyboard_view);
        FrameLayout bannerContainer = root.findViewById(R.id.startio_banner_container);

        StartIoBannerHelper.attach(this, bannerContainer);

        qwertyKeyboard = new Keyboard(this, R.xml.qwerty);
        keyboardView.setKeyboard(qwertyKeyboard);
        keyboardView.setOnKeyboardActionListener(this);
        keyboardView.setPreviewEnabled(false);

        if (convertButton != null) convertButton.setOnClickListener(v -> convertCurrentText());
        if (micButton != null) micButton.setOnClickListener(v -> startVoiceInput());
        
        if (suggestionText != null) {
            suggestionText.setOnClickListener(v -> {
                String text = suggestionText.getText() != null ? suggestionText.getText().toString().trim() : "";
                if (isUsableVoiceOrPreviewText(text)) processBanglaText(text, 0);
            });
        }

        createPanelIfNeeded();
        return root;
    }

    // --- Voice Input Listeners (নিচে implement করা হলো) ---
    @Override
    public void handleVoiceResult(String recognizedBangla) {
        mainHandler.post(() -> {
            if (suggestionText != null && recognizedBangla != null) {
                suggestionText.setText(recognizedBangla);
            }
        });
    }

    @Override
    public void handleVoicePartialResult(String partialText) {
        mainHandler.post(() -> {
            if (suggestionText != null && partialText != null) {
                suggestionText.setText(partialText);
            }
        });
    }

    // --- Number & Emoji Panel Logic ---
    private void createPanelIfNeeded() {
        if (rootLayout == null || panelScroll != null) return;
        panelScroll = new ScrollView(this);
        panelScroll.setBackgroundColor(0xFFE2E5E9);
        panelScroll.setVisibility(View.GONE);
        panelContent = new LinearLayout(this);
        panelContent.setOrientation(LinearLayout.VERTICAL);
        panelContent.setPadding(dp(5), dp(5), dp(5), dp(5));
        panelScroll.addView(panelContent, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rootLayout.addView(panelScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200)));
    }

    private void showNumberSymbolPanel() {
        String[] symbols = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "@", "#", "$", "%", "&", "-", "+", "(", ")", "/", "*", "\"", "'", ":", ";", "!", "?", ".", ",", "ABC", "⌫"};
        showPanel(symbols, 8);
    }

    private void showEmojiPanel() {
        String[] emojis = {"😀", "😂", "😍", "😘", "😎", "😭", "👍", "❤️", "🔥", "🙏", "👏", "🎉", "🤔", "😢", "🌟", "💯", "ABC", "⌫"};
        showPanel(emojis, 7);
    }

    private void showPanel(String[] items, int columns) {
        if (panelScroll == null || keyboardView == null) return;
        keyboardView.setVisibility(View.GONE);
        panelScroll.setVisibility(View.VISIBLE);
        panelContent.removeAllViews();
        int index = 0;
        while (index < items.length) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            panelContent.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(45)));
            for (int i = 0; i < columns; i++) {
                if (index < items.length) {
                    final String val = items[index++];
                    TextView key = new TextView(this);
                    key.setText(val);
                    key.setGravity(Gravity.CENTER);
                    key.setTextColor(0xFF202124);
                    key.setTextSize(val.length() > 1 ? 14 : 20);
                    key.setBackgroundResource(R.drawable.keyboard_key_background);
                    key.setOnClickListener(v -> {
                        if ("ABC".equals(val)) hidePanel();
                        else if ("⌫".equals(val)) getCurrentInputConnection().deleteSurroundingText(1, 0);
                        else getCurrentInputConnection().commitText(val, 1);
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
                    lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                    row.addView(key, lp);
                } else {
                    row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(40), 1f));
                }
            }
        }
    }

    private void hidePanel() {
        if (panelScroll != null) panelScroll.setVisibility(View.GONE);
        if (keyboardView != null) keyboardView.setVisibility(View.VISIBLE);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        try {
            switch (primaryCode) {
                case KEYCODE_SYMBOLS: showNumberSymbolPanel(); break;
                case KEYCODE_EMOJI: showEmojiPanel(); break;
                case KEYCODE_MIC: startVoiceInput(); break;
                case KEYCODE_GLOBE: hidePanel(); showKeyboardPicker(); break;
                case KEYCODE_DELETE: hidePanel(); ic.deleteSurroundingText(1, 0); break;
                case KEYCODE_COPY: copySelectedText(ic); break;
                case KEYCODE_PASTE: pasteClipboard(ic); break;
                case KEYCODE_SHIFT:
                    hidePanel();
                    if (qwertyKeyboard != null) {
                        qwertyKeyboard.setShifted(!qwertyKeyboard.isShifted());
                        if (keyboardView != null) keyboardView.invalidateAllKeys();
                    }
                    break;
                case 10: hidePanel(); ic.commitText("\n", 1); break;
                case 32: hidePanel(); ic.commitText(" ", 1); break;
                default:
                    hidePanel();
                    if (primaryCode >= 0 && primaryCode <= Character.MAX_VALUE) {
                        char character = (char) primaryCode;
                        if (qwertyKeyboard != null && qwertyKeyboard.isShifted() && Character.isLetter(character)) {
                            character = Character.toUpperCase(character);
                            qwertyKeyboard.setShifted(false);
                            if (keyboardView != null) keyboardView.invalidateAllKeys();
                        }
                        ic.commitText(String.valueOf(character), 1);
                    }
                    break;
            }
        } catch (Exception e) { Log.e(TAG, "Key error", e); }
    }

    private void convertCurrentText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(1000, 0);
        if (before != null && before.length() > 0) {
            String fullBefore = before.toString();
            String lastRun = extractLastBanglaRun(fullBefore);
            if (!lastRun.isEmpty()) {
                int runLength = findLastBanglaRunLength(fullBefore, lastRun);
                if (runLength > 0) { processBanglaText(lastRun, runLength); return; }
            }
        }
    }

    private void processBanglaText(String banglaText, int banglaLength) {
        String cleanText = banglaText != null ? banglaText.trim() : "";
        if (cleanText.isEmpty()) return;
        String result = null;
        try { result = DictionaryHelper.getInstance(this).lookup(cleanText); } catch (Exception ignored) {}
        if (result == null || result.trim().isEmpty()) {
            try { result = BanglaToBanglishConverter.convert(cleanText); } catch (Exception ignored) {}
        }
        commitBanglish(result != null ? result : cleanText, banglaLength);
    }

    private void commitBanglish(String banglishText, int banglaLength) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || banglishText == null) return;
        if (banglaLength > 0) ic.deleteSurroundingText(banglaLength, 0);
        ic.commitText(banglishText.trim() + " ", 1);
        if (suggestionText != null) suggestionText.setText("এখানে বাংলা দেখা যাবে...");
    }

    private void showKeyboardPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    private void copySelectedText(InputConnection ic) {
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("Banglish", selected));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void pasteClipboard(InputConnection ic) {
        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cb.hasPrimaryClip()) {
            CharSequence txt = cb.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (txt != null) ic.commitText(txt, 1);
        }
    }

    private void startVoiceInput() {
        if (suggestionText != null) suggestionText.setText("🎤 Listening...");
        try { VoiceInputHelper.startListening(this, this); } catch (Exception ignored) {}
    }

    private static String extractLastBanglaRun(String text) {
        if (text == null) return "";
        Matcher matcher = LAST_BANGLA_RUN.matcher(text);
        String last = "";
        while (matcher.find()) last = matcher.group();
        return last != null ? last.trim() : "";
    }

    private static String extractLastBanglaToken(String text) {
        if (text == null) return "";
        Matcher matcher = LAST_BANGLA_TOKEN.matcher(text);
        String last = "";
        while (matcher.find()) last = matcher.group();
        return last;
    }

    private static int findLastBanglaRunLength(String text, String run) {
        if (text == null || run == null || run.isEmpty()) return 0;
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) end--;
        int start = end;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (isBanglaChar(c)) start--;
            else if (Character.isWhitespace(c)) {
                int j = start - 1;
                while (j >= 0 && Character.isWhitespace(text.charAt(j))) j--;
                if (j >= 0 && isBanglaChar(text.charAt(j))) {
                    start = j + 1;
                    while (start > 0 && isBanglaChar(text.charAt(start - 1))) start--;
                } else break;
            } else break;
        }
        return Math.max(0, end - start);
    }
    
    private static boolean isBanglaChar(char c) { return c >= '\u0980' && c <= '\u09FF'; }

    private boolean isUsableVoiceOrPreviewText(String t) {
        return t != null && !t.trim().isEmpty() && !t.contains("Listening") && !t.contains("এখানে বাংলা");
    }

    @Override public void onText(CharSequence text) { if (text != null) getCurrentInputConnection().commitText(text, 1); }
    @Override public void onPress(int pc) {}
    @Override public void onRelease(int pc) {}
    @Override public void swipeLeft() {}
    @Override public void swipeRight() {}
    @Override public void swipeDown() {}
    @Override public void swipeUp() {}
}
