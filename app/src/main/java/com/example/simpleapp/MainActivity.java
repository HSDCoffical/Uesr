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
import android.text.TextUtils;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
        public long lastUpdateTime;
        public Conversation(String title, List<ChatMessage> messages) {
            this.title = title;
            this.messages = messages;
            if (messages != null && !messages.isEmpty()) {
                this.lastUpdateTime = messages.get(messages.size() - 1).getTimestamp();
            } else {
                this.lastUpdateTime = System.currentTimeMillis();
            }
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

    // 菜单相关
    private FrameLayout menuContainer;
    private LinearLayout menuPanel;
    private LinearLayout historyContainer;
    private EditText searchInput;
    private boolean isMenuOpen = false;

    private int statusBarHeight = 0;

    // 语音输入按钮
    private Button btnVoice;
    // AI切换按钮
    private Button btnSwitchAI;

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

        // 顶部白色背景高度改为 statusBarHeight * 2
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
                if (currentIndex >= 0 && currentIndex < conversationHistory.size()) {
                    conversationHistory.get(currentIndex).lastUpdateTime = System.currentTimeMillis();
                }
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
                                if (currentIndex >= 0 && currentIndex < conversationHistory.size()) {
                                    conversationHistory.get(currentIndex).lastUpdateTime = System.currentTimeMillis();
                                }
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

        // 顶部栏：模型名称 + 新建对话图标 + 菜单按钮
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int topPadding = dpToPx(33);
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

        // 新建对话图标 "+"（右移）
        Button btnNewChat = new Button(this);
        btnNewChat.setText("+");
        btnNewChat.setTextSize(24);
        btnNewChat.setTypeface(Typeface.DEFAULT_BOLD);
        btnNewChat.setBackground(null);
        btnNewChat.setTextColor(themeHelper.isDarkMode() ? Color.WHITE : Color.BLACK);
        btnNewChat.setPadding(96, 0, 8, 0);  // 左内边距 80 → 96
        btnNewChat.setOnClickListener(v -> {
            newConversation();
            closeMenuIfOpen();
        });
        topBar.addView(btnNewChat);

        // 菜单按钮 "☰"（右移）
        Button btnMenu = new Button(this);
        btnMenu.setText("☰");
        btnMenu.setTextSize(24);
        btnMenu.setTypeface(Typeface.DEFAULT_BOLD);
        btnMenu.setBackground(null);
        btnMenu.setTextColor(themeHelper.isDarkMode() ? Color.WHITE : Color.BLACK);
        btnMenu.setPadding(20, 0, 8, 0);  // 左内边距 16 → 20
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

        // ========== 按钮独立行：AI切换 + 语音输入（强制固定尺寸） ==========
        LinearLayout toolBar = new LinearLayout(this);
        toolBar.setOrientation(LinearLayout.HORIZONTAL);
        toolBar.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        toolBar.setPadding(4, 4, 4, 4);
        LinearLayout.LayoutParams toolBarParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
);
toolBarParams.setMargins(0, dpToPx(-16), 0, 0);  // 上移 8dp
toolBar.setLayoutParams(toolBarParams);

        // AI切换按钮（强制固定宽高）
        btnSwitchAI = new Button(this);
        btnSwitchAI.setText("模型切换");
        btnSwitchAI.setTextSize(11);
        btnSwitchAI.setTypeface(null, Typeface.BOLD);
        btnSwitchAI.setBackgroundColor(Color.TRANSPARENT);
        btnSwitchAI.setTextColor(Color.BLACK);
        btnSwitchAI.setPadding(0, 0, 0, 0);
        // 强制固定尺寸
        LinearLayout.LayoutParams aiParams = new LinearLayout.LayoutParams(dpToPx(65), dpToPx(30));
        btnSwitchAI.setLayoutParams(aiParams);
        GradientDrawable glassBg1 = new GradientDrawable();
        glassBg1.setCornerRadius(dpToPx(10));
        glassBg1.setColor(Color.parseColor("#AAFFFFFF"));
        btnSwitchAI.setBackground(glassBg1);
        btnSwitchAI.setOnClickListener(v -> showModelSelector());
        toolBar.addView(btnSwitchAI);

        // 语音输入按钮（强制固定宽高）
        btnVoice = new Button(this);
        btnVoice.setText("语音输入");
        btnVoice.setTextSize(11);
        btnVoice.setBackgroundColor(Color.TRANSPARENT);
        btnVoice.setPadding(0, 0, 0, 0);
        // 强制固定尺寸
        LinearLayout.LayoutParams voiceParams = new LinearLayout.LayoutParams(dpToPx(65), dpToPx(30));
        btnVoice.setLayoutParams(voiceParams);
        GradientDrawable glassBg2 = new GradientDrawable();
        glassBg2.setCornerRadius(dpToPx(10));
        glassBg2.setColor(Color.parseColor("#AAFFFFFF"));
        btnVoice.setBackground(glassBg2);
        btnVoice.setOnClickListener(v -> startVoiceInput());
        toolBar.addView(btnVoice);

        // 两个按钮之间的间距（4dp）
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(4), ViewGroup.LayoutParams.MATCH_PARENT));
        toolBar.addView(spacer, 1);

        main.addView(toolBar);

        // ========== 输入框区域（仅输入框 + 发送按钮） ==========
        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.setPadding(8, 4, 8, 4);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setCornerRadius(dpToPx(32));
        if (themeHelper.isDarkMode()) {
            inputBg.setColor(Color.parseColor("#55FFFFFF"));
        } else {
            inputBg.setColor(Color.parseColor("#99FFFFFF"));
        }
        inputBg.setStroke(1, themeHelper.isDarkMode() ? Color.parseColor("#44FFFFFF") : Color.parseColor("#44AAAAAA"));
        inputLayout.setBackground(inputBg);

        etInput = new EditText(this);
        etInput.setHint("输入消息...");
        etInput.setBackground(null);
        etInput.setPadding(24, 26, 24, 26);
        etInput.setTextSize(14);
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
sendParams.setMargins(dpToPx(-8), 0, 0, 0);        btnSend.setLayoutParams(sendParams);
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
    int panelWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.7);
    FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            panelWidth,
            ViewGroup.LayoutParams.MATCH_PARENT
    );
    panelParams.gravity = Gravity.END;
    menuPanel.setLayoutParams(panelParams);
    menuPanel.setBackgroundColor(Color.WHITE);
    menuPanel.setPadding(0, 100, 0, 0);

    // ----- 顶部搜索框 -----
    LinearLayout searchContainer = new LinearLayout(this);
    searchContainer.setOrientation(LinearLayout.HORIZONTAL);
    searchContainer.setPadding(15, 8, 15, 6);
    searchContainer.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
    ));
    GradientDrawable searchBg = new GradientDrawable();
    searchBg.setCornerRadius(dpToPx(20));
    searchBg.setColor(Color.parseColor("#F0F0F0"));
    searchContainer.setBackground(searchBg);

    ImageView searchIcon = new ImageView(this);
    searchIcon.setImageResource(android.R.drawable.ic_menu_search);
    searchIcon.setColorFilter(Color.GRAY);
    searchIcon.setPadding(8, 8, 8, 8);
    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            dpToPx(24), dpToPx(24)
    );
    iconParams.gravity = Gravity.CENTER_VERTICAL;
    searchIcon.setLayoutParams(iconParams);
    searchContainer.addView(searchIcon);

    searchInput = new EditText(this);
    searchInput.setHint("搜索对话");
    searchInput.setBackground(null);
    searchInput.setTextColor(Color.BLACK);
    searchInput.setHintTextColor(Color.GRAY);
    searchInput.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f
    ));
    searchInput.addTextChangedListener(new android.text.TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            refreshHistory(s.toString());
        }
        @Override public void afterTextChanged(android.text.Editable s) {}
    });
    searchContainer.addView(searchInput);

    menuPanel.addView(searchContainer);

    // ----- 对话历史列表（滚动）-----
    ScrollView historyScroll = new ScrollView(this);
    historyScroll.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1.0f
    ));

    historyContainer = new LinearLayout(this);
    historyContainer.setOrientation(LinearLayout.VERTICAL);
    historyContainer.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
    ));
    historyScroll.addView(historyContainer);
    menuPanel.addView(historyScroll);

    // ----- 底部“三个点”按钮（跳转设置）-----
    LinearLayout bottomBar = new LinearLayout(this);
    bottomBar.setOrientation(LinearLayout.HORIZONTAL);
    bottomBar.setGravity(Gravity.CENTER_VERTICAL);
    bottomBar.setPadding(16, 16, 16, 16);
    bottomBar.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
    ));
    // 左侧填充空白
    View spacer = new View(this);
    spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1.0f));
    bottomBar.addView(spacer);

    Button btnMore = new Button(this);
    btnMore.setText("⋮");
    btnMore.setTextSize(24);
    btnMore.setBackground(null);
    btnMore.setTextColor(Color.BLACK);
    btnMore.setOnClickListener(v -> {
        closeMenu();
        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
        startActivity(intent);
    });
    bottomBar.addView(btnMore);

    menuPanel.addView(bottomBar);

    menuContainer.addView(menuPanel);
    parent.addView(menuContainer);

    // 初始刷新历史
    refreshHistory("");
}

