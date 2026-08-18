package com.example.simpleapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private EditText etBaseUrl, etApiKey, etModel;
    private Button btnSave;
    private SettingsHelper settingsHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settingsHelper = new SettingsHelper(this);

        etBaseUrl = findViewById(R.id.et_base_url);
        etApiKey = findViewById(R.id.et_api_key);
        etModel = findViewById(R.id.et_model);
        btnSave = findViewById(R.id.btn_save);

        // 加载已保存的设置
        loadSettings();

        // 保存按钮点击事件
        btnSave.setOnClickListener(v -> {
            String baseUrl = etBaseUrl.getText().toString().trim();
            String apiKey = etApiKey.getText().toString().trim();
            String model = etModel.getText().toString().trim();

            if (baseUrl.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
                Toast.makeText(this, "请填写所有字段", Toast.LENGTH_SHORT).show();
                return;
            }

            settingsHelper.saveSettings(baseUrl, apiKey, model);
            Toast.makeText(this, "设置已保存！", Toast.LENGTH_LONG).show();
            finish(); // 自动返回主界面
        });
    }

    private void loadSettings() {
        etBaseUrl.setText(settingsHelper.getBaseUrl());
        etApiKey.setText(settingsHelper.getApiKey());
        etModel.setText(settingsHelper.getModel());
    }
}