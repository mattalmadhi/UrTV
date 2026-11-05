package sa.urtv.screensaver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Remote-friendly settings: every row is a button that opens a list. */
public class SettingsActivity extends Activity {
    private static final int DIALOG_THEME = android.R.style.Theme_Material_Dialog_Alert;

    private Prefs prefs;
    private LinearLayout list;
    private android.webkit.WebView preview;
    private String previewUrl;

    /** Arabic locale for this screen, so every dialog title, list and button is laid out right-to-left. */
    @Override
    protected void attachBaseContext(android.content.Context base) {
        android.content.res.Configuration c = new android.content.res.Configuration(base.getResources().getConfiguration());
        c.setLocale(new java.util.Locale("ar"));
        c.setLayoutDirection(new java.util.Locale("ar"));
        super.attachBaseContext(base.createConfigurationContext(c));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        UpdateJob.schedule(this);
        // Two panes: the real screensaver on the left, updating as you choose; the list on the right.
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        LinearLayout panes = new LinearLayout(this);
        panes.setOrientation(LinearLayout.HORIZONTAL);
        panes.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        panes.setBackground(new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Tokens.INK_SOFT, 0xFF04120C, 0xFF020806}));

        int listW = Math.round(dm.widthPixels * 0.44f), margin = dp(28);
        int previewW = dm.widthPixels - listW - 2 * margin, previewH = previewW * 9 / 16;
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        left.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView cap = new TextView(this);
        cap.setText("معاينة مباشرة");
        cap.setTextColor(Tokens.GOLD_DEEP);
        cap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        cap.setTypeface(font("Thmanyah-Medium"));
        cap.setPadding(0, 0, 0, dp(10));
        left.addView(cap);
        android.widget.FrameLayout clip = new android.widget.FrameLayout(this);
        clip.setClipChildren(true);
        android.graphics.drawable.GradientDrawable clipBg = new android.graphics.drawable.GradientDrawable();
        clipBg.setCornerRadius(dp(Tokens.RADIUS_CARD));
        clipBg.setColor(Tokens.INK);
        clip.setBackground(clipBg);
        clip.setClipToOutline(true);
        // Laid out at the TV's full size and scaled down, so it shows what the screen really shows.
        preview = ScreenView.create(this);
        preview.setPivotX(0);
        preview.setPivotY(0);
        preview.setScaleX(previewW / (float) dm.widthPixels);
        preview.setScaleY(previewW / (float) dm.widthPixels);
        clip.addView(preview, new android.widget.FrameLayout.LayoutParams(dm.widthPixels, dm.heightPixels));
        left.addView(clip, new LinearLayout.LayoutParams(previewW, previewH));
        previewUrl = new Prefs(this).dashboardUrl(this);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(previewW, -1);
        leftLp.setMargins(margin, 0, margin, 0);
        panes.addView(left, leftLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        list.setPadding(dp(8), dp(30), dp(28), dp(48));
        scroll.addView(list);
        panes.addView(scroll, new LinearLayout.LayoutParams(listW, -1));
        setContentView(panes);
        render();
    }

    /** A changed setting: the preview reloads with it. The page reads its settings once, at load. */
    private void syncPreview() {
        if (preview == null) return;
        String url = prefs.dashboardUrl(this);
        if (url.equals(previewUrl)) return;
        previewUrl = url;
        preview.evaluateJavascript("location.replace(" + JSONObject.quote(url) + ");location.reload();", null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (preview != null) preview.onPause();
    }

    @Override
    protected void onDestroy() {
        if (preview != null) { preview.destroy(); preview = null; }
        super.onDestroy();
    }

    @Override
    protected void onResume() {   // back in front (from the TV's settings, or opened again over itself): show what is set now
        super.onResume();
        if (preview != null) preview.onResume();
        render();
    }

    private void render() {
        syncPreview();
        list.removeAllViews();
        TextView title = new TextView(this);
        title.setText("تلفزيونك");
        title.setTextColor(Tokens.SAND);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTypeface(font("ThmanyahDisplay-Bold"));
        list.addView(title);
        TextView sub = new TextView(this);
        sub.setText("إعدادات شاشة التوقف");
        sub.setTextColor(Tokens.SOFT);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sub.setTypeface(font("Thmanyah-Regular"));
        sub.setPadding(0, 0, 0, dp(18));
        list.addView(sub);

        row("معاينة الشاشة", "شاهد الشاشة الآن", R.drawable.ic_play, new Runnable() { public void run() {
            startActivity(new Intent(SettingsActivity.this, MainActivity.class)); } }).requestFocus();

        header("الموقع");
        row("المدينة", prefs.city()[0], R.drawable.ic_place, new Runnable() { public void run() { pickCity(); } });

        header("المباريات");
        row("الفرق المتابعة (" + arabicNum(prefs.teams().size()) + "/" + arabicNum(Prefs.MAX_TEAMS) + ")", teamsLabel(),
            R.drawable.ic_trophy, new Runnable() { public void run() { manageTeams(); } });

        header("شاشة التوقف");
        row("المشاهد", sceneLabels(), R.drawable.ic_screensaver, new Runnable() { public void run() { pickScenes(); } });
        row("مدة كل مشهد", TextUtilsAr.digits(prefs.sceneMinutes()) + " دقائق", R.drawable.ic_sync, new Runnable() { public void run() { pickSceneMinutes(); } });
        row("لحظة الأذان", prefs.moment() ? "تظهر الصلاة وحدها حتى الإقامة" : "متوقفة", R.drawable.ic_eye,
            new Runnable() { public void run() { prefs.setMoment(!prefs.moment()); render(); } });
        row("بجانب الساعة", Prefs.panelLabel(prefs.panel()), R.drawable.ic_eye, new Runnable() { public void run() { pickPanel(false); } });

        header("الشاشة الرئيسية (تلفزيونك)");
        row("بطاقة على الشاشة الرئيسية", Prefs.panelLabel(prefs.homePanel()), R.drawable.ic_eye,
            new Runnable() { public void run() { pickPanel(true); } });
        row("ترتيب التطبيقات المفضلة", "قائمة ترتّب فيها المفضلة بالأسهم", R.drawable.ic_star,
            new Runnable() { public void run() { openDockSettings(); } });

        if ("shows".equals(prefs.panel()) || "shows".equals(prefs.homePanel())) {   // only matter for the shows card
        header("جديد المسلسلات");
        row("التصنيفات", labels(Prefs.GENRES, prefs.list("genres"), "الكل"),
            R.drawable.ic_category, new Runnable() { public void run() { pickMany("genres", "التصنيفات", Prefs.GENRES); } });
        row("اللغات", labels(Prefs.LANGS, prefs.list("langs"), "الكل"),
            R.drawable.ic_language, new Runnable() { public void run() { pickMany("langs", "اللغات", Prefs.LANGS); } });
        row("مسلسلات مختارة", showsLabel(), R.drawable.ic_star, new Runnable() { public void run() { manageShows(); } });
        row("أقل تقييم", "0".equals(prefs.minRating()) ? "بدون حد" : prefs.minRating(),
            R.drawable.ic_star, new Runnable() { public void run() { pickRating(); } });
        }

        header("التوقيت");
        row("تبديل الصورة كل", minutesLabel(Integer.parseInt(prefs.photoMinutes())), R.drawable.ic_sync,
            new Runnable() { public void run() { pickPhotoMinutes(); } });
        row("بدء شاشة التوقف بعد", idleLabel(), R.drawable.ic_screensaver,
            new Runnable() { public void run() { pickIdle(); } });

        header("التطبيق");
        row("التحديث التلقائي للصفحة", prefs.autoUpdate() ? "يعمل · كل ٦ ساعات" : "متوقف", R.drawable.ic_sync, new Runnable() { public void run() {
            prefs.setAutoUpdate(!prefs.autoUpdate()); render(); } });
        row("تحديث الشاشة الآن", Updater.versionNote(this) + " " + "ينزّل أحدث نسخة من الشاشة. تحديث التطبيق نفسه يكون عبر مساعد الذكاء الاصطناعي، لأنه يضبط الشاشة الرئيسية وشاشة التوقف، وإعداداتك كلها تبقى.", R.drawable.ic_sync, new Runnable() { public void run() { updateNow(); } });
    }

    // ---------- pickers ----------

    private void pickCity() {
        final Cities.Region[] regions = Cities.REGIONS;
        String[] labels = new String[regions.length + 1];
        labels[0] = "بحث عن مدينة أو قرية…";
        for (int i = 0; i < regions.length; i++) labels[i + 1] = regions[i].name + "  (" + regions[i].cities.length + ")";
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("المنطقة")
            .setItems(labels, (d, i) -> { if (i == 0) searchCity(); else pickCityIn(regions[i - 1]); })
            .show();
    }

    private void pickCityIn(final Cities.Region r) {
        String[] names = new String[r.cities.length];
        int sel = -1;
        for (int i = 0; i < names.length; i++) {
            names[i] = r.cities[i][0];
            if (names[i].equals(prefs.city()[0])) sel = i;
        }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle(r.name)
            .setSingleChoiceItems(names, sel, (d, i) -> {
                prefs.setCity(r.cities[i][0], r.cities[i][1], r.cities[i][2]);
                d.dismiss();
                render();
            }).show();
    }

    // any place in Saudi Arabia, from Open-Meteo's GeoNames search
    private void searchCity() {
        ask("اسم المدينة أو القرية", q -> fetch("https://geocoding-api.open-meteo.com/v1/search?count=20&language=ar&countryCode=SA&name="
            + Uri.encode(q), json -> {
                JSONArray a = new JSONObject(json).optJSONArray("results");
                if (a == null || a.length() == 0) { toast("لا توجد نتائج"); return; }
                final int n = a.length();
                final String[] names = new String[n], lats = new String[n], lons = new String[n], rows = new String[n];
                for (int i = 0; i < n; i++) {
                    JSONObject c = a.getJSONObject(i);
                    names[i] = c.optString("name");
                    lats[i] = String.valueOf(c.optDouble("latitude"));
                    lons[i] = String.valueOf(c.optDouble("longitude"));
                    String region = c.optString("admin1", "");
                    rows[i] = region.isEmpty() ? names[i] : names[i] + "  ·  " + region;
                }
                new AlertDialog.Builder(this, DIALOG_THEME).setTitle("اختر المكان")
                    .setItems(rows, (d, i) -> { prefs.setCity(names[i], lats[i], lons[i]); render(); }).show();
            }));
    }

    /** Followed teams: untick to remove, or add one (presets, league browsing, search). Up to 10. */
    private void manageTeams() {
        final List<String> cur = prefs.teams();
        String[] labels = new String[cur.size()];
        final boolean[] keep = new boolean[cur.size()];
        for (int i = 0; i < cur.size(); i++) { labels[i] = cur.get(i).split("\\|", 2)[1]; keep[i] = true; }
        AlertDialog.Builder b = new AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(cur.isEmpty() ? "لا توجد فرق متابعة" : "الفرق المتابعة (ألغِ التحديد للحذف)")
            .setPositiveButton("حفظ", (d, w) -> {
                List<String> v = new ArrayList<>();
                for (int i = 0; i < cur.size(); i++) if (keep[i]) v.add(cur.get(i));
                prefs.setList("teams", v);
                render();
            })
            .setNegativeButton("إلغاء", null);
        if (cur.size() < Prefs.MAX_TEAMS) b.setNeutralButton("إضافة فريق", (d, w) -> pickTeam(0));
        if (!cur.isEmpty()) b.setMultiChoiceItems(labels, keep, (d, i, checked) -> keep[i] = checked);
        b.show();
    }

    private void addTeam(String id, String name) {
        if (prefs.addTeam(id, name)) toast("تمت متابعة " + name);
        else toast(prefs.teams().size() >= Prefs.MAX_TEAMS ? "الحد الأعلى ١٠ فرق" : "متابَع مسبقاً");
        render();
    }

    private void pickTeam(final int slot) {
        final List<String[]> opts = new ArrayList<>();
        for (String[] t : Prefs.TEAM_PRESETS) opts.add(t);
        String[] labels = new String[opts.size() + 2];
        for (int i = 0; i < opts.size(); i++) labels[i] = opts.get(i)[1];
        labels[opts.size()] = "تصفح الدوريات…";
        labels[opts.size() + 1] = "بحث عن أي فريق أو منتخب…";
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("إضافة فريق")
            .setItems(labels, (d, i) -> {
                if (i < opts.size()) addTeam(opts.get(i)[0], opts.get(i)[1]);
                else if (i == opts.size()) pickLeague(slot);
                else searchTeam(slot);
            }).show();
    }

    /** Our bundled list first (every club of these leagues, offline); TheSportsDB only if that is missing. */
    private void pickLeague(final int slot) {
        final String[] labels = new String[Prefs.LEAGUES.length + 1];
        for (int i = 0; i < Prefs.LEAGUES.length; i++) labels[i] = Prefs.LEAGUES[i][1];
        labels[Prefs.LEAGUES.length] = "المنتخبات";
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("الدوري")
            .setItems(labels, (d, i) -> {
                String league = i < Prefs.LEAGUES.length ? Prefs.LEAGUES[i][0] : Teams.NATIONAL;
                List<TeamSearch.Team> clubs = Teams.inLeague(this, league);
                if (!clubs.isEmpty()) showOurs(clubs, labels[i], false);
                else if (i < Prefs.LEAGUES.length)
                    fetch(SPORTS + "search_all_teams.php?l=" + Uri.encode(league), json -> showTeams(slot, json, labels[i], league));
                else toast("تعذّر تحميل القائمة، جرّب البحث عن الفريق");
            }).show();
    }

    private void searchTeam(final int slot) {
        ask("اسم الفريق بالعربية أو الإنجليزية (مثل الهلال أو Real Madrid)", q -> {
            List<TeamSearch.Team> found = Teams.search(this, q);
            if (!found.isEmpty()) showOurs(found.subList(0, Math.min(found.size(), 30)), "اختر الفريق", true);
            else if (!Arabic.hasArabic(q))   // a club outside our list: TheSportsDB's search, in English only
                fetch(SPORTS + "searchteams.php?t=" + Uri.encode(q), json -> showTeams(slot, json, "اختر الفريق", null));
            else toast("لا توجد نتائج، جرّب اسم الفريق بالإنجليزية");
        });
    }

    /** Clubs from our list: their Arabic names are already there, so following one needs no network. */
    private void showOurs(final List<TeamSearch.Team> clubs, String title, boolean withLeague) {
        String[] rows = new String[clubs.size()];
        for (int i = 0; i < rows.length; i++) {
            TeamSearch.Team t = clubs.get(i);
            rows[i] = Teams.label(this, t);
            if (withLeague && t.leagues.length > 0)
                rows[i] += "  ·  " + (Teams.NATIONAL.equals(t.leagues[0]) ? "منتخب" : leagueAr(t.leagues[0]));
        }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle(title)
            .setItems(rows, (d, i) -> {
                TeamSearch.Team t = clubs.get(i);
                addTeam(t.id, t.ar.isEmpty() ? Arabic.translit(t.en) : t.ar);
            }).show();
    }

    private static final String SPORTS = "https://www.thesportsdb.com/api/v1/json/123/";

    /** TheSportsDB's answer, when our list has nothing. league != null: keep only clubs that really play in it. */
    private void showTeams(final int slot, String json, String title, String league) throws Exception {
        JSONArray all = new JSONObject(json).optJSONArray("teams");
        JSONArray a = new JSONArray();
        for (int i = 0; all != null && i < all.length(); i++) {
            JSONObject t = all.getJSONObject(i);
            if (!"Soccer".equalsIgnoreCase(t.optString("strSport", "Soccer"))) continue;
            boolean inLeague = league == null;
            for (int k = 1; !inLeague && k <= 7; k++)
                inLeague = league.equalsIgnoreCase(t.optString(k == 1 ? "strLeague" : "strLeague" + k));
            if (inLeague) a.put(t);
        }
        if (a.length() == 0) {
            toast(league == null ? "لا توجد نتائج" : "تعذّر تحميل هذا الدوري، جرّب البحث عن الفريق");
            return;
        }
        final int n = Math.min(a.length(), 60);
        final String[] ids = new String[n], names = new String[n], rows = new String[n];
        final TeamSearch.Team[] ours = new TeamSearch.Team[n];
        for (int i = 0; i < n; i++) {
            JSONObject t = a.getJSONObject(i);
            ids[i] = t.optString("idTeam");
            names[i] = t.optString("strTeam");
            ours[i] = Teams.byId(this, ids[i]);
            rows[i] = (ours[i] != null ? Teams.label(this, ours[i]) : Arabic.team(this, names[i]))
                + "  ·  " + leagueAr(t.optString("strLeague", ""));
        }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle(title)
            .setItems(rows, (d, i) -> {
                if (ours[i] != null && !ours[i].ar.isEmpty()) addTeam(ids[i], ours[i].ar);
                else inArabic(names[i], Arabic.TEAM, ar -> addTeam(ids[i], ar));
            })
            .show();
    }

    private void pickMany(final String key, String title, final String[][] options) {
        List<String> cur = prefs.list(key);
        String[] labels = new String[options.length];
        final boolean[] on = new boolean[options.length];
        for (int i = 0; i < options.length; i++) { labels[i] = options[i][1]; on[i] = cur.contains(options[i][0]); }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle(title + " (بدون اختيار = الكل)")
            .setMultiChoiceItems(labels, on, (d, i, checked) -> on[i] = checked)
            .setPositiveButton("حفظ", (d, w) -> {
                List<String> v = new ArrayList<>();
                for (int i = 0; i < options.length; i++) if (on[i]) v.add(options[i][0]);
                prefs.setList(key, v);
                render();
            })
            .setNegativeButton("إلغاء", null).show();
    }

    private void manageShows() {
        final List<String> cur = prefs.shows();
        String[] labels = new String[cur.size()];
        final boolean[] keep = new boolean[cur.size()];
        for (int i = 0; i < cur.size(); i++) { labels[i] = cur.get(i).split("\\|", 2)[1]; keep[i] = true; }
        AlertDialog.Builder b = new AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(cur.isEmpty() ? "لا توجد مسلسلات مختارة" : "المسلسلات المختارة (ألغِ التحديد للحذف)")
            .setPositiveButton("حفظ", (d, w) -> {
                List<String> v = new ArrayList<>();
                for (int i = 0; i < cur.size(); i++) if (keep[i]) v.add(cur.get(i));
                prefs.setList("shows", v);
                render();
            })
            .setNeutralButton("إضافة مسلسل", (d, w) -> searchShow())
            .setNegativeButton("إلغاء", null);
        if (!cur.isEmpty()) b.setMultiChoiceItems(labels, keep, (d, i, checked) -> keep[i] = checked);
        b.show();
    }

    private void searchShow() {
        ask("اسم المسلسل", q -> fetch("https://api.tvmaze.com/search/shows?q=" + Uri.encode(q), json -> {
            JSONArray a = new JSONArray(json);
            if (a.length() == 0) { toast("لا توجد نتائج"); return; }
            final int n = Math.min(a.length(), 15);
            final String[] ids = new String[n], names = new String[n], rows = new String[n];
            for (int i = 0; i < n; i++) {
                JSONObject s = a.getJSONObject(i).getJSONObject("show");
                ids[i] = String.valueOf(s.optInt("id"));
                names[i] = s.optString("name").replace("|", " ").replace(";", " ");
                String year = s.optString("premiered", "");
                rows[i] = names[i] + (year.length() >= 4 ? "  ·  " + year.substring(0, 4) : "")
                    + "  ·  " + labelOf(Prefs.LANGS, s.optString("language", ""));
            }
            new AlertDialog.Builder(this, DIALOG_THEME).setTitle("اختر المسلسل")
                .setItems(rows, (d, i) -> {
                    for (String s : prefs.shows()) if (s.startsWith(ids[i] + "|")) { toast("مضاف مسبقاً"); return; }
                    List<String> v = prefs.shows();
                    v.add(ids[i] + "|" + names[i]);
                    prefs.setList("shows", v);
                    toast("تمت إضافة " + names[i]);
                    render();
                }).show();
        }));
    }

    /** «ترتيب المفضلة» lives in the launcher (it owns the row). */
    private void openDockSettings() {
        try {
            startActivity(new Intent().setClassName("sa.urtv.home", "sa.urtv.home.DockActivity"));
        } catch (RuntimeException e) {
            toast("ثبّت الواجهة الرئيسية «تلفزيونك» أولاً");
        }
    }

    /** The card beside the screensaver's clock, or (home) the one on the launcher's home screen. */
    private void pickPanel(final boolean home) {
        java.util.Set<String> now = new java.util.HashSet<>();
        now.add(home ? prefs.homePanel() : prefs.panel());
        pickPictures(home ? "بطاقة على الشاشة الرئيسية" : "بجانب الساعة", Prefs.PANELS, "thumb_panel_", now, false, chosen -> {
            String v = chosen.iterator().next();
            if (home) prefs.setHomePanel(v); else prefs.setPanel(v);
            render();
        });
    }

    private String sceneLabels() {
        StringBuilder b = new StringBuilder();
        for (String[] s : Prefs.SCENES) if (prefs.scenes().contains(s[0])) {
            if (b.length() > 0) b.append("، ");
            b.append(s[1].split(":")[0]);
        }
        return b.toString();
    }

    private void pickScenes() {
        pickPictures("المشاهد: اختر مشهداً أو أكثر", Prefs.SCENES, "thumb_scene_", new java.util.HashSet<>(prefs.scenes()), true, chosen -> {
            List<String> v = new ArrayList<>();
            for (String[] s : Prefs.SCENES) if (chosen.contains(s[0])) v.add(s[0]);   // always in the same order
            prefs.setScenes(v);
            render();
        });
    }

    private void pickSceneMinutes() {
        String[] labels = new String[Prefs.SCENE_MINUTES.length];
        int sel = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = TextUtilsAr.digits(Prefs.SCENE_MINUTES[i]) + " دقائق";
            if (Prefs.SCENE_MINUTES[i].equals(prefs.sceneMinutes())) sel = i;
        }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("مدة كل مشهد")
            .setSingleChoiceItems(labels, sel, (d, i) -> {
                prefs.setSceneMinutes(Prefs.SCENE_MINUTES[i]);
                d.dismiss();
                render();
            }).show();
    }

    interface Picked { void got(java.util.Set<String> chosen); }

    /** A grid of pictures of the real screen: one choice closes it; several need «تم», and at least one stays on. */
    private void pickPictures(String title, String[][] items, String prefix, final java.util.Set<String> selected,
                              final boolean many, final Picked then) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        rows.setPadding(dp(16), dp(8), dp(16), dp(8));
        scroll.addView(rows);
        final AlertDialog[] dialog = new AlertDialog[1];
        View first = null;
        LinearLayout row = null;
        for (int i = 0; i < items.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                rows.addView(row);
            }
            final String value = items[i][0];
            View cell = pictureCell(items[i][1], getResources().getIdentifier(prefix + value, "drawable", getPackageName()),
                selected, value, many, () -> {
                    if (many) return;
                    selected.clear();
                    selected.add(value);
                    dialog[0].dismiss();
                    then.got(selected);
                });
            row.addView(cell);
            if (first == null || selected.contains(value) && !selected.contains(String.valueOf(first.getTag()))) first = cell;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this, DIALOG_THEME).setTitle(title).setView(scroll);
        if (many) b.setPositiveButton("تم", (d, w) -> {
            if (selected.isEmpty()) { toast("اختر مشهداً واحداً على الأقل"); return; }
            then.got(selected);
        });
        dialog[0] = b.show();
        if (dialog[0].getWindow() != null) dialog[0].getWindow().setLayout(dp(800), -2);
        if (first != null) first.requestFocus();
    }

    private View pictureCell(String label, int image, final java.util.Set<String> selected, final String value,
                             final boolean many, final Runnable onPick) {
        final LinearLayout cell = new LinearLayout(this);
        cell.setTag(value);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setPadding(dp(6), dp(6), dp(6), dp(8));
        final android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(Tokens.RADIUS_CARD));
        bg.setStroke(dp(3), 0x00000000);
        cell.setBackground(bg);
        android.widget.ImageView img = new android.widget.ImageView(this);
        img.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        if (image != 0) img.setImageResource(image);
        cell.addView(img, new LinearLayout.LayoutParams(dp(232), dp(130)));
        final TextView name = new TextView(this);
        name.setTextColor(Tokens.SAND);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        name.setTypeface(font("Thmanyah-Medium"));
        name.setPadding(dp(4), dp(6), dp(4), 0);
        name.setMaxLines(2);
        cell.addView(name);
        final Runnable paint = () -> {
            name.setText((selected.contains(value) ? "✓  " : "") + label);
            name.setTextColor(selected.contains(value) ? Tokens.GOLD_LIGHT : Tokens.SAND);
        };
        paint.run();
        cell.setOnFocusChangeListener((v, has) -> bg.setStroke(dp(3), has ? Tokens.GOLD_DEEP : 0x00000000));
        cell.setOnClickListener(v -> {
            if (many) {
                if (!selected.remove(value)) selected.add(value);
                paint.run();
            }
            onPick.run();
        });
        return cell;
    }

    /** Arabic digits for a plain number. */
    private static final class TextUtilsAr {
        static String digits(String s) {
            StringBuilder b = new StringBuilder(s.length());
            for (char c : s.toCharArray()) b.append(c >= '0' && c <= '9' ? (char) ('٠' + (c - '0')) : c);
            return b.toString();
        }
    }

    private void pickRating() {
        String[] labels = Prefs.MIN_RATINGS.clone();
        labels[0] = "بدون حد";
        int sel = 0;
        for (int i = 0; i < Prefs.MIN_RATINGS.length; i++) if (Prefs.MIN_RATINGS[i].equals(prefs.minRating())) sel = i;
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("أقل تقييم")
            .setSingleChoiceItems(labels, sel, (d, i) -> { prefs.setMinRating(Prefs.MIN_RATINGS[i]); d.dismiss(); render(); })
            .show();
    }

    private void pickPhotoMinutes() {
        String[] labels = new String[Prefs.PHOTO_MINUTES.length];
        int sel = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = minutesLabel(Integer.parseInt(Prefs.PHOTO_MINUTES[i]));
            if (Prefs.PHOTO_MINUTES[i].equals(prefs.photoMinutes())) sel = i;
        }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("تبديل الصورة كل")
            .setSingleChoiceItems(labels, sel, (d, i) -> { prefs.setPhotoMinutes(Prefs.PHOTO_MINUTES[i]); d.dismiss(); render(); })
            .show();
    }

    /** The TV's own idle delay. Writing it needs WRITE_SETTINGS, which TVs rarely grant: then its own screen opens. */
    private void pickIdle() {
        if (!Prefs.canWriteSettings(this)) {
            toast("غيّرها من إعدادات التلفاز، أو بأمر من مساعدك");
            openDreamSettings();
            return;
        }
        String[] labels = new String[Prefs.IDLE_MINUTES.length];
        int sel = -1;
        long now = Prefs.idleMs(this);
        for (int i = 0; i < labels.length; i++) {
            labels[i] = minutesLabel(Integer.parseInt(Prefs.IDLE_MINUTES[i]));
            if (Long.parseLong(Prefs.IDLE_MINUTES[i]) * 60000 == now) sel = i;
        }
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("بدء شاشة التوقف بعد")
            .setSingleChoiceItems(labels, sel, (d, i) -> {
                try {
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT,
                        Integer.parseInt(Prefs.IDLE_MINUTES[i]) * 60000);
                } catch (SecurityException e) {
                    toast("غيّرها من إعدادات التلفاز، أو بأمر من مساعدك");
                }
                d.dismiss();
                render();
            })
            .show();
    }

    private String idleLabel() {
        long ms = Prefs.idleMs(this);
        if (ms <= 0) return "يحدده التلفاز";
        if (ms % 60000 != 0) return arabicNum((int) (ms / 1000)) + " ثانية";
        return minutesLabel((int) (ms / 60000));
    }

    /** دقيقة واحدة · دقيقتان · ٥ دقائق · ١٥ دقيقة */
    private static String minutesLabel(int n) {
        if (n == 1) return "دقيقة واحدة";
        if (n == 2) return "دقيقتان";
        return arabicNum(n) + (n <= 10 ? " دقائق" : " دقيقة");
    }

    private void updateNow() {
        toast("جارٍ التحقق…");
        new Thread(() -> {
            final String msg = Updater.check(getApplicationContext(), true);
            runOnUiThread(() -> { toast(msg); if (!isFinishing()) render(); });
        }).start();
    }

    private void openDreamSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    // ---------- labels ----------

    /** Wikidata's Arabic name (or a transliteration) fetched off the UI thread. */
    private void inArabic(final String en, final java.util.regex.Pattern kind, final Text then) {
        toast("جارٍ جلب الاسم بالعربية…");
        new Thread(() -> {
            String k = kind == Arabic.TEAM ? Arabic.known(getApplicationContext(), en) : null;
            final String ar = k != null ? k : Arabic.lookup(en, kind);
            runOnUiThread(() -> { if (!isFinishing()) then.got(ar); });
        }).start();
    }

    private static String leagueAr(String en) {
        for (String[] l : Prefs.LEAGUES) if (l[0].equalsIgnoreCase(en)) return l[1];
        return Arabic.translit(en);
    }

    private static String labelOf(String[][] options, String key) {
        for (String[] o : options) if (o[0].equalsIgnoreCase(key)) return o[1];
        return Arabic.translit(key);
    }

    private String teamsLabel() {
        List<String> names = new ArrayList<>();
        for (String t : prefs.teams()) names.add(t.split("\\|", 2)[1]);
        return names.isEmpty() ? "لا شيء" : Prefs.join(names, "، ");
    }

    private static String arabicNum(int n) {
        StringBuilder b = new StringBuilder();
        for (char c : String.valueOf(n).toCharArray()) b.append((char) ('٠' + (c - '0')));
        return b.toString();
    }

    private String showsLabel() {
        List<String> v = prefs.shows();
        if (v.isEmpty()) return "لا شيء";
        List<String> names = new ArrayList<>();
        for (String s : v) names.add(s.split("\\|", 2)[1]);
        return Prefs.join(names, "، ");
    }

    private static String labels(String[][] options, List<String> picked, String none) {
        List<String> out = new ArrayList<>();
        for (String[] o : options) if (picked.contains(o[0])) out.add(o[1]);
        return out.isEmpty() ? none : Prefs.join(out, "، ");
    }

    // ---------- small UI helpers ----------

    private final java.util.Map<String, Typeface> fonts = new java.util.HashMap<>();

    /** Thmanyah, shared with the launcher's assets; falls back to the system font. */
    private Typeface font(String name) {
        Typeface tf = fonts.get(name);
        if (tf == null) {
            try { tf = Typeface.createFromAsset(getAssets(), "fonts/" + name + ".otf"); }
            catch (RuntimeException e) { tf = Typeface.DEFAULT; }
            fonts.put(name, tf);
        }
        return tf;
    }

    private void header(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Tokens.GOLD_DEEP);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(font("Thmanyah-Medium"));
        t.setLetterSpacing(0.05f);
        t.setPadding(dp(6), dp(22), dp(6), dp(8));
        list.addView(t);
    }

    /** A card row: icon, label and its current value on a second line, chevron; gold when focused. */
    private View row(String label, String value, int icon, final Runnable onClick) {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setPadding(dp(18), dp(12), dp(18), dp(12));
        card.setFocusable(true);
        card.setClickable(true);
        final android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(Tokens.RADIUS_ROW));
        bg.setColor(0x14FFFFFF);
        bg.setStroke(dp(2), 0x1FD6AF6B);
        card.setBackground(bg);

        final android.widget.ImageView ic = new android.widget.ImageView(this);
        final android.graphics.drawable.Drawable d = getDrawable(icon).mutate();
        d.setTint(Tokens.GOLD_DEEP);
        ic.setImageDrawable(d);
        card.addView(ic, new LinearLayout.LayoutParams(dp(24), dp(24)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        final TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(Tokens.SAND);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        name.setTypeface(font("Thmanyah-Medium"));
        name.setSingleLine(true);
        texts.addView(name);
        final TextView val = new TextView(this);
        if (value != null) {
            val.setText(value);
            val.setTextColor(Tokens.SOFT);
            val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            val.setTypeface(font("Thmanyah-Regular"));
            val.setSingleLine(true);
            val.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(val);
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.setMarginStart(dp(16));
        card.addView(texts, tp);

        final TextView chevron = new TextView(this);
        chevron.setText("›");   // shown mirrored (‹) in the RTL row
        chevron.setTextColor(0x99C6BBA4);
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        card.addView(chevron);

        card.setOnClickListener(v -> onClick.run());
        card.setOnFocusChangeListener((v, has) -> {
            // No scaling: a scaled rounded card overlapped its neighbours and blurred the text.
            bg.setColor(has ? 0x33D6AF6B : 0x14FFFFFF);
            bg.setStroke(dp(2), has ? Tokens.GOLD_DEEP : 0x1FD6AF6B);
            chevron.setTextColor(has ? Tokens.GOLD_DEEP : 0x99C6BBA4);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        list.addView(card, lp);
        return card;
    }

    interface Text { void got(String s); }
    interface Json { void got(String json) throws Exception; }

    private void ask(String hint, final Text then) {
        final EditText in = new EditText(this);
        in.setHint(hint);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this, DIALOG_THEME).setTitle("بحث").setView(in)
            .setPositiveButton("بحث", (d, w) -> {
                String q = in.getText().toString().trim();
                if (!q.isEmpty()) then.got(q);
            })
            .setNegativeButton("إلغاء", null).show();
        in.requestFocus();
    }

    /** GET on a background thread, result handled on the UI thread. */
    private void fetch(final String url, final Json then) {
        toast("جارٍ البحث…");
        new Thread(() -> {
            String body = null;
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(12000);
                con.setReadTimeout(15000);
                InputStream in = con.getInputStream();
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                in.close();
                body = b.toString("UTF-8");
            } catch (Exception ignored) {
            } finally {
                if (con != null) con.disconnect();
            }
            final String result = body;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (result == null) { toast("لا يوجد اتصال"); return; }
                try { then.got(result); } catch (Exception e) { toast("تعذرت قراءة النتائج"); }
            });
        }).start();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