private void refreshHistory(String filter) {
    if (historyContainer == null) return;
    historyContainer.removeAllViews();

    List<Conversation> filtered = new ArrayList<>();
    for (Conversation conv : conversationHistory) {
        String title = conv.title != null ? conv.title : "无标题";
        if (TextUtils.isEmpty(filter) || title.contains(filter)) {
            filtered.add(conv);
        }
    }

    if (filtered.isEmpty()) {
        TextView empty = new TextView(this);
        empty.setText("暂无对话");
        empty.setTextSize(16);
        empty.setTextColor(Color.GRAY);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, 40, 0, 40);
        historyContainer.addView(empty);
        return;
    }

    long now = System.currentTimeMillis();
    long todayStart = getDayStart(now);
    long yesterdayStart = todayStart - 24 * 60 * 60 * 1000;
    long weekStart = todayStart - 7 * 24 * 60 * 60 * 1000;
    long monthStart = todayStart - 30 * 24 * 60 * 60 * 1000;

    List<Conversation> todayList = new ArrayList<>();
    List<Conversation> yesterdayList = new ArrayList<>();
    List<Conversation> weekList = new ArrayList<>();
    List<Conversation> monthList = new ArrayList<>();
    List<Conversation> olderList = new ArrayList<>();

    for (Conversation conv : filtered) {
        long t = conv.lastUpdateTime;
        if (t >= todayStart) {
            todayList.add(conv);
        } else if (t >= yesterdayStart) {
            yesterdayList.add(conv);
        } else if (t >= weekStart) {
            weekList.add(conv);
        } else if (t >= monthStart) {
            monthList.add(conv);
        } else {
            olderList.add(conv);
        }
    }

    addGroup("今天", todayList);
    addGroup("昨天", yesterdayList);
    addGroup("7天内", weekList);
    addGroup("30天内", monthList);
    if (!olderList.isEmpty()) {
        addGroup("更早", olderList);
    }
}

