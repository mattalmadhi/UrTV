package sa.urtv.home;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Which photo the home screen shows: the same choice as the screensaver page (regionOfDay, photoTime, regionPool and
 * seededOrder in src/template.html). Each day is one region, in a loop round the country; Friday is the household's
 * own region; within the day only photos taken at that time of day (day, golden, night), in an order seeded by the day.
 */
final class Photos {
    static final String[] LOOP = {"tabuk", "jouf", "northern", "hail", "qassim", "madinah", "makkah", "baha", "asir",
        "jazan", "najran", "eastern", "riyadh"};
    // region seats: a city set by coordinates goes to the nearest one
    private static final String[] SEAT_IDS = {"riyadh", "makkah", "madinah", "qassim", "eastern", "asir", "tabuk", "hail",
        "northern", "jazan", "najran", "baha", "jouf"};
    private static final double[][] SEATS = {{24.71, 46.68}, {21.49, 39.19}, {24.47, 39.61}, {26.33, 43.97},
        {26.43, 50.10}, {18.22, 42.51}, {28.38, 36.57}, {27.52, 41.69}, {30.98, 41.04}, {16.89, 42.55}, {17.49, 44.13},
        {20.01, 41.47}, {29.97, 40.21}};

    static final class Photo {
        final String f, r, t;
        Photo(String f, String r, String t) { this.f = f; this.r = r; this.t = t; }
    }

    private Photos() {}

