package com.github.emilbayes.downloadmanager;

import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class DownloadManagerPlugin extends CordovaPlugin {

    public final static int STATUS_PENDING = 1 << 0;
    public final static int STATUS_RUNNING = 1 << 1;
    public final static int STATUS_PAUSED = 1 << 2;
    public final static int STATUS_SUCCESSFUL = 1 << 3;
    public final static int STATUS_FAILED = 1 << 4;

    /**
     * This download hasn't stated yet
     */
    public static final int DOWNLOADS_STATUS_PENDING = 190;
    /**
     * This download has started
     */
    public static final int DOWNLOADS_STATUS_RUNNING = 192;
    /**
     * This download has been paused by the owning app.
     */
    public static final int DOWNLOADS_STATUS_PAUSED_BY_APP = 193;
    /**
     * This download encountered some network error and is waiting before retrying
     * the request.
     */
    public static final int DOWNLOADS_STATUS_WAITING_TO_RETRY = 194;
    /**
     * This download is waiting for network connectivity to proceed.
     */
    public static final int DOWNLOADS_STATUS_WAITING_FOR_NETWORK = 195;
    /**
     * This download exceeded a size limit for mobile networks and is waiting for a
     * Wi-Fi
     * connection to proceed.
     */
    public static final int DOWNLOADS_STATUS_QUEUED_FOR_WIFI = 196;
    public static final int DOWNLOADS_STATUS_SUCCESS = 200;

    public static final String DOWNLOADS_COLUMN_MIME_TYPE = "mimetype";
    public static final String DOWNLOADS_COLUMN_TOTAL_BYTES = "total_bytes";
    public static final String DOWNLOADS_COLUMN_CURRENT_BYTES = "current_bytes";
    public static final String DOWNLOADS_COLUMN_LAST_MODIFICATION = "lastmod";

    private final Handler handler = new Handler();
    DownloadManager downloadManager;
    private ContentResolver mResolver;
    private Map<Integer, Integer> indexMap = new HashMap<>();
    private long mLastRxBytes = 0;
    private long mLastTxBytes = 0;
    private long mLastTime = 0;
    private long mTotalBytesDownloaded = 0;
    private Map<Integer, Integer> rangeMap = new HashMap();
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            populateNetworkRange();
            handler.postDelayed(this, 1000);
        }
    };

    private static PluginResult OK(Map obj) throws JSONException {
        return createPluginResult(obj, PluginResult.Status.OK);
    }

    private static PluginResult ERROR(Map obj) throws JSONException {
        return createPluginResult(obj, PluginResult.Status.ERROR);
    }

    private static PluginResult createPluginResult(Map map, PluginResult.Status status) throws JSONException {
        JSONObject json = new JSONObject(map);
        PluginResult result = new PluginResult(status, json);
        return result;
    }

    private static JSONArray JSONFromCursor(Cursor cursor) throws JSONException {
        JSONArray result = new JSONArray();

        cursor.moveToFirst();
        do {
            JSONObject rowObject = new JSONObject();
            if (cursor.getColumnIndex(DownloadManager.COLUMN_ID) != -1) {
                rowObject.put("id", cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_ID)));
            }
            if (cursor.getColumnIndex(DownloadManager.COLUMN_TITLE) != -1) {
                rowObject.put("title", cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)));
            }
            if (cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION) != -1) {
                rowObject.put("description",
                        cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)));
            }
            if (cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE) != -1) {
                rowObject.put("mediaType", cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)));
            }
            if (cursor.getColumnIndex(DOWNLOADS_COLUMN_MIME_TYPE) != -1) {
                rowObject.put("mediaType", cursor.getString(cursor.getColumnIndex(DOWNLOADS_COLUMN_MIME_TYPE)));
            }
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N
                    && cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME) != -1) {
                rowObject.put("localFilename",
                        cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)));
            }
            if (cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI) != -1) {
                rowObject.put("localUri", cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)));
            }
            if (cursor.getColumnIndex(DownloadManager.COLUMN_MEDIAPROVIDER_URI) != -1) {
                rowObject.put("mediaproviderUri",
                        cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_MEDIAPROVIDER_URI)));
            }
            if (cursor.getColumnIndex(DownloadManager.COLUMN_URI) != -1) {
                rowObject.put("uri", cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI)));
            }
            if (cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP) != -1) {
                rowObject.put("lastModifiedTimestamp",
                        cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)));
            }
            if (cursor.getColumnIndex(DOWNLOADS_COLUMN_LAST_MODIFICATION) != -1) {
                rowObject.put("lastModifiedTimestamp",
                        cursor.getLong(cursor.getColumnIndex(DOWNLOADS_COLUMN_LAST_MODIFICATION)));
            }
            if (cursor.getColumnIndex(DownloadManager.COLUMN_STATUS) != -1) {
                if (cursor.getColumnIndex(DOWNLOADS_COLUMN_LAST_MODIFICATION) != -1) {
                    rowObject.put("status",
                            translateStatus(cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))));
                } else {
                    rowObject.put("status", cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)));
                }
            }
            if (cursor.getColumnIndex(DownloadManager.COLUMN_REASON) != -1) {
                rowObject.put("reason", cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON)));
            }
            if (cursor.getColumnIndex(DOWNLOADS_COLUMN_CURRENT_BYTES) != -1) {
                rowObject.put("bytesDownloadedSoFar",
                        cursor.getLong(cursor.getColumnIndex(DOWNLOADS_COLUMN_CURRENT_BYTES)));
            }
            if (cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR) != -1) {
                rowObject.put("bytesDownloadedSoFar",
                        cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));
            }
            if (cursor.getColumnIndex(DOWNLOADS_COLUMN_TOTAL_BYTES) != -1) {
                rowObject.put("totalSizeBytes",
                        cursor.getLong(cursor.getColumnIndex(DOWNLOADS_COLUMN_TOTAL_BYTES)));
            }
            if (cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES) != -1) {
                rowObject.put("totalSizeBytes",
                        cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)));
            }
            result.put(rowObject);
        } while (cursor.moveToNext());

        return result;
    }

    private static int translateStatus(int status) {
        switch (status) {
            case DOWNLOADS_STATUS_PENDING:
                return STATUS_PENDING;

            case DOWNLOADS_STATUS_RUNNING:
                return STATUS_RUNNING;

            case DOWNLOADS_STATUS_PAUSED_BY_APP:
            case DOWNLOADS_STATUS_WAITING_TO_RETRY:
            case DOWNLOADS_STATUS_WAITING_FOR_NETWORK:
            case DOWNLOADS_STATUS_QUEUED_FOR_WIFI:
                return STATUS_PAUSED;

            case DOWNLOADS_STATUS_SUCCESS:
                return STATUS_SUCCESSFUL;

            default:
                return STATUS_FAILED;
        }
    }

    private static long[] longsFromJSON(JSONArray arr) throws JSONException {
        if (arr == null)
            return null;

        long[] longs = new long[arr.length()];

        for (int i = 0; i < arr.length(); i++) {
            String str = arr.getString(i);
            longs[i] = Long.valueOf(str);
        }

        return longs;
    }

    @Override
    public void initialize(final CordovaInterface cordova, final CordovaWebView webView) {
        super.initialize(cordova, webView);

        downloadManager = (DownloadManager) cordova.getActivity().getApplication().getApplicationContext()
                .getSystemService(Context.DOWNLOAD_SERVICE);
        mResolver = cordova.getActivity().getApplication().getApplicationContext().getContentResolver();
        mLastRxBytes = TrafficStats.getTotalRxBytes();
        mLastTxBytes = TrafficStats.getTotalTxBytes();
        mLastTime = System.currentTimeMillis();
        this.initSpeedLogger();
        startFetchingDownloadSpeed();
    }

    private void startFetchingDownloadSpeed() {
        handler.postDelayed(runnable, 2000);
    }

    private void populateNetworkRange() {
        try {
            double speed = getNetworkSpeed();
            if (speed > 0) {
                int key = speed < 1024 ? getFirstBucketKey(speed) : getSecondBucketKey(speed);
                int range = indexMap.get(key);
                if (rangeMap.containsKey(range)) {
                    Integer rangeKey = rangeMap.get(range);
                    rangeMap.put(range, rangeKey + 1);
                } else {
                    rangeMap.put(range, 1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getFirstBucketKey(double speed) {

        int result = (int) (Math.log(speed) / Math.log(2) - 3);
        return result < 1 ? 1 : result;
    }

    public int getSecondBucketKey(double speed) {
        int result = (int) (speed / 512) + 4;
        return result >= 16 ? -1 : result;
    }

    public void initSpeedLogger() {
        indexMap.put(1, 32);
        indexMap.put(2, 64);
        indexMap.put(3, 128);
        indexMap.put(4, 256);
        indexMap.put(5, 512);
        indexMap.put(6, 1024);
        indexMap.put(7, 1536);
        indexMap.put(8, 2048);
        indexMap.put(9, 2560);
        indexMap.put(10, 3072);
        indexMap.put(11, 3584);
        indexMap.put(-1, 4096);
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("enqueue"))
            return enqueue(args.getJSONObject(0), callbackContext);
        if (action.equals("query"))
            return query(args.getJSONObject(0), callbackContext);
        if (action.equals("remove"))
            return remove(args, callbackContext);
        if (action.equals("addCompletedDownload"))
            return addCompletedDownload(args.getJSONObject(0), callbackContext);
        if (action.equals("fetchSpeedLog"))
            return fetchSpeedLog(callbackContext);

        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
        return false;
    }

    protected boolean enqueue(JSONObject obj, CallbackContext callbackContext) throws JSONException {
        DownloadManager.Request req = deserialiseRequest(obj);

        long id = downloadManager.enqueue(req);

        callbackContext.success(Long.toString(id));

        return true;
    }

    protected boolean query(JSONObject obj, CallbackContext callbackContext) throws JSONException {
        DownloadManager.Query query = deserialiseQuery(obj);

        Cursor downloads;
        try {
            downloads = downloadManager.query(query);
        } catch (SQLiteException e) {
            e.printStackTrace();

            // SELECT _id, _id, mediaprovider_uri, destination, title, description, uri,
            // status, hint, media_type, total_size, last_modified_timestamp, bytes_so_far,
            // allow_write, local_uri, reason
            // FROM downloads WHERE ((uid=10175 OR otheruid=10175)) AND ((_id = ? ) AND
            // deleted != '1') ORDER BY lastmod DESC

            Uri uri = Uri.parse("content://downloads/my_downloads");
            String[] projection = new String[] {
                    DownloadManager.COLUMN_ID,
                    DownloadManager.COLUMN_MEDIAPROVIDER_URI,
                    DownloadManager.COLUMN_TITLE,
                    DownloadManager.COLUMN_DESCRIPTION,
                    DownloadManager.COLUMN_URI,
                    DownloadManager.COLUMN_STATUS,
                    DOWNLOADS_COLUMN_MIME_TYPE,
                    DOWNLOADS_COLUMN_TOTAL_BYTES,
                    DOWNLOADS_COLUMN_LAST_MODIFICATION,
                    DOWNLOADS_COLUMN_CURRENT_BYTES
            };
            String selection = "(_id = ? ) AND deleted != '1'";
            String[] selectionArgs = new String[] {
                    obj.optJSONArray("ids").getString(0)
            };
            String orderBy = "lastmod DESC";

            downloads = mResolver.query(uri, projection, selection, selectionArgs, orderBy);
        }

        if (downloads.getCount() > 0) {
            callbackContext.success(JSONFromCursor(downloads));
        }

        if (downloads != null) {
            downloads.close();
        }

        return true;
    }

    protected boolean remove(JSONArray arr, CallbackContext callbackContext) throws JSONException {
        long[] ids = longsFromJSON(arr);

        // Store download info before removal for the event
        JSONArray downloadInfoArray = new JSONArray();

        // Query for download information before removing
        for (long id : ids) {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(id);

            try {
                Cursor cursor = downloadManager.query(query);
                if (cursor != null && cursor.moveToFirst()) {
                    JSONObject downloadInfo = new JSONObject();
                    downloadInfo.put("downloadId", id);
                    if (cursor.getColumnIndex(DownloadManager.COLUMN_TITLE) != -1) {
                        downloadInfo.put("title",
                                cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)));
                    }
                    if (cursor.getColumnIndex(DownloadManager.COLUMN_STATUS) != -1) {
                        downloadInfo.put("status", cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)));
                    }
                    downloadInfoArray.put(downloadInfo);
                }
                if (cursor != null) {
                    cursor.close();
                }
            } catch (Exception e) {
                Log.e("DownloadManagerPlugin", "Error querying download info: " + e.getMessage());
            }
        }

        int removed = downloadManager.remove(ids);
        callbackContext.success(removed);

        // Fire JS event with download information
        try {
            JSONObject event = new JSONObject();
            event.put("status", "cancelled");
            event.put("removedCount", removed);
            event.put("downloads", downloadInfoArray);
            fireEvent("downloadCancelled", event);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return true;
    }

  
    private void fireEvent(final String eventName, final JSONObject data) {
    if (cordova != null && webView != null) {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                // Use proper event dispatching
                final String js = String.format(
                    "if (typeof cordova !== 'undefined' && cordova.fireWindowEvent) {" +
                    "  cordova.fireWindowEvent('%s', %s);" +
                    "} else {" +
                    "  window.dispatchEvent(new CustomEvent('%s', { detail: %s }));" +
                    "}", 
                    eventName, data.toString(), eventName, data.toString()
                );
                webView.loadUrl("javascript:" + js);
            } catch (Exception e) {
                Log.e("DownloadManagerPlugin", "Error firing event: " + e.getMessage());
            }
        });
    } else {
        Log.e("DownloadManagerPlugin", "webView or cordova is null, cannot fire event");
    }
}
    protected boolean addCompletedDownload(JSONObject obj, CallbackContext callbackContext) throws JSONException {

        long id = downloadManager.addCompletedDownload(obj.optString("title"), obj.optString("description"),
                obj.optBoolean("isMediaScannerScannable", false), obj.optString("mimeType"), obj.optString("path"),
                obj.optLong("length"), obj.optBoolean("showNotification", true));
        // NOTE: If showNotification is false, you need
        // <uses-permission android: name =
        // "android.permission.DOWNLOAD_WITHOUT_NOTIFICATION" />

        callbackContext.success(Long.toString(id));

        return true;
    }

    protected DownloadManager.Request deserialiseRequest(JSONObject obj) throws JSONException {
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(obj.getString("uri")));

        req.setTitle(obj.optString("title"));
        req.setDescription(obj.optString("description"));
        req.setMimeType(obj.optString("mimeType", null));

        if (obj.has("destinationInExternalFilesDir")) {
            Context context = cordova.getActivity().getApplication().getApplicationContext();

            JSONObject params = obj.getJSONObject("destinationInExternalFilesDir");

            req.setDestinationInExternalFilesDir(context, params.optString("dirType"), params.optString("subPath"));
        } else if (obj.has("destinationInExternalPublicDir")) {
            JSONObject params = obj.getJSONObject("destinationInExternalPublicDir");

            req.setDestinationInExternalPublicDir(params.optString("dirType"), params.optString("subPath"));
        } else if (obj.has("destinationUri"))
            req.setDestinationUri(Uri.parse(obj.getString("destinationUri")));

        req.setVisibleInDownloadsUi(obj.optBoolean("visibleInDownloadsUi", true));
        req.setNotificationVisibility(obj.optInt("notificationVisibility"));

        if (obj.has("headers")) {
            JSONArray arrHeaders = obj.optJSONArray("headers");
            for (int i = 0; i < arrHeaders.length(); i++) {
                JSONObject headerObj = arrHeaders.getJSONObject(i);
                req.addRequestHeader(headerObj.optString("header"), headerObj.optString("value"));
            }
        }

        return req;
    }

    protected DownloadManager.Query deserialiseQuery(JSONObject obj) throws JSONException {
        DownloadManager.Query query = new DownloadManager.Query();

        long[] ids = longsFromJSON(obj.optJSONArray("ids"));
        query.setFilterById(ids);

        if (obj.has("status")) {
            query.setFilterByStatus(obj.getInt("status"));
        }

        return query;
    }

    protected boolean fetchSpeedLog(CallbackContext callbackContext) {
        try {
            JSONObject speedLog = new JSONObject();

            long totalKBdownloaded = mTotalBytesDownloaded / 1024;

            JSONObject distribution = new JSONObject();

            for (Map.Entry<Integer, Integer> entry : rangeMap.entrySet()) {
                distribution.put(entry.getKey().toString(), entry.getValue());
            }

            speedLog.put("totalKBdownloaded", totalKBdownloaded);
            speedLog.put("distributionInKBPS", distribution);

            callbackContext.success(speedLog);
            mTotalBytesDownloaded = 0;
            rangeMap.clear();
            return true;
        } catch (Exception e) {
            callbackContext.error(e.toString());
            mTotalBytesDownloaded = 0;
            rangeMap.clear();
            return false;
        }
    }

    private double getNetworkSpeed() {
        try {
            long currentRxBytes = TrafficStats.getTotalRxBytes();
            long currentTxBytes = TrafficStats.getTotalTxBytes();
            long usedRxBytes = currentRxBytes - mLastRxBytes;
            long usedTxBytes = currentTxBytes - mLastTxBytes;
            long currentTime = System.currentTimeMillis();
            long usedTime = currentTime - mLastTime;

            mLastRxBytes = currentRxBytes;
            mLastTxBytes = currentTxBytes;
            mLastTime = currentTime;

            long totalBytes = usedRxBytes + usedTxBytes;
            double totalSpeed = 0;
            if (usedTime > 0) {
                totalSpeed = (double) totalBytes / usedTime;
            }
            mTotalBytesDownloaded += totalBytes;
            return totalSpeed;

        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        if (handler != null) {
            handler.removeMessages(0);
        }

    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        if (handler != null && runnable != null) {
            handler.postDelayed(runnable, 1000);
        }

    }

}
