package com.glass.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class Api {
    public static class Msg {
        public final long id, ts;
        public final String from, to, text;
        Msg(long id, String from, String to, String text, long ts) {
            this.id = id; this.from = from; this.to = to; this.text = text; this.ts = ts;
        }
    }

    public static class Chat {
        public final String peer, last;
        public final long ts;
        public final boolean mine;
        Chat(String peer, String last, long ts, boolean mine) {
            this.peer = peer; this.last = last; this.ts = ts; this.mine = mine;
        }
    }

    private final String base;

    public Api(String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private String call(String path, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(8000);
        if (body != null) {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.getOutputStream().write(body.getBytes("UTF-8"));
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    public List<Chat> chats(String me) throws Exception {
        JSONArray a = new JSONArray(call("/chats?me=" + enc(me), null));
        List<Chat> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            out.add(new Chat(o.getString("peer"), o.getString("last"), o.getLong("ts"), o.getBoolean("mine")));
        }
        return out;
    }

    public List<Msg> history(String me, String peer, long after) throws Exception {
        JSONArray a = new JSONArray(call("/history?me=" + enc(me) + "&peer=" + enc(peer) + "&after=" + after, null));
        List<Msg> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            out.add(new Msg(o.getLong("id"), o.getString("from"), o.getString("to"), o.getString("text"), o.getLong("ts")));
        }
        return out;
    }

    public void send(String me, String to, String text) throws Exception {
        call("/send", new JSONObject().put("from", me).put("to", to).put("text", text).toString());
    }
}

class Glass {
    public static final int BLUE = 0xFF3D7BFF, VIOLET = 0xFF9B4DFF, CYAN = 0xFF25D9E8, PINK = 0xFFFF4DA6;
    public static final int WHITE = Color.WHITE, DIM = 0x99FFFFFF;

    public static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    /** Стекло: полупрозрачный градиент + светлая кромка. */
    public static GradientDrawable glass(Context c, int radiusDp, int tint, float strength) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{alpha(tint, (int) (46 * strength)), alpha(tint, (int) (13 * strength))});
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(Math.max(1, dp(c, 1)), 0x66FFFFFF);
        return d;
    }

    /** Чёрный фон с цветными размытыми пятнами. */
    public static class Bg extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Bg(Context c) { super(c); }

        private void blob(Canvas cv, int color, float x, float y, float r) {
            p.setShader(new RadialGradient(x, y, r, alpha(color, 102), alpha(color, 0), Shader.TileMode.CLAMP));
            cv.drawCircle(x, y, r, p);
        }

        @Override
        protected void onDraw(Canvas cv) {
            cv.drawColor(Color.BLACK);
            float w = getWidth(), h = getHeight();
            blob(cv, VIOLET, w * 0.10f, h * 0.10f, w * 0.85f);
            blob(cv, CYAN, w * 0.95f, h * 0.45f, w * 0.70f);
            blob(cv, PINK, w * 0.20f, h * 0.92f, w * 0.80f);
            blob(cv, BLUE, w * 0.85f, h * 0.98f, w * 0.55f);
        }
    }

    public static TextView text(Context c, String s, int sp, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    public static EditText field(Context c, String hint, boolean multiline) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(0x73FFFFFF);
        e.setTextColor(WHITE);
        e.setTextSize(16);
        e.setBackground(glass(c, 18, WHITE, 1f));
        e.setPadding(dp(c, 16), dp(c, 13), dp(c, 16), dp(c, 13));
        if (multiline) {
            e.setMaxLines(4);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        } else {
            e.setSingleLine(true);
        }
        return e;
    }

    public static TextView button(Context c, String s) {
        TextView t = text(c, s, 16, true, WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackground(glass(c, 18, BLUE, 2.2f));
        t.setPadding(dp(c, 22), dp(c, 14), dp(c, 22), dp(c, 14));
        return t;
    }

    public static TextView avatar(Context c, String name, int sizeDp) {
        int[] colors = {BLUE, VIOLET, CYAN, PINK};
        int col = colors[(name.hashCode() & 0x7fffffff) % colors.length];
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{alpha(col, 230), alpha(col, 64)});
        d.setShape(GradientDrawable.OVAL);
        d.setStroke(Math.max(1, dp(c, 1)), 0x80FFFFFF);
        TextView t = text(c, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(),
                (int) (sizeDp / 2.4), true, WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackground(d);
        return t;
    }
}

