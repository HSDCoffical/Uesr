package com.example.simpleapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "chat_prefs";
    private static final String KEY_HISTORY = "chat_history";

    private SettingsHelper settingsHelper;
    private ApiHelper apiHelper;
    private LinearLayout chatContainer;
    private EditText etInput;
    private Button btnSend;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private ScrollView scrollView;
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;
    private boolean waitingForResponse = false;

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

        loadHistory();

        // 主布局
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(16, 16, 16, 16);
        mainLayout.setBackgroundColor(Color.parseColor("#F5F5F5"));

        // 标题栏
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(0, 0, 0, 16);

        TextView title = new TextView(this);
        title.setText("AI Chat");
        title.setTextSize(24);
        title.setTextColor(Color.parseColor("#1A1A1A"));
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        titleBar.addView(title);

        // 设置按钮
        Button btnSettings = createIconButton("⚙️");
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
        titleBar.addView(btnSettings);

        // 清空按钮
        Button btnClear = createIconButton("🗑️");
        btnClear.setOnClickListener(v -> {
            messages.clear();
            saveHistory();
            renderMessages();
            Toast.makeText(this, "对话已清空", Toast.LENGTH_SHORT).show();
        });
        titleBar.addView(btnClear);

        mainLayout.addView(titleBar);

        // 状态
        tvStatus = new TextView(this);
        tvStatus.setText(getStatusText());
        tvStatus.setTextSize(14);
        tvStatus.setTextColor(Color.parseColor("#666666"));
        tvStatus.setPadding(0, 0, 0, 16);
        mainLayout.addView(tvStatus);

        // 聊天容器（滚动）
        scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));
        scrollView.setBackgroundColor(Color.parseColor("#F5F5F5"));

        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(8, 8, 8, 8);
        scrollView.addView(chatContainer);
        mainLayout.addView(scrollView);

        // 输入区域
        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.setPadding(0, 16, 0, 0);

        etInput = new EditText(this);
        etInput.setHint("输入消息...");
        etInput.setBackgroundResource(android.R.drawable.editbox_background);
        etInput.setPadding(16, 12, 16, 12);
        etInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        inputLayout.addView(etInput);

        btnSend = new Button(this);
        btnSend.setText("发送");
        btnSend.setBackgroundColor(Color.parseColor("#007AFF"));
        btnSend.setTextColor(Color.WHITE);
        btnSend.setPadding(24, 12, 24, 12);
        inputLayout.addView(btnSend);
        mainLayout.addView(inputLayout);

        progressBar = new ProgressBar(this);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        progressBar.setVisibility(View.GONE);
        mainLayout.addView(progressBar);

        setContentView(mainLayout);

        renderMessages();

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
                renderMessages();

                etInput.setText("");
                setLoading(true);
                waitingForResponse = true;

                // 超时检测（30秒）
                timeoutRunnable = () -> {
                    if (waitingForResponse) {
                        waitingForResponse = false;
                        setLoading(false);
                        messages.add(new ChatMessage("ai", "[超时] 请求超时，请检查网络或重试"));
                        saveHistory();
                        renderMessages();
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

                apiHelper.sendMessage(baseUrl, apiKey, model, messages, new ApiHelper.ChatCallback() {
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
                            renderMessages();
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
                            renderMessages();
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
                renderMessages();
                Toast.makeText(this, "发送失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                setLoading(false);
            }
        });
    }

    // 创建图标按钮
    private Button createIconButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(20);
        btn.setBackground(null);
        btn.setPadding(8, 0, 8, 0);
        btn.setMinimumWidth(0);
        btn.setMinimumHeight(0);
        return btn;
    }

    // 渲染消息列表（气泡样式）
    private void renderMessages() {
        chatContainer.removeAllViews();

        if (messages.isEmpty()) {
            TextView emptyHint = new TextView(this);
            emptyHint.setText("欢迎使用 AI Chat\n请输入消息开始对话");
            emptyHint.setTextSize(16);
            emptyHint.setTextColor(Color.parseColor("#999999"));
            emptyHint.setGravity(Gravity.CENTER);
            emptyHint.setPadding(0, 40, 0, 40);
            chatContainer.addView(emptyHint);
            return;
        }

        for (ChatMessage msg : messages) {
            boolean isUser = msg.getRole().equals("user");

            // 每条消息的外层容器
            LinearLayout wrapper = new LinearLayout(this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            // 消息气泡
            TextView bubble = new TextView(this);
            bubble.setText(msg.getContent());
            bubble.setTextSize(16);
            bubble.setPadding(16, 12, 16, 12);
            bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.75));

            // 设置气泡样式
            GradientDrawable drawable = new GradientDrawable();
            drawable.setCornerRadius(16);
            if (isUser) {
                drawable.setColor(Color.parseColor("#007AFF"));
                bubble.setTextColor(Color.WHITE);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                params.gravity = Gravity.END;
                params.setMargins(40, 8, 0, 8);
                bubble.setLayoutParams(params);
            } else {
                drawable.setColor(Color.parseColor("#E5E5EA"));
                bubble.setTextColor(Color.BLACK);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                params.gravity = Gravity.START;
                params.setMargins(0, 8, 40, 8);
                bubble.setLayoutParams(params);
            }
            bubble.setBackground(drawable);

            wrapper.addView(bubble);
            chatContainer.addView(wrapper);
        }

        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
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

    private void saveHistory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = gson.toJson(messages);
        prefs.edit().putString(KEY_HISTORY, json).apply();
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