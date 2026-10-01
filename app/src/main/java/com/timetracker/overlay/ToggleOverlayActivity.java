package com.timetracker.overlay;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

/**
 * Invisible screen behind the "Start/stop overlay" shortcut. It does what the
 * Start/Stop Overlay button in MainActivity does, then closes, so whatever was
 * on screen stays there and the app never opens.
 *
 * Two ways in:
 * - the app shortcut from res/xml/shortcuts.xml (long-press the app icon, or
 *   any automation app that lists app shortcuts)
 * - the older "create shortcut" list (home screen widget picker, gesture apps),
 *   which asks this activity for a shortcut via ACTION_CREATE_SHORTCUT
 */
public class ToggleOverlayActivity extends Activity {

    private static final int OVERLAY_PERM_CODE = 100;
    private static final String SHORTCUT_ID = "toggle_overlay";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction())) {
            setResult(RESULT_OK, buildShortcutResult());
            close();
            return;
        }

        // Same steps as MainActivity's toggle button
        if (OverlayService.isOverlayVisible) {
            stopService(new Intent(this, OverlayService.class));
        } else {
            if (!Settings.canDrawOverlays(this)) {
                // Stays open (invisible) until the user comes back from the
                // permission screen, then starts the overlay if it was granted
                startActivityForResult(
                    new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())),
                    OVERLAY_PERM_CODE);
                return;
            }
            startOverlayService();
        }
        close();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == OVERLAY_PERM_CODE && Settings.canDrawOverlays(this)) {
            startOverlayService();
        }
        close();
    }

    private void startOverlayService() {
        Intent svc = new Intent(this, OverlayService.class);
        svc.putExtra(OverlayService.EXTRA_SHOW_OVERLAY, true);
        startForegroundService(svc);
    }

    private void close() {
        finish();
        overridePendingTransition(0, 0);
    }

    /**
     * The answer to ACTION_CREATE_SHORTCUT. Launchers read the pin request that
     * ShortcutManager puts in, other apps read the older extras, so both go in
     * (the same thing AndroidX's ShortcutManagerCompat does).
     */
    @SuppressWarnings("deprecation")
    private Intent buildShortcutResult() {
        String label = getString(R.string.toggle_overlay_label);
        // The app's own icon, so a build variant with its own icon gets that one
        int iconRes = getApplicationInfo().icon;
        Intent toggle = new Intent(this, ToggleOverlayActivity.class)
            .setAction(Intent.ACTION_VIEW);

        Intent result = null;
        try {
            ShortcutManager sm = getSystemService(ShortcutManager.class);
            if (sm != null) {
                ShortcutInfo info = new ShortcutInfo.Builder(this, SHORTCUT_ID)
                    .setShortLabel(label)
                    .setIcon(Icon.createWithResource(this, iconRes))
                    .setIntent(toggle)
                    .build();
                result = sm.createShortcutResultIntent(info);
            }
        } catch (RuntimeException e) {
            // The older extras below still work without the pin request
        }
        if (result == null) result = new Intent();

        result.putExtra(Intent.EXTRA_SHORTCUT_INTENT, toggle);
        result.putExtra(Intent.EXTRA_SHORTCUT_NAME, label);
        result.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
            Intent.ShortcutIconResource.fromContext(this, iconRes));
        return result;
    }
}
