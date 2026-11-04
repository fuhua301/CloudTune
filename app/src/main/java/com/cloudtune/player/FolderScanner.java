package com.cloudtune.player;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Persisted SAF folder grants; incremental recursive import without broad storage permission. */
final class FolderScanner {
    interface Progress { void update(int checked, int imported); }
    static final class Result {
        int checked, imported, unchanged, failed;
        boolean cancelled;
    }
    static ArrayList<Uri> folders(Context context) {
        ArrayList<Uri> out = new ArrayList<Uri>();
        try {
            JSONArray items = new JSONArray(context.getSharedPreferences("music_folders", 0).getString("uris", "[]"));
            for (int i = 0; i < items.length(); i++) out.add(Uri.parse(items.getString(i)));
        } catch (Exception ignored) { }
        return out;
    }
    static synchronized void add(Context context, Uri uri) throws Exception {
        ArrayList<Uri> all = folders(context);
        if (!all.contains(uri)) all.add(uri);
        saveFolders(context, all);
    }
    static synchronized void remove(Context context, Uri uri) throws Exception {
        ArrayList<Uri> all = folders(context);
        all.remove(uri);
        saveFolders(context, all);
        try { context.getContentResolver().releasePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
    }
    private static void saveFolders(Context context, ArrayList<Uri> all) throws Exception {
        JSONArray array = new JSONArray();
        for (Uri uri : all) array.put(uri.toString());
        if (!context.getSharedPreferences("music_folders", 0).edit().putString("uris", array.toString()).commit()) throw new IOException("无法保存音乐文件夹");
    }
    static boolean audio(String name, String mime) {
        if (mime != null && mime.startsWith("audio/")) return true;
        if (name == null) return false;
        return name.toLowerCase(Locale.US).matches(".*\\.(mp3|flac|wav|m4a|aac|ogg|opus|wma|ape|aiff|aif|amr|mka|mp2|alac)$");
    }
    private static JSONObject readCache(Context context) {
        try {
            return new JSONObject(LocalMusic.readText(new File(context.getFilesDir(), "folder-scan.json")));
        } catch (Exception ignored) { return new JSONObject(); }
    }
    private static void saveCache(Context context, JSONObject cache) throws Exception {
        File temporary = new File(context.getFilesDir(), "folder-scan.tmp");
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(temporary);
            output.write(cache.toString().getBytes("UTF-8"));
            output.flush();
            output.getFD().sync();
        } finally { if (output != null) try { output.close(); } catch (IOException ignored) { } }
        if (!temporary.renameTo(new File(context.getFilesDir(), "folder-scan.json"))) throw new IOException("无法保存扫描记录");
    }
    static Result scan(Context context, AtomicBoolean cancel, Progress progress) {
        Result result = new Result();
        JSONObject previous = readCache(context), next = new JSONObject();
        HashSet<String> imported = new HashSet<String>();
        for (Track track : LocalMusic.read(context)) imported.add(track.hash);
        for (Uri tree : folders(context)) {
            ArrayDeque<String> pending = new ArrayDeque<String>();
            HashSet<String> visited = new HashSet<String>();
            try { pending.add(DocumentsContract.getTreeDocumentId(tree)); }
            catch (Exception ignored) { result.failed++; continue; }
            while (!pending.isEmpty()) {
                if (cancel.get() || Thread.currentThread().isInterrupted()) { result.cancelled = true; break; }
                String id = pending.removeFirst();
                if (!visited.add(id)) continue;
                Cursor cursor = null;
                try {
                    String[] columns = new String[5];
                    columns[0] = DocumentsContract.Document.COLUMN_DOCUMENT_ID;
                    columns[1] = DocumentsContract.Document.COLUMN_DISPLAY_NAME;
                    columns[2] = DocumentsContract.Document.COLUMN_MIME_TYPE;
                    columns[3] = DocumentsContract.Document.COLUMN_SIZE;
                    columns[4] = DocumentsContract.Document.COLUMN_LAST_MODIFIED;
                    Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
                    cursor = context.getContentResolver().query(children, columns, null, null, null);
                    if (cursor == null) { result.failed++; continue; }
                    while (cursor.moveToNext()) {
                        if (cancel.get() || Thread.currentThread().isInterrupted()) { result.cancelled = true; break; }
                        String childId = cursor.getString(0), name = cursor.getString(1), mime = cursor.getString(2);
                        if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) { pending.add(childId); continue; }
                        if (!audio(name, mime)) continue;
                        result.checked++;
                        try {
                            Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, childId);
                            String key = document.toString();
                            long size = cursor.isNull(3) ? -1 : cursor.getLong(3);
                            long modified = cursor.isNull(4) ? 0 : cursor.getLong(4);
                            String stamp = modified + ":" + size + ":" + name;
                            JSONObject cached = previous.optJSONObject(key);
                            if (modified > 0 && size >= 0 && cached != null && stamp.equals(cached.optString("stamp")) && imported.contains(cached.optString("hash"))) {
                                next.put(key, cached);
                                result.unchanged++;
                            } else {
                                Track track = LocalMusic.importUri(context, document, cancel);
                                if (imported.add(track.hash)) result.imported++; else result.unchanged++;
                                next.put(key, new JSONObject().put("stamp", stamp).put("hash", track.hash));
                            }
                        } catch (Exception ignored) {
                            if (cancel.get() || Thread.currentThread().isInterrupted()) { result.cancelled = true; break; }
                            result.failed++;
                        }
                        progress.update(result.checked, result.imported);
                    }
                } catch (Exception ignored) { result.failed++; }
                finally { if (cursor != null) try { cursor.close(); } catch (Exception ignored) { } }
                if (result.cancelled) break;
            }
            if (result.cancelled) break;
        }
        // A cancelled/partial scan must not discard signatures for untouched folders.
        if (!result.cancelled && result.failed == 0) try { saveCache(context, next); } catch (Exception ignored) { result.failed++; }
        return result;
    }
}