private void addGroup(String label, List<Conversation> list) {
    if (list.isEmpty()) return;
    TextView header = new TextView(this);
    header.setText(label);
    header.setTextSize(14);
    header.setTextColor(Color.GRAY);
    header.setPadding(16, 16, 16, 8);
    historyContainer.addView(header);

    for (int i = 0; i < list.size(); i++) {
        final int index = conversationHistory.indexOf(list.get(i));
        if (index == -1) continue;
        Conversation conv = list.get(i);
        String title = conv.title != null && !conv.title.isEmpty() ? conv.title : "无标题";
        LinearLayout item = createMenuItem(title);
        if (currentIndex == index) {
            item.setBackgroundColor(Color.parseColor("#33007AFF"));
        }
        final int finalIndex = index;
        item.setOnClickListener(v -> {
            closeMenu();
            loadConversation(finalIndex);
        });
        item.setOnLongClickListener(v -> {
            showHistoryItemMenu(finalIndex);
            return true;
        });
        historyContainer.addView(item);
    }
}

private long getDayStart(long time) {
    java.util.Calendar cal = java.util.Calendar.getInstance();
    cal.setTimeInMillis(time);
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
    cal.set(java.util.Calendar.MINUTE, 0);
    cal.set(java.util.Calendar.SECOND, 0);
    cal.set(java.util.Calendar.MILLISECOND, 0);
    return cal.getTimeInMillis();
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
    bg.setCornerRadius(dpToPx(4));
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
    refreshHistory(searchInput != null ? searchInput.getText().toString() : "");
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
}

