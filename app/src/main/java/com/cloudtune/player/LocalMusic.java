package com.cloudtune.player;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Private audio copies, using explicit resource cleanup for AIDE's editor/parser. */
final class LocalMusic {
    static File index(Context context) {
        return new File(context.getFilesDir(), "local-library.json");
    }

    static synchronized ArrayList<Track> read(Context context) {
        ArrayList<Track> tracks = new ArrayList<Track>();
        try {
            JSONArray array = new JSONArray(readText(index(context)));
            for (int i = 0; i < array.length(); i++) {
                Track track = new Track(array.getJSONObject(i));
                if (track.isLocal() && new File(track.localPath()).isFile()) tracks.add(track);
            }
        } catch (Exception ignored) {
        }
        return tracks;
    }

    static synchronized void save(Context context, ArrayList<Track> tracks) throws Exception {
        JSONArray array = new JSONArray();
        for (Track track : tracks) array.put(track.source);
        File temporary = new File(context.getFilesDir(), "local-library.tmp");
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(temporary);
            output.write(array.toString().getBytes("UTF-8"));
            output.flush();
            output.getFD().sync();
        } finally {
            close(output);
        }
        if (!temporary.renameTo(index(context))) throw new IOException("无法保存本地列表");
    }

    static String displayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            String[] columns = new String[1];
            columns[0] = OpenableColumns.DISPLAY_NAME;
            cursor = context.getContentResolver().query(uri, columns, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                try { cursor.close(); } catch (Exception ignored) { }
            }
        }
        String path = uri.getLastPathSegment();
        return path == null ? "本地音乐" : path;
    }

    static Track importUri(Context context, Uri uri) throws Exception {
        return importUri(context, uri, null);
    }

    static Track importUri(Context context, Uri uri, AtomicBoolean cancel) throws Exception {
        File directory = new File(context.getFilesDir(), "local-audio");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建本地音乐目录");
        File temporary = File.createTempFile("import-", ".part", directory);
        try {
            String filename = displayName(context, uri);
            MessageDigest digest = MessageDigest.getInstance("MD5");
            long size = copy(context, uri, temporary, digest, cancel);
            if (size == 0) throw new IOException("音乐文件为空");
            String hash = KugouApi.hex(digest.digest());
            synchronized (LocalMusic.class) {
                for (Track existing : read(context)) {
                    if (existing.hash.equals("local:" + hash)) return existing;
                }
            }
            String ext = extension(filename);
            File destination = new File(directory, hash + "." + ext);
            JSONObject data = new JSONObject();
            data.put("hash", "local:" + hash);
            data.put("filename", filename);
            data.put("local_path", destination.getAbsolutePath());
            data.put("local_md5", hash);
            data.put("local_size", size);
            data.put("local_ext", ext);
            int split = filename.indexOf(" - ");
            if (split >= 0) data.put("author_name", filename.substring(0, split));
            readMetadata(temporary, data);
            if (!destination.exists() && !temporary.renameTo(destination)) throw new IOException("无法保存音乐文件");
            Track track = new Track(data);
            if ("我的酷狗云盘".equals(track.artist)) {
                data.put("author_name", "本地音乐");
                track = new Track(data);
            }
            synchronized (LocalMusic.class) {
                ArrayList<Track> tracks = read(context);
                boolean found = false;
                for (Track existing : tracks) {
                    if (existing.hash.equals(track.hash)) { found = true; break; }
                }
                if (!found) { tracks.add(track); save(context, tracks); }
            }
            return track;
        } finally {
            temporary.delete();
        }
    }

    private static long copy(Context context, Uri uri, File temporary, MessageDigest digest, AtomicBoolean cancel) throws Exception {
        InputStream input = null;
        OutputStream output = null;
        try {
            input = context.getContentResolver().openInputStream(uri);
            if (input == null) throw new IOException("无法读取音乐文件");
            output = new FileOutputStream(temporary);
            byte[] buffer = new byte[65536];
            long size = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted() || (cancel != null && cancel.get())) throw new InterruptedIOException("导入已取消");
                output.write(buffer, 0, count);
                digest.update(buffer, 0, count);
                size += count;
            }
            output.flush();
            return size;
        } finally {
            close(output);
            close(input);
        }
    }

    private static void readMetadata(File file, JSONObject data) throws Exception {
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        boolean audio = false;
        try {
            metadata.setDataSource(file.getPath());
            String duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            audio = duration != null;
            String title = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (title != null && !title.trim().isEmpty()) data.put("name", title);
            if (artist != null && !artist.trim().isEmpty()) data.put("author_name", artist);
            data.put("local_duration", duration == null ? 0 : Long.parseLong(duration));
            String bitrate = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            data.put("local_bitrate", bitrate == null ? 0 : Long.parseLong(bitrate));
        } catch (Exception ignored) {
        } finally {
            try { metadata.release(); } catch (Exception ignored) { }
        }
        if (!audio) throw new IOException("无法识别音频，请选择MP3、FLAC、M4A、WAV等音乐文件");
    }

    static String readText(File file) throws IOException {
        InputStream input = null;
        try {
            input = new FileInputStream(file);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] bytes = new byte[8192];
            int count;
            while ((count = input.read(bytes)) != -1) {
                output.write(bytes, 0, count);
                if (output.size() > 32000000) throw new IOException("本地列表过大");
            }
            return new String(output.toByteArray(), "UTF-8");
        } finally { close(input); }
    }

    private static void close(Closeable resource) {
        if (resource != null) {
            try { resource.close(); } catch (IOException ignored) { }
        }
    }

    static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        String ext = dot < 0 ? "mp3" : filename.substring(dot + 1).toLowerCase(Locale.US);
        return ext.matches("[a-z0-9]{1,8}") ? ext : "mp3";
    }
}
