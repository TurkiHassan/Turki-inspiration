package com.ztv.player.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PosterLoader {
    /** Posters render at ~156dp; decode near that size instead of full resolution. */
    private static final int TARGET_WIDTH = 360;
    private static final int TARGET_HEIGHT = 540;

    /** Raw image bytes are kept on disk so a cold start renders artwork without the network. */
    private static final long DISK_MAX_BYTES = 64L * 1024 * 1024;
    private static final long DISK_TRIM_TO_BYTES = 48L * 1024 * 1024;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(32 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap.getByteCount();
                }
            };
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(6);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();
    private static final Map<String, List<WeakReference<ImageView>>> IN_FLIGHT = new HashMap<>();
    /** URLs that recently failed; skipped for a while so scrolling doesn't re-hammer dead hosts. */
    private static final Map<String, Long> FAILED = new HashMap<>();
    private static final long FAILURE_RETRY_MS = 3 * 60 * 1000;
    private static volatile File diskDir;

    private PosterLoader() {
    }

    /** Call once with the app cache dir to enable the on-disk artwork cache. */
    public static void init(File cacheRoot) {
        if (diskDir != null || cacheRoot == null) {
            return;
        }
        File dir = new File(cacheRoot, "posters");
        diskDir = dir;
        EXECUTOR.execute(() -> {
            dir.mkdirs();
            trimDiskCache(dir);
        });
    }

    public static void load(String url, ImageView imageView) {
        load(url, imageView, true);
    }

    public static void load(String url, ImageView imageView, boolean showPlaceholder) {
        imageView.setTag(url);
        imageView.animate().cancel();
        imageView.setAlpha(1f);
        if (showPlaceholder) {
            imageView.setBackground(ZtvUi.posterPlaceholder());
        }
        if (url == null || url.trim().isEmpty()) {
            imageView.setImageBitmap(null);
            return;
        }

        synchronized (LOCK) {
            Bitmap cached = CACHE.get(url);
            if (cached != null) {
                imageView.setAlpha(1f);
                imageView.setImageBitmap(cached);
                return;
            }
            Long failedAt = FAILED.get(url);
            if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_RETRY_MS) {
                imageView.setImageBitmap(null);
                return;
            }
            List<WeakReference<ImageView>> waitingTargets = IN_FLIGHT.get(url);
            if (waitingTargets != null) {
                imageView.setImageBitmap(null);
                waitingTargets.add(new WeakReference<>(imageView));
                return;
            }
            waitingTargets = new ArrayList<>();
            waitingTargets.add(new WeakReference<>(imageView));
            IN_FLIGHT.put(url, waitingTargets);
        }

        imageView.setImageBitmap(null);
        EXECUTOR.execute(() -> {
            Bitmap bitmap = loadFromDisk(url);
            boolean fromDiskCache = bitmap != null;
            if (bitmap == null) {
                bitmap = download(url);
            }
            List<WeakReference<ImageView>> targets;
            synchronized (LOCK) {
                if (bitmap != null) {
                    CACHE.put(url, bitmap);
                    FAILED.remove(url);
                } else {
                    FAILED.put(url, System.currentTimeMillis());
                }
                targets = IN_FLIGHT.remove(url);
            }
            if (bitmap == null || targets == null) {
                return;
            }
            Bitmap result = bitmap;
            MAIN.post(() -> {
                for (WeakReference<ImageView> reference : targets) {
                    ImageView target = reference.get();
                    if (target != null && url.equals(target.getTag())) {
                        target.setImageBitmap(result);
                        if (fromDiskCache) {
                            target.setAlpha(1f);
                        } else {
                            target.setAlpha(0f);
                            target.animate().alpha(1f).setDuration(180).start();
                        }
                    }
                }
            });
        });
    }

    private static Bitmap loadFromDisk(String url) {
        File dir = diskDir;
        if (dir == null) {
            return null;
        }
        File file = new File(dir, hash(url));
        if (!file.isFile()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = in.read(data, offset, data.length - offset);
                if (read == -1) {
                    break;
                }
                offset += read;
            }
            file.setLastModified(System.currentTimeMillis());
            return decodeScaled(data);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void saveToDisk(String url, byte[] data) {
        File dir = diskDir;
        if (dir == null || data.length == 0) {
            return;
        }
        File file = new File(dir, hash(url));
        File temp = new File(dir, file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(data);
            out.flush();
            if (!temp.renameTo(file)) {
                temp.delete();
            }
        } catch (Exception ignored) {
            temp.delete();
        }
    }

    private static void trimDiskCache(File dir) {
        try {
            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }
            long total = 0;
            for (File file : files) {
                total += file.length();
            }
            if (total <= DISK_MAX_BYTES) {
                return;
            }
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            for (File file : files) {
                if (total <= DISK_TRIM_TO_BYTES) {
                    break;
                }
                total -= file.length();
                file.delete();
            }
        } catch (Exception ignored) {
            // Cache trimming must never break image loading.
        }
    }

    private static String hash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                int unsigned = value & 0xff;
                builder.append(HEX[unsigned >>> 4]);
                builder.append(HEX[unsigned & 0x0f]);
            }
            return builder.toString();
        } catch (Exception exception) {
            return Integer.toHexString(url.hashCode());
        }
    }

    private static Bitmap download(String url) {
        byte[] data = fetch(url);
        // Some IPTV artwork hosts serve broken TLS (the handshake stalls, then dies)
        // while plain http works; the stream URLs are http anyway, so fall back.
        if (data == null && url.startsWith("https://")) {
            data = fetch("http://" + url.substring("https://".length()));
        }
        if (data == null) {
            return null;
        }
        saveToDisk(url, data);
        return decodeScaled(data);
    }

    private static byte[] fetch(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "ZTV/0.1 Android");
            // Draining and closing the stream (without disconnect()) lets the socket
            // return to the connection pool -- posters all come from the same host.
            try (InputStream stream = connection.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(32 * 1024);
                byte[] chunk = new byte[8192];
                int read;
                while ((read = stream.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                byte[] data = buffer.toByteArray();
                return data.length == 0 ? null : data;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Bitmap decodeScaled(byte[] data) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        int sampleSize = 1;
        while (options.outWidth / (sampleSize * 2) >= TARGET_WIDTH
                && options.outHeight / (sampleSize * 2) >= TARGET_HEIGHT) {
            sampleSize *= 2;
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }
}
