package com.opencode.hub;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.WindowManager;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class AccessibilityBridge extends AccessibilityService {
    private static final String TAG = "OpenCodeHub";
    public static AccessibilityBridge instance;
    public ADBService adbService;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private SoundPool soundPool;
    private Map<String, Integer> sounds = new HashMap<>();
    private Vibrator vibrator;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "AccessibilityBridge connected");

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | 
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 50;
        setServiceInfo(info);

        initSounds();
        vibrator = getVibrator();
        showNotification("Hub Activado", "Listo para controlar");
    }

    private void initSounds() {
        AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
    }

    private Vibrator getVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm.getDefaultVibrator();
        }
        return (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void playSound(String name) {
        Integer soundId = sounds.get(name);
        if (soundId != null) {
            soundPool.play(soundId, 1, 1, 1, 0, 1);
        }
    }

    public void vibrate(int ms) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        }
    }

    public void vibratePattern(long[] pattern) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        }
    }

    public String executeCommand(String action, String args) {
        try {
            switch (action) {
                case "click": return click(args);
                case "text": return inputText(args);
                case "scroll": return scroll(args);
                case "back": return pressBack();
                case "home": return pressHome();
                case "recents": return pressRecents();
                case "power": return pressPower();
                case "volume_up": return volumeUp();
                case "volume_down": return volumeDown();
                case "search": return searchNode(args);
                case "dump": return dumpUI();
                case "focus": return getFocused();
                case "clear": return clearText();
                case "launch": return launchApp(args);
                case "swipe": return swipe(args);
                case "longclick": return longClick(args);
                case "wait": return wait(args);
                case "adb": return executeADB(args);
                case "ashizuku": return executeShizuku(args);
                default: return "ERROR: Unknown action: " + action;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage(), e);
            return "ERROR: " + e.getMessage();
        }
    }

    private String click(String selector) {
        AccessibilityNodeInfo node = findNode(selector);
        if (node == null) return "ERROR: Not found: " + selector;
        
        if (node.isClickable()) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            vibrate(50);
            node.recycle();
            return "OK: clicked " + selector;
        }
        
        AccessibilityNodeInfo parent = node.getParent();
        while (parent != null) {
            if (parent.isClickable()) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                parent.recycle();
                node.recycle();
                vibrate(50);
                return "OK: clicked parent " + selector;
            }
            AccessibilityNodeInfo next = parent.getParent();
            parent.recycle();
            parent = next;
        }
        
        node.recycle();
        return "ERROR: Not clickable: " + selector;
    }

    private String longClick(String selector) {
        AccessibilityNodeInfo node = findNode(selector);
        if (node == null) return "ERROR: Not found: " + selector;
        
        Bundle args = new Bundle();
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_LONG_CLICK_DURATION, 500);
        node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
        vibrate(100);
        node.recycle();
        return "OK: long click " + selector;
    }

    private String inputText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "ERROR: No window";
        
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null && focused.isEditable()) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            focused.recycle();
            root.recycle();
            vibrate(30);
            return "OK: text entered";
        }
        if (focused != null) focused.recycle();
        root.recycle();
        return "ERROR: No editable field";
    }

    private String clearText() {
        return inputText("");
    }

    private String scroll(String direction) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "ERROR: No window";
        
        int action = "up".equalsIgnoreCase(direction) ? 
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : 
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
        
        boolean result = root.performAction(action);
        root.recycle();
        if (result) {
            vibrate(40);
            return "OK: scrolled " + direction;
        }
        return "ERROR: scroll failed";
    }

    private String pressBack() {
        boolean result = performGlobalAction(GLOBAL_ACTION_BACK);
        vibrate(30);
        return result ? "OK: back" : "ERROR: back failed";
    }

    private String pressHome() {
        boolean result = performGlobalAction(GLOBAL_ACTION_HOME);
        vibrate(30);
        return result ? "OK: home" : "ERROR: home failed";
    }

    private String pressRecents() {
        boolean result = performGlobalAction(GLOBAL_ACTION_RECENTS);
        vibrate(30);
        return result ? "OK: recents" : "ERROR: recents failed";
    }

    private String pressPower() {
        boolean result = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        vibrate(50);
        return result ? "OK: power dialog" : "ERROR: power failed";
    }

    private String volumeUp() {
        return executeADB("input keyevent 24");
    }

    private String volumeDown() {
        return executeADB("input keyevent 25");
    }

    private String launchApp(String packageName) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                vibrate(50);
                return "OK: launched " + packageName;
            }
            return "ERROR: Package not found: " + packageName;
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String swipe(String args) {
        String[] parts = args.split(",");
        if (parts.length != 4) return "ERROR: Use x1,y1,x2,y2";
        try {
            int x1 = Integer.parseInt(parts[0].trim());
            int y1 = Integer.parseInt(parts[1].trim());
            int x2 = Integer.parseInt(parts[2].trim());
            int y2 = Integer.parseInt(parts[3].trim());
            
            String cmd = String.format("input swipe %d %d %d %d 300", x1, y1, x2, y2);
            return executeADB(cmd);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String wait(String seconds) {
        try {
            Thread.sleep(Integer.parseInt(seconds) * 1000);
            return "OK: waited " + seconds + "s";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String searchNode(String criteria) {
        AccessibilityNodeInfo node = findNode(criteria);
        if (node == null) return "ERROR: Not found: " + criteria;
        
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        String id = node.getViewIdResourceName();
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String className = node.getClassName().toString();
        
        node.recycle();
        
        String result = "class=" + className;
        if (id != null) result += ", id=" + id;
        if (text != null) result += ", text=" + text;
        if (desc != null) result += ", desc=" + desc;
        result += ", bounds=" + bounds.left + "," + bounds.top + "," + bounds.right + "," + bounds.bottom;
        
        return "OK: " + result;
    }

    private String dumpUI() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "ERROR: No window";
        
        StringBuilder sb = new StringBuilder();
        dumpTree(root, sb, 0);
        root.recycle();
        
        String out = sb.toString();
        if (out.length() > 5000) out = out.substring(0, 5000) + "\n...[TRUNCATED]";
        return "OK:\n" + out;
    }

    private void dumpTree(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null || depth > 3) return;
        
        String indent = "";
        for (int i = 0; i < depth; i++) indent += "  ";
        
        String id = node.getViewIdResourceName();
        String text = node.getText() != null ? node.getText().toString() : "";
        String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";
        String className = node.getClassName() != null ? node.getClassName().toString() : "View";
        
        if ((id != null && !id.isEmpty()) || (text != null && !text.isEmpty()) || (desc != null && !desc.isEmpty())) {
            sb.append(indent).append(className).append("|");
            if (id != null && !id.isEmpty()) sb.append("id=").append(id).append("|");
            if (text != null && !text.isEmpty()) sb.append("text=").append(text).append("|");
            if (desc != null && !desc.isEmpty()) sb.append("desc=").append(desc);
            sb.append("\n");
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            dumpTree(child, sb, depth + 1);
            if (child != null) child.recycle();
        }
    }

    private String getFocused() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "ERROR: No window";
        
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null) {
            String id = focused.getViewIdResourceName();
            String text = focused.getText() != null ? focused.getText().toString() : "";
            String className = focused.getClassName().toString();
            focused.recycle();
            root.recycle();
            return "OK: focused=" + className + " id=" + id + " text=" + text;
        }
        root.recycle();
        return "ERROR: No focused element";
    }

    private String findNode(String criteria) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        
        criteria = criteria.toLowerCase();
        
        AccessibilityNodeInfo found = searchTree(root, criteria);
        root.recycle();
        return found;
    }

    private AccessibilityNodeInfo searchTree(AccessibilityNodeInfo node, String criteria) {
        if (node == null) return null;
        
        String text = node.getText() != null ? node.getText().toString().toLowerCase() : "";
        String desc = node.getContentDescription() != null ? node.getContentDescription().toString().toLowerCase() : "";
        String id = node.getViewIdResourceName() != null ? node.getViewIdResourceName().toLowerCase() : "";
        
        if (text.contains(criteria) || desc.contains(criteria) || id.contains(criteria)) {
            return AccessibilityNodeInfo.obtain(node);
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = searchTree(child, criteria);
            if (child != null) child.recycle();
            if (result != null) return result;
        }
        
        return null;
    }

    private String executeADB(String cmd) {
        if (adbService != null) {
            return adbService.execute(cmd);
        }
        return "ERROR: ADB service not connected";
    }

    private String executeShizuku(String cmd) {
        return executeADB(cmd);
    }

    private void showNotification(String title, String text) {
        try {
            Notification.Builder builder = new Notification.Builder(this);
            builder.setSmallIcon(android.R.drawable.ic_menu_compass);
            builder.setContentTitle(title);
            builder.setContentText(text);
            builder.setPriority(Notification.PRIORITY_LOW);
            
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(1, builder.build());
        } catch (Exception e) {
            Log.e(TAG, "Notification error: " + e.getMessage());
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        if (soundPool != null) soundPool.release();
    }
}