private void closeMenuIfOpen() {
    if (isMenuOpen) closeMenu();
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
    sb.append("导出时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n");
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
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
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
                refreshHistory(searchInput != null ? searchInput.getText().toString() : "");
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
}

// ----- AI切换对话框 -----
private void showModelSelector() {
    List<ApiConfig> configs = settingsHelper.getConfigs();
    if (configs.isEmpty()) {
        Toast.makeText(this, "请先在设置中添加AI配置", Toast.LENGTH_LONG).show();
        return;
    }
    String[] names = new String[configs.size()];
    for (int i = 0; i < configs.size(); i++) {
        names[i] = configs.get(i).getName() + " (" + configs.get(i).getModel() + ")";
    }
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("选择AI模型");
    builder.setItems(names, (dialog, which) -> {
        ApiConfig selected = configs.get(which);
        settingsHelper.setCurrentConfigId(selected.getId());
        if (tvStatus != null) {
            tvStatus.setText(selected.getModel());
        }
        Toast.makeText(this, "已切换到: " + selected.getName(), Toast.LENGTH_SHORT).show();
    });
    builder.setNegativeButton("取消", null);
    AlertDialog dialog = builder.create();
    dialog.show();
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

    private String formatTime(long timestamp) {
        if (timestamp == 0) {
            return "刚刚";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
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
                runOnUiThread(() -> {
                    waitingForResponse = false;
                    if (timeoutRunnable != null) {
                        timeoutHandler.removeCallbacks(timeoutRunnable);
                    }
                    setLoading(false);
                    if (!messages.isEmpty()) {
                        messages.set(newAiIndex, new ChatMessage("ai", "[错误] " + error));
                        saveAllConversations();
                        renderMessages();
                    }
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String generateTitle(List<ChatMessage> msgs) {
        for (ChatMessage msg : msgs) {
            if (msg.getRole().equals("user")) {
                String text = msg.getContent();
                if (text.length() <= 20) return text;
                return text.substring(0, 20) + "...";
            }
        }
        return "新对话";
    }

    private void saveAllConversations() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!messages.isEmpty() && currentIndex >= 0 && currentIndex < conversationHistory.size()) {
            conversationHistory.get(currentIndex).lastUpdateTime = System.currentTimeMillis();
        }
        if (!messages.isEmpty()) {
            if (currentIndex == -1) {
                String title = generateTitle(messages);
                Conversation conv = new Conversation(title, new ArrayList<>(messages));
                conversationHistory.add(conv);
                currentIndex = conversationHistory.size() - 1;
            } else {
                conversationHistory.get(currentIndex).messages = new ArrayList<>(messages);
                String title = generateTitle(messages);
                conversationHistory.get(currentIndex).title = title;
                conversationHistory.get(currentIndex).lastUpdateTime = System.currentTimeMillis();
            }
        }
        String json = gson.toJson(conversationHistory);
        prefs.edit().putString(KEY_CONVERSATIONS, json)
                .putInt(KEY_CURRENT_INDEX, currentIndex)
                .apply();
    }

    private void loadAllConversations() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_CONVERSATIONS, "");
        if (!json.isEmpty()) {
            Type type = new TypeToken<List<Conversation>>() {}.getType();
            List<Conversation> loaded = gson.fromJson(json, type);
            if (loaded != null) {
                conversationHistory = loaded;
                for (Conversation conv : conversationHistory) {
                    if (conv.lastUpdateTime == 0 && conv.messages != null && !conv.messages.isEmpty()) {
                        conv.lastUpdateTime = conv.messages.get(conv.messages.size() - 1).getTimestamp();
                    }
                }
            }
        }
    }

    private void loadCurrentIndex() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentIndex = prefs.getInt(KEY_CURRENT_INDEX, -1);
        if (currentIndex >= 0 && currentIndex < conversationHistory.size()) {
            Conversation conv = conversationHistory.get(currentIndex);
            messages = new ArrayList<>(conv.messages);
        } else {
            messages.clear();
            currentIndex = -1;
        }
        if (messages.isEmpty()) {
            currentIndex = -1;
        }
    }

    private void loadConversation(int index) {
        if (index < 0 || index >= conversationHistory.size()) return;
        Conversation conv = conversationHistory.get(index);
        messages = new ArrayList<>(conv.messages);
        currentIndex = index;
        saveAllConversations();
        renderMessages();
        Toast.makeText(this, "已切换到: " + conv.title, Toast.LENGTH_SHORT).show();
    }

    private void newConversation() {
        if (!messages.isEmpty()) {
            if (currentIndex == -1) {
                String title = generateTitle(messages);
                Conversation conv = new Conversation(title, new ArrayList<>(messages));
                conversationHistory.add(conv);
            } else {
                conversationHistory.get(currentIndex).messages = new ArrayList<>(messages);
                String title = generateTitle(messages);
                conversationHistory.get(currentIndex).title = title;
                conversationHistory.get(currentIndex).lastUpdateTime = System.currentTimeMillis();
            }
            saveAllConversations();
        }
        messages.clear();
        currentIndex = -1;
        saveAllConversations();
        renderMessages();
        Toast.makeText(this, "已创建新对话", Toast.LENGTH_SHORT).show();
        if (isMenuOpen) {
            refreshHistory(searchInput != null ? searchInput.getText().toString() : "");
        }
    }

    private void startVoiceInput() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
                return;
            }
        }
        PackageManager pm = getPackageManager();
        Intent checkIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        if (pm.queryIntentActivities(checkIntent, 0).isEmpty()) {
            Toast.makeText(this, "您的设备不支持语音识别", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话...");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            startActivityForResult(intent, REQUEST_VOICE);
        } catch (Exception e) {
            Toast.makeText(this, "无法启动语音识别：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceInput();
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音输入", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0);
                etInput.setText(spokenText);
                etInput.setSelection(spokenText.length());
                Toast.makeText(this, "语音识别完成", Toast.LENGTH_SHORT).show();
            }
            return;
        }
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

    private String exportContent;

    private void exportChat() {
        if (messages.isEmpty()) {
            Toast.makeText(this, "没有可导出的对话", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("AI Chat 对话导出\n");
        sb.append("导出时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n\n");
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
        intent.putExtra(Intent.EXTRA_TITLE, "AI_Chat_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt");
        startActivityForResult(intent, REQUEST_CODE_SAVE_FILE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
    }

    private void setLoading(boolean loading) {
        btnSend.setEnabled(!loading);
        etInput.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}