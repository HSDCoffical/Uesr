package com.example.simpleapp;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SettingsHelper {
    private static final String PREF_NAME = "app_settings";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    // 新增：多配置存储
    private static final String KEY_CONFIG_LIST = "config_list";
    private static final String KEY_CURRENT_ID = "current_config_id";

    private SharedPreferences prefs;
    private Gson gson;

    public SettingsHelper(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        // 自动迁移旧数据到多配置（如果从未使用过多配置）
        migrateOldData();
    }

    // ---------- 原有方法保持不变 ----------
    public void saveSettings(String baseUrl, String apiKey, String model) {
        prefs.edit()
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_MODEL, model)
            .apply();
    }

    public String getBaseUrl() {
        return prefs.getString(KEY_BASE_URL, "https://api.openai.com/v1");
    }

    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    public String getModel() {
        return prefs.getString(KEY_MODEL, "gpt-3.5-turbo");
    }

    public boolean hasSettings() {
        return !getApiKey().isEmpty();
    }

    // ---------- 新增：多配置管理 ----------
    // 获取所有配置列表
    public List<ApiConfig> getConfigs() {
        String json = prefs.getString(KEY_CONFIG_LIST, "");
        if (json.isEmpty()) {
            // 如果旧配置存在，自动迁移
            String oldBase = getBaseUrl();
            String oldKey = getApiKey();
            String oldModel = getModel();
            if (!oldKey.isEmpty()) {
                List<ApiConfig> list = new ArrayList<>();
                list.add(new ApiConfig(UUID.randomUUID().toString(), "默认配置", oldBase, oldKey, oldModel));
                saveConfigs(list);
                setCurrentConfigId(list.get(0).getId());
                return list;
            }
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<ApiConfig>>() {}.getType();
        List<ApiConfig> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    // 保存配置列表
    private void saveConfigs(List<ApiConfig> configs) {
        String json = gson.toJson(configs);
        prefs.edit().putString(KEY_CONFIG_LIST, json).apply();
    }

    // 获取当前配置ID
    public String getCurrentConfigId() {
        return prefs.getString(KEY_CURRENT_ID, null);
    }

    // 设置当前配置ID
    public void setCurrentConfigId(String id) {
        prefs.edit().putString(KEY_CURRENT_ID, id).apply();
        // 同步更新旧存储（保持兼容）
        ApiConfig current = getCurrentConfig();
        if (current != null) {
            prefs.edit()
                    .putString(KEY_BASE_URL, current.getBaseUrl())
                    .putString(KEY_API_KEY, current.getApiKey())
                    .putString(KEY_MODEL, current.getModel())
                    .apply();
        }
    }

    // 获取当前配置对象
    public ApiConfig getCurrentConfig() {
        String id = getCurrentConfigId();
        if (id == null) return null;
        for (ApiConfig c : getConfigs()) {
            if (c.getId().equals(id)) return c;
        }
        return null;
    }

    // 添加配置
    public void addConfig(ApiConfig config) {
        List<ApiConfig> list = getConfigs();
        list.add(config);
        saveConfigs(list);
        if (getCurrentConfigId() == null) {
            setCurrentConfigId(config.getId());
        }
    }

    // 删除配置
    public void deleteConfig(String id) {
        List<ApiConfig> list = getConfigs();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(id)) {
                list.remove(i);
                break;
            }
        }
        saveConfigs(list);
        if (getCurrentConfigId() != null && getCurrentConfigId().equals(id)) {
            if (!list.isEmpty()) {
                setCurrentConfigId(list.get(0).getId());
            } else {
                prefs.edit().remove(KEY_CURRENT_ID).apply();
            }
        }
    }

    // 迁移旧数据（首次使用时将旧配置转为多配置）
    private void migrateOldData() {
        String json = prefs.getString(KEY_CONFIG_LIST, "");
        if (!json.isEmpty()) return; // 已有配置，跳过
        String oldKey = prefs.getString(KEY_API_KEY, "");
        if (oldKey.isEmpty()) return; // 没有旧数据
        // 旧配置存在，迁移
        String oldBase = prefs.getString(KEY_BASE_URL, "https://api.openai.com/v1");
        String oldModel = prefs.getString(KEY_MODEL, "gpt-3.5-turbo");
        List<ApiConfig> list = new ArrayList<>();
        list.add(new ApiConfig(UUID.randomUUID().toString(), "默认配置", oldBase, oldKey, oldModel));
        saveConfigs(list);
        setCurrentConfigId(list.get(0).getId());
    }
}