public class MainActivity extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private SharedPreferences prefs;
    private FrameLayout root;
    private Api api;
    private String me = "", server = "", peer = null, listSig = "";
    private long lastId = 0;
    private int gen = 0; // номер текущего экрана: устаревшие ответы отбрасываются
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        root = new FrameLayout(this);
        setContentView(root);
        prefs = getSharedPreferences("glass", MODE_PRIVATE);
        me = prefs.getString("me", "");
        server = prefs.getString("server", "http://10.0.2.2:8080");
        if (me.isEmpty()) showLogin(); else showList();
    }

    @Override
    public void onBackPressed() {
        if (peer != null) showList(); else super.onBackPressed();
    }

    private int dp(int v) { return Glass.dp(this, v); }

    private void setScreen(View v) {
        root.removeAllViews();
        root.addView(new Glass.Bg(this), new FrameLayout.LayoutParams(MATCH, MATCH));
        root.addView(v, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    private LinearLayout.LayoutParams lp(int w, int hh, float weight, int l, int t, int r, int bt) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, hh, weight);
        p.setMargins(dp(l), dp(t), dp(r), dp(bt));
        return p;
    }

    private static String fmt(long ts) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
    }

    private static String cleanNick(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.trim().toLowerCase().toCharArray())
            if (Character.isLetterOrDigit(ch) || ch == '_') sb.append(ch);
        return sb.length() > 24 ? sb.substring(0, 24) : sb.toString();
    }

    // ---------- ВХОД ----------
    private void showLogin() {
        gen++;
        h.removeCallbacksAndMessages(null);
        peer = null;
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(dp(28), dp(28), dp(28), dp(28));

        col.addView(Glass.text(this, "Glass", 44, true, Glass.WHITE));
        col.addView(Glass.text(this, "мессенджер из стекла", 16, false, Glass.DIM));
        final EditText nick = Glass.field(this, "Твой ник (буквы, цифры, _)", false);
        final EditText srv = Glass.field(this, "Адрес сервера", false);
        srv.setText(server);
        col.addView(nick, lp(MATCH, WRAP, 0, 0, 36, 0, 12));
        col.addView(srv, lp(MATCH, WRAP, 0, 0, 0, 0, 24));
        TextView go = Glass.button(this, "Войти");
        go.setOnClickListener(v -> {
            String n = cleanNick(nick.getText().toString());
            String s = srv.getText().toString().trim();
            if (n.isEmpty() || s.isEmpty()) return;
            me = n; server = s;
            prefs.edit().putString("me", me).putString("server", server).apply();
            showList();
        });
        col.addView(go, lp(MATCH, WRAP, 0, 0, 0, 0, 0));
        setScreen(col);
    }

    // ---------- СПИСОК ЧАТОВ ----------
    private void showList() {
        final int g = ++gen;
        h.removeCallbacksAndMessages(null);
        peer = null;
        listSig = "";
        api = new Api(server);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(20), dp(16), dp(20), dp(16));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Glass.text(this, "Glass", 30, true, Glass.WHITE));
        final TextView sub = Glass.text(this, "@" + me, 13, false, Glass.DIM);
        titles.addView(sub);
        head.addView(titles, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView out = Glass.text(this, "выйти", 13, false, Glass.WHITE);
        out.setBackground(Glass.glass(this, 14, Glass.WHITE, 1f));
        out.setPadding(dp(12), dp(8), dp(12), dp(8));
        out.setOnClickListener(v -> {
            prefs.edit().remove("me").apply();
            me = "";
            showLogin();
        });
        head.addView(out);
        col.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), 0, dp(16), 0);
        ScrollView sv = new ScrollView(this);
        sv.addView(list, new FrameLayout.LayoutParams(MATCH, WRAP));
        col.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(16), dp(16), dp(16), dp(16));
        final EditText f = Glass.field(this, "Ник, кому написать", false);
        bottom.addView(f, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView open = Glass.button(this, "Чат");
        open.setOnClickListener(v -> {
            String p = cleanNick(f.getText().toString());
            if (!p.isEmpty()) { f.setText(""); showChat(p); }
        });
        bottom.addView(open, lp(WRAP, WRAP, 0, 10, 0, 0, 0));
        col.addView(bottom, new LinearLayout.LayoutParams(MATCH, WRAP));
        setScreen(col);

        final Runnable[] loop = new Runnable[1];
        loop[0] = () -> io.execute(() -> {
            List<Api.Chat> r = null;
            try { r = api.chats(me); } catch (Exception ignored) { }
            final List<Api.Chat> res = r;
            h.post(() -> {
                if (g != gen) return;
                if (res == null) {
                    sub.setText("нет связи с сервером");
                    sub.setTextColor(Glass.PINK);
                } else {
                    sub.setText("@" + me);
                    sub.setTextColor(Glass.DIM);
                    fillList(list, res);
                }
                h.postDelayed(loop[0], 2000);
            });
        });
        loop[0].run();
    }

    private void fillList(LinearLayout list, List<Api.Chat> chats) {
        StringBuilder sig = new StringBuilder();
        for (Api.Chat c : chats) sig.append(c.peer).append(c.ts).append(';');
        if (sig.toString().equals(listSig)) return;
        listSig = sig.toString();
        list.removeAllViews();
        if (chats.isEmpty()) {
            TextView hint = Glass.text(this, "Пока пусто. Напиши другу по нику ↓", 15, false, 0x80FFFFFF);
            hint.setGravity(Gravity.CENTER);
            list.addView(hint, lp(MATCH, WRAP, 0, 0, 80, 0, 0));
            return;
        }
        for (final Api.Chat c : chats) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Glass.glass(this, 22, Glass.WHITE, 1f));
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            row.addView(Glass.avatar(this, c.peer, 48), new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.addView(Glass.text(this, c.peer, 17, true, Glass.WHITE));
            String last = c.last.replace('\n', ' ');
            if (last.length() > 60) last = last.substring(0, 60);
            mid.addView(Glass.text(this, (c.mine ? "Ты: " : "") + last, 14, false, Glass.DIM));
            row.addView(mid, lp(0, WRAP, 1f, 14, 0, 0, 0));
            row.addView(Glass.text(this, fmt(c.ts), 12, false, 0x80FFFFFF));
            row.setOnClickListener(v -> showChat(c.peer));
            list.addView(row, lp(MATCH, WRAP, 0, 0, 0, 0, 10));
        }
    }

    // ---------- ПЕРЕПИСКА ----------
    private void showChat(final String p) {
        final int g = ++gen;
        h.removeCallbacksAndMessages(null);
        peer = p;
        lastId = 0;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackground(Glass.glass(this, 24, Glass.WHITE, 1f));
        head.setPadding(dp(10), dp(10), dp(10), dp(10));
        TextView back = Glass.text(this, "←", 22, false, Glass.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showList());
        head.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));
        head.addView(Glass.avatar(this, p, 40), new LinearLayout.LayoutParams(dp(40), dp(40)));
        head.addView(Glass.text(this, p, 18, true, Glass.WHITE), lp(WRAP, WRAP, 0, 12, 0, 0, 0));
        col.addView(head, lp(MATCH, WRAP, 0, 12, 12, 12, 12));

        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), 0, dp(14), 0);
        final ScrollView sv = new ScrollView(this);
        sv.addView(box, new FrameLayout.LayoutParams(MATCH, WRAP));
        col.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.BOTTOM);
        bottom.setPadding(dp(12), dp(12), dp(12), dp(12));
        final EditText input = Glass.field(this, "Сообщение", true);
        bottom.addView(input, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView sendBtn = Glass.text(this, "➤", 20, false, Glass.WHITE);
        sendBtn.setGravity(Gravity.CENTER);
        sendBtn.setBackground(Glass.glass(this, 25, Glass.CYAN, 2.2f));
        sendBtn.setOnClickListener(v -> {
            final String t = input.getText().toString().trim();
            if (t.isEmpty()) return;
            input.setText("");
            io.execute(() -> {
                try { api.send(me, p, t); }
                catch (Exception e) { h.post(() -> { if (g == gen) input.setText(t); }); }
            });
        });
        bottom.addView(sendBtn, lp(dp(50), dp(50), 0, 10, 0, 0, 0));
        col.addView(bottom, new LinearLayout.LayoutParams(MATCH, WRAP));
        setScreen(col);

        final Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            final long after = lastId;
            io.execute(() -> {
                List<Api.Msg> r = null;
                try { r = api.history(me, p, after); } catch (Exception ignored) { }
                final List<Api.Msg> res = r;
                h.post(() -> {
                    if (g != gen) return;
                    if (res != null && !res.isEmpty()) {
                        for (Api.Msg m : res) addBubble(box, m);
                        lastId = res.get(res.size() - 1).id;
                        sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
                    }
                    h.postDelayed(loop[0], 1200);
                });
            });
        };
        loop[0].run();
    }

    private void addBubble(LinearLayout box, Api.Msg m) {
        boolean mine = m.from.equals(me);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(mine ? Gravity.END : Gravity.START);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackground(Glass.glass(this, 20, mine ? Glass.BLUE : Glass.WHITE, mine ? 2.2f : 1f));
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView t = Glass.text(this, m.text, 16, false, Glass.WHITE);
        t.setMaxWidth(dp(270));
        TextView time = Glass.text(this, fmt(m.ts), 11, false, 0x8CFFFFFF);
        time.setGravity(Gravity.END);
        bubble.addView(t);
        bubble.addView(time, new LinearLayout.LayoutParams(MATCH, WRAP));

        row.addView(bubble, new LinearLayout.LayoutParams(WRAP, WRAP));
        box.addView(row, lp(MATCH, WRAP, 0, 0, 0, 0, 8));
    }
              }
