package com.opencode.hub;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

public class MainActivity extends Activity {
    private static final String TAG = "OpenCodeHub";
    private static final int PORT = 8888;
    private static final int REQUEST_ACCESSIBILITY = 1001;
    
    private Socket socket;
    private DataOutputStream out;
    private BufferedReader in;
    private Handler handler = new Handler(Looper.getMainHandler());
    private boolean isConnected = false;
    
    private LinearLayout mainLayout;
    private GridLayout categoryGrid;
    private TextView statusText;
    private ImageView robotGif;
    private MediaPlayer clickSound;
    private MediaPlayer successSound;
    private Animation pulseAnim;
    
    private ADBService adbService;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        setupUI();
        initServices();
        checkPermissions();
    }
    
    private void setupUI() {
        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#1a1a2e"));
        mainLayout.setPadding(20, 20, 20, 20);
        
        setupHeader();
        setupRobot();
        setupCategories();
        setupLogArea();
        
        setContentView(mainLayout);
    }
    
    private void setupHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER);
        
        ImageView logo = new ImageView(this);
        logo.setImageResource(android.R.drawable.ic_menu_compass);
        logo.setColorFilter(Color.parseColor("#00d9ff"));
        
        TextView title = new TextView(this);
        title.setText("Ɱօلìէօ Hub");
        title.setTextColor(Color.parseColor("#00d9ff"));
        title.setTextSize(28);
        title.setTypeface(android.graphics.Typeface.createFromAsset(getAssets(), "fonts/robot.ttf"));
        
        statusText = new TextView(this);
        statusText.setText("⚪ Inicializando...");
        statusText.setTextColor(Color.parseColor("#888888"));
        statusText.setTextSize(14);
        
        header.addView(logo);
        header.addView(title);
        
        LinearLayout statusLayout = new LinearLayout(this);
        statusLayout.setOrientation(LinearLayout.VERTICAL);
        statusLayout.addView(header);
        statusLayout.addView(statusText);
        
        mainLayout.addView(statusLayout);
    }
    
    private void setupRobot() {
        robotGif = new ImageView(this);
        robotGif.setImageResource(android.R.drawable.ic_menu_manage);
        robotGif.setColorFilter(Color.parseColor("#ff6b6b"));
        
        int size = 150;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.gravity = Gravity.CENTER;
        params.setMargins(0, 20, 0, 20);
        robotGif.setLayoutParams(params);
        
        mainLayout.addView(robotGif);
    }
    
    private void setupCategories() {
        ScrollView scroll = new ScrollView(this);
        
        categoryGrid = new GridLayout(this);
        categoryGrid.setColumnCount(4);
        categoryGrid.setRowCount(5);
        
        addCategoryButton("📱 Apps", "apps", Color.parseColor("#4ecdc4"));
        addCategoryButton("🖱 Click", "click", Color.parseColor("#45b7d1"));
        addCategoryButton("⌨️ Texto", "text", Color.parseColor("#96ceb4"));
        addCategoryButton("↩️ Navegar", "nav", Color.parseColor("#ffeaa7"));
        addCategoryButton("📜 UI", "ui", Color.parseColor("#dfe6e9"));
        addCategoryButton("🔧 ADB", "adb", Color.parseColor("#a29bfe"));
        addCategoryButton("⚙️ Sistema", "system", Color.parseColor("#fd79a8"));
        addCategoryButton("🌐 Red", "net", Color.parseColor("#00b894"));
        
        scroll.addView(categoryGrid);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        params.setMargins(0, 20, 0, 20);
        scroll.setLayoutParams(params);
        
        mainLayout.addView(scroll);
    }
    
    private void addCategoryButton(String label, String category, int color) {
        FrameLayout button = new FrameLayout(this);
        button.setBackgroundColor(color);
        button.setPadding(20, 30, 20, 30);
        
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(Color.BLACK);
        text.setTextSize(16);
        text.setGravity(Gravity.CENTER);
        
        button.addView(text);
        
        button.setOnClickListener(v -> showCategory(category));
        
        int index = categoryGrid.getChildCount();
        int row = index / 4;
        int col = index % 4;
        
        GridLayout.Spec rowSpec = GridLayout.spec(row);
        GridLayout.Spec colSpec = GridLayout.spec(col);
        
        categoryGrid.addView(button, new GridLayout.LayoutParams(rowSpec, colSpec));
    }
    
    private void setupLogArea() {
        ScrollView logScroll = new ScrollView(this);
        
        TextView logText = new TextView(this);
        logText.setText(">>> Hub iniciado\n");
        logText.setTextColor(Color.parseColor("#00ff00"));
        logText.setTextSize(12);
        logText.setId(View.generateViewId());
        
        logScroll.addView(logText);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 200);
        logScroll.setLayoutParams(params);
        
        mainLayout.addView(logScroll);
    }
    
    private void showCategory(String category) {
        showToast("Categoría: " + category);
        
        switch(category) {
            case "apps":
                adbService.listPackages();
                break;
            case "click":
                executeAction("click", "Aceptar");
                break;
            case "nav":
                executeAction("back", "");
                break;
            case "ui":
                executeAction("dump", "");
                break;
            case "adb":
                executeAction("ashizuku", "whoami");
                break;
        }
    }
    
    private void executeAction(String action, String args) {
        if (AccessibilityBridge.instance != null) {
            String result = AccessibilityBridge.instance.executeCommand(action, args);
            showToast(result);
        } else {
            showToast("⚠️ Servicio Accessibility no conectado");
        }
    }
    
    private void initServices() {
        adbService = new ADBService(this);
        adbService.checkShizuku();
        
        String uid = adbService.getUid();
        updateStatus("🟢 Conectado | UID: " + uid);
    }
    
    private void checkPermissions() {
        if (!isAccessibilityServiceEnabled()) {
            showToast("⚠️ Habilita Accessibility en Settings");
            startActivityForResult(
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                REQUEST_ACCESSIBILITY);
        } else {
            updateStatus("🟢 Accessibility: OK");
        }
    }
    
    private boolean isAccessibilityServiceEnabled() {
        String pkg = getPackageName();
        String svc = pkg + "/" + AccessibilityBridge.class.getName();
        
        String enabled = android.provider.Settings.Secure.getString(
            getContentResolver(),
            "enabled_accessibility_services");
        
        return enabled != null && enabled.contains(svc);
    }
    
    private void updateStatus(String status) {
        if (statusText != null) {
            statusText.setText(status);
        }
    }
    
    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adbService != null) {
            adbService.destroy();
        }
    }
}