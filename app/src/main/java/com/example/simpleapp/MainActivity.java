package com.example.simpleapp;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Hello World!\n应用启动成功！");
        tv.setTextSize(24);
        tv.setGravity(android.view.Gravity.CENTER);
        setContentView(tv);
    }
}