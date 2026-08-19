package com.example.simpleapp;

public class ChatMessage {
    private String role;    // "user" 或 "ai"
    private String content;
    private long timestamp; // 新增：消息时间戳（毫秒）

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    // 带时间戳的构造方法（用于加载历史数据）
    public ChatMessage(String role, String content, long timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}