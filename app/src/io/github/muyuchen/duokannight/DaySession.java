package io.github.muyuchen.duokannight;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;

/** Starting this runner replaces the temporary night session, then exits. */
public final class DaySession extends Instrumentation {
    private String nonce;
    public void onCreate(Bundle args) {
        super.onCreate(args);
        nonce = args == null ? null : args.getString("nonce");
        start();
    }
    public void onStart() {
        Bundle result = new Bundle();
        try {
            StateProvider.report(getTargetContext(), nonce, false);
            result.putString("mode", "day");
            result.putBoolean("readerPreferencesWritten", false);
            finish(0, result);
        } catch (Throwable error) {
            result.putString("error", error.getClass().getSimpleName());
            finish(1, result);
        }
    }
}
