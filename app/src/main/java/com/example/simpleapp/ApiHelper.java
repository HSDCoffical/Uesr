package com.example.simpleapp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ApiHelper {
    private static final String TAG = "ApiHelper";
    private final OkHttpClient client;
    private final Gson gson;
    private final Handler mainHandler;

    public interface ChatCallback {
        void onSuccess(String response);   // 完整回复（保留兼容）
        void onChunk(String chunk);        // 逐字片段
        void onError(String error);
    }

    public ApiHelper() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)  // 流式响应需更长时间
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
                // 构建消息数组
                com.google.gson.JsonArray messagesArray = new com.google.gson.JsonArray();
                for (ChatMessage msg : history) {
                    JsonObject msgObj = new JsonObject();
                    String role = msg.getRole().equals("user") ? "user" : "assistant";
                    msgObj.addProperty("role", role);
                    msgObj.addProperty("content", msg.getContent());
                    messagesArray.add(msgObj);
                }

                // 请求体：stream: true 开启流式
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", model);
                requestBody.addProperty("stream", true);
                requestBody.add("messages", messagesArray);

                String jsonBody = gson.toJson(requestBody);
                Log.d(TAG, "请求体: " + jsonBody);

                Request request = new Request.Builder()
                        .url(baseUrl + "/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                        .build();

                long startTime = System.currentTimeMillis();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "未知错误";
                        Log.e(TAG, "API 错误: " + response.code() + " - " + errorBody);
                        mainHandler.post(() -> callback.onError("API 错误: " + response.code()));
                        return;
                    }

                    // 流式读取
                    ResponseBody body = response.body();
                    if (body == null) {
                        mainHandler.post(() -> callback.onError("响应为空"));
                        return;
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()));
                    String line;
                    StringBuilder fullContent = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data)) {
                                break;
                            }
                            try {
                                JsonObject jsonChunk = gson.fromJson(data, JsonObject.class);
                                if (jsonChunk.has("choices")) {
                                    com.google.gson.JsonArray choices = jsonChunk.getAsJsonArray("choices");
                                    if (choices.size() > 0) {
                                        JsonObject firstChoice = choices.get(0).getAsJsonObject();
                                        JsonObject delta = firstChoice.getAsJsonObject("delta");
                                        if (delta != null && delta.has("content")) {
                                            String chunk = delta.get("content").getAsString();
                                            if (chunk != null && !chunk.isEmpty()) {
                                                fullContent.append(chunk);
                                                // 逐字回调
                                                mainHandler.post(() -> callback.onChunk(chunk));
                                            }
                                        }
                                        // 检查 finish_reason
                                        if (firstChoice.has("finish_reason") && !firstChoice.get("finish_reason").isJsonNull()) {
                                            String reason = firstChoice.get("finish_reason").getAsString();
                                            if ("stop".equals(reason)) {
                                                break;
                                            }
                                        }
                                    }
                                }
                            } catch (JsonSyntaxException e) {
                                Log.e(TAG, "解析JSON块失败: " + data, e);
                            }
                        }
                    }
                    reader.close();

                    // 最终成功回调，传递完整内容
                    mainHandler.post(() -> callback.onSuccess(fullContent.toString()));

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