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
        if (context == null) return;

        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Toast.makeText(context, "Voice recognition available na ei phone e", Toast.LENGTH_SHORT).show();
                    return;
                }

                // আগের কোনো সেশন চালু থাকলে তা রিলিজ করা
                if (speechRecognizer != null) {
                    try {
                        speechRecognizer.destroy();
                    } catch (Exception ignored) {}
                    speechRecognizer = null;
                }

                // ১. স্পিচ রিকগনাইজার তৈরি
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);

                // ২. লিসনার সেট করা (শুরু করার আগে অবশ্যই লিসনার দিতে হবে)
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        mainHandler.post(() -> Toast.makeText(context, "বলুন...", Toast.LENGTH_SHORT).show());
                    }

                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() {}

                    @Override
                    public void onError(int error) {
                        mainHandler.post(() -> {
                            if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                                Toast.makeText(context, "Voice error: " + error, Toast.LENGTH_SHORT).show();
                            }
                        });
                        releaseRecognizer();
                    }

                    @Override
                    public void onResults(Bundle results) {
                        if (results != null) {
                            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                            if (matches != null && !matches.isEmpty()) {
                                String text = matches.get(0);
                                if (ime != null) {
                                    ime.handleVoiceResult(text);
                                }
                            }
                        }
                        releaseRecognizer();
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        if (partialResults != null) {
                            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                            if (matches != null && !matches.isEmpty()) {
                                String partialText = matches.get(0);
                                if (ime != null) {
                                    // লাইভ বাংলা টেক্সট প্রিভিউ বারে পাঠাবে
                                    ime.handleVoicePartialResult(partialText);
                                }
                            }
                        }
                    }

                    @Override public void onEvent(int eventType, Bundle params) {}
                });

                // ৩. ইনটেন্ট কনফিগারেশন
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD");
                intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

                // ৪. লিসনিং শুরু করা
                speechRecognizer.startListening(intent);

            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                releaseRecognizer();
            }
        });
    }

    private static void releaseRecognizer() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.stopListening();
                    speechRecognizer.destroy();
                } catch (Exception ignored) {}
                speechRecognizer = null;
            }
        });
    }
}
