package com.example.simpleapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
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

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "chat_prefs";
    private static final String KEY_HISTORY = "chat_history";
    private static final String KEY_TITLE = "chat_title";
    private static final int REQUEST_CODE_SAVE_FILE = 1004;

    private SettingsHelper settingsHelper;
    private ApiHelper apiHelper;
    private LinearLayout chatContainer;
    private EditText etInput;
    private Button btnSend;
    private ProgressBar progressBar;
    private TextView tvModelName;
    private TextView tvTitle;
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
    private String chatTitle = null;

    // 菜单相关
    private FrameLayout menuContainer;
    private LinearLayout menuPanel;
    private boolean isMenuOpen = false;

    // 状态栏高度
    private int statusBarHeight = 0;

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

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

        // 加载历史消息和标题
        loadHistory();
        chatTitle = loadTitle();

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
        updateTitleDisplay();
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

                // 生成标题：仅在第一条用户消息且标题为空时
                if (chatTitle == null && messages.isEmpty()) {
                    chatTitle = generateTitle(input);
                    saveTitle(chatTitle);
                    updateTitleDisplay();
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

                messages.add(new ChatMessage("ai", ""));
                final int aiMsgIndex = messages.size() - 1;
                saveHistory();
                renderMessages();

                apiHelper.sendMessage(baseUrl, apiKey, model, messages, new ApiHelper.ChatCallback() {
                    @Override
                    public void onSuccess(String response) {
                        runOnUiThread(() -> {
                            waitingForResponse = false;
                            if (timeoutRunnable != null) {
                                timeoutHandler.removeCallbacks(timeoutRunnable);
                            }
                            setLoading(false);
                            if (!messages.isEmpty()) {
                                messages.set(aiMsgIndex, new ChatMessage("ai", response));
                                saveHistory();
                                renderMessages();
                                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                            }
                        });
                    }

                    @Override
                    public void onChunk(String chunk) {
                        runOnUiThread(() -> {
                            if (!messages.isEmpty()) {
                                String current = messages.get(aiMsgIndex).getContent();
                                messages.set(aiMsgIndex, new ChatMessage("ai", current + chunk));
                                renderMessages();
                                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                            }
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
                            if (!messages.isEmpty()) {
                                messages.set(aiMsgIndex, new ChatMessage("ai", "[错误] " + error));
                                saveHistory();
                                renderMessages();
                            }
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

        // 顶部栏（标题 + 模型名称）
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int topPadding = statusBarHeight - dpToPx(8);
        if (topPadding < 0) topPadding = 0;
        topBar.setPadding(16, topPadding, 16, 16);
        topBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // 标题行（包含标题 + 菜单按钮）
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        tvTitle = new TextView(this);
        tvTitle.setText("新对话");
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(themeHelper.isDarkMode() ? Color.WHITE : Color.BLACK);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        titleRow.addView(tvTitle);

        Button btnMenu = new Button(this);
        btnMenu.setText("☰");
        btnMenu.setTextSize(24);
        btnMenu.setTypeface(Typeface.DEFAULT_BOLD);
        btnMenu.setBackground(null);
        btnMenu.setTextColor(themeHelper.isDarkMode() ? Color.WHITE : Color.BLACK);
        btnMenu.setPadding(150, 0, 8, 0);
        btnMenu.setOnClickListener(v -> toggleMenu());
        titleRow.addView(btnMenu);

        topBar.addView(titleRow);

        // 模型名称（副标题）
        tvModelName = new TextView(this);
        String modelName = settingsHelper.getModel();
        if (modelName == null || modelName.isEmpty()) {
            tvModelName.setText("未配置");
        } else {
            tvModelName.setText(modelName);
        }
        tvModelName.setTextSize(12);
        tvModelName.setTextColor(themeHelper.isDarkMode() ? Color.LTGRAY : Color.GRAY);
        tvModelName.setPadding(0, 2, 0, 0);
        topBar.addView(tvModelName);

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
        etInput.setPadding(16, 40, 16, 40);
        etInput.setTextSize(12);
        etInput.setTextColor(themeHelper.isDarkMode() ? Color.WHITE : Color.BLACK);
        etInput.setHintTextColor(themeHelper.isDarkMode() ? Color.LTGRAY : Color.GRAY);
        etInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        inputLayout.addView(etInput);

        btnSend = new Button(this);
        btnSend.setText("发送");
        btnSend.setBackgroundColor(Color.parseColor("#007AFF"));
        btnSend.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                dpToPx(54),
                dpToPx(28)
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
    menuPanel.setPadding(20, 100, 20, 20);

    // 设置
    LinearLayout itemSettings = createMenuItem("设置");
    itemSettings.setOnClickListener(v -> {
        closeMenu();
        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
        startActivity(intent);
    });
    menuPanel.addView(itemSettings);

    // 分割线1
    View divider1 = new View(this);
    divider1.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(1)
    ));
    divider1.setBackgroundColor(themeHelper.isDarkMode() ? Color.parseColor("#44FFFFFF") : Color.parseColor("#44000000"));
    menuPanel.addView(divider1);

    // 清空
    LinearLayout itemClear = createMenuItem("清空");
    itemClear.setOnClickListener(v -> {
        closeMenu();
        messages.clear();
        saveHistory();
        renderMessages();
        Toast.makeText(MainActivity.this, "对话已清空", Toast.LENGTH_SHORT).show();
    });
    menuPanel.addView(itemClear);

    // 分割线2
    View divider2 = new View(this);
    divider2.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(1)
    ));
    divider2.setBackgroundColor(themeHelper.isDarkMode() ? Color.parseColor("#44FFFFFF") : Color.parseColor("#44000000"));
    menuPanel.addView(divider2);

    // 导出对话
    LinearLayout itemExport = createMenuItem("导出对话");
    itemExport.setOnClickListener(v -> {
        closeMenu();
        exportChat();
    });
    menuPanel.addView(itemExport);

    // 分割线3（将新建对话与上面功能分开）
    View divider3 = new View(this);
    divider3.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(1)
    ));
    divider3.setBackgroundColor(themeHelper.isDarkMode() ? Color.parseColor("#44FFFFFF") : Color.parseColor("#44000000"));
    menuPanel.addView(divider3);

    // 新建对话
    LinearLayout itemNewChat = createMenuItem("新建对话");
    itemNewChat.setOnClickListener(v -> {
        closeMenu();
        newConversation();
    });
    menuPanel.addView(itemNewChat);

    // 底部空白区域
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

