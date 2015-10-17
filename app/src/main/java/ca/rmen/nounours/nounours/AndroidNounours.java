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

package ca.rmen.nounours.nounours;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

import ca.rmen.nounours.Constants;
import ca.rmen.nounours.Nounours;
import ca.rmen.nounours.NounoursAnimationHandler;
import ca.rmen.nounours.NounoursSoundHandler;
import ca.rmen.nounours.NounoursVibrateHandler;
import ca.rmen.nounours.R;
import ca.rmen.nounours.compat.DisplayCompat;
import ca.rmen.nounours.data.Image;
import ca.rmen.nounours.data.Theme;
import ca.rmen.nounours.io.StreamLoader;
import ca.rmen.nounours.nounours.cache.ImageCache;
import ca.rmen.nounours.settings.NounoursSettings;
import ca.rmen.nounours.util.ThemeUtil;

/**
 * Implementation of the abstract Nounours class, containing logic specific to
 * Android.
 *
 * @author Carmen Alvarez
 */
public class AndroidNounours extends Nounours {

    public interface AndroidNounoursListener {
        void onThemeLoaded();
    }

    private static final String TAG = Constants.TAG + AndroidNounours.class.getSimpleName();

    private final Context mContext;
    private final Handler mUIHandler;
    private final ImageView mImageView;
    private final AndroidNounoursListener mListener;
    private final ImageCache mImageCache = new ImageCache();
    private final SoundHandler mSoundHandler;

    private ProgressDialog mProgressDialog;


    /**
     * Open the CSV data files and call the superclass
     * {@link Nounours#init(StreamLoader, NounoursAnimationHandler, NounoursSoundHandler, NounoursVibrateHandler, InputStream, InputStream, String)}
     * method.
     *
     * @param context The android mContext.
     */
    public AndroidNounours(final Context context, Handler uiHandler, ImageView imageView, AndroidNounoursListener listener) {

        mContext = context;
        mUIHandler = uiHandler;
        mImageView = imageView;
        mListener = listener;
        StreamLoader streamLoader = new AssetStreamLoader(context);

        String themeId = NounoursSettings.getThemeId(context);
        AnimationHandler animationHandler = new AnimationHandler(this);
        mSoundHandler = new SoundHandler(context);
        VibrateHandler vibrateHandler = new VibrateHandler(context);
        final InputStream propertiesFile = context.getResources().openRawResource(R.raw.nounours);
        final InputStream themesFile = context.getResources().openRawResource(R.raw.themes);

        try {
            init(streamLoader, animationHandler, mSoundHandler, vibrateHandler, propertiesFile,
                    themesFile, themeId);
            setEnableVibrate(NounoursSettings.isSoundEnabled(context));
            setEnableSound(NounoursSettings.isSoundEnabled(context));
            setIdleTimeout(NounoursSettings.getIdleTimeout(context));
        } catch (final IOException e) {
            Log.e(TAG, "Error initializing nounours", e);
        }
    }

    @Override
    protected boolean cacheResources() {
        boolean result = mImageCache.cacheImages(mContext, getCurrentTheme().getImages().values(), mUIHandler, mImageCacheListener);
        mSoundHandler.cacheSounds(getCurrentTheme());
        return result;
    }

    /**
     * Load the new image set in a separate thread, showing the progress bar
     */
    @Override
    public void useTheme(final String id) {
        Log.v(TAG, "useTheme " + id);

        // Get the name of this theme.
        Theme theme = getThemes().get(id);
        CharSequence themeLabel = ThemeUtil.getThemeLabel(mContext, theme);

        // MEMORY
        mImageView.setImageResource(R.drawable.defaultimg_sm);
        mImageCache.clearImageCache();
        Runnable themeLoader = new Runnable() {
            @SuppressWarnings("synthetic-access")
            @Override
            public void run() {

                AndroidNounours.super.useTheme(id);

                runTask(new Runnable() {
                    public void run() {
                        themeLoaded();
                    }
                });

            }
        };
        runTaskWithProgressBar(themeLoader, mContext.getString(R.string.loading, themeLabel), theme.getImages().size());
    }

