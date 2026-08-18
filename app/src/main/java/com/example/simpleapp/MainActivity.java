package com.example.simpleapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private SettingsHelper settingsHelper;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsHelper = new SettingsHelper(this);

        // 主布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(32, 32, 32, 32);

        // 标题
        TextView title = new TextView(this);
        title.setText("AI Chat");
        title.setTextSize(32);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        // 状态信息
        tvStatus = new TextView(this);
        tvStatus.setText(getStatusText());
        tvStatus.setTextSize(16);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 32, 0, 32);
        layout.addView(tvStatus);

        // 进入设置按钮
        Button btnSettings = new Button(this);
        btnSettings.setText("进入设置");
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
        layout.addView(btnSettings);

        setContentView(layout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回时更新状态
        if (tvStatus != null) {
            tvStatus.setText(getStatusText());
        }
    }

    private String getStatusText() {
        if (settingsHelper.hasSettings()) {
            return "已配置 API\n模型: " + settingsHelper.getModel();
        } else {
            return "请先进入设置配置 API";
        }
    }
}