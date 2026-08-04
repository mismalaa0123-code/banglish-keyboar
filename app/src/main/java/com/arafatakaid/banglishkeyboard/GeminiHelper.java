package com.arafatakaid.banglishkeyboard;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiHelper {

    private static final String MODEL = "gemini-2.0-flash";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ResultCallback {
        void onResult(String banglishText);
        void onError(String message);
    }

    public GeminiHelper() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public void convertToBanglish(String banglaText, ResultCallback callback) {
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            callback.onError("API key set kora hoyni. local.properties check koro.");
            return;
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + MODEL + ":generateContent?key=" + apiKey;

        String prompt = "Convert the following Bangla (Bengali) text into 'Banglish' "
                + "— meaning write the exact same words using English letters, "
                + "exactly the way ordinary Bangladeshi people casually type it in "
                + "Facebook Messenger or WhatsApp chats. Do not translate the meaning, "
                + "only transliterate the sound. Return ONLY the Banglish text, "
                + "nothing else — no explanation, no quotes, no extra words.\n\n"
                + "Bangla text: " + banglaText;

        try {
            JSONObject part = new JSONObject().put("text", prompt);
            JSONArray parts = new JSONArray().put(part);
            JSONObject content = new JSONObject().put("parts", parts);
            JSONArray contents = new JSONArray().put(content);
            JSONObject body = new JSONObject().put("contents", contents);

            RequestBody requestBody = RequestBody.create(body.toString(), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            mainHandler.post(() -> callback.onError("API error: " + response.code()));
                            return;
                        }
                        String resBody = response.body().string();
                        JSONObject resJson = new JSONObject(resBody);
                        String text = resJson
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text")
                                .trim();

                        mainHandler.post(() -> callback.onResult(text));
                    } catch (Exception ex) {
                        mainHandler.post(() -> callback.onError("Parse error: " + ex.getMessage()));
                    } finally {
                        response.close();
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("Request build error: " + e.getMessage());
        }
    }
}
