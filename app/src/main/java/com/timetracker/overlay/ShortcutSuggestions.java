package com.timetracker.overlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional extension point that lets a build variant offer shortcut names to
 * pre-fill the overlay's batch-add dialog (long-press on +), for instance
 * names it knows from another device.
 *
 * Nothing registers a provider in the standard build, so the dialog opens
 * empty there, exactly as before. Like {@link BackupExtensions}, the shape is
 * generic on purpose: a variant can be added or removed without the overlay
 * code ever changing again.
 */
public class ShortcutSuggestions {

    /** Implemented by a build variant that knows shortcut names worth adding. */
    public interface Provider {
        /**
         * @param existing the shortcut names already on the overlay
         * @return names to offer, shown one per line in the dialog; null or
         *         empty means nothing to offer
         */
        List<String> suggest(List<String> existing);
    }

    private static Provider provider;

    /** Registered once at startup by a variant that has suggestions to make. */
    public static void register(Provider p) {
        provider = p;
    }

    /**
     * Names worth adding. Never null, and a faulty provider never reaches the
     * dialog: the overlay must keep working whatever a variant does.
     */
    public static List<String> suggest(List<String> existing) {
        if (provider == null) return new ArrayList<>();
        try {
            List<String> out = provider.suggest(existing);
            return out == null ? new ArrayList<>() : out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
