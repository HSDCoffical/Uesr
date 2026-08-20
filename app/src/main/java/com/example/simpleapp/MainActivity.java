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
import android.speech.RecognizerIntent;
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
    private static final String KEY_CONVERSATIONS = "conversations";
    private static final String KEY_CURRENT_INDEX = "current_index";
    private static final int REQUEST_CODE_SAVE_FILE = 1004;
    private static final int REQUEST_RECORD_AUDIO = 1006;
    private static final int REQUEST_VOICE = 1007;

    public static class Conversation {
        public String title;
        public List<ChatMessage> messages;
        public Conversation(String title, List<ChatMessage> messages) {
            this.title = title;
            this.messages = messages;
        }
    }

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
    private List<Conversation> conversationHistory = new ArrayList<>();
    private int currentIndex = -1;

    private Gson gson = new Gson();
    private ThemeHelper themeHelper;
    private FrameLayout mainLayout;
    private ImageView bgImage;
    private int bgAlpha = 100;

    private FrameLayout menuContainer;
    private LinearLayout menuPanel;
    private LinearLayout historyContainer;
    private boolean isMenuOpen = false;

    private int statusBarHeight = 0;

    // 语音输入按钮
    private Button btnVoice;

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

        loadAllConversations();
        loadCurrentIndex();

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
                statusBarHeight * 3
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
                saveAllConversations();
                renderMessages();

                etInput.setText("");
                setLoading(true);
                waitingForResponse = true;

                timeoutRunnable = () -> {
                    if (waitingForResponse) {
                        waitingForResponse = false;
                        setLoading(false);
                        messages.add(new ChatMessage("ai", "[超时] 请求超时，请检查网络或重试"));
                        saveAllConversations();
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
                saveAllConversations();
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
                                saveAllConversations();
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
                                saveAllConversations();
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
                saveAllConversations();
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

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int topPadding = dpToPx(45);
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
        btnMenu.setPadding(150, 0, 8, 0);
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
        etInput.setPadding(16, 40, 16, 40);
        etInput.setTextSize(12);
        etInput.setTextColor(themeHelper.isDarkMode() ? Color.WHITE : Color.BLACK);
        etInput.setHintTextColor(themeHelper.isDarkMode() ? Color.LTGRAY : Color.GRAY);
        etInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        inputLayout.addView(etInput);

        // 麦克风按钮
        btnVoice = new Button(this);
        btnVoice.setText("🎤");
        btnVoice.setTextSize(16);
        btnVoice.setBackground(null);
        btnVoice.setOnClickListener(v -> startVoiceInput());
        inputLayout.addView(btnVoice);

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
    // 左侧遮罩背景：半透明黑色，用于渐暗效果
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
    // 菜单面板纯白背景
    menuPanel.setBackgroundColor(Color.WHITE);
    menuPanel.setPadding(20, 130, 20, 20);

    LinearLayout itemSettings = createMenuItem("设置");
    itemSettings.setOnClickListener(v -> {
        closeMenu();
        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
        startActivity(intent);
    });
    menuPanel.addView(itemSettings);

    View divider1 = new View(this);
    divider1.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(1)
    ));
    divider1.setBackgroundColor(Color.parseColor("#44000000"));
    menuPanel.addView(divider1);

    LinearLayout itemClear = createMenuItem("清空");
    itemClear.setOnClickListener(v -> {
        closeMenu();
        messages.clear();
        saveAllConversations();
        renderMessages();
        Toast.makeText(this, "对话已清空", Toast.LENGTH_SHORT).show();
    });
    menuPanel.addView(itemClear);

    View divider2 = new View(this);
    divider2.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(1)
    ));
    divider2.setBackgroundColor(Color.parseColor("#44000000"));
    menuPanel.addView(divider2);

    LinearLayout itemExport = createMenuItem("导出对话");
    itemExport.setOnClickListener(v -> {
        closeMenu();
        exportChat();
    });
    menuPanel.addView(itemExport);

    View divider3 = new View(this);
    divider3.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(1)
    ));
    divider3.setBackgroundColor(Color.parseColor("#44000000"));
    menuPanel.addView(divider3);

    LinearLayout itemNewChat = createMenuItem("新建对话");
    itemNewChat.setOnClickListener(v -> {
        closeMenu();
        newConversation();
    });
    menuPanel.addView(itemNewChat);

    View divider4 = new View(this);
    divider4.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(1)
    ));
    divider4.setBackgroundColor(Color.parseColor("#44000000"));
    menuPanel.addView(divider4);

    LinearLayout headerRow = new LinearLayout(this);
    headerRow.setOrientation(LinearLayout.HORIZONTAL);
    headerRow.setPadding(16, 16, 16, 8);
    headerRow.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
    ));
    TextView headerLabel = new TextView(this);
    headerLabel.setText("对话历史");
    headerLabel.setTextSize(14);
    headerLabel.setTextColor(Color.GRAY);
    headerRow.addView(headerLabel);
    menuPanel.addView(headerRow);

    historyContainer = new LinearLayout(this);
    historyContainer.setOrientation(LinearLayout.VERTICAL);
    historyContainer.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
    ));
    menuPanel.addView(historyContainer);

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
    item.setPadding(16, 24, 16, 24);
    item.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
    ));
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dpToPx(8));
    bg.setColor(Color.TRANSPARENT);
    item.setBackground(bg);

    TextView label = new TextView(this);
    label.setText(text);
    label.setTextSize(16);
    label.setTextColor(Color.BLACK);
    label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
    item.addView(label);

    TextView arrow = new TextView(this);
    arrow.setText(">");
    arrow.setTextSize(16);
    arrow.setTextColor(Color.parseColor("#999999"));
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
    refreshHistory();
    menuContainer.setVisibility(View.VISIBLE);
    // 左侧渐暗：从透明到半透明黑色
    AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
    fadeIn.setDuration(300);
    fadeIn.setInterpolator(new AccelerateDecelerateInterpolator());
    menuContainer.startAnimation(fadeIn);
    // 右侧滑入
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
    // 左侧渐亮：从半透明黑色到透明
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
    // 右侧滑出
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
}

