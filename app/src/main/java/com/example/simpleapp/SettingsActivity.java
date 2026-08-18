package com.example.simpleapp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private EditText etBaseUrl, etApiKey, etModel;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etBaseUrl = findViewById(R.id.et_base_url);
        etApiKey = findViewById(R.id.et_api_key);
        etModel = findViewById(R.id.et_model);
        btnSave = findViewById(R.id.btn_save);

        // 暂时只显示 Toast，不实际保存
        btnSave.setOnClickListener(v -> {
            String baseUrl = etBaseUrl.getText().toString();
            String apiKey = etApiKey.getText().toString();
            String model = etModel.getText().toString();
            Toast.makeText(SettingsActivity.this, 
                "Base URL: " + baseUrl + "\nModel: " + model, 
                Toast.LENGTH_LONG).show();
        });
    }
}