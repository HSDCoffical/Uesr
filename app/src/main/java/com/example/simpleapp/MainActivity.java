package com.example.simpleapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
    private ThemeHelper themeHelper;
    private FrameLayout mainLayout;
    private ImageView bgImage;
    private int bgAlpha = 100; // 默认不透明

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 沉浸式
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }

        themeHelper = new ThemeHelper(this);
        settingsHelper = new SettingsHelper(this);
        apiHelper = new ApiHelper();
        bgAlpha = themeHelper.getBgAlpha();

        loadHistory();

        mainLayout = new FrameLayout(this);
        bgImage = new ImageView(this);
        bgImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bgImage.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        if (themeHelper.isDarkMode()) {
            bgImage.setBackgroundColor(Color.parseColor("#303030"));
        } else {
            bgImage.setBackgroundColor(Color.parseColor("#F5F5F5"));
        }
        mainLayout.addView(bgImage);

        // 内容层
        FrameLayout contentLayer = new FrameLayout(this);
        contentLayer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout chatUI = buildChatUI();
        contentLayer.addView(chatUI);

        mainLayout.addView(contentLayer);
        setContentView(mainLayout);

        applyBackground();
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
                messages.add(new ChatMessage("user", input));
                saveHistory();
                renderMessages();

                etInput.setText("");
                setLoading(true);
                waitingForResponse = true;

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

    private LinearLayout buildChatUI() {
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(16, 40, 16, 12); // 顶部留出状态栏

        // 顶部栏（模型名称 + 按钮）
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 8, 0, 12);

        tvStatus = new TextView(this);
        tvStatus.setText(getStatusText());
        tvStatus.setTextSize(24);
        tvStatus.setTextColor(themeHelper.isDarkMode() ? Color.WHITE : Color.BLACK);
        tvStatus.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        topBar.addView(tvStatus);

        Button btnSettings = createIconButton("⚙️");
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
        topBar.addView(btnSettings);

        Button btnClear = createIconButton("🗑️");
        btnClear.setOnClickListener(v -> {
            messages.clear();
            saveHistory();
            renderMessages();
            Toast.makeText(this, "对话已清空", Toast.LENGTH_SHORT).show();
        });
        topBar.addView(btnClear);

        main.addView(topBar);

        // 聊天容器
        scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));
        scrollView.setBackgroundColor(Color.TRANSPARENT);

        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(6, 6, 6, 6);
        scrollView.addView(chatContainer);
        main.addView(scrollView);

        // 输入区域（毛玻璃）
        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.setPadding(8, 10, 8, 10); // 减少垂直间距
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(24);
        if (themeHelper.isDarkMode()) {
            glass.setColor(Color.parseColor("#88222222"));
        } else {
            glass.setColor(Color.parseColor("#CCFFFFFF"));
        }
        glass.setStroke(1, themeHelper.isDarkMode() ? Color.parseColor("#44FFFFFF") : Color.parseColor("#44AAAAAA"));
        inputLayout.setBackground(glass);

        etInput = new EditText(this);
        etInput.setHint("输入消息...");
        etInput.setBackground(null);
        etInput.setPadding(16, 10, 16, 10);
        etInput.setTextColor(themeHelper.isDarkMode() ? Color.WHITE : Color.BLACK);
        etInput.setHintTextColor(themeHelper.isDarkMode() ? Color.LTGRAY : Color.GRAY);
        etInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        inputLayout.addView(etInput);

        btnSend = new Button(this);
        btnSend.setText("发送");
        btnSend.setBackgroundColor(Color.parseColor("#007AFF"));
        btnSend.setTextColor(Color.WHITE);
        btnSend.setPadding(20, 10, 20, 10);
        GradientDrawable btnShape = new GradientDrawable();
        btnShape.setCornerRadius(20);
        btnShape.setColor(Color.parseColor("#007AFF"));
        btnSend.setBackground(btnShape);
        inputLayout.addView(btnSend);

        main.addView(inputLayout);

        progressBar = new ProgressBar(this);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        progressBar.setVisibility(View.GONE);
        main.addView(progressBar);

        return main;
    }

    private void applyBackground() {
        Bitmap bg = themeHelper.getBackground();
        if (bg != null) {
            bgImage.setImageBitmap(bg);
            bgImage.setBackgroundColor(Color.TRANSPARENT);
            // 应用透明度
            int alpha = (int) (bgAlpha / 100.0 * 255);
            bgImage.setAlpha(alpha);
        } else {
            bgImage.setImageDrawable(null);
            if (themeHelper.isDarkMode()) {
                bgImage.setBackgroundColor(Color.parseColor("#303030"));
            } else {
                bgImage.setBackgroundColor(Color.parseColor("#F5F5F5"));
            }
            bgImage.setAlpha(255);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bgAlpha = themeHelper.getBgAlpha();
        applyBackground();
        if (tvStatus != null) {
            tvStatus.setText(getStatusText());
        }
        renderMessages();
    }

    private Button createIconButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(16);
        btn.setBackground(null);
        btn.setPadding(4, 0, 4, 0);
        btn.setMinimumWidth(0);
        btn.setMinimumHeight(0);
        btn.setTextColor(themeHelper.isDarkMode() ? Color.WHITE : Color.BLACK);
        return btn;
    }

    private void renderMessages() {
        chatContainer.removeAllViews();

        if (messages.isEmpty()) {
            TextView emptyHint = new TextView(this);
            emptyHint.setText("欢迎使用 AI Chat\n请输入消息开始对话");
            emptyHint.setTextSize(14);
            emptyHint.setTextColor(Color.parseColor("#999999"));
            emptyHint.setGravity(Gravity.CENTER);
            emptyHint.setPadding(0, 40, 0, 40);
            chatContainer.addView(emptyHint);
            return;
        }

        for (ChatMessage msg : messages) {
            boolean isUser = msg.getRole().equals("user");
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            TextView bubble = createBubble(msg.getContent(), isUser);
            if (isUser) {
                row.setGravity(Gravity.END);
                row.addView(bubble);
            } else {
                row.setGravity(Gravity.START);
                row.addView(bubble);
            }
            chatContainer.addView(row);
        }

        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private TextView createBubble(String text, boolean isUser) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(14);
        bubble.setPadding(14, 10, 14, 10);
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.7));

        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(18);
        // 毛玻璃效果：半透明白色/深色
        if (isUser) {
            drawable.setColor(Color.parseColor("#BB007AFF"));
            bubble.setTextColor(Color.WHITE);
        } else {
            drawable.setColor(Color.parseColor("#BBE5E5EA"));
            bubble.setTextColor(Color.BLACK);
        }
        bubble.setBackground(drawable);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(isUser ? 30 : 0, 4, isUser ? 0 : 30, 4);
        bubble.setLayoutParams(params);

        return bubble;
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
