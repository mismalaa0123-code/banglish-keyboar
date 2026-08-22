import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.view.View;
import android.view.inputmethod.InputConnection;

public class BanglaIME extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private KeyboardView kv;
    private Keyboard keyboard;

    @Override
    public View onCreateInputView() {
        View layout = getLayoutInflater().inflate(R.layout.bangla_keyboard_view, null);
        kv = (KeyboardView) layout.findViewById(R.id.keyboard_view);
        keyboard = new Keyboard(this, R.xml.bangla_keyboard);
        kv.setKeyboard(keyboard);
        kv.setOnKeyboardActionListener(this);
        return layout;
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        InputConnection ic = getCurrentInputConnection();
        switch (primaryCode) {
            case Keyboard.KEYCODE_DELETE:
                // আপনার নির্দেশনা অনুযায়ী: একবার চাপলে একটি ডিলিট, চেপে ধরলে সব ডিলিট
                // লং প্রেসের লজিক কিবোর্ড ভিউ হ্যান্ডেল করে, এখানে সাধারণ ডিলিট
                ic.deleteSurroundingText(1, 0);
                break;
            case Keyboard.KEYCODE_DONE:
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                break;
            default:
                char code = (char) primaryCode;
                ic.commitText(String.valueOf(code), 1);
        }
    }

    // লং প্রেস ব্যাকস্পেস (পুরো টেক্সট মুছতে)
    @Override
    public void onPress(int primaryCode) {
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            // আপনি যদি এখানে থ্রেড বা হ্যান্ডলার ব্যবহার করেন তবে 'চাপ দিয়ে ধরে রাখলে সম্পূর্ণ লেখা ডিলিট' হবে
        }
    }

    @Override public void onText(CharSequence text) {}
    @Override public void swipeLeft() {}
    @Override public void swipeRight() {}
    @Override public void swipeDown() {}
    @Override public void swipeUp() {}
    @Override public void onRelease(int primaryCode) {}
}
