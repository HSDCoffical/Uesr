package com.example.simpleapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SettingsActivity extends Activity {
    private static final int PICK_IMAGE_REQUEST = 1001;
    private static final int PERMISSION_REQUEST = 1002;

    private Gson gson = new Gson();
    private LinearLayout contentContainer;
    private List<ApiConfig> configs = new ArrayList<>();
    private String currentConfigId = null;

    private ThemeHelper themeHelper;
    private Button tabAi, tabTheme, tabAbout;
    private boolean darkMode = false;
    private Button fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        themeHelper = new ThemeHelper(this);
        darkMode = themeHelper.isDarkMode();

        loadConfigs();

        // 主布局
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(12, 12, 12, 12);
        mainLayout.setBackgroundColor(darkMode ? Color.parseColor("#303030") : Color.parseColor("#F5F5F5"));

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(20);
        title.setTextColor(darkMode ? Color.WHITE : Color.BLACK);
        title.setPadding(0, 0, 0, 16);
        mainLayout.addView(title);

        // Tab 栏
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setWeightSum(3);
        tabBar.setPadding(0, 0, 0, 12);

        tabAi = createMaterialTab("管理AI");
        tabTheme = createMaterialTab("管理软件");
        tabAbout = createMaterialTab("关于软件");

        tabBar.addView(tabAi);
        tabBar.addView(tabTheme);
        tabBar.addView(tabAbout);
        mainLayout.addView(tabBar);

        // 内容容器（FrameLayout 用于悬浮按钮）
        FrameLayout frameContainer = new FrameLayout(this);
        frameContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(8, 8, 8, 8);
        scrollView.addView(contentContainer);
        frameContainer.addView(scrollView);

        // 悬浮添加按钮（仅管理AI页面显示）
        fab = new Button(this);
        fab.setText("+");
        fab.setTextSize(28);
        fab.setBackgroundColor(Color.parseColor("#007AFF"));
        fab.setTextColor(Color.WHITE);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor("#007AFF"));
        fab.setBackground(circle);
        fab.setPadding(20, 14, 20, 14);
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        fabParams.gravity = Gravity.BOTTOM | Gravity.END;
        fabParams.setMargins(0, 0, 24, 24);
        fab.setLayoutParams(fabParams);
        fab.setOnClickListener(v -> showAddConfigDialog());
        fab.setVisibility(View.GONE);
        frameContainer.addView(fab);

        mainLayout.addView(frameContainer);
        setContentView(mainLayout);

        // 默认显示管理AI，显示FAB
        showAiManagement();
        fab.setVisibility(View.VISIBLE);

        tabAi.setOnClickListener(v -> { resetTabColors(); highlightTab(tabAi); showAiManagement(); fab.setVisibility(View.VISIBLE); });
        tabTheme.setOnClickListener(v -> { resetTabColors(); highlightTab(tabTheme); showThemeManagement(); fab.setVisibility(View.GONE); });
        tabAbout.setOnClickListener(v -> { resetTabColors(); highlightTab(tabAbout); showAbout(); fab.setVisibility(View.GONE); });
    }

    private Button createMaterialTab(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(14);
        btn.setAllCaps(false);
        btn.setPadding(12, 8, 12, 8);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(20);
        shape.setColor(Color.parseColor("#E0E0E0"));
        btn.setBackground(shape);
        btn.setTextColor(Color.BLACK);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(3, 0, 3, 0);
        btn.setLayoutParams(params);
        return btn;
    }

    private void resetTabColors() {
        tabAi.setBackgroundColor(Color.parseColor("#E0E0E0"));
        tabAi.setTextColor(Color.BLACK);
        tabTheme.setBackgroundColor(Color.parseColor("#E0E0E0"));
        tabTheme.setTextColor(Color.BLACK);
        tabAbout.setBackgroundColor(Color.parseColor("#E0E0E0"));
        tabAbout.setTextColor(Color.BLACK);
    }

    private void highlightTab(Button tab) {
        tab.setBackgroundColor(Color.parseColor("#007AFF"));
        tab.setTextColor(Color.WHITE);
    }

    // ---------- 管理AI ----------
    private void showAiManagement() {
        contentContainer.removeAllViews();

        TextView currentHint = new TextView(this);
        currentHint.setText("当前使用: " + getCurrentConfigName());
        currentHint.setTextSize(14);
        currentHint.setTextColor(darkMode ? Color.WHITE : Color.BLACK);
        currentHint.setPadding(0, 0, 0, 12);
        contentContainer.addView(currentHint);

        for (ApiConfig config : configs) {
            LinearLayout item = createConfigItem(config);
            contentContainer.addView(item);
        }
    }

    private LinearLayout createConfigItem(ApiConfig config) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable card = new GradientDrawable();
        card.setCornerRadius(8);
        card.setColor(darkMode ? Color.parseColor("#424242") : Color.WHITE);
        card.setStroke(1, darkMode ? Color.parseColor("#666666") : Color.parseColor("#DDDDDD"));
        item.setBackground(card);
        item.setPadding(12, 12, 12, 12);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 4, 0, 4);
        item.setLayoutParams(params);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView nameView = new TextView(this);
        nameView.setText(config.getName() + " (" + config.getModel() + ")");
        nameView.setTextSize(14);
        nameView.setTextColor(darkMode ? Color.WHITE : Color.BLACK);
        info.addView(nameView);

        TextView urlView = new TextView(this);
        urlView.setText(config.getBaseUrl());
        urlView.setTextSize(11);
        urlView.setTextColor(darkMode ? Color.LTGRAY : Color.GRAY);
        info.addView(urlView);

        item.addView(info);

        boolean isCurrent = config.getId().equals(currentConfigId);
        Button useBtn = new Button(this);
        useBtn.setAllCaps(false);
        useBtn.setTextSize(12);
        if (isCurrent) {
            useBtn.setText("✓ 已使用");
            useBtn.setEnabled(false);
            useBtn.setBackgroundColor(Color.parseColor("#34C759"));
        } else {
            useBtn.setText("使用");
            useBtn.setBackgroundColor(Color.parseColor("#007AFF"));
            useBtn.setOnClickListener(v -> {
                setCurrentConfig(config.getId());
                showAiManagement();
                Toast.makeText(this, "已切换到: " + config.getName(), Toast.LENGTH_SHORT).show();
            });
        }
        useBtn.setTextColor(Color.WHITE);
        useBtn.setPadding(14, 6, 14, 6);
        GradientDrawable btnShape = new GradientDrawable();
        btnShape.setCornerRadius(16);
        btnShape.setColor(isCurrent ? Color.parseColor("#34C759") : Color.parseColor("#007AFF"));
        useBtn.setBackground(btnShape);
        item.addView(useBtn);

        Button deleteBtn = new Button(this);
        deleteBtn.setText("✕");
        deleteBtn.setTextSize(14);
        deleteBtn.setBackground(null);
        deleteBtn.setTextColor(Color.RED);
        deleteBtn.setPadding(6, 0, 6, 0);
        deleteBtn.setOnClickListener(v -> confirmDelete(config.getId()));
        item.addView(deleteBtn);

        return item;
    }

    private void showAddConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("新增AI配置");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

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
            String id = UUID.randomUUID().toString();
            ApiConfig newConfig = new ApiConfig(id, name, baseUrl, apiKey, model);
            configs.add(newConfig);
            saveConfigs();
            if (configs.size() == 1) {
                setCurrentConfig(id);
            }
            showAiManagement();
            Toast.makeText(this, "配置已添加", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void confirmDelete(String id) {
        new AlertDialog.Builder(this)
                .setTitle("删除配置")
                .setMessage("确定要删除此配置吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    configs.removeIf(c -> c.getId().equals(id));
                    if (currentConfigId != null && currentConfigId.equals(id)) {
                        currentConfigId = null;
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
        SharedPreferences prefs = getSharedPreferences("settings_prefs", MODE_PRIVATE);
        String json = prefs.getString("api_configs", "");
        if (!json.isEmpty()) {
            Type type = new TypeToken<List<ApiConfig>>() {}.getType();
            List<ApiConfig> loaded = gson.fromJson(json, type);
            if (loaded != null) {
                configs = loaded;
            }
        }
        currentConfigId = prefs.getString("current_config_id", null);
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
            setCurrentConfig(configs.get(0).getId());
            saveConfigs();
        }
    }

    private void saveConfigs() {
        SharedPreferences prefs = getSharedPreferences("settings_prefs", MODE_PRIVATE);
        String json = gson.toJson(configs);
        prefs.edit()
                .putString("api_configs", json)
                .putString("current_config_id", currentConfigId)
                .apply();
        updateSettingsHelper();
    }

    private void setCurrentConfig(String id) {
        currentConfigId = id;
        saveConfigs();
    }

    private void updateSettingsHelper() {
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

    // ---------- 管理软件 ----------
    private void showThemeManagement() {
        contentContainer.removeAllViews();

        // 深色模式 - 使用 Switch
        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeRow.setGravity(Gravity.CENTER_VERTICAL);
        themeRow.setPadding(0, 0, 0, 20);

        TextView themeLabel = new TextView(this);
        themeLabel.setText("深色模式");
        themeLabel.setTextSize(16);
        themeLabel.setTextColor(darkMode ? Color.WHITE : Color.BLACK);
        themeLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        themeRow.addView(themeLabel);

        Switch switchDark = new Switch(this);
        switchDark.setChecked(darkMode);
        switchDark.setOnCheckedChangeListener((buttonView, isChecked) -> {
            darkMode = isChecked;
            themeHelper.setDarkMode(darkMode);
            recreate();
        });
        themeRow.addView(switchDark);
        contentContainer.addView(themeRow);

        // 自定义背景
        TextView bgLabel = new TextView(this);
        bgLabel.setText("自定义背景");
        bgLabel.setTextSize(16);
        bgLabel.setTextColor(darkMode ? Color.WHITE : Color.BLACK);
        bgLabel.setPadding(0, 16, 0, 8);
        contentContainer.addView(bgLabel);

        LinearLayout bgRow = new LinearLayout(this);
        bgRow.setOrientation(LinearLayout.HORIZONTAL);
        bgRow.setGravity(Gravity.CENTER_VERTICAL);

        Button uploadBtn = new Button(this);
        uploadBtn.setText("选择图片");
        uploadBtn.setAllCaps(false);
        uploadBtn.setTextSize(14);
        uploadBtn.setBackgroundColor(Color.parseColor("#E0E0E0"));
        uploadBtn.setTextColor(Color.BLACK);
        uploadBtn.setPadding(16, 8, 16, 8);
        GradientDrawable upShape = new GradientDrawable();
        upShape.setCornerRadius(16);
        upShape.setColor(Color.parseColor("#E0E0E0"));
        uploadBtn.setBackground(upShape);
        uploadBtn.setOnClickListener(v -> checkPermissionAndPickImage());
        bgRow.addView(uploadBtn);

        Button clearBtn = new Button(this);
        clearBtn.setText("清除");
        clearBtn.setAllCaps(false);
        clearBtn.setTextSize(14);
        clearBtn.setBackgroundColor(Color.parseColor("#E0E0E0"));
        clearBtn.setTextColor(Color.BLACK);
        clearBtn.setPadding(16, 8, 16, 8);
        GradientDrawable clrShape = new GradientDrawable();
        clrShape.setCornerRadius(16);
        clrShape.setColor(Color.parseColor("#E0E0E0"));
        clearBtn.setBackground(clrShape);
        clearBtn.setOnClickListener(v -> {
            themeHelper.saveBackground(null);
            Toast.makeText(this, "背景已清除", Toast.LENGTH_SHORT).show();
            showThemeManagement();
        });
        bgRow.addView(clearBtn);

        contentContainer.addView(bgRow);

        // 背景透明度控制
        TextView alphaLabel = new TextView(this);
        int currentAlpha = themeHelper.getBgAlpha();
        alphaLabel.setText("背景透明度 (" + currentAlpha + "%)");
        alphaLabel.setTextSize(14);
        alphaLabel.setTextColor(darkMode ? Color.WHITE : Color.BLACK);
        alphaLabel.setPadding(0, 16, 0, 8);
        contentContainer.addView(alphaLabel);

        LinearLayout alphaRow = new LinearLayout(this);
        alphaRow.setOrientation(LinearLayout.HORIZONTAL);
        alphaRow.setGravity(Gravity.CENTER_VERTICAL);

        // 将 etAlpha 声明为 final 并放在这里（在监听器之前）
        final EditText etAlpha = new EditText(this);
        etAlpha.setInputType(InputType.TYPE_CLASS_NUMBER);
        etAlpha.setText(String.valueOf(currentAlpha));
        etAlpha.setMinWidth(60);
        etAlpha.setGravity(Gravity.CENTER);
        etAlpha.setPadding(8, 4, 8, 4);
        etAlpha.setBackgroundResource(android.R.drawable.editbox_background);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgress(currentAlpha);
        seekBar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    themeHelper.setBgAlpha(progress);
                    alphaLabel.setText("背景透明度 (" + progress + "%)");
                    etAlpha.setText(String.valueOf(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        alphaRow.addView(seekBar);
        alphaRow.addView(etAlpha);

        contentContainer.addView(alphaRow);

        // 数值输入框焦点丢失时同步
        etAlpha.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    int val = Integer.parseInt(etAlpha.getText().toString());
                    if (val < 0) val = 0;
                    if (val > 100) val = 100;
                    themeHelper.setBgAlpha(val);
                    seekBar.setProgress(val);
                    alphaLabel.setText("背景透明度 (" + val + "%)");
                } catch (NumberFormatException ignored) {}
            }
        });

        // 状态显示
        TextView status = new TextView(this);
        status.setText(themeHelper.hasBackground() ? "当前已设置自定义背景" : "当前未设置背景");
        status.setTextSize(12);
        status.setTextColor(darkMode ? Color.LTGRAY : Color.GRAY);
        status.setPadding(0, 8, 0, 0);
        contentContainer.addView(status);
    }

    // ---------- 权限与图片选择 ----------
    private void checkPermissionAndPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                        PERMISSION_REQUEST);
            } else {
                pickImage();
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST);
            } else {
                pickImage();
            }
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImage();
            } else {
                Toast.makeText(this, "需要存储权限才能选择图片", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                themeHelper.saveBackground(bitmap);
                Toast.makeText(this, "背景已更新", Toast.LENGTH_SHORT).show();
                showThemeManagement();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ---------- 关于软件 ----------
    private void showAbout() {
        contentContainer.removeAllViews();

        TextView about = new TextView(this);
        about.setText("Simple AI Chat\n版本 1.0\n\n基于 OpenCode / Agnes 等 API 开发\n\n声明：本应用仅供学习交流使用。\n所有AI回复由第三方API生成。\n\n开发者：凉数中");
        about.setTextSize(14);
        about.setTextColor(darkMode ? Color.WHITE : Color.BLACK);
        about.setGravity(Gravity.CENTER);
        about.setPadding(16, 40, 16, 40);
        contentContainer.addView(about);
    }
}