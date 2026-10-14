package de.robv.android.xposed;

import java.lang.reflect.Member;

public class XposedBridge {
    public static void log(String text) {
        android.util.Log.i("Xposed", text);
    }

    public static void log(Throwable t) {
        android.util.Log.e("Xposed", "Error", t);
    }

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        return null;
    }
}
