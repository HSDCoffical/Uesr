package com.example.simpleapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "chat_prefs";
    private static final String KEY_HISTORY = "chat_history";

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

    // 消息列表
    private List<ChatMessage> messages = new ArrayList<>();
    private Gson gson = new Gson();

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

        // 加载历史记录
        loadHistory();

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

        // 设置按钮
        Button btnSettings = new Button(this);
        btnSettings.setText("⚙️");
        btnSettings.setTextSize(24);
        btnSettings.setBackground(null);
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
        titleBar.addView(btnSettings);

        // 清空按钮
        Button btnClear = new Button(this);
        btnClear.setText("🗑️");
        btnClear.setTextSize(24);
        btnClear.setBackground(null);
        btnClear.setOnClickListener(v -> {
            messages.clear();
            saveHistory();
            updateChatDisplay();
            Toast.makeText(this, "对话已清空", Toast.LENGTH_SHORT).show();
        });
        titleBar.addView(btnClear);

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

        // 更新显示
        updateChatDisplay();

        // 初始化超时 Handler
        timeoutHandler = new Handler();

        btnSend.setOnClickListener(v -> {
            try {
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

                // 添加用户消息
                messages.add(new ChatMessage("user", input));
                saveHistory();
                updateChatDisplay();

                etInput.setText("");
                setLoading(true);
                waitingForResponse = true;

                // 超时检测
                timeoutRunnable = () -> {
                    if (waitingForResponse) {
                        waitingForResponse = false;
                        setLoading(false);
                        messages.add(new ChatMessage("ai", "[超时] 请求超时，请检查网络或重试"));
                        saveHistory();
                        updateChatDisplay();
                        Toast.makeText(MainActivity.this, "请求超时", Toast.LENGTH_LONG).show();
                    }
                };
                timeoutHandler.postDelayed(timeoutRunnable, 30000);

                String baseUrl = settingsHelper.getBaseUrl();
                String apiKey = settingsHelper.getApiKey();
                String model = settingsHelper.getModel();

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
                            messages.add(new ChatMessage("ai", response));
                            saveHistory();
                            updateChatDisplay();
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
                            messages.add(new ChatMessage("ai", "[错误] " + error));
                            saveHistory();
                            updateChatDisplay();
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
                messages.add(new ChatMessage("ai", "[系统错误] " + e.getMessage()));
                saveHistory();
                updateChatDisplay();
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

    // 加载历史记录
    private void loadHistory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_HISTORY, "");
        if (!json.isEmpty()) {
            Type type = new TypeToken<List<ChatMessage>>() {}.getType();
            List<ChatMessage> loaded = gson.fromJson(json, type);
            if (loaded != null) {
                messages = loaded;
            }
        }
    }

    // 保存历史记录
    private void saveHistory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = gson.toJson(messages);
        prefs.edit().putString(KEY_HISTORY, json).apply();
    }

    // 更新聊天显示
    private void updateChatDisplay() {
        StringBuilder sb = new StringBuilder();
        if (messages.isEmpty()) {
            sb.append("欢迎使用 AI Chat\n请输入消息开始对话\n");
        } else {
            for (ChatMessage msg : messages) {
                String prefix = msg.getRole().equals("user") ? "我: " : "AI: ";
                sb.append(prefix).append(msg.getContent()).append("\n\n");
            }
        }
        tvChat.setText(sb.toString());
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
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