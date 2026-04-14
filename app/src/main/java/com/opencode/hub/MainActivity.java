package com.opencode.hub;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;

public class MainActivity extends Activity {
    private TextView statusText;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        
        TextView title = new TextView(this);
        title.setText("Ɱօلìէօ Hub");
        title.setTextSize(24f);
        
        statusText = new TextView(this);
        statusText.setText("Conectando...");
        
        Button appsBtn = new Button(this);
        appsBtn.setText("Apps");
        appsBtn.setOnClickListener(v -> executeAction("adb:pm list packages | head -10"));
        
        Button dumpBtn = new Button(this);
        dumpBtn.setText("UI Dump");
        dumpBtn.setOnClickListener(v -> executeAction("dump"));
        
        Button backBtn = new Button(this);
        backBtn.setText("Back");
        backBtn.setOnClickListener(v -> executeAction("back"));
        
        layout.addView(title);
        layout.addView(statusText);
        layout.addView(appsBtn);
        layout.addView(dumpBtn);
        layout.addView(backBtn);
        
        setContentView(layout);
        
        initServices();
    }
    
    private void initServices() {
        new Thread(() -> {
            ADBService adb = new ADBService(this);
            String uid = adb.getUid();
            runOnUiThread(() -> statusText.setText("UID: " + uid));
        }).start();
    }
    
    private void executeAction(String action) {
        if (AccessibilityBridge.instance != null) {
            String result = AccessibilityBridge.instance.executeCommand(action.split(":")[0], action.contains(":") ? action.split(":")[1] : "");
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Accessibility no conectado", Toast.LENGTH_SHORT).show();
        }
    }
}