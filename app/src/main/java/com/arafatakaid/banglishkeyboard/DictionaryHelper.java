package com.arafatakaid.banglishkeyboard;

import android.content.Context;
import android.content.res.AssetManager;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DictionaryHelper {

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

    private void loadDictionary(Context context) {
        try {
            AssetManager am = context.getAssets();
            InputStream is = am.open("dictionary.json");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            is.close();

            JSONObject json = new JSONObject(sb.toString());
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                dictionary.put(key.trim(), json.getString(key));
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    public String lookup(String banglaText) {
        if (banglaText == null) return null;
        return dictionary.get(banglaText.trim());
    }

    public boolean contains(String banglaText) {
        return dictionary.containsKey(banglaText.trim());
    }
}
