package com.timetracker.overlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        OverlayPreferences prefs = new OverlayPreferences(context);
        if (prefs.isImmersiveClockEnabled()) {
            Intent svc = new Intent(context, OverlayService.class);
            context.startForegroundService(svc);
        }
    }
}
