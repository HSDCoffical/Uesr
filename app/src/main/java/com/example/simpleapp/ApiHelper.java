package com.example.simpleapp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.util.List;
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
                .retryOnConnectionFailure(true)
                .build();
        gson = new Gson();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void sendMessage(String baseUrl, String apiKey, String model,
                            List<ChatMessage> history, ChatCallback callback) {
        if (callback == null) {
            Log.e(TAG, "callback is null");
            return;
        }

        new Thread(() -> {
            try {
                JsonArray messagesArray = new JsonArray();
                for (ChatMessage msg : history) {
                    JsonObject msgObj = new JsonObject();
                    String role = msg.getRole().equals("user") ? "user" : "assistant";
                    msgObj.addProperty("role", role);
                    msgObj.addProperty("content", msg.getContent());
                    messagesArray.add(msgObj);
                }

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", model);
                requestBody.addProperty("stream", false);
                requestBody.add("messages", messagesArray);

                String jsonBody = gson.toJson(requestBody);
                Log.d(TAG, "请求体: " + jsonBody);

                Request request = new Request.Builder()
                        .url(baseUrl + "/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "未知错误";
                        Log.e(TAG, "API 错误: " + response.code() + " - " + errorBody);
                        mainHandler.post(() -> callback.onError("API 错误: " + response.code()));
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "原始响应: " + responseBody);

                    try {
                        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                        JsonArray choices = jsonResponse.getAsJsonArray("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonObject firstChoice = choices.get(0).getAsJsonObject();
                            JsonObject message = firstChoice.getAsJsonObject("message");
                            String content = message.get("content").getAsString();
                            mainHandler.post(() -> callback.onSuccess(content));
                        } else {
                            mainHandler.post(() -> callback.onError("未收到有效回复"));
                        }
                    } catch (JsonSyntaxException e) {
                        Log.e(TAG, "JSON 解析异常", e);
                        mainHandler.post(() -> callback.onError("解析异常: " + responseBody));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "网络异常", e);
                mainHandler.post(() -> callback.onError("网络异常: " + e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "未知异常", e);
                mainHandler.post(() -> callback.onError("系统错误: " + e.getMessage()));
            }
        }).start();
    }
}