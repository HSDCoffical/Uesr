package com.example.simpleapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private SettingsHelper settingsHelper;
    private ApiHelper apiHelper;
    private TextView tvChat;
    private EditText etInput;
    private Button btnSend;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsHelper = new SettingsHelper(this);
        apiHelper = new ApiHelper();

        // 主布局（垂直）
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(16, 16, 16, 16);

        // --- 顶部标题栏（含设置图标） ---
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(0, 0, 0, 16);

        TextView title = new TextView(this);
        title.setText("AI Chat");
        title.setTextSize(24);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        ));
        titleBar.addView(title);

        // 设置按钮（右上角）
        Button btnSettings = new Button(this);
        btnSettings.setText("⚙️");
        btnSettings.setTextSize(24);
        btnSettings.setBackground(null); // 去掉背景，只显示文字
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
        titleBar.addView(btnSettings);

        mainLayout.addView(titleBar);

        // --- 状态栏（显示模型和配置状态） ---
        tvStatus = new TextView(this);
        tvStatus.setText(getStatusText());
        tvStatus.setTextSize(14);
        tvStatus.setPadding(0, 0, 0, 16);
        mainLayout.addView(tvStatus);

        // --- 聊天显示区域（滚动） ---
        scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        tvChat = new TextView(this);
        tvChat.setText("欢迎使用 AI Chat\n请输入消息开始对话\n");
        tvChat.setTextSize(16);
        tvChat.setPadding(16, 16, 16, 16);
        scrollView.addView(tvChat);
        mainLayout.addView(scrollView);

        // --- 输入区域（水平） ---
        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.setPadding(0, 16, 0, 0);

        etInput = new EditText(this);
        etInput.setHint("输入消息...");
        etInput.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        ));
        inputLayout.addView(etInput);

        btnSend = new Button(this);
        btnSend.setText("发送");
        inputLayout.addView(btnSend);

        mainLayout.addView(inputLayout);

        // --- 进度条 ---
        progressBar = new ProgressBar(this);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        progressBar.setVisibility(View.GONE);
        mainLayout.addView(progressBar);

        setContentView(mainLayout);

        // 发送按钮点击
        btnSend.setOnClickListener(v -> {
            String input = etInput.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入消息", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!settingsHelper.hasSettings()) {
                Toast.makeText(this, "请先进入设置配置 API", Toast.LENGTH_LONG).show();
                return;
            }

            appendToChat("我: " + input);
            etInput.setText("");
            setLoading(true);

            apiHelper.sendMessage(
                    settingsHelper.getBaseUrl(),
                    settingsHelper.getApiKey(),
                    settingsHelper.getModel(),
                    input,
                    new ApiHelper.ChatCallback() {
                        @Override
                        public void onSuccess(String response) {
                            setLoading(false);
                            appendToChat("AI: " + response);
                            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                        }

                        @Override
                        public void onError(String error) {
                            setLoading(false);
                            appendToChat("错误: " + error);
                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                        }
                    }
            );
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 刷新状态
        if (tvStatus != null) {
            tvStatus.setText(getStatusText());
        }
    }

    private void appendToChat(String text) {
        String current = tvChat.getText().toString();
        tvChat.setText(current + "\n" + text + "\n");
    }

    private void setLoading(boolean loading) {
        btnSend.setEnabled(!loading);
        etInput.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private String getStatusText() {
        if (settingsHelper.hasSettings()) {
            return "模型: " + settingsHelper.getModel();
        } else {
            return "⚠️ 请先进入设置配置 API";
        }
    }
}