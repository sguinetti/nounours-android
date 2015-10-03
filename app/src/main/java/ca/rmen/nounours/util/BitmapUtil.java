/*
 *   Copyright (c) 2009 - 2015 Carmen Alvarez
 *
 *   This file is part of Nounours for Android.
 *
 *   Nounours for Android is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Nounours for Android is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Nounours for Android.  If not, see <http://www.gnu.org/licenses/>.
 */

package ca.rmen.nounours.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

import java.io.File;

import ca.rmen.nounours.Constants;
import ca.rmen.nounours.compat.BitmapCompat;
import ca.rmen.nounours.compat.EnvironmentCompat;
import ca.rmen.nounours.data.Image;

public class BitmapUtil {
    private static final String TAG = Constants.TAG + BitmapUtil.class.getSimpleName();

    private static final int BITMAP_LOAD_RETRIES = 3;
    private static final int BITMAP_INITIAL_SUB_SAMPLE = 0;

    public static Bitmap createBitmap(Context context, Image image) {
        final Bitmap result;
        // This is one of the downloaded images, in the sdcard.
        String externalFilesPath = EnvironmentCompat.getExternalFilesPath(context);
        if (externalFilesPath != null && image.getFilename().contains(externalFilesPath)) {
            // Load the new image
            Log.v(TAG, "Load themed image.");
            result = loadBitmap(context, image.getFilename());
        }
        // This is one of the default images bundled in the apk.
        else {
            final int imageResId = context.getResources().getIdentifier(image.getFilename(), "drawable",
                    context.getClass().getPackage().getName());
            // Load the image from the resource file.
            Log.v(TAG, "Load default image " + imageResId);
            result = loadBitmap(context, imageResId);
        }
        return result;
    }

    public static BitmapDrawable createBitmapDrawable(Context context, Image image) {
        Bitmap bitmap = createBitmap(context, image);
        return BitmapCompat.createBitmapDrawable(context, bitmap);
    }

    private static Bitmap loadBitmap(Context context, String filename) {
        return loadBitmap(context, new File(filename), 0, BITMAP_INITIAL_SUB_SAMPLE, BITMAP_LOAD_RETRIES);
    }

    private static Bitmap loadBitmap(Context context, int resourceId) {
        return loadBitmap(context, null, resourceId, BITMAP_INITIAL_SUB_SAMPLE, BITMAP_LOAD_RETRIES);
    }

    private static Bitmap loadBitmap(Context context, File file, int resourceId, int initialSubSample, int retries) {
        int inSampleSize = BITMAP_LOAD_RETRIES - retries + initialSubSample;
        BitmapFactory.Options options = BitmapCompat.createBitmapFactoryOptions(inSampleSize);
        try {
            Log.v(TAG, "Load image " + (file == null ? "" + resourceId : file.getAbsolutePath()) + ".  "
                    + retries + " left.  Sample size = " + options.inSampleSize);

            if (file != null)
                return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            else
                return BitmapFactory.decodeResource(context.getResources(), resourceId, options);
        } catch (OutOfMemoryError e) {
            System.gc();
            if (retries > 0)
                return loadBitmap(context, file, resourceId, initialSubSample, retries - 1);
        }
        return null;
    }

}
