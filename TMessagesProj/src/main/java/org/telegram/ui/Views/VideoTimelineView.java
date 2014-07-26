/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Views;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;

@TargetApi(10)
public class VideoTimelineView extends View {
    private long videoLength = 0;
    private float progressLeft = 0;
    private float progressRight = 1;
    private Paint paint;
    private Paint paint2;
    private boolean pressedLeft = false;
    private boolean pressedRight = false;
    private float pressDx = 0;
    private MediaMetadataRetriever mediaMetadataRetriever = null;
    private VideoTimelineViewDelegate delegate = null;
    private ArrayList<Bitmap> frames = new ArrayList<Bitmap>();
    private AsyncTask<Integer, Integer, Bitmap> currentTask = null;
    private static final Integer sync = 1;
    private long frameTimeOffset = 0;
    private int frameWidth = 0;
    private int frameHeight = 0;
    private int framesToLoad = 0;

    public abstract interface VideoTimelineViewDelegate {
        public void onLeftProgressChanged(float progress);
        public void onRifhtProgressChanged(float progress);
    }

    private void init() {
        paint = new Paint();
        paint.setColor(0xff66d1ee);
        paint2 = new Paint();
        paint2.setColor(0x2266d1ee);
    }

    public VideoTimelineView(Context context) {
        super(context);
        init();
    }

    public VideoTimelineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VideoTimelineView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public float getLeftProgress() {
        return progressLeft;
    }

    public float getRightProgress() {
        return progressRight;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();

        int width = getMeasuredWidth() - AndroidUtilities.dp(12);
        int startX = (int)(width * progressLeft) + AndroidUtilities.dp(3);
        int endX = (int)(width * progressRight) + AndroidUtilities.dp(9);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int additionWidth = AndroidUtilities.dp(12);
            if (startX - additionWidth <= x && x <= startX + additionWidth && y >= 0 && y <= getMeasuredHeight()) {
                pressedLeft = true;
                pressDx = (int)(x - startX);
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            } else if (endX - additionWidth <= x && x <= endX + additionWidth && y >= 0 && y <= getMeasuredHeight()) {
                pressedRight = true;
                pressDx = (int)(x - endX);
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (pressedLeft) {
                pressedLeft = false;
                return true;
            } else if (pressedRight) {
                pressedRight = false;
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (pressedLeft) {
                startX = (int)(x - pressDx);
                if (startX < AndroidUtilities.dp(3)) {
                    startX = AndroidUtilities.dp(3);
                } else if (startX > endX - AndroidUtilities.dp(6)) {
                    startX = endX - AndroidUtilities.dp(6);
                }
                progressLeft = (float)(startX - AndroidUtilities.dp(3)) / (float)width;
                if (delegate != null) {
                    delegate.onLeftProgressChanged(progressLeft);
                }
                invalidate();
                return true;
            } else if (pressedRight) {
                endX = (int)(x - pressDx);
                if (endX < startX + AndroidUtilities.dp(6)) {
                    endX = startX + AndroidUtilities.dp(6);
                } else if (endX > width + AndroidUtilities.dp(9)) {
                    endX = width + AndroidUtilities.dp(9);
                }
                progressRight = (float)(endX - AndroidUtilities.dp(9)) / (float)width;
                if (delegate != null) {
                    delegate.onRifhtProgressChanged(progressRight);
                }
                invalidate();
                return true;
            }
        }
        return false;
    }

    public void setVideoPath(String path) {
        mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(path);
        String duration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        videoLength = Long.parseLong(duration);
    }

    public void setDelegate(VideoTimelineViewDelegate delegate) {
        this.delegate = delegate;
    }

    private void reloadFrames(int frameNum) {
        if (mediaMetadataRetriever == null) {
            return;
        }
        if (frameNum == 0) {
            frameHeight = getMeasuredHeight() - AndroidUtilities.dp(4);
            framesToLoad = getMeasuredWidth() / frameHeight;
            frameWidth = (int)Math.ceil((float)getMeasuredWidth() / (float)framesToLoad);
            frameTimeOffset = videoLength / framesToLoad;
        }
        currentTask = new AsyncTask<Integer, Integer, Bitmap>() {
            private int frameNum = 0;

            @Override
            protected Bitmap doInBackground(Integer... objects) {
                frameNum = objects[0];
                Bitmap bitmap = null;
                if (isCancelled()) {
                    return null;
                }
                try {
                    bitmap = mediaMetadataRetriever.getFrameAtTime(frameTimeOffset * frameNum * 1000);
                    if (isCancelled()) {
                        return null;
                    }
                    if (bitmap != null) {
                        Bitmap result = Bitmap.createBitmap(frameWidth, frameHeight, bitmap.getConfig());
                        Canvas canvas = new Canvas(result);
                        float scaleX = (float) frameWidth / (float) bitmap.getWidth();
                        float scaleY = (float) frameHeight / (float) bitmap.getHeight();
                        float scale = scaleX > scaleY ? scaleX : scaleY;
                        int w = (int) (bitmap.getWidth() * scale);
                        int h = (int) (bitmap.getHeight() * scale);
                        Rect srcRect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                        Rect destRect = new Rect((frameWidth - w) / 2, (frameHeight - h) / 2, w, h);
                        Paint paint = new Paint();
                        canvas.drawBitmap(bitmap, srcRect, destRect, null);
                        bitmap.recycle();
                        bitmap = result;
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                return bitmap;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (!isCancelled()) {
                    frames.add(bitmap);
                    invalidate();
                    if (frameNum < framesToLoad) {
                        reloadFrames(frameNum + 1);
                    }
                }
            }
        };

        if (android.os.Build.VERSION.SDK_INT >= 11) {
            currentTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, frameNum, null, null);
        } else {
            currentTask.execute(frameNum, null, null);
        }
    }

    public void destroy() {
        synchronized (sync) {
            try {
                if (mediaMetadataRetriever != null) {
                    mediaMetadataRetriever.release();
                    mediaMetadataRetriever = null;
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        for (Bitmap bitmap : frames) {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        frames.clear();
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
    }

    public void clearFrames() {
        for (Bitmap bitmap : frames) {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        frames.clear();
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (frames.isEmpty() && currentTask == null) {
            reloadFrames(0);
        } else {
            int offset = 0;
            for (Bitmap bitmap : frames) {
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, offset * frameWidth, AndroidUtilities.dp(2), null);
                }
                offset++;
            }
        }
        int width = getMeasuredWidth() - AndroidUtilities.dp(12);
        int startX = (int)(width * progressLeft);
        int endX = (int)(width * progressRight);
        canvas.drawRect(startX, 0, startX + AndroidUtilities.dp(6), getMeasuredHeight(), paint);
        canvas.drawRect(endX + AndroidUtilities.dp(6), 0, endX + AndroidUtilities.dp(12), getMeasuredHeight(), paint);
        canvas.drawRect(startX + AndroidUtilities.dp(6), AndroidUtilities.dp(4), endX + AndroidUtilities.dp(6), getMeasuredHeight() - AndroidUtilities.dp(4), paint2);
        canvas.drawRect(startX + AndroidUtilities.dp(6), AndroidUtilities.dp(2), endX + AndroidUtilities.dp(6), AndroidUtilities.dp(4), paint);
        canvas.drawRect(startX + AndroidUtilities.dp(6), getMeasuredHeight() - AndroidUtilities.dp(4), endX + AndroidUtilities.dp(6), getMeasuredHeight() - AndroidUtilities.dp(2), paint);
    }
}
