/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.data;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.database.DownloadTable;
import org.andstatus.app.database.FriendshipTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.MsgOfUserTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SelectionAndArgs;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Clean database from outdated information
 * old Messages, log files...
 */
public class DataPruner {
    private MyContext mMyContext;
    private ContentResolver mContentResolver;
    private int mDeleted = 0;
    static final long MAX_DAYS_LOGS_TO_KEEP = 10;
    static final long PRUNE_MIN_PERIOD_DAYS = 1;	

    public DataPruner(MyContext myContext) {
        mMyContext = myContext;
        mContentResolver = myContext.context().getContentResolver();
    }

    /**
     * @return true if done successfully, false if skipped or an error
     */
    public boolean prune() {
        final String method = "prune";
        boolean pruned = false;
        if (!isTimeToPrune()) {
            return pruned;
        }
        MyLog.v(this, method + " started");

        mDeleted = 0;
        int nDeletedTime = 0;
        // We're using global preferences here
        SharedPreferences sp = SharedPreferencesUtil
                .getDefaultSharedPreferences();

        // Don't delete messages, which are favorited by any user
        String sqlNotFavoritedMessage = "NOT EXISTS ("
                + "SELECT * FROM " + MsgOfUserTable.TABLE_NAME + " AS gnf WHERE "
                + MsgTable.TABLE_NAME + "." + MsgTable._ID + "=gnf." + MsgOfUserTable.MSG_ID
                + " AND gnf." + MsgOfUserTable.FAVORITED + "=1"
                + ")";
        String sqlNotLatestMessageByFollowedUser = MsgTable.TABLE_NAME + "." + MsgTable._ID + " NOT IN("
                + "SELECT " + UserTable.USER_MSG_ID
                + " FROM " + UserTable.TABLE_NAME + " AS userf"
                + " INNER JOIN " + FriendshipTable.TABLE_NAME
                + " ON" 
                + " userf." + UserTable._ID + "=" + FriendshipTable.TABLE_NAME + "." + FriendshipTable.FRIEND_ID
                + " AND " + FriendshipTable.TABLE_NAME + "." + FriendshipTable.FOLLOWED + "=1"
                + ")";

        int maxDays = Integer.parseInt(sp.getString(MyPreferences.KEY_HISTORY_TIME, "3"));
        long latestTimestamp = 0;

        int nTweets = 0;
        int nToDeleteSize = 0;
        int nDeletedSize = 0;
        int maxSize = Integer.parseInt(sp.getString(MyPreferences.KEY_HISTORY_SIZE, "2000"));
        long latestTimestampSize = 0;
        Cursor cursor = null;
        try {
            if (maxDays > 0) {
                latestTimestamp = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(maxDays);
                SelectionAndArgs sa = new SelectionAndArgs();
                sa.addSelection(MsgTable.TABLE_NAME + "." + MsgTable.INS_DATE + " <  ?",
                        new String[] {String.valueOf(latestTimestamp)});
                sa.addSelection(sqlNotFavoritedMessage);
                sa.addSelection(sqlNotLatestMessageByFollowedUser);
                nDeletedTime = mContentResolver.delete(MatchedUri.MSG_CONTENT_URI, sa.selection, sa.selectionArgs);
            }

            if (maxSize > 0) {
                nDeletedSize = 0;
                cursor = mContentResolver.query(MatchedUri.MSG_CONTENT_COUNT_URI, null, null, null, null);
                if (cursor.moveToFirst()) {
                    // Count is in the first column
                    nTweets = cursor.getInt(0);
                    nToDeleteSize = nTweets - maxSize;
                }
                cursor.close();
                if (nToDeleteSize > 0) {
                    // Find INS_DATE of the most recent tweet to delete
                    cursor = mContentResolver.query(MatchedUri.MSG_CONTENT_URI, new String[] {
                            MsgTable.INS_DATE
                    }, null, null, MsgTable.INS_DATE + " ASC LIMIT 0," + nToDeleteSize);
                    if (cursor.moveToLast()) {
                        latestTimestampSize = cursor.getLong(0);
                    }
                    cursor.close();
                    if (latestTimestampSize > 0) {
                        SelectionAndArgs sa = new SelectionAndArgs();
                        sa.addSelection(MsgTable.TABLE_NAME + "." + MsgTable.INS_DATE + " <=  ?",
                                new String[] {String.valueOf(latestTimestampSize)});
                        sa.addSelection(sqlNotFavoritedMessage);
                        sa.addSelection(sqlNotLatestMessageByFollowedUser);
                        nDeletedSize = mContentResolver.delete(MatchedUri.MSG_CONTENT_URI, sa.selection,
                                sa.selectionArgs);
                    }
                }
            }
            pruned = true;
        } catch (Exception e) {
            MyLog.i(this, method + " failed", e);
        } finally {
            DbUtils.closeSilently(cursor);
        }
        mDeleted = nDeletedTime + nDeletedSize;
        if (mDeleted > 0) {
            pruneAttachments();
        }
        pruneLogs(MAX_DAYS_LOGS_TO_KEEP);
        setDataPrunedNow();
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this,
                    method + " " + (pruned ? "succeeded" : "failed") + "; History time=" + maxDays + " days; deleted " + nDeletedTime
                    + " , before " + new Date(latestTimestamp).toString());
            MyLog.v(this, method + "; History size=" + maxSize + " messages; deleted "
                    + nDeletedSize + " of " + nTweets + " messages, before " + new Date(latestTimestampSize).toString());
        }
        return pruned;
    }

    long pruneAttachments() {
        final String method = "pruneAttachments";
        String sql = "SELECT DISTINCT " + DownloadTable.MSG_ID + " FROM " + DownloadTable.TABLE_NAME
                + " WHERE " + DownloadTable.MSG_ID + " NOT NULL"
                + " AND NOT EXISTS (" 
                + "SELECT * FROM " + MsgTable.TABLE_NAME
                + " WHERE " + MsgTable.TABLE_NAME + "." + MsgTable._ID + "=" + DownloadTable.MSG_ID
                + ")";
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(this, method + "; Database is null");
            return 0;
        }
        long nDeleted = 0;
        List<Long> list = new ArrayList<Long>();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, null);
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
        for (Long msgId : list) {
            DownloadData.deleteAllOfThisMsg(msgId);
            nDeleted++;
        }
        if (nDeleted > 0) {
            MyLog.v(this, method + "; Attachments deleted for " + nDeleted + " messages");
        }
        return nDeleted;
    }

    public static void setDataPrunedNow() {
        SharedPreferencesUtil.putLong(MyPreferences.KEY_DATA_PRUNED_DATE, System.currentTimeMillis());
    }

    private boolean isTimeToPrune()	{
        return !mMyContext.isInForeground() && RelativeTime.moreSecondsAgoThan(
                SharedPreferencesUtil.getLong(MyPreferences.KEY_DATA_PRUNED_DATE),
                PRUNE_MIN_PERIOD_DAYS * RelativeTime.SECONDS_IN_A_DAY);
    }

    long pruneLogs(long maxDaysToKeep) {
        final String method = "pruneLogs";
        long latestTimestamp = System.currentTimeMillis() 
                - java.util.concurrent.TimeUnit.DAYS.toMillis(maxDaysToKeep);
        long deletedCount = 0;
        File dir = MyLog.getLogDir(true);
        if (dir == null) {
            return deletedCount;
        }
        long errorCount = 0;
        long skippedCount = 0;
        for (String filename : dir.list()) {
            File file = new File(dir, filename);
            if (file.isFile() && (file.lastModified() < latestTimestamp)) {
                if (file.delete()) {
                    deletedCount++;
                    if (deletedCount < 10 && MyLog.isVerboseEnabled()) {
                        MyLog.v(this, method + "; deleted: " + file.getName());
                    }
                } else {
                    errorCount++;
                    if (errorCount < 10 && MyLog.isVerboseEnabled()) {
                        MyLog.v(this, method + "; couldn't delete: " + file.getAbsolutePath());
                    }
                }
            } else {
                skippedCount++;
                if (skippedCount < 10 && MyLog.isVerboseEnabled()) {
                    MyLog.v(this, method + "; skipped: " + file.getName() + ", modified " + new Date(file.lastModified()).toString());
                }
            }
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this,
                    method + "; deleted " + deletedCount
                    + " files, before " + new Date(latestTimestamp).toString()
                    + ", skipped " + skippedCount + ", couldn't delete " + errorCount);
        }
        return deletedCount;
    }

    /**
     * @return number of Messages deleted
     */
    public int getDeleted() {
        return mDeleted;
    }
}