    static long riyadhDay() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Riyadh"));
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        return Math.floorDiv(utc.getTimeInMillis(), 86400000L);
    }

    static boolean friday() {
        return Calendar.getInstance(TimeZone.getTimeZone("Asia/Riyadh")).get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
    }

    static String homeRegion(String region, double lat, double lon) {
        for (String id : SEAT_IDS) if (id.equals(region)) return region;
        double k = Math.cos(Math.toRadians(lat)), best = Double.MAX_VALUE;
        String out = "riyadh";
        for (int i = 0; i < SEATS.length; i++) {
            double d = Math.pow(SEATS[i][0] - lat, 2) + Math.pow((SEATS[i][1] - lon) * k, 2);
            if (d < best) { best = d; out = SEAT_IDS[i]; }
        }
        return out;
    }

    static String regionOfDay(String home) {
        String[] season = seasonRegions();
        if (season != null) return season[(int) Math.floorMod(riyadhDay(), (long) season.length)];
        return friday() ? home : LOOP[(int) Math.floorMod(riyadhDay(), (long) LOOP.length)];
    }

    /** month (1-12) and day of a date in the Umm al-Qura calendar */
    private static int[] hijri(long millis) {
        android.icu.util.IslamicCalendar c = new android.icu.util.IslamicCalendar(android.icu.util.TimeZone.getTimeZone("Asia/Riyadh"));
        c.setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        c.setTimeInMillis(millis);
        return new int[] {c.get(android.icu.util.Calendar.MONTH) + 1, c.get(android.icu.util.Calendar.DAY_OF_MONTH)};
    }

    /** seasonNow() in the screensaver page: Ramadan and the Eids show Makkah and Madinah, the two national days Riyadh */
    private static String[] seasonRegions() {
        long now = System.currentTimeMillis();
        int[] h = hijri(now);
        if (h[0] == 9 || (h[0] == 10 && h[1] <= 3) || (h[0] == 12 && h[1] >= 10 && h[1] <= 13)) return new String[] {"makkah", "madinah"};
        Calendar g = Calendar.getInstance(TimeZone.getTimeZone("Asia/Riyadh"));
        int mo = g.get(Calendar.MONTH) + 1, d = g.get(Calendar.DAY_OF_MONTH);
        if ((mo == 9 && d == 23) || (mo == 2 && d == 22)) return new String[] {"riyadh"};
        return null;
    }

    static final class Occasion {
        final String name; final int days;
        Occasion(String name, int days) { this.name = name; this.days = days; }
    }

    /** nextOccasion() in the screensaver page */
    static Occasion nextOccasion() {
        Calendar g = Calendar.getInstance(TimeZone.getTimeZone("Asia/Riyadh"));
        long now = System.currentTimeMillis();
        for (int i = 0; i < 400; i++) {
            long at = now + i * 86400000L;
            g.setTimeInMillis(at);
            int[] h = hijri(at);
            int mo = g.get(Calendar.MONTH) + 1, d = g.get(Calendar.DAY_OF_MONTH);
            if (h[0] == 9 && h[1] == 1) return new Occasion("رمضان", i);
            if (h[0] == 10 && h[1] == 1) return new Occasion("عيد الفطر", i);
            if (h[0] == 12 && h[1] == 9) return new Occasion("يوم عرفة", i);
            if (h[0] == 12 && h[1] == 10) return new Occasion("عيد الأضحى", i);
            if (mo == 9 && d == 23) return new Occasion("اليوم الوطني", i);
            if (mo == 2 && d == 22) return new Occasion("يوم التأسيس", i);
        }
        return null;
    }

    /**
     * Fajr, Dhuhr, Asr, Maghrib, Isha in minutes of Riyadh time: computePrayers() in the screensaver page, Umm al-Qura:
     * Fajr at 18.5 degrees, Asr at shadow factor 1, Isha 90 minutes after Maghrib (120 in Ramadan).
     */
    static int[] prayers(Calendar c, double lat, double lng) {
        double jd0 = Math.floorDiv(dayStartUtc(c), 86400000L) + 2440587.5 - lng / (15 * 24);
        double fajr = angleTime(jd0, 18.5, 5, true, lat), sunset = angleTime(jd0, .833, 18, false, lat);
        double[] noon12 = sun(jd0, 12), asr13 = sun(jd0, 13);
        double dhuhr = fix(12 - noon12[1], 24);
        double asrAngle = -Math.toDegrees(Math.atan(1 / (1 + Math.tan(Math.toRadians(Math.abs(lat - asr13[0]))))));
        double asr = angleTime(jd0, asrAngle, 13, false, lat);
        double adj = 3 - lng / 15;
        int maghrib = round(sunset + adj);
        int isha = maghrib + (hijri(c.getTimeInMillis())[0] == 9 ? 120 : 90);
        return new int[] {round(fajr + adj), round(dhuhr + adj), round(asr + adj), maghrib, isha};
    }

    private static int round(double hours) { return (int) Math.round(fix(hours, 24) * 60) % 1440; }

    /** declination, equation of time */
    private static double[] sun(double jd0, double t) {
        double D = jd0 + t / 24 - 2451545;
        double g = fix(357.529 + .98560028 * D, 360), q = fix(280.459 + .98564736 * D, 360);
        double L = fix(q + 1.915 * Math.sin(Math.toRadians(g)) + .020 * Math.sin(Math.toRadians(2 * g)), 360);
        double e = 23.439 - .00000036 * D;
        double ra = fix(Math.toDegrees(Math.atan2(Math.cos(Math.toRadians(e)) * Math.sin(Math.toRadians(L)),
            Math.cos(Math.toRadians(L)))) / 15, 24);
        return new double[] {Math.toDegrees(Math.asin(Math.sin(Math.toRadians(e)) * Math.sin(Math.toRadians(L)))), q / 15 - ra};
    }

    private static double angleTime(double jd0, double angle, double t, boolean before, double lat) {
        double[] s = sun(jd0, t);
        double noon = fix(12 - s[1], 24);
        double T = Math.toDegrees(Math.acos((-Math.sin(Math.toRadians(angle)) - Math.sin(Math.toRadians(s[0]))
            * Math.sin(Math.toRadians(lat))) / (Math.cos(Math.toRadians(s[0])) * Math.cos(Math.toRadians(lat))))) / 15;
        return before ? noon - T : noon + T;
    }

    /** golden within 75 min of sunrise or sunset, night from then until 75 min before sunrise, day otherwise */
    static String timeOfDay(double lat, double lon) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Riyadh"));
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int[] sun = sunriseSunset(c, lat, lon);
        int sr = sun[0], mg = sun[1];
        if (now < sr - 75 || now > mg + 75) return "night";
        if (Math.abs(now - sr) <= 75 || Math.abs(now - mg) <= 75) return "golden";
        return "day";
    }

    /** Sunrise and sunset in minutes of Riyadh time (UTC+3), the page's computePrayers() math. */
    static int[] sunriseSunset(Calendar c, double lat, double lng) {
        double jd0 = Math.floorDiv(dayStartUtc(c), 86400000L) + 2440587.5 - lng / (15 * 24);
        double[] r = new double[2];
        for (int i = 0; i < 2; i++) {
            double t = i == 0 ? 6 : 18;
            double D = jd0 + t / 24 - 2451545;
            double g = fix(357.529 + .98560028 * D, 360), q = fix(280.459 + .98564736 * D, 360);
            double L = fix(q + 1.915 * Math.sin(Math.toRadians(g)) + .020 * Math.sin(Math.toRadians(2 * g)), 360);
            double e = 23.439 - .00000036 * D;
            double ra = fix(Math.toDegrees(Math.atan2(Math.cos(Math.toRadians(e)) * Math.sin(Math.toRadians(L)),
                Math.cos(Math.toRadians(L)))) / 15, 24);
            double decl = Math.toDegrees(Math.asin(Math.sin(Math.toRadians(e)) * Math.sin(Math.toRadians(L))));
            double noon = fix(12 - (q / 15 - ra), 24);
            double T = Math.toDegrees(Math.acos((-Math.sin(Math.toRadians(.833)) - Math.sin(Math.toRadians(decl))
                * Math.sin(Math.toRadians(lat))) / (Math.cos(Math.toRadians(decl)) * Math.cos(Math.toRadians(lat))))) / 15;
            r[i] = (i == 0 ? noon - T : noon + T) + 3 - lng / 15;
        }
        return new int[] {(int) Math.round(fix(r[0], 24) * 60), (int) Math.round(fix(r[1], 24) * 60)};
    }

    private static long dayStartUtc(Calendar c) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    private static double fix(double a, double b) {
        a = a - b * Math.floor(a / b);
        return a < 0 ? a + b : a;
    }

    private static final java.util.Map<String, List<String>> NEAR = new java.util.HashMap<>();
    static {
        String[][] n = {{"northern", "jouf", "hail"}, {"jouf", "northern", "tabuk"}, {"hail", "qassim", "jouf"},
            {"qassim", "hail", "riyadh"}, {"najran", "asir", "jazan"}, {"jazan", "asir", "najran"}, {"baha", "asir", "makkah"},
            {"asir", "baha", "jazan"}, {"tabuk", "madinah", "jouf"}, {"madinah", "makkah", "tabuk"},
            {"makkah", "madinah", "baha"}, {"eastern", "riyadh"}, {"riyadh", "qassim", "eastern"}};
        for (String[] row : n) NEAR.put(row[0], Arrays.asList(row).subList(1, row.length));
    }

    private static List<Photo> of(List<Photo> all, List<String> regions, String t) {
        List<Photo> out = new ArrayList<>();
        for (Photo p : all) if (regions.contains(p.r) && (t == null || t.equals(p.t))) out.add(p);
        return out;
    }

    static List<Photo> pool(List<Photo> all, String region, String t) {
        List<Photo> pool = of(all, Arrays.asList(region), t);
        if (pool.isEmpty()) pool = of(all, Arrays.asList(region), null);
        if (of(all, Arrays.asList(region), null).size() < 2) {   // a region with fewer than two photos at all
            List<String> near = NEAR.containsKey(region) ? NEAR.get(region) : new ArrayList<>();
            List<Photo> n = of(all, near, t);
            pool.addAll(n.isEmpty() ? of(all, near, null) : n);
        }
        return pool;
    }

    /** Park-Miller, seeded by the day: the page's seededOrder(). */
    static List<Photo> order(List<Photo> pool, long day) {
        long s = Math.floorMod(day, 2147483646L) + 1;
        List<long[]> keys = new ArrayList<>();
        for (int i = 0; i < pool.size(); i++) {
            s = (s * 48271L) % 2147483647L;
            keys.add(new long[] {s, i});
        }
        keys.sort((a, b) -> Long.compare(a[0], b[0]));
        List<Photo> out = new ArrayList<>();
        for (long[] k : keys) out.add(pool.get((int) k[1]));
        return out;
    }

    static List<Photo> parse(String json) {
        List<Photo> out = new ArrayList<>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                String f = o.optString("f"), r = o.optString("r");
                if (f.matches("[A-Za-z0-9_-]+\\.jpg") && !r.isEmpty()) out.add(new Photo(f, r, o.optString("t", "day")));
            }
        } catch (Exception ignored) { }
        return out;
    }
}
