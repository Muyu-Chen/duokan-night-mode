package io.github.muyuchen.duokannight;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.UUID;

public final class LauncherActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status, detail, countdown;
    private Button nightButton, dayButton;
    private boolean switching;
    private String nonce;
    private final ContentObserver observer = new ContentObserver(main) {
        public void onChange(boolean selfChange) { readState(); }
    };

    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root);

        TextView eyebrow = text("DUOKAN · NIGHT MODE", 13, 0xff666666);
        root.addView(eyebrow);
        TextView title = text("多看夜间", 30, Color.BLACK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, dp(10), 0, dp(6));
        root.addView(title);
        root.addView(text("为原生阅读页切换黑底白字", 17, 0xff555555));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(panel(0xfff2f2f2, 0xffcccccc));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, dp(24), 0, dp(22));
        root.addView(card, cardParams);
        card.addView(text("当前状态", 14, 0xff555555));
        status = text("正在检查…", 25, Color.BLACK);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setPadding(0, dp(8), 0, dp(8));
        card.addView(status);
        detail = text("正在确认阅读进程中的模式", 16, 0xff555555);
        card.addView(detail);

        nightButton = button("开启黑底白字", true);
        dayButton = button("恢复白底黑字", false);
        root.addView(nightButton, buttonParams());
        root.addView(dayButton, buttonParams());
        countdown = text("", 20, Color.BLACK);
        countdown.setPadding(0, dp(12), 0, dp(8));
        root.addView(countdown);
        root.addView(text("切换后会自动打开多看。\n进入 EPUB / TXT 书籍，才能看到配色效果。", 17, 0xff333333));
        TextView footer = text("重启设备或阅读进程退出后，需要重新开启夜间模式。\n实验版 · 已验证固件见项目说明", 13, 0xff777777);
        footer.setPadding(0, dp(24), 0, 0);
        root.addView(footer);
        setContentView(scroll);
        getContentResolver().registerContentObserver(StateProvider.URI, false, observer);
    }

    public void onResume() {
        super.onResume();
        if (!switching) checkSession();
    }

    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        getContentResolver().unregisterContentObserver(observer);
        super.onDestroy();
    }

    private void checkSession() {
        nonce = UUID.randomUUID().toString();
        status.setText("正在检查…");
        detail.setText("正在确认阅读进程中的模式");
        Intent query = new Intent(StateProvider.QUERY_ACTION).setPackage("com.duokan.einkreader");
        query.putExtra("nonce", nonce);
        sendBroadcast(query);
        final String pending = nonce;
        main.postDelayed(new Runnable() {
            public void run() {
                if (switching || !pending.equals(nonce)) return;
                Bundle state = getContentResolver().call(StateProvider.URI, "get", null, null);
                if (state == null || !pending.equals(state.getString("nonce"))) showState(false);
                else readState();
            }
        }, 1400);
    }

    private void readState() {
        Bundle state = getContentResolver().call(StateProvider.URI, "get", null, null);
        if (state != null && nonce != null && nonce.equals(state.getString("nonce")))
            showState(state.getBoolean("night"));
    }

    private void showState(boolean night) {
        status.setText(night ? "夜间模式已开启" : "夜间模式已关闭");
        detail.setText(night ? "进入书籍后显示黑底白字" : "当前使用默认白底黑字");
    }

    private void switchMode(final boolean night) {
        if (switching) return;
        switching = true;
        nightButton.setEnabled(false);
        dayButton.setEnabled(false);
        final Runnable timer = new Runnable() {
            int seconds = 3;
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                if (seconds > 0) {
                    countdown.setText((night ? "开启夜间模式" : "恢复日间模式") + " · " + seconds + " 秒后打开多看");
                    seconds--;
                    main.postDelayed(this, 1000);
                    return;
                }
                nonce = UUID.randomUUID().toString();
                Bundle args = new Bundle();
                args.putString("nonce", nonce);
                args.putString("mode", "night");
                try {
                    String runner = night ? "NightSession" : "DaySession";
                    boolean accepted = startInstrumentation(new ComponentName(StateProvider.PACKAGE,
                        StateProvider.PACKAGE + "." + runner), null, args);
                    if (!accepted) throw new IllegalStateException("切换请求未被接受");
                    countdown.setText("正在切换并打开多看…");
                    main.postDelayed(new Runnable() {
                        public void run() {
                            switching = false;
                            nightButton.setEnabled(true);
                            dayButton.setEnabled(true);
                            countdown.setText("");
                            Intent reader = getPackageManager().getLaunchIntentForPackage("com.duokan.einkreader");
                            if (reader == null) {
                                status.setText("无法打开多看");
                                detail.setText("请确认已安装受支持的原生阅读器");
                                return;
                            }
                            reader.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(reader);
                        }
                    }, 700);
                } catch (RuntimeException error) {
                    switching = false;
                    nightButton.setEnabled(true);
                    dayButton.setEnabled(true);
                    countdown.setText("");
                    status.setText("切换失败");
                    detail.setText("请检查阅读器版本及签名是否兼容");
                }
            }
        };
        main.post(timer);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String content, int size, int color) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }
    private GradientDrawable panel(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(10));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }
    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(62));
        params.setMargins(0, 0, 0, dp(12));
        return params;
    }
    private Button button(String title, final boolean night) {
        Button view = new Button(this);
        view.setText(title);
        view.setTextSize(20);
        view.setAllCaps(false);
        view.setTextColor(night ? Color.WHITE : Color.BLACK);
        view.setBackground(panel(night ? 0xff111111 : Color.WHITE, night ? 0xff111111 : 0xffaaaaaa));
        view.setOnClickListener(new View.OnClickListener() {
            public void onClick(View ignored) { switchMode(night); }
        });
        return view;
    }
}
