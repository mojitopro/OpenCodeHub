package com.opencode.hub;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ADBService {
    private static final String TAG = "ADBService";
    private static final String RISH_PATH = "/data/data/com.termux/files/usr/bin/rish_shizuku.dex";
    private static final int PORT = 8889;
    
    private Context context;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isShizukuAvailable = false;
    private Process rishProcess;
    private DataOutputStream rishOut;
    private BufferedReader rishIn;
    private BufferedReader rishErr;
    
    private static ADBService instance;
    
    public ADBService(Context ctx) {
        this.context = ctx;
        instance = this;
    }
    
    public static ADBService getInstance() {
        return instance;
    }
    
    public String execute(String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) {
            return "ERROR: Empty command";
        }
        
        return executeSync(cmd);
    }
    
    private String executeSync(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.redirectErrorStream(true);
            
            String shellCmd;
            if (isShizukuAvailable && cmd.startsWith("adb ") || cmd.startsWith("pm ") || cmd.startsWith("am ")) {
                shellCmd = "app_process -Djava.class.path=" + RISH_PATH + 
                    " /system/bin --nice-name=rish rikka.shizuku.shell.ShizukuShellLoader -c '" + cmd + "'";
            } else {
                shellCmd = cmd;
            }
            
            pb.command("/system/bin/sh", "-c", shellCmd);
            Process p = pb.start();
            
            BufferedReader output = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = output.readLine()) != null) {
                sb.append(line).append("\n");
            }
            output.close();
            
            int exitCode = p.waitFor();
            String result = sb.toString();
            
            if (exitCode == 0) {
                return result.isEmpty() ? "OK" : "OK:\n" + result;
            } else {
                return "ERROR (" + exitCode + "): " + result;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error executing: " + cmd, e);
            return "ERROR: " + e.getMessage();
        }
    }
    
    public String executeWithShizuku(String cmd) {
        return executeSync("app_process -Djava.class.path=" + RISH_PATH + 
            " /system/bin --nice-name=rish rikka.shizuku.shell.ShizukuShellLoader -c '" + cmd + "'");
    }
    
    public boolean checkShizuku() {
        java.io.File f = new java.io.File(RISH_PATH);
        isShizukuAvailable = f.exists();
        Log.i(TAG, "Shizuku available: " + isShizukuAvailable);
        return isShizukuAvailable;
    }
    
    public boolean isShizukuReady() {
        return isShizukuAvailable;
    }
    
    public String getUid() {
        return executeSync("id");
    }
    
    public String getAndroidVersion() {
        return executeSync("getprop ro.build.version.release");
    }
    
    public String getSdkVersion() {
        return executeSync("getprop ro.build.version.sdk");
    }
    
    public String listPackages() {
        return executeSync("pm list packages");
    }
    
    public String installPackage(String apkPath) {
        return executeSync("pm install -r " + apkPath);
    }
    
    public String uninstallPackage(String packageName) {
        return executeSync("pm uninstall " + packageName);
    }
    
    public String enablePackage(String packageName) {
        return executeSync("pm enable " + packageName);
    }
    
    public String disablePackage(String packageName) {
        return executeSync("pm disable-user --user 0 " + packageName);
    }
    
    public String startApp(String packageName) {
        return executeSync("monkey -p " + packageName + " -c android.intent.category.LAUNCHER 1");
    }
    
    public String forceStop(String packageName) {
        return executeSync("am force-stop " + packageName);
    }
    
    public String clearData(String packageName) {
        return executeSync("pm clear " + packageName);
    }
    
    public String getAppInfo(String packageName) {
        return executeSync("dumpsys package " + packageName);
    }
    
    public String dumpActivity() {
        return executeSync("dumpsys activity activities");
    }
    
    public String dumpWindow() {
        return executeSync("dumpsys window windows");
    }
    
    public String inputText(String text) {
        return executeSync("input text '" + text.replace(" ", "%s") + "'");
    }
    
    public String inputKeyevent(int code) {
        return executeSync("input keyevent " + code);
    }
    
    public String inputSwipe(int x1, int y1, int x2, int y2, int duration) {
        return executeSync("input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + duration);
    }
    
    public String inputTap(int x, int y) {
        return executeSync("input tap " + x + " " + y);
    }
    
    public String screenshot(String path) {
        return executeSync("screencap -p " + path);
    }
    
    public String recordScreen(String path, int duration) {
        return executeSync("screenrecord --time-limit " + duration + " " + path);
    }
    
    public String getSettings(String namespace, String key) {
        return executeSync("settings get " + namespace + " " + key);
    }
    
    public String putSettings(String namespace, String key, String value) {
        return executeSync("settings put " + namespace + " " + key + " " + value);
    }
    
    public String getIPAddress() {
        return executeSync("ip addr show wlan0");
    }
    
    public String reboot(String mode) {
        return executeSync("reboot " + mode);
    }
    
    public String shutdown() {
        return executeSync("reboot -p");
    }
    
    public String killProcess(int pid) {
        return executeSync("kill " + pid);
    }
    
    public String listProcesses() {
        return executeSync("ps -A");
    }
    
    public String getMemoryInfo() {
        return executeSync("cat /proc/meminfo");
    }
    
    public String getCpuInfo() {
        return executeSync("cat /proc/cpuinfo");
    }
    
    public String getBatteryStatus() {
        return executeSync("dumpsys battery");
    }
    
    public String getLocationProviders() {
        return executeSync("dumpsys location");
    }
    
    public String toggleWifi(boolean on) {
        return executeSync("svc wifi " + (on ? "enable" : "disable"));
    }
    
    public String toggleBluetooth(boolean on) {
        return executeSync("svc bluetooth " + (on ? "enable" : "disable"));
    }
    
    public String toggleData(boolean on) {
        return executeSync("svc data " + (on ? "enable" : "disable"));
    }
    
    public void destroy() {
        if (rishProcess != null) {
            rishProcess.destroy();
            rishProcess = null;
        }
        executor.shutdown();
    }
}