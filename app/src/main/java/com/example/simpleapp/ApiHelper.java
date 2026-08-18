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

    public interface ChatCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public ApiHelper() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        gson = new Gson();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void sendMessage(String baseUrl, String apiKey, String model, String userMessage, ChatCallback callback) {
        // 防止 callback 为空
        if (callback == null) {
            Log.e(TAG, "callback is null");
            return;
        }

        new Thread(() -> {
            try {
                // 构建 JSON 请求体
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

                // 构建请求
                Request request = new Request.Builder()
                        .url(baseUrl + "/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                        .build();

                // 执行请求
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "未知错误";
                        Log.e(TAG, "API 错误: " + response.code() + " - " + errorBody);
                        mainHandler.post(() -> callback.onError("API 错误: " + response.code()));
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "API 响应: " + responseBody);

                    // 解析响应
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                    if (jsonResponse == null) {
                        mainHandler.post(() -> callback.onError("响应为空"));
                        return;
                    }

                    JsonArray choices = jsonResponse.getAsJsonArray("choices");
                    if (choices == null || choices.size() == 0) {
                        mainHandler.post(() -> callback.onError("未收到有效回复"));
                        return;
                    }

                    JsonObject firstChoice = choices.get(0).getAsJsonObject();
                    JsonObject message = firstChoice.getAsJsonObject("message");
                    if (message == null) {
                        mainHandler.post(() -> callback.onError("回复内容为空"));
                        return;
                    }

                    String content = message.get("content").getAsString();
                    if (content == null) {
                        mainHandler.post(() -> callback.onError("回复内容为空"));
                        return;
                    }

                    mainHandler.post(() -> callback.onSuccess(content));
                }
            } catch (IOException e) {
                Log.e(TAG, "网络异常", e);
                mainHandler.post(() -> callback.onError("网络异常: " + e.getMessage()));
            } catch (JsonSyntaxException e) {
                Log.e(TAG, "JSON 解析异常", e);
                mainHandler.post(() -> callback.onError("解析异常: " + e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "未知异常", e);
                mainHandler.post(() -> callback.onError("系统错误: " + e.getMessage()));
            }
        }).start();
    }
}