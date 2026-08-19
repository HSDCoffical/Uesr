package com.example.simpleapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SettingsActivity extends Activity {
    private static final String PREFS_NAME = "settings_prefs";
    private static final String KEY_CONFIGS = "api_configs";
    private static final String KEY_CURRENT_ID = "current_config_id";

    private Gson gson = new Gson();
    private LinearLayout contentContainer;
    private List<ApiConfig> configs = new ArrayList<>();
    private String currentConfigId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 加载数据
        loadConfigs();

        // 主布局
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(16, 16, 16, 16);
        mainLayout.setBackgroundColor(Color.parseColor("#F5F5F5"));

        // 标题
        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(24);
        title.setTextColor(Color.BLACK);
        title.setPadding(0, 0, 0, 24);
        mainLayout.addView(title);

        // Tab 栏
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setWeightSum(3);
        tabBar.setPadding(0, 0, 0, 16);

        Button tabAi = createTabButton("管理AI");
        Button tabTheme = createTabButton("管理软件");
        Button tabAbout = createTabButton("关于软件");

        tabBar.addView(tabAi);
        tabBar.addView(tabTheme);
        tabBar.addView(tabAbout);
        mainLayout.addView(tabBar);

        // 内容容器（滚动）
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));
        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(8, 8, 8, 8);
        scrollView.addView(contentContainer);
        mainLayout.addView(scrollView);

        setContentView(mainLayout);

        // 默认显示管理AI
        showAiManagement();

        // Tab 点击切换
        tabAi.setOnClickListener(v -> showAiManagement());
        tabTheme.setOnClickListener(v -> showThemeManagement());
        tabAbout.setOnClickListener(v -> showAbout());
    }

    private Button createTabButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(16);
        btn.setBackgroundColor(Color.parseColor("#E0E0E0"));
        btn.setTextColor(Color.BLACK);
        btn.setPadding(8, 8, 8, 8);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(4, 0, 4, 0);
        btn.setLayoutParams(params);
        return btn;
    }

    // ---------- 管理AI ----------
    private void showAiManagement() {
        contentContainer.removeAllViews();

        // 当前使用配置提示
        TextView currentHint = new TextView(this);
        currentHint.setText("当前使用: " + getCurrentConfigName());
        currentHint.setTextSize(16);
        currentHint.setTextColor(Color.parseColor("#333333"));
        currentHint.setPadding(0, 0, 0, 16);
        contentContainer.addView(currentHint);

        // 配置列表
        for (ApiConfig config : configs) {
            LinearLayout item = createConfigItem(config);
            contentContainer.addView(item);
        }

        // 悬浮添加按钮（右下角）
        Button fab = new Button(this);
        fab.setText("+");
        fab.setTextSize(32);
        fab.setBackgroundColor(Color.parseColor("#007AFF"));
        fab.setTextColor(Color.WHITE);
        fab.setPadding(24, 16, 24, 16);
        // 模拟浮动
        LinearLayout.LayoutParams fabParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        fabParams.gravity = Gravity.END | Gravity.BOTTOM;
        fabParams.setMargins(0, 0, 16, 16);
        fab.setLayoutParams(fabParams);
        fab.setOnClickListener(v -> showAddConfigDialog());

        // 由于LinearLayout不支持悬浮，我们直接将按钮放在底部并右对齐
        // 改为使用FrameLayout更好，但为了简单，我们放在列表最后并右对齐
        LinearLayout fabWrapper = new LinearLayout(this);
        fabWrapper.setOrientation(LinearLayout.HORIZONTAL);
        fabWrapper.setGravity(Gravity.END);
        fabWrapper.addView(fab);
        contentContainer.addView(fabWrapper);
    }

    private LinearLayout createConfigItem(ApiConfig config) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setBackgroundColor(Color.WHITE);
        item.setPadding(12, 12, 12, 12);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 4, 0, 4);
        item.setLayoutParams(params);

        // 信息区
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView nameView = new TextView(this);
        nameView.setText(config.getName() + " (" + config.getModel() + ")");
        nameView.setTextSize(16);
        nameView.setTextColor(Color.BLACK);
        info.addView(nameView);

        TextView urlView = new TextView(this);
        urlView.setText(config.getBaseUrl());
        urlView.setTextSize(12);
        urlView.setTextColor(Color.GRAY);
        info.addView(urlView);

        item.addView(info);

        // 使用按钮
        Button useBtn = new Button(this);
        boolean isCurrent = config.getId().equals(currentConfigId);
        if (isCurrent) {
            useBtn.setText("✓ 已使用");
            useBtn.setEnabled(false);
        } else {
            useBtn.setText("使用");
            useBtn.setOnClickListener(v -> {
                setCurrentConfig(config.getId());
                // 刷新界面
                showAiManagement();
                Toast.makeText(this, "已切换到: " + config.getName(), Toast.LENGTH_SHORT).show();
            });
        }
        useBtn.setBackgroundColor(isCurrent ? Color.parseColor("#34C759") : Color.parseColor("#007AFF"));
        useBtn.setTextColor(Color.WHITE);
        useBtn.setPadding(16, 8, 16, 8);
        item.addView(useBtn);

        // 删除按钮（长按或直接加个小叉）
        Button deleteBtn = new Button(this);
        deleteBtn.setText("✕");
        deleteBtn.setTextSize(16);
        deleteBtn.setBackgroundColor(Color.TRANSPARENT);
        deleteBtn.setTextColor(Color.RED);
        deleteBtn.setPadding(8, 0, 8, 0);
        deleteBtn.setOnClickListener(v -> {
            confirmDelete(config.getId());
        });
        item.addView(deleteBtn);

        return item;
    }

    private void showAddConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("新增AI配置");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        // 配置名称
        final EditText etName = new EditText(this);
        etName.setHint("配置名称（如：Agnes）");
        layout.addView(etName);

        final EditText etBaseUrl = new EditText(this);
        etBaseUrl.setHint("Base URL");
        layout.addView(etBaseUrl);

        final EditText etApiKey = new EditText(this);
        etApiKey.setHint("API Key");
        etApiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etApiKey);

        final EditText etModel = new EditText(this);
        etModel.setHint("模型名称");
        layout.addView(etModel);

        builder.setView(layout);
        builder.setPositiveButton("保存", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String baseUrl = etBaseUrl.getText().toString().trim();
            String apiKey = etApiKey.getText().toString().trim();
            String model = etModel.getText().toString().trim();
            if (name.isEmpty() || baseUrl.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show();
                return;
            }
            // 生成ID
            String id = UUID.randomUUID().toString();
            ApiConfig newConfig = new ApiConfig(id, name, baseUrl, apiKey, model);
            configs.add(newConfig);
            saveConfigs();
            // 如果这是第一个配置，自动设为当前
            if (configs.size() == 1) {
                setCurrentConfig(id);
            }
            showAiManagement();
            Toast.makeText(this, "配置已添加", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void confirmDelete(final String id) {
        new AlertDialog.Builder(this)
                .setTitle("删除配置")
                .setMessage("确定要删除此配置吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    configs.removeIf(c -> c.getId().equals(id));
                    if (currentConfigId != null && currentConfigId.equals(id)) {
                        currentConfigId = null;
                        // 如果有其他配置，自动设为第一个
                        if (!configs.isEmpty()) {
                            setCurrentConfig(configs.get(0).getId());
                        }
                    }
                    saveConfigs();
                    showAiManagement();
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String getCurrentConfigName() {
        if (currentConfigId == null) return "未配置";
        for (ApiConfig c : configs) {
            if (c.getId().equals(currentConfigId)) {
                return c.getName();
            }
        }
        return "未配置";
    }

    // ---------- 数据持久化 ----------
    private void loadConfigs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_CONFIGS, "");
        if (!json.isEmpty()) {
            Type type = new TypeToken<List<ApiConfig>>() {}.getType();
            List<ApiConfig> loaded = gson.fromJson(json, type);
            if (loaded != null) {
                configs = loaded;
            }
        }
        currentConfigId = prefs.getString(KEY_CURRENT_ID, null);
        // 如果当前ID无效，重置
        if (currentConfigId != null) {
            boolean exists = false;
            for (ApiConfig c : configs) {
                if (c.getId().equals(currentConfigId)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) currentConfigId = null;
        }
        if (currentConfigId == null && !configs.isEmpty()) {
            // 默认选第一个
            setCurrentConfig(configs.get(0).getId());
            saveConfigs();
        }
    }

    private void saveConfigs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = gson.toJson(configs);
        prefs.edit()
                .putString(KEY_CONFIGS, json)
                .putString(KEY_CURRENT_ID, currentConfigId)
                .apply();
        // 同时更新 SettingsHelper 中的当前配置（兼容旧存储）
        updateSettingsHelper();
    }

    private void setCurrentConfig(String id) {
        currentConfigId = id;
        saveConfigs();
    }

    private void updateSettingsHelper() {
        // 找到当前配置，更新到 SettingsHelper 的旧存储中
        if (currentConfigId == null) return;
        ApiConfig current = null;
        for (ApiConfig c : configs) {
            if (c.getId().equals(currentConfigId)) {
                current = c;
                break;
            }
        }
        if (current != null) {
            SettingsHelper helper = new SettingsHelper(this);
            helper.saveSettings(current.getBaseUrl(), current.getApiKey(), current.getModel());
        }
    }

    // ---------- 管理软件（占位） ----------
    private void showThemeManagement() {
        contentContainer.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText("主题切换、背景图上传等\n（功能开发中）");
        tv.setTextSize(18);
        tv.setTextColor(Color.GRAY);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 40, 0, 40);
        contentContainer.addView(tv);

        // 简单示例：深色/浅色切换（仅改变背景色）
        Button btnDark = new Button(this);
        btnDark.setText("深色模式（实验）");
        btnDark.setOnClickListener(v -> {
            contentContainer.setBackgroundColor(Color.parseColor("#333333"));
            // 简单示意
        });
        contentContainer.addView(btnDark);
    }

    // ---------- 关于软件 ----------
    private void showAbout() {
        contentContainer.removeAllViews();
        TextView about = new TextView(this);
        about.setText("Simple AI Chat\n版本 1.0\n\n基于 OpenCode Agnes API 开发\n\n声明：本应用仅供学习交流使用。\n所有AI回复由第三方API生成。\n\n开发者：凉数中");
        about.setTextSize(16);
        about.setTextColor(Color.BLACK);
        about.setGravity(Gravity.CENTER);
        about.setPadding(16, 40, 16, 40);
        contentContainer.addView(about);
    }
}