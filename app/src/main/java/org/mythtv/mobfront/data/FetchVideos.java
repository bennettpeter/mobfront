/*
 * Copyright (c) 2016 The Android Open Source Project
 * Copyright (c) 2019-2020 Peter Bennett
 *
 * Incorporates code from "Android TV Samples"
 * <https://github.com/android/tv-samples>
 * Modified by Peter Bennett
 *
 * This file is part of MythTV-leanfront.
 *
 * MythTV-leanfront is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * MythTV-leanfront is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with MythTV-leanfront.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.mythtv.mobfront.data;

import android.app.Activity;
import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

//import org.mythtv.leanfront.model.Settings;
//import org.mythtv.leanfront.ui.AsyncMainLoader;
//import org.mythtv.leanfront.ui.MainFragment;
import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

//import static org.mythtv.leanfront.data.XmlNode.mythApiUrl;

/**
 * FetchVideoService is responsible for fetching the videos from the Internet and inserting the
 * results into a local SQLite database.
 */
public class FetchVideos implements Runnable{

    public interface Listener {
        void onPostExecute(FetchVideos taskRunner);
    }

    private static final String TAG = "mfe";
    private static final String CLASS = "FetchVideos";
    public static final String RECORDEDID = "RecordedId";
    public static final String RECTYPE = "RecType";
    public static final String RECGROUP = "RecGroup";
    public static final String ISPROGRESSBAR = "IsProgressBar";
    public static ReentrantLock fullRunLock = new ReentrantLock();
    private static ExecutorService executor = Executors.newCachedThreadPool();
    private Listener listener;
    private int recType;
    private String recordedId;
    private String recGroup;
    private Context  context;
    public boolean success = false;

    /**
     * Creates an IntentService with a default name for the worker thread.
     */
    public FetchVideos(Context context, int recType, String recordedId, String recGroup) {
        this.context = context;
        this.recType = recType;
        this.recordedId = recordedId;
        this.recGroup = recGroup;
    }

    public void execute( @Nullable Listener listener) {
        this.listener = listener;
        executor.submit(this);
    }

