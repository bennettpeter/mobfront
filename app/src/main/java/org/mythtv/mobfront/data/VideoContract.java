package org.mythtv.mobfront.data;

import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.net.Uri;
import android.provider.BaseColumns;

/**
 * VideoContract represents the contract for storing videos in the SQLite database.
 */
public final class VideoContract {
    // The name for the entire content provider.
    public static final String CONTENT_AUTHORITY = "org.mythtv.leanfront";
    // Base of all URIs that will be used to contact the content provider.
    public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);
    // The content paths.
    public static final String PATH_VIDEO = "video";

    public static final class VideoEntry implements BaseColumns {
        public static final Uri CONTENT_URI =
                BASE_CONTENT_URI.buildUpon().appendPath(PATH_VIDEO).build();
        public static final String CONTENT_TYPE =
                ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "." + PATH_VIDEO;
        // Name of the video table.
        public static final String TABLE_NAME = "video";
        // View that joins this with videostatus
        public static final String VIEW_NAME = "videoview";
        // Rectype - Video, recording, channel, ...
        public static final String COLUMN_RECTYPE = "rectype";
        // The order of these determines the sequence on screen.
        public static final int RECTYPE_RECORDING = 1;
        public static final int RECTYPE_VIDEO = 2;
        public static final int RECTYPE_CHANNEL = 3;
        // Recording or video title
        public static final String COLUMN_TITLE = "title";
        // Simplified title for sorting and matching
        public static final String COLUMN_TITLEMATCH = "titlematch";
        // Description of the video.
        public static final String COLUMN_DESC = "description";
        // Subtitle.
        public static final String COLUMN_SUBTITLE = "subtitle";
        // The url to the video content.
        public static final String COLUMN_VIDEO_URL = "video_url";
        // path part of video URL
        // e.g. /Content/GetFile?StorageGroup=Default&FileName=/10763_20220711145100.ts
        public static final String COLUMN_VIDEO_URL_PATH = "video_url_path";
        // Directory and name of video file, applies only to Videos storage group
        public static final String COLUMN_FILENAME = "filename";
        public static final String COLUMN_FILESIZE = "filesize";
        // Host name of video file
        public static final String COLUMN_HOSTNAME = "hostname";
        // The url to the background image.
        public static final String COLUMN_BG_IMAGE_URL = "bg_image_url";
        // The channel name.
        public static final String COLUMN_CHANNEL = "channel";
        // The card image for the video.
        public static final String COLUMN_CARD_IMG = "card_image";
        // The content type of the video.
        public static final String COLUMN_CONTENT_TYPE = "content_type";
        // The year the video was produced.
        public static final String COLUMN_PRODUCTION_YEAR = "year";
        // The duration of the video in milliseconds
        public static final String COLUMN_DURATION = "duration";
        // The original air date string yyyy-mm-dd
        public static final String COLUMN_AIRDATE  = "airdate";
        // The start time string format 2018-08-13T20:30:00Z
        public static final String COLUMN_STARTTIME  = "starttime";
        // End Time
        public static final String COLUMN_ENDTIME  = "endtime";
        // This contains recordedid or video id
        public static final String COLUMN_RECORDEDID = "recordedid";
        // Storage Group "Videos' for Videos
        public static final String COLUMN_STORAGEGROUP = "storagegroup";
        // Empty Recgroup indicates a video file instead of a recording.
        public static final String COLUMN_RECGROUP = "recgroup";
        public static final String COLUMN_PLAYGROUP = "playgroup";
        public static final String COLUMN_SEASON = "season";
        public static final String COLUMN_EPISODE = "episode";
        // Returns the Uri referencing a video with the specified id.
        public static Uri buildVideoUri(long id) {
            return ContentUris.withAppendedId(CONTENT_URI, id);
        }
        // see libmyth/programtypes.h for list of values.
        public static final String COLUMN_PROGFLAGS = "progflags";
        // see libmyth/programtypes.h for list of values.
        public static final String COLUMN_VIDEOPROPS = "videoprops";
        public static final String COLUMN_VIDEOPROPNAMES = "videopropnames";
        // Channel columns - use channel for the channel name
        public static final String COLUMN_CHANID = "chanid";
        public static final String COLUMN_CHANNUM = "channum";
        public static final String COLUMN_CALLSIGN = "callsign";
    } // end of VideoEntry

    /* Inner class that defines the status table */
    public static class StatusEntry implements BaseColumns {
        public static final String TABLE_NAME = "videostatus";
        // video_url has only the path and matches video_url_path
        // in video table. Name discrepancy is due to difficulty of renaming
        // a database column that is unique and used in a view.
        public static final String COLUMN_VIDEO_URL_PATH = "video_url";
        public static final String COLUMN_LAST_USED = "last_used";
        public static final String COLUMN_BOOKMARK = "bookmark";
        public static final String COLUMN_SHOW_RECENT = "show_recent";
    }

}
