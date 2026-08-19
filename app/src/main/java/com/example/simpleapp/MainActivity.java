package com.example.simpleapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
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
    private int bgAlpha = 100;

    // 菜单相关
    private FrameLayout menuContainer;
    private LinearLayout menuPanel;
    private boolean isMenuOpen = false;

    // 状态栏高度
    private int statusBarHeight = 0;

    // 获取状态栏高度
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    // dp 转 px 工具方法
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        // 获取状态栏高度
        statusBarHeight = getStatusBarHeight();

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

        // 状态栏白色背景（遮挡背景图顶部），高度扩展为状态栏高度的2倍
        View statusBarView = new View(this);
        statusBarView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                statusBarHeight * 2
        ));
        statusBarView.setBackgroundColor(Color.WHITE);
        mainLayout.addView(statusBarView);

        FrameLayout contentLayer = new FrameLayout(this);
        contentLayer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout chatUI = buildChatUI();
        contentLayer.addView(chatUI);

        setupMenu(contentLayer);

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
        main.setPadding(16, 0, 16, 22);

        // 顶部栏
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int topPadding = statusBarHeight - dpToPx(8);
        if (topPadding < 0) topPadding = 0;
        topBar.setPadding(16, topPadding, 16, 16);
        topBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        tvStatus = new TextView(this);
        String modelName = settingsHelper.getModel();
        if (modelName == null || modelName.isEmpty()) {
            tvStatus.setText("未配置");
        } else {
            tvStatus.setText(modelName);
        }
        tvStatus.setTextSize(24);
        tvStatus.setTypeface(Typeface.DEFAULT_BOLD);
        tvStatus.setTextColor(themeHelper.isDarkMode() ? Color.WHITE : Color.BLACK);
        tvStatus.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        topBar.addView(tvStatus);

        Button btnMenu = new Button(this);
        btnMenu.setText("☰");
        btnMenu.setTextSize(24);
        btnMenu.setTypeface(Typeface.DEFAULT_BOLD);
        btnMenu.setBackground(null);
        btnMenu.setTextColor(themeHelper.isDarkMode() ? Color.WHITE : Color.BLACK);
        // 左边距增加，让按钮再向右移动
        btnMenu.setPadding(32, 0, 8, 0);
        btnMenu.setOnClickListener(v -> toggleMenu());
        topBar.addView(btnMenu);

        main.addView(topBar);

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

        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.setPadding(8, 2, 8, 2);

        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(24);
        if (themeHelper.isDarkMode()) {
            glass.setColor(Color.parseColor("#55FFFFFF"));
        } else {
            glass.setColor(Color.parseColor("#99FFFFFF"));
        }
        glass.setStroke(1, themeHelper.isDarkMode() ? Color.parseColor("#44FFFFFF") : Color.parseColor("#44AAAAAA"));
        inputLayout.setBackground(glass);

        etInput = new EditText(this);
        etInput.setHint("输入消息...");
        etInput.setBackground(null);
        // 输入框恢复之前大小（padding从7改为12）
        etInput.setPadding(16, 12, 16, 12);
        etInput.setTextColor(themeHelper.isDarkMode() ? Color.WHITE : Color.BLACK);
        etInput.setHintTextColor(themeHelper.isDarkMode() ? Color.LTGRAY : Color.GRAY);
        etInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        inputLayout.addView(etInput);

        btnSend = new Button(this);
        btnSend.setText("发送");
        btnSend.setBackgroundColor(Color.parseColor("#007AFF"));
        btnSend.setTextColor(Color.WHITE);
        // 仅缩小发送按钮（宽68dp，高32dp）
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                dpToPx(68),
                dpToPx(32)
        );
        btnSend.setLayoutParams(sendParams);
        btnSend.setPadding(0, 0, 0, 0);
        btnSend.setTextSize(12);
        GradientDrawable btnShape = new GradientDrawable();
        btnShape.setCornerRadius(16);
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
    }private void setupMenu(FrameLayout parent) {
    menuContainer = new FrameLayout(this);
    menuContainer.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
    ));
    menuContainer.setBackgroundColor(Color.parseColor("#66000000"));
    menuContainer.setVisibility(View.GONE);
    menuContainer.setOnClickListener(v -> closeMenu());

    menuPanel = new LinearLayout(this);
    menuPanel.setOrientation(LinearLayout.VERTICAL);
    menuPanel.setGravity(Gravity.TOP | Gravity.END);
    int panelWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.6);
    FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            panelWidth,
            ViewGroup.LayoutParams.MATCH_PARENT
    );
    panelParams.gravity = Gravity.END;
    menuPanel.setLayoutParams(panelParams);
    menuPanel.setBackgroundColor(themeHelper.isDarkMode() ? Color.parseColor("#DD333333") : Color.parseColor("#DDEEEEEE"));
    menuPanel.setPadding(20, 80, 20, 20);

    // 菜单项1：设置（左边文字，右边方向键）
    LinearLayout itemSettings = createMenuItem("设置");
    itemSettings.setOnClickListener(v -> {
        closeMenu();
        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
        startActivity(intent);
    });
    menuPanel.addView(itemSettings);

    // 菜单项2：清空（左边文字，右边方向键）
    LinearLayout itemClear = createMenuItem("清空");
    itemClear.setOnClickListener(v -> {
        closeMenu();
        messages.clear();
        saveHistory();
        renderMessages();
        Toast.makeText(MainActivity.this, "对话已清空", Toast.LENGTH_SHORT).show();
    });
    menuPanel.addView(itemClear);

    View closeArea = new View(this);
    closeArea.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1.0f
    ));
    closeArea.setOnClickListener(v -> closeMenu());
    menuPanel.addView(closeArea);

    menuContainer.addView(menuPanel);
    parent.addView(menuContainer);
}

