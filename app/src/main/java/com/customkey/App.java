package com.customkey;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Installs a crash catcher so a failure shows the real stack trace on screen
 * instead of "CustomKey keeps stopping" with nothing to go on.
 */
public class App extends Application {

    public static final String CRASH_FILE = "last_crash.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Prefs.init(this);
        } catch (Throwable ignored) {
        }
        installCrashHandler();
    }

    private void installCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                String trace = "CustomKey 2.0 (versionCode 2)\n"
                        + "Device: " + Build.MANUFACTURER + " " + Build.MODEL
                        + "\nAndroid: " + Build.VERSION.RELEASE
                        + " (API " + Build.VERSION.SDK_INT + ")\n"
                        + "Thread: " + thread.getName()
                        + "\n\n" + Log.getStackTraceString(error);

                File file = new File(getFilesDir(), CRASH_FILE);
                OutputStream out = new FileOutputStream(file);
                try {
                    out.write(trace.getBytes("UTF-8"));
                } finally {
                    out.close();
                }

                Intent intent = new Intent(this, CrashActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);

                // CrashActivity lives in its own process, so give it a moment
                // to come up before this one dies.
                Thread.sleep(350);
            } catch (Throwable ignored) {
            }

            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        });
    }
}
