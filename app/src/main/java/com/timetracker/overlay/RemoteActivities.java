package com.timetracker.overlay;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Optional extension point for activities running right now on another
 * device, so the app and the overlay can show them next to the local one.
 *
 * Nothing registers a provider in the standard build, so this is inert there:
 * no extra history rows, no extra timeline segments, nothing added to the
 * totals. Like {@link BackupExtensions} and {@link ShortcutSuggestions}, the
 * shape is generic on purpose, so a build variant can be added or removed
 * without the app or the overlay ever changing again.
 */
public class RemoteActivities {

    /** One activity running elsewhere. */
    public static class Live {
        /** The activity's name. */
        public final String name;
        /** The color this device uses for that name. */
        public final int color;
        /** When it started, epoch millis. */
        public final long startTime;
        /** Tracked seconds at {@link #asOfMs}, pauses excluded. */
        public final int elapsedSeconds;
        /** The moment {@link #elapsedSeconds} was true, epoch millis. */
        public final long asOfMs;
        /** Whether it was counting at {@link #asOfMs}, rather than paused. */
        public final boolean counting;
        /** A short label for where it runs, shown in the history row as is. */
        public final String where;

        public Live(String name, int color, long startTime, int elapsedSeconds,
                    long asOfMs, boolean counting, String where) {
            this.name = name;
            this.color = color;
            this.startTime = startTime;
            this.elapsedSeconds = elapsedSeconds;
            this.asOfMs = asOfMs;
            this.counting = counting;
            this.where = where;
        }

        /** Tracked seconds right now: the known figure, plus the time since if it was counting. */
        public int secondsNow() {
            if (!counting) return elapsedSeconds;
            long since = (System.currentTimeMillis() - asOfMs) / 1000;
            return elapsedSeconds + (int) Math.max(0, since);
        }
    }

    /** Implemented by a build variant that knows about activities on other devices. */
    public interface Provider {
        /**
         * The activities running elsewhere right now. Called on every timeline
         * redraw, so it must be cheap: no disk or database work on this path.
         */
        List<Live> current();
    }

    private static Provider provider;
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private static final Handler mainThread = new Handler(Looper.getMainLooper());

    /** Registered once at startup by a variant that has activities to report. */
    public static void register(Provider p) {
        provider = p;
    }

    /** Never null, and a faulty provider never reaches the caller. */
    public static List<Live> current() {
        if (provider == null) return new ArrayList<>();
        try {
            List<Live> out = provider.current();
            return out == null ? new ArrayList<>() : out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** A screen that should redraw when the list changes. Runs on the main thread. */
    public static void addListener(Runnable r) {
        if (r != null && !listeners.contains(r)) listeners.add(r);
    }

    public static void removeListener(Runnable r) {
        listeners.remove(r);
    }

    /** Called by a provider when its list changed. Safe to call from any thread. */
    public static void notifyChanged() {
        mainThread.post(() -> {
            for (Runnable r : listeners) {
                try {
                    r.run();
                } catch (Exception e) {
                    // One faulty screen must not stop the others from redrawing.
                }
            }
        });
    }
}
