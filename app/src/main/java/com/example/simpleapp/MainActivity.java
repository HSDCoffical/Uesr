package com.example.simpleapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private SettingsHelper settingsHelper;
    private TextView tvInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsHelper = new SettingsHelper(this);

        // 创建主布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(32, 32, 32, 32);

        // 标题
        TextView tvTitle = new TextView(this);
        tvTitle.setText("AI Chat");
        tvTitle.setTextSize(32);
        tvTitle.setGravity(Gravity.CENTER);
        layout.addView(tvTitle);

        // 状态信息
        tvInfo = new TextView(this);
        tvInfo.setText(getStatusText());
        tvInfo.setTextSize(16);
        tvInfo.setGravity(Gravity.CENTER);
        tvInfo.setPadding(0, 32, 0, 32);
        layout.addView(tvInfo);

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
        // 每次返回主界面时刷新状态
        if (tvInfo != null) {
            tvInfo.setText(getStatusText());
        }
    }

    private String getStatusText() {
        if (settingsHelper.hasSettings()) {
            return "已配置 API\n模型: " + settingsHelper.getModel();
        } else {
            return "未配置\n请进入设置填写 API 信息";
        }
    }
}