    @Override
    public void run() {
//        boolean isProgressBar = workIntent.getBooleanExtra(ISPROGRESSBAR, false);
        success = true;
        if (recType == -1 || recordedId == null) {
            // Do not allow two full downloads at the same time
            if (!fullRunLock.tryLock())
                return;
        }
        try {
            if (recType != VideoContract.VideoEntry.RECTYPE_RECORDING)
                recGroup = null;
            VideoDbBuilder builder = new VideoDbBuilder(context);

            // recordings are 0, videos are 1, channels are 2
            int [] start = {0,0,0};
            int [] maxAvailable = {1000000,1000000,1000000};
            int maxLoad = Settings.getInt("pref_max_vids");
            int actual = 0;
            int pagesize = 5000;

            boolean firstLoop  = true;
            while (start[0] < maxAvailable[0] || start[1] < maxAvailable[1]) {
                if (actual >= maxLoad)
                    break;
                if (maxLoad - actual < pagesize)
                    pagesize = maxLoad - actual;
                String[] urls = new String[3];
                if (recType == -1) {
                    // MythTV recording list URL: http://andromeda:6544/Dvr/GetRecordedList
                    // MythTV video list URL: http://andromeda:6544/Video/GetVideoList
                    urls[0] = XmlNode.mythApiUrl(null,
                            "/Dvr/GetRecordedList?IncCast=false&Descending=true&Count=" + pagesize + "&StartIndex=" + start[0]);
                    urls[1] = XmlNode.mythApiUrl(null,
                            "/Video/GetVideoList?Descending=true&Count=" + pagesize + "&StartIndex=" + start[1]);
                    if (firstLoop)
                        urls[2] = XmlNode.mythApiUrl(null, "/Channel/GetChannelInfoList?OnlyVisible=true");
                } else if (recType == VideoContract.VideoEntry.RECTYPE_RECORDING) {
                    maxAvailable[1]  = 0;  // Prevent looping for videos
                    if (recordedId != null)
                        urls[0] = XmlNode.mythApiUrl(null, "/Dvr/GetRecorded?RecordedId=" + recordedId);
                    else if (recGroup != null) {
                        urls[0] = XmlNode.mythApiUrl(null,
                                "/Dvr/GetRecordedList?IncCast=false&Descending=true&Count=" + pagesize + "&StartIndex=" + start[0]
                                        + "RecGroup=" + URLEncoder.encode(recGroup, "UTF-8"));
                        if ("LiveTV".equals(recGroup) && start[0] ==  0)
                            urls[2] = XmlNode.mythApiUrl(null, "/Channel/GetChannelInfoList?OnlyVisible=true");
                    } else
                        urls[0] = XmlNode.mythApiUrl(null,
                                "/Dvr/GetRecordedList?IncCast=false&Descending=true&Count=" + pagesize + "&StartIndex=" + start[0]);
                } else if (recType == VideoContract.VideoEntry.RECTYPE_VIDEO) {
                    maxAvailable[0]  = 0;  // Prevent looping for recordings
                    if (recordedId != null)
                        urls[1] = XmlNode.mythApiUrl(null, "/Video/GetVideo?Id=" + recordedId);
                    else
                        urls[1] = XmlNode.mythApiUrl(null,
                                "/Video/GetVideoList?Descending=true&Count=" + pagesize + "&StartIndex=" + start[1]);
                }
                List<ContentValues> contentValuesList = new ArrayList<>();
                for (int i = 0; i < urls.length; i++) {
                    String url = urls[i];
                    if (url != null && start[i] < maxAvailable[i]) {
                        // This call expects recordings to be 0, videos to be 1, channels to be 2
                        maxAvailable[i] = builder.fetch(url, i, contentValuesList);
                        start[i] += pagesize;
                    }
                }
                if (firstLoop) {
                    try {
                        VideoDbHelper dbh = VideoDbHelper.getInstance(context);
                        SQLiteDatabase db = dbh.getWritableDatabase();
                        if (db == null)
                            return;
                        if (recType == -1)
                            db.execSQL("DELETE FROM " + VideoContract.VideoEntry.TABLE_NAME); //delete all rows in a table
                        else {
                            if (recordedId == null && recGroup == null)
                                db.execSQL("DELETE FROM " + VideoContract.VideoEntry.TABLE_NAME
                                        + " WHERE RECTYPE = '" + recType + "'");
                            else if (recordedId != null)
                                db.execSQL("DELETE FROM " + VideoContract.VideoEntry.TABLE_NAME
                                        + " WHERE RECORDEDID = '" + recordedId
                                        + "' AND RECTYPE = '" + recType + "'");
                            else if (recGroup != null) {
                                db.execSQL("DELETE FROM " + VideoContract.VideoEntry.TABLE_NAME
                                        + " WHERE RECGROUP = '" + recGroup.replace("'", "''")
                                        + "' AND RECTYPE = '" + recType + "'");
                                if ("LiveTV".equals(recGroup))
                                    db.execSQL("DELETE FROM " + VideoContract.VideoEntry.TABLE_NAME
                                            + " WHERE RECTYPE = '" + VideoContract.VideoEntry.RECTYPE_CHANNEL + "'");
                            }
                        }
                        VideoDbHelper.releaseDatabase();
                    } finally {
//                        AsyncMainLoader.lock.unlock();
                    }
                }
                VideoDbHelper dbh = VideoDbHelper.getInstance(context);
                SQLiteDatabase db = dbh.getWritableDatabase();
                db.beginTransaction();
                for (ContentValues row : contentValuesList) {
                    long id = db.insert(VideoContract.VideoEntry.TABLE_NAME,null,row);
                    if (id == -1) {
                        success = false;
                        break;
                    }
                }
                if (success)
                    db.setTransactionSuccessful();
                db.endTransaction();
                if (!success)
                    break;
                actual += contentValuesList.size();
                Log.i(TAG, CLASS + " Number of downloaded records: " + actual);

                if (recordedId != null)
                    break;
                firstLoop = false;
            }
        } catch (IOException | XmlPullParserException e) {
            success = false;
//            MainFragment.mFetchTime = 0;
            Log.e(TAG, CLASS + " Error occurred in downloading videos", e);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            if (recType == -1 || recordedId == null)
                fullRunLock.unlock();
            if (listener != null)
                listener.onPostExecute(this);
        }
    }
}
