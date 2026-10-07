package io.github.muyuchen.duokannight;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/** Small authenticated bridge; stores helper state only, never reader data. */
public final class StateProvider extends ContentProvider {
    public static final String PACKAGE = "io.github.muyuchen.duokannight";
    public static final Uri URI = Uri.parse("content://" + PACKAGE + ".state");
    public static final String QUERY_ACTION = PACKAGE + ".QUERY_SESSION";

    public boolean onCreate() { return true; }

    public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        int caller = Binder.getCallingUid();
        if ("get".equals(method)) {
            if (caller != Process.myUid()) throw new SecurityException("Helper-only state read");
            SharedPreferences prefs = context.getSharedPreferences("session-state", Context.MODE_PRIVATE);
            Bundle result = new Bundle();
            result.putString("nonce", prefs.getString("nonce", ""));
            result.putBoolean("night", prefs.getBoolean("night", false));
            return result;
        }
        if (!"report".equals(method) || extras == null)
            throw new IllegalArgumentException("Unsupported state operation");

        // The exported provider accepts reports only from the installed target UID,
        // and only when target and helper certificates match.
        try {
            int targetUid = context.getPackageManager().getApplicationInfo("com.duokan.einkreader", 0).uid;
            if (caller != targetUid || context.getPackageManager().checkSignatures(caller, Process.myUid())
                    != PackageManager.SIGNATURE_MATCH) throw new SecurityException("Untrusted state reporter");
        } catch (PackageManager.NameNotFoundException missing) {
            throw new SecurityException("Target not installed");
        }
        String nonce = extras.getString("nonce", "");
        if (!nonce.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("Invalid state nonce");
        context.getSharedPreferences("session-state", Context.MODE_PRIVATE).edit()
            .putString("nonce", nonce).putBoolean("night", extras.getBoolean("night")).apply();
        context.getContentResolver().notifyChange(URI, null);
        return Bundle.EMPTY;
    }

    public static void report(Context target, String nonce, boolean night) {
        if (nonce == null || !nonce.matches("[0-9a-f-]{36}")) return;
        Bundle data = new Bundle();
        data.putString("nonce", nonce);
        data.putBoolean("night", night);
        target.getContentResolver().call(URI, "report", null, data);
    }

    public Cursor query(Uri uri, String[] p, String s, String[] a, String order) { return null; }
    public String getType(Uri uri) { return null; }
    public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
