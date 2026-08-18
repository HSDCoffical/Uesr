package com.example.simpleapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 使用一个简单的布局，包含 TextView 和 Button
        TextView tv = new TextView(this);
        tv.setText("Hello World!\n应用启动成功！\n点击下方进入设置");
        tv.setTextSize(24);
        tv.setGravity(android.view.Gravity.CENTER);
        
        Button btn = new Button(this);
        btn.setText("进入设置");
        btn.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
        
        // 使用 LinearLayout 垂直排列
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.addView(tv);
        layout.addView(btn);
        setContentView(layout);
    }
}