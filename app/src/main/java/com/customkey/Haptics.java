package com.customkey;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

public final class Haptics {

    private Haptics() {
    }

    /**
     * @param strength 0..100, or a negative value to stay silent.
     */
    public static void tap(View view, int strength) {
        if (view == null || strength < 0) {
            return;
        }
        if (strength == 0) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            return;
        }

        Vibrator vibrator = vibrator(view.getContext());
        if (vibrator == null || !vibrator.hasVibrator()) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            return;
        }

        int amplitude = Math.max(1, Math.min(255, strength * 255 / 100));
        long millis = 8L + strength / 8L;

        try {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, amplitude));
        } catch (Throwable t) {
            try {
                vibrator.vibrate(millis);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void test(Context context, int strength) {
        Vibrator vibrator = vibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        int s = Math.max(0, Math.min(100, strength));
        int amplitude = Math.max(1, Math.min(255, s * 255 / 100));
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(24L, amplitude));
        } catch (Throwable t) {
            try {
                vibrator.vibrate(24L);
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean available(Context context) {
        Vibrator vibrator = vibrator(context);
        return vibrator != null && vibrator.hasVibrator();
    }

    private static Vibrator vibrator(Context context) {
        if (context == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Object manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (manager instanceof VibratorManager) {
                return ((VibratorManager) manager).getDefaultVibrator();
            }
        }
        Object service = context.getSystemService(Context.VIBRATOR_SERVICE);
        return service instanceof Vibrator ? (Vibrator) service : null;
    }
}
