package com.example.simpleapp;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsHelper {
    private static final String PREF_NAME = "app_settings";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";

    private SharedPreferences prefs;

    public SettingsHelper(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // 保存设置
    public void saveSettings(String baseUrl, String apiKey, String model) {
        prefs.edit()
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_MODEL, model)
            .apply();
    }

    // 读取 Base URL
    public String getBaseUrl() {
        return prefs.getString(KEY_BASE_URL, "https://api.openai.com/v1");
    }

    // 读取 API Key
    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    // 读取 Model
    public String getModel() {
        return prefs.getString(KEY_MODEL, "gpt-3.5-turbo");
    }

    // 检查是否已保存完整设置（用于判断是否首次使用）
    public boolean hasSettings() {
        return !getApiKey().isEmpty();
    }
}