private void refreshHistory() {
    if (historyContainer == null) return;
    historyContainer.removeAllViews();

    if (conversationHistory.isEmpty()) {
        TextView empty = new TextView(this);
        empty.setText("暂无对话");
        empty.setTextSize(14);
        empty.setTextColor(Color.GRAY);
        empty.setPadding(16, 12, 16, 12);
        historyContainer.addView(empty);
        return;
    }

    for (int i = 0; i < conversationHistory.size(); i++) {
        final int index = i;
        Conversation conv = conversationHistory.get(i);
        String title = conv.title != null && !conv.title.isEmpty() ? conv.title : "无标题";
        LinearLayout item = createMenuItem(title);
        if (currentIndex == i) {
            item.setBackgroundColor(Color.parseColor("#33007AFF"));
        }
        item.setOnClickListener(v -> {
            closeMenu();
            loadConversation(index);
        });
        item.setOnLongClickListener(v -> {
            showHistoryItemMenu(index);
            return true;
        });
        historyContainer.addView(item);
    }
}

private void showHistoryItemMenu(final int index) {
    if (index < 0 || index >= conversationHistory.size()) return;
    final Conversation conv = conversationHistory.get(index);
    String title = conv.title != null && !conv.title.isEmpty() ? conv.title : "无标题";

    String[] options = {"导出此对话", "删除此对话"};
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(title);
    builder.setItems(options, (dialog, which) -> {
        if (which == 0) {
            exportSingleConversation(index);
        } else if (which == 1) {
            deleteConversation(index);
        }
    });
    builder.show();
}

private void exportSingleConversation(int index) {
    if (index < 0 || index >= conversationHistory.size()) return;
    Conversation conv = conversationHistory.get(index);
    if (conv.messages == null || conv.messages.isEmpty()) {
        Toast.makeText(this, "该对话为空，无法导出", Toast.LENGTH_SHORT).show();
        return;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("AI Chat 对话导出\n");
    sb.append("导出时间: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date())).append("\n");
    sb.append("标题: ").append(conv.title != null ? conv.title : "无标题").append("\n\n");
    sb.append("====================================\n\n");

    for (ChatMessage msg : conv.messages) {
        String role = msg.getRole().equals("user") ? "我" : "AI";
        String time = formatTime(msg.getTimestamp());
        sb.append("[").append(role).append("] ").append(time).append("\n");
        sb.append(msg.getContent()).append("\n\n");
    }
    sb.append("====================================\n");
    sb.append("—— 导出结束 ——");

    String content = sb.toString();

    try {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String fileName = "AI_Chat_" + conv.title.replaceAll("[^a-zA-Z0-9]", "_") + "_" +
                new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date()) + ".txt";
        File file = new File(dir, fileName);
        FileWriter writer = new FileWriter(file);
        writer.write(content);
        writer.close();
        Toast.makeText(this, "导出成功！\n" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
    } catch (Exception e) {
        Log.e(TAG, "导出失败", e);
        Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
}

private void deleteConversation(int index) {
    if (index < 0 || index >= conversationHistory.size()) return;
    String title = conversationHistory.get(index).title;
    new AlertDialog.Builder(this)
            .setTitle("删除对话")
            .setMessage("确定要删除 \"" + title + "\" 吗？")
            .setPositiveButton("删除", (dialog, which) -> {
                conversationHistory.remove(index);
                if (currentIndex == index) {
                    currentIndex = -1;
                    messages.clear();
                    renderMessages();
                } else if (currentIndex > index) {
                    currentIndex--;
                }
                saveAllConversations();
                refreshHistory();
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
}private void applyBackground() {
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
    saveAllConversations();
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
    saveAllConversations();
    renderMessages();

    setLoading(true);
    waitingForResponse = true;
    timeoutRunnable = () -> {
        if (waitingForResponse) {
            waitingForResponse = false;
            setLoading(false);
            messages.set(newAiIndex, new ChatMessage("ai", "[超时] 请求超时，请检查网络或重试"));
            saveAllConversations();
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
                    saveAllConversations();
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
            runOn