// 创建菜单项（左边文字 + 右边方向键）
private LinearLayout createMenuItem(String text) {
    LinearLayout item = new LinearLayout(this);
    item.setOrientation(LinearLayout.HORIZONTAL);
    item.setGravity(Gravity.CENTER_VERTICAL);
    item.setPadding(16, 20, 16, 20);
    item.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
    ));
    // 添加分割线（底部细线）
    GradientDrawable divider = new GradientDrawable();
    divider.setColor(themeHelper.isDarkMode() ? Color.parseColor("#44FFFFFF") : Color.parseColor("#44000000"));
    divider.setSize(0, 1);
    item.setBackground(divider);

    // 左边文字
    TextView label = new TextView(this);
    label.setText(text);
    label.setTextSize(18);
    label.setTextColor(themeHelper.isDarkMode() ? Color.WHITE : Color.BLACK);
    label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
    item.addView(label);

    // 右边方向键 ">"
    TextView arrow = new TextView(this);
    arrow.setText(">");
    arrow.setTextSize(18);
    arrow.setTextColor(themeHelper.isDarkMode() ? Color.LTGRAY : Color.GRAY);
    arrow.setPadding(16, 0, 0, 0);
    item.addView(arrow);

    return item;
}

private void toggleMenu() {
    if (isMenuOpen) {
        closeMenu();
    } else {
        openMenu();
    }
}

private void openMenu() {
    if (menuContainer == null) return;
    menuContainer.setVisibility(View.VISIBLE);
    TranslateAnimation slideIn = new TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 1.0f,
            Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, 0.0f
    );
    slideIn.setDuration(300);
    slideIn.setInterpolator(new AccelerateDecelerateInterpolator());
    menuPanel.startAnimation(slideIn);
    isMenuOpen = true;
}

private void closeMenu() {
    if (menuContainer == null) return;
    TranslateAnimation slideOut = new TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, 1.0f,
            Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, 0.0f
    );
    slideOut.setDuration(300);
    slideOut.setInterpolator(new AccelerateDecelerateInterpolator());
    slideOut.setAnimationListener(new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {}
        @Override
        public void onAnimationEnd(Animation animation) {
            menuContainer.setVisibility(View.GONE);
        }
        @Override
        public void onAnimationRepeat(Animation animation) {}
    });
    menuPanel.startAnimation(slideOut);
    isMenuOpen = false;
}    private void applyBackground() {
        Bitmap bg = themeHelper.getBackground();
        if (bg != null) {
            bgImage.setImageBitmap(bg);
            bgImage.setBackgroundColor(Color.TRANSPARENT);
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
            String modelName = settingsHelper.getModel();
            tvStatus.setText(modelName != null && !modelName.isEmpty() ? modelName : "未配置");
        }
        renderMessages();
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
}