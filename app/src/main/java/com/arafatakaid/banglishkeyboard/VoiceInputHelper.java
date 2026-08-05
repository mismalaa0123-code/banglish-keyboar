package com.arafatakaid.banglishkeyboard;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Toast;

import java.util.ArrayList;

public class VoiceInputHelper {

    private static SpeechRecognizer speechRecognizer;

    public static void startListening(Context context, BanglishIME ime) {
        // মেইন থ্রেডে রান নিশ্চিত করার জন্য Handler ব্যবহার করা হলো
        new Handler(Looper.getMainLooper()).post(() -> {
            if (context == null) return;

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Toast.makeText(context, "Voice recognition available na ei phone e", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                // পূর্বে কোনো রিকগনাইজার চালু থাকলে তা নিরাপদভাবে রিলিজ করা
                releaseRecognizer();

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);

                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD");
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                
                // লাইভ বা চলমান ফলাফল পাওয়ার জন্য এটি যুক্ত করা হয়েছে
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        Toast.makeText(context, "বলুন...", Toast.LENGTH_SHORT).show();
                    }

                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() {}

                    @Override
                    public void onResults(Bundle results) {
                        if (results != null) {
                            ArrayList<String> matches = results.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION);
                            if (matches != null && !matches.isEmpty()) {
                                String text = matches.get(0);
                                if (ime != null) {
                                    // চূড়ান্ত ফলাফল পাঠানোর জন্য
                                    ime.handleVoiceResult(text);
                                }
                            } else {
                                Toast.makeText(context, "কিছু বোঝা যায়নি, আবার চেষ্টা করুন", Toast.LENGTH_SHORT).show();
                            }
                        }
                        releaseRecognizer();
                    }

                    @Override
                    public void onError(int error) {
                        Toast.makeText(context, "Voice error code: " + error, Toast.LENGTH_SHORT).show();
                        releaseRecognizer();
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        // কথা বলার সময় লাইভ বাংলা টেক্সট প্রিভিউ বক্সে দেখানোর জন্য
                        if (partialResults != null) {
                            ArrayList<String> matches = partialResults.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION);
                            if (matches != null && !matches.isEmpty()) {
                                String partialText = matches.get(0);
                                if (ime != null) {
                                    // আপনার BanglishIME ক্লাসে এই লাইভ বাংলা টেক্সট দেখাবে
                                    ime.handleVoicePartialResult(partialText);
                                }
                            }
                        }
                    }

                    @Override public void onEvent(int eventType, Bundle params) {}
                });

                speechRecognizer.startListening(intent);

            } catch (Exception e) {
                Toast.makeText(context, "Voice error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                releaseRecognizer();
            }
        });
    }

    private static void releaseRecognizer() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.destroy();
                } catch (Exception ignored) {}
                speechRecognizer = null;
            }
        });
    }
}
