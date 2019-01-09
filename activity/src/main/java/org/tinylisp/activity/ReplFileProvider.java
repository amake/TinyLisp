package org.tinylisp.activity;

import android.content.Context;
import android.net.Uri;
import android.support.v4.content.FileProvider;

import java.io.File;

public class ReplFileProvider extends FileProvider {
    public static Uri getUriForFile(Context context, File file) {
        String authority = context.getApplicationContext().getPackageName() + ".org.tinylisp.activity.fileprovider";
        return getUriForFile(context, authority, file);
    }
}
