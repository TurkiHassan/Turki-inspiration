package com.ztv.player;

import android.app.Activity;
import android.os.Build;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Switches the screen refresh rate to match the playing video (AFR) through the window's
 * preferred display mode. The preference only lives on this window, so the system restores the
 * original mode as soon as the player closes.
 */
final class DisplayModeSwitcher {
    private final Activity activity;
    private float appliedFrameRate = -1f;

    DisplayModeSwitcher(Activity activity) {
        this.activity = activity;
    }

    void matchFrameRate(float frameRate) {
        if (!(frameRate > 0f) || frameRate == appliedFrameRate) {
            return;
        }
        Display display = currentDisplay();
        if (display == null) {
            return;
        }
        Display.Mode current = display.getMode();
        List<FrameRateMatchPolicy.DisplayMode> modes = new ArrayList<>();
        for (Display.Mode mode : display.getSupportedModes()) {
            modes.add(toPolicyMode(mode));
        }
        int modeId = FrameRateMatchPolicy.pickModeId(frameRate, modes, toPolicyMode(current));
        if (modeId == FrameRateMatchPolicy.NO_MATCH) {
            return;
        }
        appliedFrameRate = frameRate;
        if (modeId == current.getModeId()) {
            return;
        }
        Window window = activity.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.preferredDisplayModeId = modeId;
        window.setAttributes(params);
    }

    private Display currentDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return activity.getDisplay();
        }
        return activity.getWindowManager().getDefaultDisplay();
    }

    private static FrameRateMatchPolicy.DisplayMode toPolicyMode(Display.Mode mode) {
        return new FrameRateMatchPolicy.DisplayMode(mode.getModeId(),
                mode.getPhysicalWidth(), mode.getPhysicalHeight(), mode.getRefreshRate());
    }
}
