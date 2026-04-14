package com.opencode.hub;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class AccessibilityBridge extends AccessibilityService {
    private static final String TAG = "OpenCodeHub";
    public static AccessibilityBridge instance;
    public ADBService adbService;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

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
                case "search": return searchNode(args);
                case "dump": return dumpUI();
                case "focus": return getFocused();
                case "clear": return clearText();
                case "launch": return launchApp(args);
                case "swipe": return swipe(args);
                case "wait": return wait(args);
                case "adb": return executeADB(args);
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
            node.recycle();
            return "OK: clicked " + selector;
        }
        
        AccessibilityNodeInfo parent = node.getParent();
        while (parent != null) {
            if (parent.isClickable()) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                parent.recycle();
                node.recycle();
                return "OK: clicked parent " + selector;
            }
            AccessibilityNodeInfo next = parent.getParent();
            parent.recycle();
            parent = next;
        }
        
        node.recycle();
        return "ERROR: Not clickable: " + selector;
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
        return result ? "OK: scrolled" : "ERROR: scroll failed";
    }

    private String pressBack() {
        boolean result = performGlobalAction(GLOBAL_ACTION_BACK);
        return result ? "OK: back" : "ERROR: back failed";
    }

    private String pressHome() {
        boolean result = performGlobalAction(GLOBAL_ACTION_HOME);
        return result ? "OK: home" : "ERROR: home failed";
    }

    private String pressRecents() {
        boolean result = performGlobalAction(GLOBAL_ACTION_RECENTS);
        return result ? "OK: recents" : "ERROR: recents failed";
    }

    private String launchApp(String packageName) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return "OK: launched " + packageName;
            }
            return "ERROR: Package not found: " + packageName;
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String swipe(String args) {
        if (adbService != null) {
            return adbService.inputSwipe(args);
        }
        return "ERROR: ADB service not available";
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
        
        String id = node.getViewIdResourceName();
        String text = node.getText() != null ? node.getText().toString() : "";
        node.recycle();
        
        return "OK: id=" + id + " text=" + text;
    }

    private String dumpUI() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "ERROR: No window";
        
        StringBuilder sb = new StringBuilder();
        dumpTree(root, sb, 0);
        root.recycle();
        
        String out = sb.toString();
        if (out.length() > 3000) out = out.substring(0, 3000) + "\n...[TRUNCATED]";
        return "OK:\n" + out;
    }

    private void dumpTree(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null || depth > 3) return;
        
        String indent = "";
        for (int i = 0; i < depth; i++) indent += "  ";
        
        String id = node.getViewIdResourceName();
        String text = node.getText() != null ? node.getText().toString() : "";
        
        if ((id != null && !id.isEmpty()) || (text != null && !text.isEmpty())) {
            sb.append(indent).append(id).append("|").append(text).append("\n");
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
            focused.recycle();
            root.recycle();
            return "OK: focused id=" + id;
        }
        root.recycle();
        return "ERROR: No focused element";
    }

    private String findNode(String criteria) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        
        AccessibilityNodeInfo found = searchTree(root, criteria.toLowerCase());
        root.recycle();
        return found;
    }

    private AccessibilityNodeInfo searchTree(AccessibilityNodeInfo node, String criteria) {
        if (node == null) return null;
        
        String text = node.getText() != null ? node.getText().toString().toLowerCase() : "";
        String id = node.getViewIdResourceName() != null ? node.getViewIdResourceName().toLowerCase() : "";
        
        if (text.contains(criteria) || id.contains(criteria)) {
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

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }
}