package com.opencode.hub;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class AccessibilityBridge extends AccessibilityService {
    private static final String TAG = "OpenCodeHub";
    public static AccessibilityBridge instance;
    public ADBService adbService;

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
                case "back": return pressBack();
                case "home": return pressHome();
                case "recents": return pressRecents();
                case "search": return searchNode(args);
                case "dump": return dumpUI();
                case "focus": return getFocused();
                case "launch": return launchApp(args);
                case "adb": 
                case "ashizuku": return executeADB(args);
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
        boolean clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        node.recycle();
        return clicked ? "OK: clicked " + selector : "ERROR: click failed";
    }

    private String inputText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "ERROR: No window";
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null && focused.isEditable()) {
            Bundle bundle = new Bundle();
            bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            boolean result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle);
            focused.recycle();
            root.recycle();
            return result ? "OK: text entered" : "ERROR: input failed";
        }
        if (focused != null) focused.recycle();
        root.recycle();
        return "ERROR: No editable field";
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

    private String launchApp(String pkg) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return "OK: launched " + pkg;
            }
            return "ERROR: Package not found: " + pkg;
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String searchNode(String criteria) {
        AccessibilityNodeInfo node = findNode(criteria);
        if (node == null) return "ERROR: Not found: " + criteria;
        String id = node.getViewIdResourceName();
        node.recycle();
        return "OK: id=" + id;
    }

    private String dumpUI() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "ERROR: No window";
        String out = dumpTree(root, 0);
        root.recycle();
        return out.substring(0, Math.min(out.length(), 2000));
    }

    private String dumpTree(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 2) return "";
        StringBuilder sb = new StringBuilder();
        String id = node.getViewIdResourceName();
        String text = node.getText() != null ? node.getText().toString() : "";
        if (id != null || text.length() > 0) {
            sb.append(id).append("|").append(text).append("\n");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                sb.append(dumpTree(child, depth + 1));
                child.recycle();
            }
        }
        return sb.toString();
    }

    private String getFocused() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "ERROR: No window";
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        String result;
        if (focused != null) {
            result = "OK: focused id=" + focused.getViewIdResourceName();
            focused.recycle();
        } else {
            result = "ERROR: No focused element";
        }
        root.recycle();
        return result;
    }

    private AccessibilityNodeInfo findNode(String criteria) {
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
        return "ERROR: ADB not connected";
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