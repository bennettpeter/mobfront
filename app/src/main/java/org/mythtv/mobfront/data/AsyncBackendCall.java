package org.mythtv.mobfront.data;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.Log;

import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncBackendCall implements Runnable {

    public interface OnBackendCallListener {
        void onPostExecute(AsyncBackendCall taskRunner);
    }

    public ArrayList<Video> videos = new ArrayList<>();
    // Class of params and response are dependent on the call
    public Object params;
    public Object response;
    private ArrayList<XmlNode> xmlResults = new ArrayList<>();
    private Integer[] inTasks;
    private int[] tasks;
    private Activity activity;
    private OnBackendCallListener listener;
//    private View view;
    private final static ExecutorService executor = Executors.newCachedThreadPool();
    // Whether Last Play works
    static boolean lastPlay = true;
    final String TAG = "mfe";
    // provide activity or view. Whichever is not null will be used
    public AsyncBackendCall(@Nullable Activity activity, @Nullable OnBackendCallListener listener) {
//                            @Nullable View view) {
        this.activity = activity;
        this.listener = listener;
//        this.view = view;
    }


    public int[] getTasks() {
        return tasks;
    }

    public ArrayList<XmlNode> getXmlResults() {
        return xmlResults;
    }

    public XmlNode getXmlResult() {
        if (xmlResults.size() > 0)
            return xmlResults.get(0);
        else
            return null;
    }

    public void execute(Integer... tasks) {
        Log.i("mfe","AsyncBackendCall.execute:" + tasks[0]);
        this.inTasks = tasks;
        executor.submit(this);
    }


    @Override
    public void run() {
        if (!XmlNode.isSetupDone())
            return;
        try {
            if (XmlNode.getIpAndPort(null) == null)
                return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            return;
        }
        try {
            runTasks();
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            if (listener != null) {
                if (activity != null)
                    activity.runOnUiThread(() -> listener.onPostExecute(this));
//                else if (view != null)
//                    view.post(() -> listener.onPostExecute(this));
                else
                    listener.onPostExecute(this);
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    private void runTasks() {
       tasks = new int[inTasks.length];
//        BackendCache bCache = BackendCache.getInstance();
//        Context context = MyApplication.getAppContext();
//        HttpURLConnection urlConnection = null;
        int videoIndex = 0;
        int taskIndex = -1;
        Video video = null;

        for (; ; ) {
            boolean doGetOnly = false;
            // If there is a rowAdapter, take each video in the adapter and run
            // all tasks on it.
            taskIndex++;
            if (taskIndex >= tasks.length) {
                if (videos.size() == 0)
                    break;
                taskIndex = 0;
                videoIndex++;
            }
            if (videos.size() > 0) {
                if (videoIndex >= videos.size())
                    break;
                video = videos.get(videoIndex);
            }
            boolean isRecording = false;
            if (video != null)
                isRecording = (video.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING);

//            if (rowAdapter != null) {
                // in row adapter only process videos and series.
//                if ( ! (video.type == MainFragment.TYPE_VIDEO
//                        || video.type == MainFragment.TYPE_EPISODE))
//                    continue;

            int task = inTasks[taskIndex];
            tasks[taskIndex] = task;
            XmlNode xmlResult = null;
            switch (task) {
                case Action.GET_BOOKMARK:
                    //Response is long[2], bookmark and posbookmark
                    try {
                        response = fetchLastPlayPos(video);
                    } catch (IOException | XmlPullParserException e) {
                        response = new long[]{0, 0};
                        e.printStackTrace();
                    }
                    break;
                case Action.REMOVE_BOOKMARK:
                    params = new long[] {0,0};
                case Action.SET_BOOKMARK:
                    //Params are is long[2], bookmark and posbookmark
                    //Result in xmlResult
                    try {
                        xmlResult = updateLastPlayPos(video, (long[]) params);
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    break;
                case Action.GET_STREAM_INFO:
                    try {
                        String urlString = XmlNode.mythApiUrl(video.hostname,
                                "/Video/GetStreamInfo?StorageGroup="
                                    + video.storageGroup
                                    + "&FileName="
                                    + URLEncoder.encode(video.filename, "UTF-8"));
                        xmlResult = XmlNode.fetch(urlString, null);
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                    break;
            }
            xmlResults.add(xmlResult);
        }

    }

    // method: GetSavedBookmark or GetLastPlayPos
    // Return array has bookmark and posbookmark or lastplaypos values
    private long[] fetchLastPlayPos(Video video) throws XmlPullParserException, IOException {
        boolean isRecording = (video.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING);
        String urlString;
        XmlNode bkmrkData = null;
        long[] retValue = {0, -1};
        String method;
        if (lastPlay)
            method = "GetLastPlayPos";
        else
            method = "GetSavedBookmark";
        if (isRecording) {
            urlString = XmlNode.mythApiUrl(video.hostname,
                    "/Dvr/" + method + "?OffsetType=duration&RecordedId="
                            + video.recordedid);
            bkmrkData = XmlNode.safeFetch(urlString, null);
            try {
                retValue[0] = Long.parseLong(bkmrkData.getString());
            } catch (NumberFormatException e) {
                Exception e2 = bkmrkData.getException();
                if (lastPlay && e2 != null && e2 instanceof FileNotFoundException) {
                    lastPlay = false;
                    Log.w(TAG,"AsyncBakendCall.fetchLastPlayPos failed will use bookmarks instead");
                    return fetchLastPlayPos(video);
                }
                e.printStackTrace();
                retValue[0] = -1;
            }
            // sanity check bookmark - between 0 and 24 hrs.
            // note -1 means a bookmark but no seek table
            // older version of service returns garbage value when there is
            // no seek table.
            if (retValue[0] > 24 * 60 * 60 * 1000 || retValue[0] < 0)
                retValue[0] = -1;
        }
        if (retValue[0] == -1 || !isRecording) {
            // look for a position bookmark (for recording with no seek table)
            if (isRecording)
                urlString = XmlNode.mythApiUrl(video.hostname,
                        "/Dvr/" + method + "?OffsetType=frame&RecordedId="
                                + video.recordedid);
            else
                urlString = XmlNode.mythApiUrl(video.hostname,
                        "/Video/" + method + "?Id="
                                + video.recordedid);
            bkmrkData = XmlNode.safeFetch(urlString, null);
            try {
                retValue[1] = Long.parseLong(bkmrkData.getString());
            } catch (NumberFormatException e) {
                e.printStackTrace();
                retValue[1] = -1;
            }
        }
        return retValue;
    }

    // method: SetSavedBookmark or SetLastPlayPos
    // array has bookmark and posbookmark
    // Return object contains true if successful
    private XmlNode updateLastPlayPos(Video video, long[] params)
            throws XmlPullParserException, IOException {
        long mark = params[0];
        long pos = params[1];
        boolean isRecording = (video.rectype == VideoContract.VideoEntry.RECTYPE_RECORDING);
        String urlString;
        XmlNode xmlResult = null;
        boolean found = false;
        String method;
        if (lastPlay)
            method = "SetLastPlayPos";
        else
            method = "SetSavedBookmark";

        // store a mythtv bookmark
        if (isRecording) {
            urlString = XmlNode.mythApiUrl(video.hostname,
                    "/Dvr/"+method+"?OffsetType=duration&RecordedId="
                            + video.recordedid + "&Offset=" + mark);
            xmlResult = XmlNode.safeFetch(urlString, "POST");
            String result = xmlResult.getString();
            if ("true".equals(result))
                found = true;
        }
        if (!found && pos >= 0) {
            // store a mythtv position bookmark (in case there is no seek table)
            if (isRecording)
                urlString = XmlNode.mythApiUrl(video.hostname,
                        "/Dvr/"+method+"?RecordedId="
                                + video.recordedid + "&Offset=" + pos);
            else
                urlString = XmlNode.mythApiUrl(video.hostname,
                        "/Video/"+method+"?Id="
                                + video.recordedid + "&Offset=" + pos);
            xmlResult = XmlNode.safeFetch(urlString, "POST");
        }
        return xmlResult;
    }

}
