package com.arafatakaid.banglishkeyboard;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DictionaryHelper {

    private static final String TAG = "DictionaryHelper";

    private Map<String, String> dictionary = new HashMap<>();
    private static DictionaryHelper instance;

    private DictionaryHelper(Context context) {
        loadDictionary(context);
    }

    public static synchronized DictionaryHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DictionaryHelper(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Normalize a key: trim, NFC normalize and remove common invisible characters
     */
    private static String normalizeKey(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFC);
        // Remove common invisible/zero-width characters (zero-width space, BOM)
        return n.replace("\u200B", "").replace("\uFEFF", "");
    }

    private void loadDictionary(Context context) {
        AssetManager am = context.getAssets();
        // try-with-resources ensures streams are closed properly
        try (InputStream is = am.open("dictionary.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JSONObject json = new JSONObject(sb.toString());
            Iterator<String> keys = json.keys();
            int added = 0;
            while (keys.hasNext()) {
                String key = keys.next();
                String normalizedKey = normalizeKey(key);
                if (normalizedKey == null || normalizedKey.isEmpty()) continue;
                String value = json.optString(key, "");
                dictionary.put(normalizedKey, value);
                added++;
            }

            Log.d(TAG, "Loaded dictionary entries: " + added + " (map size: " + dictionary.size() + ")");
        } catch (IOException e) {
            Log.e(TAG, "IOException while loading dictionary: " + e.getMessage());
            e.printStackTrace();
        } catch (JSONException e) {
            Log.e(TAG, "JSONException while parsing dictionary.json: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String lookup(String banglaText) {
        if (banglaText == null) return null;
        String key = normalizeKey(banglaText);
        if (key == null || key.isEmpty()) return null;
        return dictionary.get(key);
    }

    public boolean contains(String banglaText) {
        String key = normalizeKey(banglaText);
        if (key == null || key.isEmpty()) return false;
        return dictionary.containsKey(key);
    }

    // ডিকশনারিতে মোট কতগুলো শব্দ আছে তা জানার মেথড
    public int getWordCount() {
        if (dictionary != null) {
            return dictionary.size();
        }
        return 0;
    }
}
