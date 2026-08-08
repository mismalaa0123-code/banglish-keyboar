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

    private static volatile DictionaryHelper instance;

    private final Map<String, String> dictionary = new HashMap<>();
    private volatile boolean loaded = false;

    private DictionaryHelper(Context context) {
        loadDictionary(context.getApplicationContext());
    }

    public static DictionaryHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (DictionaryHelper.class) {
                if (instance == null) {
                    instance = new DictionaryHelper(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Normalize dictionary keys and lookup text consistently.
     * Keeps the existing NFC normalization and invisible-character cleanup.
     */
    private static String normalizeKey(String s) {
        if (s == null) return null;

        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFC);

        return n
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\uFEFF", "");
    }

    private void loadDictionary(Context context) {
        if (loaded) return;

        synchronized (dictionary) {
            if (loaded) return;

            AssetManager am = context.getAssets();

            try (InputStream is = am.open("dictionary.json");
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(is, StandardCharsets.UTF_8))) {

                StringBuilder sb = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                JSONObject json = new JSONObject(sb.toString());

                Iterator<String> keys = json.keys();
                int added = 0;
                int skipped = 0;

                while (keys.hasNext()) {
                    String key = keys.next();

                    String normalizedKey = normalizeKey(key);
                    if (normalizedKey == null || normalizedKey.isEmpty()) {
                        skipped++;
                        continue;
                    }

                    String value = json.optString(key, "");
                    if (value == null) {
                        skipped++;
                        continue;
                    }

                    value = value.trim();
                    if (value.isEmpty()) {
                        skipped++;
                        continue;
                    }

                    // Keep the first valid value for a normalized key.
                    // This prevents a later duplicate normalized key from
                    // silently replacing an earlier dictionary entry.
                    if (!dictionary.containsKey(normalizedKey)) {
                        dictionary.put(normalizedKey, value);
                        added++;
                    } else {
                        skipped++;
                    }
                }

                loaded = true;

                Log.d(
                        TAG,
                        "Loaded dictionary entries: "
                                + added
                                + " (map size: "
                                + dictionary.size()
                                + ", skipped: "
                                + skipped
                                + ")"
                );

            } catch (IOException e) {
                loaded = false;
                Log.e(TAG, "IOException while loading dictionary.json", e);

            } catch (JSONException e) {
                loaded = false;
                Log.e(TAG, "JSONException while parsing dictionary.json", e);

            } catch (RuntimeException e) {
                loaded = false;
                Log.e(TAG, "Unexpected error while loading dictionary.json", e);
            }
        }
    }

    /**
     * Exact normalized dictionary lookup.
     */
    public String lookup(String banglaText) {
        String key = normalizeKey(banglaText);

        if (key == null || key.isEmpty()) {
            return null;
        }

        return dictionary.get(key);
    }

    /**
     * Checks whether an exact normalized key exists.
     */
    public boolean contains(String banglaText) {
        String key = normalizeKey(banglaText);

        if (key == null || key.isEmpty()) {
            return false;
        }

        return dictionary.containsKey(key);
    }

    /**
     * Returns the number of successfully loaded dictionary entries.
     */
    public int getWordCount() {
        return dictionary.size();
    }

    /**
     * Returns whether dictionary.json was loaded successfully.
     */
    public boolean isLoaded() {
        return loaded;
    }
}