private LinearLayout createMenuItem(String text) {
    LinearLayout item = new LinearLayout(this);
    item.setOrientation(LinearLayout.HORIZONTAL);
    item.setGravity(Gravity.CENTER_VERTICAL);
    item.setPadding(16, 20, 16, 20);
    item.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
    ));
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dpToPx(8));
    bg.setColor(themeHelper.isDarkMode() ? Color.parseColor("#66333333") : Color.parseColor("#88E0E0E0"));
    item.setBackground(bg);

    TextView label = new TextView(this);
    label.setText(text);
    label.setTextSize(18);
    label.setTextColor(themeHelper.isDarkMode() ? Color.WHITE : Color.BLACK);
    label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
    item.addView(label);

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
    AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
    fadeIn.setDuration(300);
    fadeIn.setInterpolator(new AccelerateDecelerateInterpolator());
    menuContainer.startAnimation(fadeIn);
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
    AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
    fadeOut.setDuration(300);
    fadeOut.setInterpolator(new AccelerateDecelerateInterpolator());
    fadeOut.setAnimationListener(new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {}
        @Override
        public void onAnimationEnd(Animation animation) {
            menuContainer.setVisibility(View.GONE);
        }
        @Override
        public void onAnimationRepeat(Animation animation) {}
    });
    menuContainer.startAnimation(fadeOut);
    TranslateAnimation slideOut = new TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, 1.0f,
            Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, 0.0f
    );
    slideOut.setDuration(300);
    slideOut.setInterpolator(new AccelerateDecelerateInterpolator());
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
        // 更新模型名称（可能设置中已切换）
        if (tvModelName != null) {
            String modelName = settingsHelper.getModel();
            tvModelName.setText(modelName != null && !modelName.isEmpty() ? modelName : "未配置");
        }
        renderMessages();
    }

    private String formatTime(long timestamp) {
        if (timestamp == 0) {
            return "刚刚";
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
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

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            boolean isUser = msg.getRole().equals("user");

            LinearLayout wrapper = new LinearLayout(this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            if (isUser) {
                wrapper.setGravity(Gravity.END);
            } else {
                wrapper.setGravity(Gravity.START);
            }

            TextView bubble = createBubble(msg.getContent(), isUser);
            final int position = i;
            bubble.setOnLongClickListener(v -> {
                showMessageMenu(position);
                return true;
            });
            wrapper.addView(bubble);

            TextView timeView = new TextView(this);
            timeView.setText(formatTime(msg.getTimestamp()));
            timeView.setTextSize(10);
            timeView.setTextColor(Color.parseColor("#999999"));
            timeView.setPadding(4, 4, 4, 4);
            if (isUser) {
                timeView.setGravity(Gravity.END);
            } else {
                timeView.setGravity(Gravity.START);
            }
            wrapper.addView(timeView);

            chatContainer.addView(wrapper);
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

    // ----- 长按菜单功能 -----
    private void showMessageMenu(final int position) {
        if (position < 0 || position >= messages.size()) return;
        final ChatMessage msg = messages.get(position);
        if (msg.getContent().startsWith("[超时]") || msg.getContent().startsWith("[错误]") || msg.getContent().startsWith("[系统错误]")) {
            Toast.makeText(this, "错误消息不支持操作", Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] options;
        final boolean isAi = msg.getRole().equals("ai");
        if (isAi) {
            options = new String[]{"复制", "重新生成", "删除"};
        } else {
            options = new String[]{"复制", "删除"};
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择操作");
        builder.setItems(options, (dialog, which) -> {
            String selected = options[which];
            if ("复制".equals(selected)) {
                copyMessage(msg.getContent());
            } else if ("重新生成".equals(selected)) {
                regenerateMessage(position);
            } else if ("删除".equals(selected)) {
                deleteMessage(position);
            }
        });
        builder.show();
    }

    private void copyMessage(String content) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("ChatMessage", content);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }

    private void deleteMessage(int position) {
        if (position < 0 || position >= messages.size()) return;
        messages.remove(position);
        saveHistory();
        renderMessages();
        Toast.makeText(this, "消息已删除", Toast.LENGTH_SHORT).show();
    }

    private void regenerateMessage(int position) {
        if (position < 0 || position >= messages.size()) return;
        if (!messages.get(position).getRole().equals("ai")) {
            Toast.makeText(this, "只能重新生成AI回复", Toast.LENGTH_SHORT).show();
            return;
        }

        int userMsgIndex = -1;
        for (int i = position - 1; i >= 0; i--) {
            if (messages.get(i).getRole().equals("user")) {
                userMsgIndex = i;
                break;
            }
        }
        if (userMsgIndex == -1) {
            Toast.makeText(this, "没有找到对应的用户消息", Toast.LENGTH_SHORT).show();
            return;
        }

        messages.remove(position);
        int currentSize = messages.size();
        if (position < currentSize) {
            messages.subList(position, currentSize).clear();
        }
        messages.add(new ChatMessage("ai", ""));
        final int newAiIndex = messages.size() - 1;
        saveHistory();
        renderMessages();

        setLoading(true);
        waitingForResponse = true;
        timeoutRunnable = () -> {
            if (waitingForResponse) {
                waitingForResponse = false;
                setLoading(false);
                messages.set(newAiIndex, new ChatMessage("ai", "[超时] 请求超时，请检查网络或重试"));
                saveHistory();
                renderMessages();
                Toast.makeText(MainActivity.this, "请求超时", Toast.LENGTH_LONG).show();
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, 30000);

        String baseUrl = settingsHelper.getBaseUrl();
        String apiKey = settingsHelper.getApiKey();
        String model = settingsHelper.getModel();

        apiHelper.sendMessage(baseUrl, apiKey, model, messages, new ApiHelper.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    waitingForResponse = false;
                    if (timeoutRunnable != null) {
                        timeoutHandler.removeCallbacks(timeoutRunnable);
                    }
                    setLoading(false);
                    if (!messages.isEmpty()) {
                        messages.set(newAiIndex, new ChatMessage("ai", response));
                        saveHistory();
                        renderMessages();
                        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                    }
                });
            }

            @Override
            public void onChunk(String chunk) {
                runOnUiThread(() -> {
                    if (!messages.isEmpty()) {
                        String current = messages.get(newAiIndex).getContent();
                        messages.set(newAiIndex, new ChatMessage("ai", current + chunk));
                        renderMessages();
                        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                    }
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
                    if (!messages.isEmpty()) {
                        messages.set(newAiIndex, new ChatMessage("ai", "[错误] " + error));
                        saveHistory();
                        renderMessages();
                    }
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ----- 标题相关 -----
    private String generateTitle(String text) {
        if (text.length() <= 20) {
            return text;
        }
        return text.substring(0, 20) + "...";
    }

    private void saveTitle(String title) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_TITLE, title).apply();
    }

    private String loadTitle() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(KEY_TITLE, null);
    }

    private void updateTitleDisplay() {
        if (tvTitle != null) {
            if (chatTitle != null && !chatTitle.isEmpty()) {
                tvTitle.setText(chatTitle);
            } else {
                tvTitle.setText("新对话");
            }
        }
    }

    // ----- 新建对话 -----
    private void newConversation() {
        messages.clear();
        chatTitle = null;
        saveHistory();
        saveTitle(null);
        updateTitleDisplay();
        renderMessages();
        Toast.makeText(this, "已创建新对话", Toast.LENGTH_SHORT).show();
    }

    // ----- 导出对话（使用系统文件选择器） -----
    private String exportContent;

    private void exportChat() {
        if (messages.isEmpty()) {
            Toast.makeText(this, "没有可导出的对话", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("AI Chat 对话导出\n");
        sb.append("导出时间: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date())).append("\n\n");
        sb.append("====================================\n\n");

        for (ChatMessage msg : messages) {
            String role = msg.getRole().equals("user") ? "我" : "AI";
            String time = formatTime(msg.getTimestamp());
            sb.append("[").append(role).append("] ").append(time).append("\n");
            sb.append(msg.getContent()).append("\n\n");
        }

        sb.append("====================================\n");
        sb.append("—— 导出结束 ——");

        exportContent = sb.toString();

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "AI_Chat_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date()) + ".txt");
        startActivityForResult(intent, REQUEST_CODE_SAVE_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SAVE_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    android.content.ContentResolver resolver = getContentResolver();
                    java.io.OutputStream os = resolver.openOutputStream(uri);
                    if (os != null) {
                        os.write(exportContent.getBytes());
                        os.close();
                        Toast.makeText(this, "导出成功！", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "导出失败：无法写入文件", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "导出失败", e);
                    Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
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
                for (ChatMessage msg : messages) {
                    if (msg.getTimestamp() == 0) {
                        msg.setTimestamp(System.currentTimeMillis());
                    }
                }
                saveHistory();
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