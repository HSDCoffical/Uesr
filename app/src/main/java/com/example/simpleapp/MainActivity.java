package com.example.simpleapp;

import android.app.Activity;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsHelper = new SettingsHelper(this);
        apiHelper = new ApiHelper();

        // 创建主布局（垂直滚动）
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(16, 16, 16, 16);

        // 状态栏
        TextView tvStatus = new TextView(this);
        tvStatus.setText("模型: " + settingsHelper.getModel());
        tvStatus.setTextSize(14);
        tvStatus.setPadding(0, 0, 0, 16);
        mainLayout.addView(tvStatus);

        // 聊天显示区域（在 ScrollView 中）
        ScrollView scrollView = new ScrollView(this);
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

        // 输入区域（水平布局）
        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.setPadding(0, 16, 0, 0);

        etInput = new EditText(this);
        etInput.setHint("输入消息...");
        etInput.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        ));
        inputLayout.addView(etInput);

        btnSend = new Button(this);
        btnSend.setText("发送");
        inputLayout.addView(btnSend);

        mainLayout.addView(inputLayout);

        // 进度条（加载指示器）
        progressBar = new ProgressBar(this);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        progressBar.setVisibility(View.GONE);
        mainLayout.addView(progressBar);

        setContentView(mainLayout);

        // 发送按钮点击事件
        btnSend.setOnClickListener(v -> {
            String input = etInput.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入消息", Toast.LENGTH_SHORT).show();
                return;
            }

            // 检查是否已配置
            if (!settingsHelper.hasSettings()) {
                Toast.makeText(this, "请先进入设置配置 API", Toast.LENGTH_LONG).show();
                return;
            }

            // 显示用户消息
            appendToChat("我: " + input);

            // 清空输入框
            etInput.setText("");

            // 显示加载状态
            setLoading(true);

            // 调用 API
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
                            // 滚动到底部
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

    private void appendToChat(String text) {
        String current = tvChat.getText().toString();
        tvChat.setText(current + "\n" + text + "\n");
    }

    private void setLoading(boolean loading) {
        btnSend.setEnabled(!loading);
        etInput.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}