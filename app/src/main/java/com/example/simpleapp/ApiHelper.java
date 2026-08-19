package com.example.simpleapp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiHelper {
    private static final String TAG = "ApiHelper";
    private final OkHttpClient client;
    private final Gson gson;
    private final Handler mainHandler;
    private volatile boolean cancelled = false;  // 取消标志

    public interface ChatCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public ApiHelper() {
        // 增加超时时间并添加连接、读取、写入超时
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)  // 自动重试
                .build();
        gson = new Gson();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void sendMessage(String baseUrl, String apiKey, String model, String userMessage, ChatCallback callback) {
        if (callback == null) {
            Log.e(TAG, "callback is null");
            return;
        }

        // 取消之前的请求（如果有）
        cancelled = false;

        new Thread(() -> {
            try {
                // 构建请求体
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", model);
                requestBody.addProperty("stream", false);

                JsonArray messages = new JsonArray();
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", userMessage);
                messages.add(userMsg);
                requestBody.add("messages", messages);

                String jsonBody = gson.toJson(requestBody);
                Log.d(TAG, "请求体: " + jsonBody);

                // 构建请求
                Request request = new Request.Builder()
                        .url(baseUrl + "/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                        .build();

                // 执行请求
                long startTime = System.currentTimeMillis();
                try (Response response = client.newCall(request).execute()) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    Log.d(TAG, "请求耗时: " + elapsed + "ms");

                    if (cancelled) {
                        Log.d(TAG, "请求已取消");
                        return;
                    }

                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "未知错误";
                        Log.e(TAG, "API 错误: " + response.code() + " - " + errorBody);
                        mainHandler.post(() -> callback.onError("API 错误: " + response.code()));
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "原始响应: " + responseBody);

                    // 尝试解析
                    try {
                        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                        JsonArray choices = jsonResponse.getAsJsonArray("choices");
                        if (choices.size() > 0) {
                            JsonObject firstChoice = choices.get(0).getAsJsonObject();
                            JsonObject message = firstChoice.getAsJsonObject("message");
                            String content = message.get("content").getAsString();
                            mainHandler.post(() -> callback.onSuccess(content));
                        } else {
                            mainHandler.post(() -> callback.onError("未收到有效回复"));
                        }
                    } catch (JsonSyntaxException e) {
                        Log.e(TAG, "JSON 解析异常", e);
                        // 如果不是 JSON，可能是纯文本错误
                        mainHandler.post(() -> callback.onError("解析异常: " + responseBody));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "网络异常", e);
                if (!cancelled) {
                    mainHandler.post(() -> callback.onError("网络异常: " + e.getMessage()));
                }
            } catch (Exception e) {
                Log.e(TAG, "未知异常", e);
                if (!cancelled) {
                    mainHandler.post(() -> callback.onError("系统错误: " + e.getMessage()));
                }
            }
        }).start();
    }

    // 取消当前请求（可扩展）
    public void cancel() {
        cancelled = true;
    }
}