    private void themeLoaded() {
        Log.v(TAG, "themeLoaded");
        resizeView();
        mProgressDialog.dismiss();
        mListener.onThemeLoaded();
    }

    private void resizeView() {
        Theme theme = getCurrentTheme();
        if (theme == null)
            return;
        ViewGroup.LayoutParams layoutParams = mImageView.getLayoutParams();

        float widthRatio = (float) DisplayCompat.getWidth(mContext) / theme.getWidth();
        float heightRatio = (float) DisplayCompat.getHeight(mContext) / theme.getHeight();
        Log.v(TAG, widthRatio + ": " + heightRatio);
        float ratioToUse = widthRatio > heightRatio ? heightRatio : widthRatio;

        layoutParams.height = (int) (ratioToUse * theme.getHeight());
        layoutParams.width = (int) (ratioToUse * theme.getWidth());
        Log.v(TAG, "Scaling view to " + layoutParams.width + "x" + layoutParams.height);
        mImageView.setLayoutParams(layoutParams);

    }

    /**
     * Display a picture on the screen.
     *
     * @see ca.rmen.nounours.Nounours#displayImage(ca.rmen.nounours.data.Image)
     */
    @Override
    protected void displayImage(final Image image) {
        Log.v(TAG, "displayImage " + image);
        if (image == null) {
            return;
        }
        final Bitmap bitmap = mImageCache.getDrawableImage(mContext, image);
        if (bitmap == null)
            return;
        mImageView.setImageBitmap(bitmap);
    }

    /**
     * Trace.
     */
    @Override
    protected void debug(final Object o) {
        if (o instanceof Throwable) {
            Throwable t = (Throwable) o;
            Log.w(TAG, t.getMessage(), t);
        } else {
            Log.v(TAG, "" + o);
        }
    }

    /**
     * UI threads should be run with an Android thread call.
     *
     * @see ca.rmen.nounours.Nounours#runTask(java.lang.Runnable)
     */
    @Override
    protected void runTask(final Runnable task) {
        mUIHandler.post(task);
    }

    /**
     * Run a task, showing the progress bar while the task runs.
     */
    private void runTaskWithProgressBar(final Runnable task, String message, int max) {
        if (mProgressDialog != null)
            mProgressDialog.dismiss();
        createProgressDialog(max, message);
        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                task.run();
                mProgressDialog.dismiss();
                Log.v(TAG, "runTaskWithProgressBar complete");
            }
        };
        new Thread(runnable).start();
    }

    /**
     * Update the currently showing progress bar.
     */
    private void updateProgressBar(final int progress, final int max, final String message) {
        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                // show the progress bar if it is not already showing.
                if (mProgressDialog == null || !mProgressDialog.isShowing())
                    createProgressDialog(max, message);
                // Update the progress
                mProgressDialog.setProgress(progress);
                mProgressDialog.setMax(max);
                mProgressDialog.setMessage(message);
                debug("updateProgressBar " + progress + "/" + max + ": " + message);
                if (progress == max) mProgressDialog.dismiss();

            }
        };
        runTask(runnable);
    }

    /**
     * Create a determinate progress dialog with the given size and text.
     */
    private void createProgressDialog(int max, String message) {
        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setTitle("");
        mProgressDialog.setMessage(message);
        mProgressDialog.setIndeterminate(max < 0);
        mProgressDialog.setMax(max);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setProgress(0);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
        debug("createProgressDialog " + max + ": " + message);
    }

    /**
     * Cleanup.
     */
    public void onDestroy() {
        debug("destroy");
        mImageCache.clearImageCache();
    }

    @Override
    protected int getDeviceHeight() {
        return mImageView.getHeight();
    }

    @Override
    protected int getDeviceWidth() {
        return mImageView.getWidth();
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final ImageCache.ImageCacheListener mImageCacheListener = new ImageCache.ImageCacheListener() {
        @Override
        public void onImageLoaded(final Image image, int progress, int total) {
            Log.v(TAG, "onImageLoaded: " + progress + "/" + total);
            setImage(image);
            CharSequence themeName = ThemeUtil.getThemeLabel(mContext, getCurrentTheme());
            updateProgressBar(progress, total, mContext.getString(R.string.loading, themeName));
        }
    };
}
