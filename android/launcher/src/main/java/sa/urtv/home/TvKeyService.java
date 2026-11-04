package sa.urtv.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * The remote's TV button opens an app of the owner's choice (optimize/dock.sh tvkey PKG). TCL remotes send their own
 * key code (4001) that TCL's GlobalKeyService turns into GUIDE; other remotes send TV or GUIDE. Only those keys are
 * caught, and only while an app is chosen: everything else passes untouched. An accessibility service is the one
 * place Android lets an app see a key before the system acts on it; it reads no screen content.
 */
public class TvKeyService extends AccessibilityService {
    static final int TCL_TV = 4001;

    static boolean isTvKey(int code) {
        return code == TCL_TV || code == KeyEvent.KEYCODE_TV || code == KeyEvent.KEYCODE_GUIDE;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (!isTvKey(e.getKeyCode())) return false;
        String pkg = Dock.prefs(this).getString("tvKeyApp", "");
        if (pkg.isEmpty()) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && e.getRepeatCount() == 0) launch(pkg);
        return true;   // the system never sees it, so TCL's guide does not open over the app
    }

    private void launch(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return;   // uninstalled: the key does nothing until it is set again
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(i);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }
}
