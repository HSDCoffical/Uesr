package com.example.simpleapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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
    private static final String TAG = "MainActivity";
    private SettingsHelper settingsHelper;
    private ApiHelper apiHelper;
    private TextView tvChat;
    private EditText etInput;
    private Button btnSend;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private ScrollView scrollView;
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;
    private boolean waitingForResponse = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            settingsHelper = new SettingsHelper(this);
            apiHelper = new ApiHelper();
        } catch (Exception e) {
            Log.e(TAG, "初始化失败", e);
            Toast.makeText(this, "初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 主布局
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(16, 16, 16, 16);

        // 标题栏
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

        Button btnSettings = new Button(this);
        btnSettings.setText("⚙️");
        btnSettings.setTextSize(24);
        btnSettings.setBackground(null);
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
        titleBar.addView(btnSettings);
        mainLayout.addView(titleBar);

        // 状态
        tvStatus = new TextView(this);
        tvStatus.setText(getStatusText());
        tvStatus.setTextSize(14);
        tvStatus.setPadding(0, 0, 0, 16);
        mainLayout.addView(tvStatus);

        // 聊天区域
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

        // 输入区域
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

        progressBar = new ProgressBar(this);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        progressBar.setVisibility(View.GONE);
        mainLayout.addView(progressBar);

        setContentView(mainLayout);

        // 初始化超时 Handler
        timeoutHandler = new Handler();

        btnSend.setOnClickListener(v -> {
            try {
                // 取消之前的超时
                if (timeoutRunnable != null) {
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                }

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
                waitingForResponse = true;

                // 设置超时检测（30秒）
                timeoutRunnable = () -> {
                    if (waitingForResponse) {
                        waitingForResponse = false;
                        setLoading(false);
                        appendToChat("[超时] 请求超时，请检查网络或重试");
                        Toast.makeText(MainActivity.this, "请求超时", Toast.LENGTH_LONG).show();
                    }
                };
                timeoutHandler.postDelayed(timeoutRunnable, 30000);

                String baseUrl = settingsHelper.getBaseUrl();
                String apiKey = settingsHelper.getApiKey();
                String model = settingsHelper.getModel();

                Log.d(TAG, "发送消息，BaseURL=" + baseUrl + ", Model=" + model);

                // 检查参数
                if (baseUrl == null || baseUrl.isEmpty()) {
                    throw new IllegalArgumentException("Base URL 不能为空");
                }
                if (apiKey == null || apiKey.isEmpty()) {
                    throw new IllegalArgumentException("API Key 不能为空");
                }

                apiHelper.sendMessage(baseUrl, apiKey, model, input, new ApiHelper.ChatCallback() {
                    @Override
                    public void onSuccess(String response) {
                        runOnUiThread(() -> {
                            waitingForResponse = false;
                            if (timeoutRunnable != null) {
                                timeoutHandler.removeCallbacks(timeoutRunnable);
                            }
                            setLoading(false);
                            appendToChat("AI: " + response);
                            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            waitingForResponse = false;
                            if (timeoutRunnable != null) {
                                timeoutHandler.removeCallbacks(timeoutRunnable);
                            }
                            setLoading(false);
                            appendToChat("[错误] " + error);
                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "点击发送异常", e);
                waitingForResponse = false;
                if (timeoutRunnable != null) {
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                }
                appendToChat("[系统错误] " + e.getMessage());
                Toast.makeText(this, "发送失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                setLoading(false);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tvStatus != null) {
            tvStatus.setText(getStatusText());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
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