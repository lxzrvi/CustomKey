package com.customkey;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/** Export / import of the whole {@link Config} as a single JSON file. */
public final class Backup {

    public static final String MIME = "application/json";

    private Backup() {
    }

    public static Intent exportIntent() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(MIME);
        intent.putExtra(Intent.EXTRA_TITLE, "customkey-backup.json");
        return intent;
    }

    public static Intent importIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{MIME, "text/plain"});
        return intent;
    }

    /** Keeps images and fonts readable after a process restart. */
    public static void persist(Context context, Uri uri) {
        try {
            context.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
        }
    }

    public static boolean write(Context context, Uri uri, String data) {
        OutputStream out = null;
        try {
            out = context.getContentResolver().openOutputStream(uri);
            if (out == null) {
                return false;
            }
            out.write(data.getBytes("UTF-8"));
            out.flush();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            close(out);
        }
    }

    public static String read(Context context, Uri uri) {
        InputStream in = null;
        try {
            in = context.getContentResolver().openInputStream(uri);
            if (in == null) {
                return null;
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, "UTF-8"));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } catch (Throwable t) {
            return null;
        } finally {
            close(in);
        }
    }

    private static void close(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
