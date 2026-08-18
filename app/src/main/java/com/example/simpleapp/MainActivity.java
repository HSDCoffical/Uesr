package com.example.simpleapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.view.Gravity;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Hello World!\n应用启动成功！");
        tv.setTextSize(24);
        tv.setGravity(Gravity.CENTER);
        setContentView(tv);
    }
}