package com.morningpanel.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RssImageLoader {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    private RssImageLoader() { }

    static void load(Context context, ImageView view, String url) {
        view.setTag(url);
        if (url == null || url.isEmpty()) return;
        Bitmap cached = MEMORY.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        Context app = context.getApplicationContext();
        int width = Math.max(view.getWidth(), 480);
        int height = Math.max(view.getHeight(), 300);
        WORKERS.execute(() -> {
            Bitmap bitmap = null;
            try {
                File file = cacheFile(app, url);
                if (!file.isFile() || file.length() <= 0 || file.length() > MAX_BYTES) download(url, file);
                try { bitmap = decode(file, width, height); }
                catch (Exception invalidImage) { file.delete(); throw invalidImage; }
                if (bitmap != null) MEMORY.put(url, bitmap);
            } catch (Exception ignored) { }
            Bitmap result = bitmap;
            view.post(() -> {
                if (url.equals(view.getTag()) && result != null) view.setImageBitmap(result);
            });
        });
    }

    static void loadFull(Context context, ImageView view, String url, LoadListener listener) {
        String cacheKey = url == null ? "" : url + "|full";
        view.setTag(cacheKey);
        if (url == null || url.isEmpty()) {
            if (listener != null) view.post(() -> listener.onLoaded(false));
            return;
        }
        Bitmap cached = MEMORY.get(cacheKey);
        if (cached != null) {
            view.setImageBitmap(cached);
            if (listener != null) listener.onLoaded(true);
            return;
        }
        Context app = context.getApplicationContext();
        int width = Math.max(view.getResources().getDisplayMetrics().widthPixels, 720);
        int height = Math.max(view.getResources().getDisplayMetrics().heightPixels, 480);
        WORKERS.execute(() -> {
            Bitmap bitmap = null;
            try {
                File file = cacheFile(app, url);
                if (!file.isFile() || file.length() <= 0 || file.length() > MAX_BYTES) download(url, file);
                try { bitmap = decode(file, width, height); }
                catch (Exception invalidImage) { file.delete(); throw invalidImage; }
                if (bitmap != null) MEMORY.put(cacheKey, bitmap);
            } catch (Exception ignored) { }
            Bitmap result = bitmap;
            view.post(() -> {
                if (!cacheKey.equals(view.getTag())) return;
                if (result != null) view.setImageBitmap(result);
                if (listener != null) listener.onLoaded(result != null);
            });
        });
    }

    interface LoadListener { void onLoaded(boolean success); }

    private static void download(String address, File destination) throws Exception {
        URL url = new URL(address);
        if (!"http".equalsIgnoreCase(url.getProtocol()) && !"https".equalsIgnoreCase(url.getProtocol()))
            throw new IllegalArgumentException("Unsupported image URL");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(9000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "MorningPanel/0.4");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new java.io.IOException("Image request failed");
            String contentType = connection.getContentType();
            if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("image/"))
                throw new java.io.IOException("URL did not return an image");
            File parent = destination.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) throw new java.io.IOException("Unable to create image cache");
            File temporary = new File(parent, destination.getName() + ".tmp");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int size = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    size += read;
                    if (size > MAX_BYTES) throw new java.io.IOException("Image is too large");
                    bytes.write(buffer, 0, read);
                }
                try (FileOutputStream output = new FileOutputStream(temporary)) { bytes.writeTo(output); }
            }
            if (!temporary.renameTo(destination)) {
                temporary.delete();
                throw new java.io.IOException("Unable to save image cache");
            }
            trimCache(parent, destination);
        } finally { connection.disconnect(); }
    }

    private static void trimCache(File directory, File newest) {
        File[] files = directory.listFiles();
        if (files == null) return;
        java.util.Arrays.sort(files, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        long total = 0L;
        for (File file : files) if (file.isFile() && !file.getName().endsWith(".tmp")) total += file.length();
        for (File file : files) {
            if (total <= 32L * 1024L * 1024L) break;
            if (file.isFile() && !file.equals(newest) && !file.getName().endsWith(".tmp")) {
                long length = file.length();
                if (file.delete()) total -= length;
            }
        }
    }

    private static Bitmap decode(File file, int targetWidth, int targetHeight) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (FileInputStream input = new FileInputStream(file)) { BitmapFactory.decodeStream(input, null, bounds); }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new java.io.IOException("Invalid image");
        int sample = 1;
        while (bounds.outWidth / sample > targetWidth * 2 || bounds.outHeight / sample > targetHeight * 2) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        try (FileInputStream input = new FileInputStream(file)) { return BitmapFactory.decodeStream(input, null, options); }
    }

    private static File cacheFile(Context context, String address) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(address.getBytes("UTF-8"));
        StringBuilder key = new StringBuilder();
        for (byte value : digest) key.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return new File(new File(context.getCacheDir(), "rss-images"), key + ".img");
    }
}
