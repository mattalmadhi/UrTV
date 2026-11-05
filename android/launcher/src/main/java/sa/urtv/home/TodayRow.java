package sa.urtv.home;

import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The "Today" row above the dock: next prayer, next match, weather, next occasion. Native views, no WebView: this is
 * always on screen. Prayer and occasion are computed here; the weather and the match come from the same services as
 * the screensaver and keep the last answer when offline. It only shows information, so it never takes focus.
 */
final class TodayRow {
    private static final String CONFIG = "content://sa.urtv.screensaver.config";
    private static final long NET_EVERY_MS = 30 * 60 * 1000L;

    private final Activity a;
    private final SharedPreferences store;
    private final LinearLayout row;
    private final Map<String, TextView[]> chips = new HashMap<>();
    private ImageView badgeHome, badgeAway;
    private double lat = 24.6877, lon = 46.7219;
    private String city = "", teams = "";

    TodayRow(Activity a, SharedPreferences store, Fonts fonts, float density) {
        this.a = a;
        this.store = store;
        row = new LinearLayout(a);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setFocusable(false);
        for (String key : new String[]{"prayer", "match", "weather", "occasion"}) row.addView(chip(key, fonts, density));
    }

    interface Fonts { Typeface get(String family); }

    View view() { return row; }

    private View chip(String key, Fonts fonts, float d) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        int px = Math.round(14 * d), py = Math.round(8 * d);
        box.setPadding(px, py, px, py);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Tokens.RADIUS_CARD * d);
        bg.setColor(0xB8040E09);
        bg.setStroke(Math.round(d), 0x29DCB86F);
        box.setBackground(bg);

        TextView label = text(fonts.get("medium"), 11, Tokens.GOLD_DEEP);
        TextView value = text(fonts.get("display"), 17, Tokens.CREAM);
        TextView sub = text(fonts.get("regular"), 11, Tokens.SOFT);
        box.addView(label);
        if ("match".equals(key)) {
            LinearLayout line = new LinearLayout(a);
            line.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            int s = Math.round(20 * d), m = Math.round(6 * d);
            badgeHome = badge(s);
            badgeAway = badge(s);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.setMarginEnd(m);
            line.addView(badgeHome, lp);
            line.addView(value, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(s, s);
            lp2.setMarginStart(m);
            line.addView(badgeAway, lp2);
            box.addView(line);
        } else {
            box.addView(value);
        }
        box.addView(sub);
        chips.put(key, new TextView[]{label, value, sub});
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, "match".equals(key) ? 1.45f : 1f);
        int gap = Math.round(5 * d);
        p.setMargins(gap, 0, gap, 0);
        box.setLayoutParams(p);
        return box;
    }

    private ImageView badge(int size) {
        ImageView v = new ImageView(a);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setVisibility(View.GONE);
        return v;
    }

    private TextView text(Typeface tf, float sp, int color) {
        TextView t = new TextView(a);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setIncludeFontPadding(true);
        return t;
    }

    private void set(String key, String label, String value, String sub) {
        TextView[] t = chips.get(key);
        t[0].setText(label);
        t[1].setText(value);
        t[2].setText(sub);
    }

    /** Every minute and on return to the home screen: the part that needs no network. */
    void tick() {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Riyadh"));
        int mins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int[] p = Photos.prayers(now, lat, lon);
        String[] names = {"الفجر", now.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY ? "الجمعة" : "الظهر", "العصر", "المغرب", "العشاء"};
        int next = -1;
        for (int i = 0; i < p.length && next < 0; i++) if (p[i] > mins) next = i;
        int at = next < 0 ? p[0] + 1440 : p[next];
        String name = next < 0 ? names[0] : names[next];
        set("prayer", "الصلاة القادمة", name + "  " + clock12(at % 1440), "بعد " + remaining(at - mins));
        Photos.Occasion o = Photos.nextOccasion();
        if (o != null) set("occasion", "المناسبة القادمة", o.name,
            o.days == 0 ? "اليوم" : o.days == 1 ? "غداً" : "بعد " + days(o.days));
    }

    /** On return to the home screen: settings, the cached answers at once, the network if they are old. */
    void refresh() {
        String frag = null;
        try (Cursor c = a.getContentResolver().query(Uri.parse(CONFIG), null, null, null, null)) {
            if (c != null && c.moveToFirst()) frag = c.getString(0);
        } catch (RuntimeException ignored) { }
        if (frag != null) {
            Uri u = Uri.parse("x://config/?" + frag);
            try {
                lat = Double.parseDouble(u.getQueryParameter("lat"));
                lon = Double.parseDouble(u.getQueryParameter("lon"));
            } catch (RuntimeException ignored) { }
            city = u.getQueryParameter("city") == null ? "" : u.getQueryParameter("city");
            teams = u.getQueryParameter("teams") == null ? "" : u.getQueryParameter("teams");
        }
        tick();
        paintWeather();
        paintMatch();
        if (System.currentTimeMillis() - store.getLong("todayAt", 0) < NET_EVERY_MS) return;
        new Thread(() -> {
            boolean any = fetchWeather();
            any |= fetchMatch();
            if (any) store.edit().putLong("todayAt", System.currentTimeMillis()).apply();
            a.runOnUiThread(() -> { paintWeather(); paintMatch(); });
        }).start();
    }

    // ---------------------------------------------------------------- weather

    private static final Map<Integer, String> SKY = new HashMap<>();
    static {
        Object[][] m = {{0, "صافي"}, {1, "صافي غالباً"}, {2, "غائم جزئياً"}, {3, "غائم"}, {45, "ضباب"}, {48, "ضباب"},
            {51, "رذاذ"}, {53, "رذاذ"}, {55, "رذاذ"}, {61, "مطر خفيف"}, {63, "مطر"}, {65, "مطر غزير"},
            {80, "زخات"}, {81, "زخات"}, {82, "زخات غزيرة"}, {95, "عواصف رعدية"}, {96, "عواصف"}, {99, "عواصف"}};
        for (Object[] r : m) SKY.put((Integer) r[0], (String) r[1]);
    }

    private boolean fetchWeather() {
        try {
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&timezone=Asia%2FRiyadh&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&forecast_days=1";
            JSONObject j = new JSONObject(get(url));
            JSONObject cur = j.getJSONObject("current"), day = j.getJSONObject("daily");
            store.edit().putString("todayWx", new JSONObject()
                .put("t", cur.getDouble("temperature_2m")).put("c", cur.getInt("weather_code"))
                .put("hi", day.getJSONArray("temperature_2m_max").getDouble(0))
                .put("lo", day.getJSONArray("temperature_2m_min").getDouble(0)).toString()).apply();
            return true;
        } catch (Exception e) { return false; }
    }

    private void paintWeather() {
        String label = city.isEmpty() ? "الطقس" : "الطقس · " + city;
        try {
            JSONObject w = new JSONObject(store.getString("todayWx", ""));
            String sky = SKY.containsKey(w.getInt("c")) ? SKY.get(w.getInt("c")) : "";
            set("weather", label, digits(String.valueOf(Math.round(w.getDouble("t")))) + "°  " + sky,
                digits(Math.round(w.getDouble("hi")) + "") + "° / " + digits(Math.round(w.getDouble("lo")) + "") + "°");
        } catch (Exception e) { set("weather", label, "—", ""); }
    }

    // ---------------------------------------------------------------- the next match of a followed team

    private boolean fetchMatch() {
        if (teams.isEmpty()) { store.edit().remove("todayMatch").apply(); return true; }
        JSONObject best = null;
        long bestAt = Long.MAX_VALUE;
        boolean answered = false;
        for (String id : teams.split(",")) {
            try {
                JSONArray ev = new JSONObject(get("https://www.thesportsdb.com/api/v1/json/123/eventsnext.php?id=" + id)).optJSONArray("events");
                answered = true;
                if (ev == null || ev.length() == 0) continue;
                JSONObject e = ev.getJSONObject(0);
                long at = kickoff(e);
                if (at > 0 && at < bestAt) { bestAt = at; best = e; }
            } catch (Exception ignored) { }
        }
        if (!answered) return false;
        if (best == null) { store.edit().remove("todayMatch").apply(); return true; }
        try {
            store.edit().putString("todayMatch", new JSONObject()
                .put("at", bestAt)
                .put("home", best.optString("strHomeTeam")).put("homeId", best.optString("idHomeTeam")).put("homeBadge", best.optString("strHomeTeamBadge"))
                .put("away", best.optString("strAwayTeam")).put("awayId", best.optString("idAwayTeam")).put("awayBadge", best.optString("strAwayTeamBadge"))
                .toString()).apply();
            JSONObject m = new JSONObject(store.getString("todayMatch", ""));
            for (String side : new String[]{"home", "away"}) {
                String id = m.optString(side + "Id"), url = m.optString(side + "Badge");
                if (url.isEmpty() && !id.isEmpty()) {
                    JSONArray t = new JSONObject(get("https://www.thesportsdb.com/api/v1/json/123/lookupteam.php?id=" + id)).optJSONArray("teams");
                    if (t != null && t.length() > 0) url = t.getJSONObject(0).optString("strBadge");
                }
                saveBadge(id, url);
            }
        } catch (Exception ignored) { }
        return true;
    }

    private void saveBadge(String id, String url) {
        if (id.isEmpty() || url.isEmpty()) return;
        File f = new File(a.getFilesDir(), "badge-" + id + ".png");
        if (f.isFile() && System.currentTimeMillis() - f.lastModified() < 7 * 24 * 3600 * 1000L) return;
        try {
            byte[] b;
            try { b = getBytes(url + "/small"); } catch (Exception e) { b = getBytes(url); }
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(b); }
        } catch (Exception ignored) { }
    }

    private static long kickoff(JSONObject e) {
        try {
            String s = e.optString("strTimestamp");
            if (s.isEmpty()) s = e.optString("dateEvent") + "T" + e.optString("strTime", "00:00:00");
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(s).getTime();
        } catch (Exception x) { return -1; }
    }

    private void paintMatch() {
        long now = System.currentTimeMillis();
        try {
            JSONObject m = new JSONObject(store.getString("todayMatch", ""));
            long at = m.getLong("at");
            if (at < now - 150 * 60 * 1000L) throw new IllegalStateException("over");
            set("match", "المباراة القادمة", teamName(m.optString("homeId"), m.optString("home")) + " × "
                + teamName(m.optString("awayId"), m.optString("away")),
                (at <= now ? "جارية الآن" : "بعد " + remainingLong(at - now)) + " · " + clock12(minuteOfDay(at)));
            paintBadge(badgeHome, m.optString("homeId"));
            paintBadge(badgeAway, m.optString("awayId"));
        } catch (Exception e) {
            set("match", "المباريات", "لا مباريات قريبة", "");
            badgeHome.setVisibility(View.GONE);
            badgeAway.setVisibility(View.GONE);
        }
    }

    private void paintBadge(ImageView v, String id) {
        Bitmap b = BitmapFactory.decodeFile(new File(a.getFilesDir(), "badge-" + id + ".png").getPath());
        v.setImageBitmap(b);
        v.setVisibility(b == null ? View.GONE : View.VISIBLE);
    }

    private Map<String, String> arabicTeams;

    /** Arabic from assets/shared/teams.json (the screensaver's list); the English name if the club is not on it. */
    private String teamName(String id, String fallback) {
        if (arabicTeams == null) {
            arabicTeams = new HashMap<>();
            try (java.io.InputStream in = a.getAssets().open("shared/teams.json")) {
                JSONArray t = new JSONArray(new String(readAll(in), "UTF-8"));
                for (int i = 0; i < t.length(); i++) arabicTeams.put(t.getJSONObject(i).optString("id"), t.getJSONObject(i).optString("ar"));
            } catch (Exception ignored) { }
        }
        String ar = arabicTeams.get(id);
        return ar == null || ar.isEmpty() ? fallback : ar;
    }

    // ---------------------------------------------------------------- text and network

    private static int minuteOfDay(long millis) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Riyadh"));
        c.setTime(new Date(millis));
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    static String digits(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) b.append(c >= '0' && c <= '9' ? (char) ('٠' + (c - '0')) : c);
        return b.toString();
    }

    private static String clock12(int m) {
        int h = m / 60, h12 = h % 12 == 0 ? 12 : h % 12;
        return digits(h12 + ":" + String.format(Locale.ROOT, "%02d", m % 60)) + (h < 12 ? " ص" : " م");
    }

    private static String qty(int n, String one, String two, String few, String many) {
        if (n == 1) return one;
        if (n == 2) return two;
        return digits(String.valueOf(n)) + " " + (n <= 10 ? few : many);
    }

    private static String days(int n) { return qty(n, "يوم", "يومين", "أيام", "يوم"); }

    private static String remaining(int mins) {
        int h = mins / 60, m = mins % 60;
        List<String> out = new ArrayList<>();
        if (h > 0) out.add(qty(h, "ساعة", "ساعتين", "ساعات", "ساعة"));
        if (m > 0 || h == 0) out.add(qty(Math.max(m, 1), "دقيقة", "دقيقتين", "دقائق", "دقيقة"));
        return android.text.TextUtils.join(" و ", out);
    }

    private static String remainingLong(long ms) {
        int d = (int) (ms / 86400000L), h = (int) (ms % 86400000L / 3600000L);
        if (d == 0) return remaining((int) Math.max(1, ms / 60000L));
        return h == 0 ? days(d) : days(d) + " و " + qty(h, "ساعة", "ساعتين", "ساعات", "ساعة");
    }

    private static String get(String url) throws Exception { return new String(getBytes(url), "UTF-8"); }

    private static byte[] getBytes(String url) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        try (java.io.InputStream in = c.getInputStream()) { return readAll(in); }
        finally { c.disconnect(); }
    }

    private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toByteArray();
    }
}
