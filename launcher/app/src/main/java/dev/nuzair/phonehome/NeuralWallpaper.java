package dev.nuzair.phonehome;

import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import android.media.MediaPlayer;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Local silent video; no network, audio focus, wake lock, or background playback. */
public final class NeuralWallpaper extends WallpaperService {
    private final java.util.concurrent.CopyOnWriteArrayList<VideoEngine> engines = new java.util.concurrent.CopyOnWriteArrayList<>();
    @Override public Engine onCreateEngine() { return new VideoEngine(); }
    @Override protected void dump(FileDescriptor fd, PrintWriter out, String[] args) {
        super.dump(fd, out, args);
        for (VideoEngine engine : engines) out.println("CustomPhone wallpaper: " + engine.state + " visible=" + engine.visible + " surface=" + engine.available);
    }
    private final class VideoEngine extends Engine {
        private MediaPlayer player;
        private boolean visible, available, failed;
        private volatile String state = "idle";
        @Override public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder); engines.add(this); setTouchEventsEnabled(false); setOffsetNotificationsEnabled(false);
        }
        @Override public void onVisibilityChanged(boolean value) {
            visible = value;
            if (visible) open(); else release();
        }
        @Override public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder); available = true; failed = false; open();
        }
        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            if (player == null) open();
        }
        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            available = false; release(); super.onSurfaceDestroyed(holder);
        }
        @Override public void onDestroy() { release(); engines.remove(this); super.onDestroy(); }
        private void drawPoster() {
            if (!available || player != null) return;
            Canvas canvas = null;
            Bitmap poster = null;
            try {
                canvas = getSurfaceHolder().lockCanvas();
                if (canvas == null) return;
                canvas.drawColor(Color.BLACK);
                poster = BitmapFactory.decodeResource(getResources(), R.drawable.neural_poster);
                float scale = Math.max(canvas.getWidth() / (float) poster.getWidth(), canvas.getHeight() / (float) poster.getHeight());
                float w = poster.getWidth() * scale, h = poster.getHeight() * scale;
                canvas.drawBitmap(poster, null, new RectF((canvas.getWidth()-w)/2, (canvas.getHeight()-h)/2,
                    (canvas.getWidth()+w)/2, (canvas.getHeight()+h)/2), new Paint(Paint.FILTER_BITMAP_FLAG));
            } catch (RuntimeException e) { Log.w("CustomPhoneWallpaper", "Poster unavailable", e); }
            finally {
                if (canvas != null) getSurfaceHolder().unlockCanvasAndPost(canvas);
                if (poster != null) poster.recycle();
            }
        }
        private void open() {
            if (!visible || !available || failed || player != null || !getSurfaceHolder().getSurface().isValid()) return;
            final MediaPlayer p = new MediaPlayer(); player = p; state = "preparing";
            try (AssetFileDescriptor data = getResources().openRawResourceFd(R.raw.neural)) {
                p.setDataSource(data.getFileDescriptor(), data.getStartOffset(), data.getLength());
                p.setSurface(getSurfaceHolder().getSurface());
                p.setVolume(0, 0); p.setLooping(true);
                p.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                p.setOnPreparedListener(mp -> {
                    if (player == mp && visible && available) {
                        mp.start(); state = "playing"; Log.i("CustomPhoneWallpaper", "Video playing");
                    }
                });
                p.setOnInfoListener((mp, what, extra) -> {
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && player == mp) {
                        state = "rendering"; Log.i("CustomPhoneWallpaper", "First frame rendered");
                    }
                    return false;
                });
                p.setOnErrorListener((mp, what, extra) -> {
                    Log.w("CustomPhoneWallpaper", "Video error " + what + "/" + extra);
                    if (player == mp) { release(); failed = true; state = "poster fallback"; drawPoster(); }
                    return true;
                });
                p.prepareAsync();
            } catch (Exception e) {
                Log.w("CustomPhoneWallpaper", "Video unavailable", e);
                release(); failed = true; state = "poster fallback"; drawPoster();
            }
        }
        private void release() {
            MediaPlayer old = player; player = null;
            if (old != null) { old.setOnPreparedListener(null); old.setOnInfoListener(null); old.setOnErrorListener(null); old.release(); }
            state = "released";
        }
    }
}
