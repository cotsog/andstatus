/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.text.TextUtils;

import org.andstatus.app.database.MsgOfUserTable;
import org.andstatus.app.util.ContentValuesUtils;

class MsgOfUserValues {
    private long rowId;
    private long userId;
    private long msgId;
    private ContentValues contentValues = new ContentValues();

    public MsgOfUserValues(long userId) {
        this.userId = userId;
        contentValues.put(MsgOfUserTable.USER_ID, userId);
    }

    /**
     * Move all keys that belong to MsgOfUser table from values to the newly created ContentValues. 
     * Returns null if we don't need MsgOfUser for this Msg
     * @param values
     * @return
     */
    public static MsgOfUserValues valueOf(long userId, ContentValues values) {
        return valueOf(userId, "", values);
    }

    public static MsgOfUserValues valuesOfOtherUser(ContentValues values) {
        long userId = ContentValuesUtils.moveLongKey(MsgOfUserTable.USER_ID, MsgOfUserTable.SUFFIX_FOR_OTHER_USER, values, null);
        return valueOf(userId, MsgOfUserTable.SUFFIX_FOR_OTHER_USER, values);
    }

    private static MsgOfUserValues valueOf(long userId, String sourceSuffix, ContentValues values) {
        MsgOfUserValues userValues = new MsgOfUserValues(userId);
        userValues.setMsgId(values.getAsLong(BaseColumns._ID));
        ContentValuesUtils.moveBooleanKey(MsgOfUserTable.SUBSCRIBED, sourceSuffix, values, userValues.contentValues);
        ContentValuesUtils.moveBooleanKey(MsgOfUserTable.FAVORITED, sourceSuffix, values, userValues.contentValues);
        ContentValuesUtils.moveBooleanKey(MsgOfUserTable.REBLOGGED, sourceSuffix, values, userValues.contentValues);
        // The value is String!
        ContentValuesUtils.moveStringKey(MsgOfUserTable.REBLOG_OID, sourceSuffix, values, userValues.contentValues);
        ContentValuesUtils.moveBooleanKey(MsgOfUserTable.MENTIONED, sourceSuffix, values, userValues.contentValues);
        ContentValuesUtils.moveBooleanKey(MsgOfUserTable.REPLIED, sourceSuffix, values, userValues.contentValues);
        ContentValuesUtils.moveBooleanKey(MsgOfUserTable.DIRECTED, sourceSuffix, values, userValues.contentValues);
        return userValues;
    }

    boolean isValid() {
        return userId != 0 && msgId != 0;
    }
    
    boolean isEmpty() {
        boolean empty = true;
        if (isTrue(MsgOfUserTable.SUBSCRIBED)
                || isTrue(MsgOfUserTable.FAVORITED)
                || isTrue(MsgOfUserTable.REBLOGGED)
                || isTrue(MsgOfUserTable.MENTIONED)
                || isTrue(MsgOfUserTable.REPLIED)
                || isTrue(MsgOfUserTable.DIRECTED)
                        ) {
            empty = false;
        }
        if (empty && contentValues.containsKey(MsgOfUserTable.REBLOG_OID)
                && !TextUtils.isEmpty(contentValues.getAsString(MsgOfUserTable.REBLOG_OID))) {
            empty = false;
        }
        if (!isValid()) {
            empty = true;
        }
        return empty;
    }

    private boolean isTrue(String key) {
        boolean value = false;
        if (contentValues.containsKey(key) ) {
            value = contentValues.getAsInteger(key) != 0;
        }
        return value;
    }
    
    long getUserId() {
        return userId;
    }
    
    public long getMsgId() {
        return msgId;
    }

    public void setMsgId(Long msgIdIn) {
        if (msgIdIn != null) {
            msgId = msgIdIn;
        } else {
            msgId = 0;
        }
        contentValues.put(MsgOfUserTable.MSG_ID, msgId);
    }
    
    long insert(SQLiteDatabase db) {
        if (!isEmpty()) {
            rowId = db.insert(MsgOfUserTable.TABLE_NAME, MsgOfUserTable.MSG_ID, contentValues);
            if (rowId == -1) {
                throw new SQLException("Failed to insert row into " + MsgOfUserTable.TABLE_NAME);
            }
        }
        return rowId;
    }

    public int update(SQLiteDatabase db) {
        int count = 0;
        if (!isValid()) {
            return count;
        }
        String where = "(" + MsgOfUserTable.MSG_ID + "=" + msgId + " AND "
                + MsgOfUserTable.USER_ID + "="
                + userId + ")";
        String sql = "SELECT * FROM " + MsgOfUserTable.TABLE_NAME + " WHERE "
                + where;
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, null);
            boolean exists = cursor.moveToFirst();
            DbUtils.closeSilently(cursor);
            if (exists) {
                count += db.update(MsgOfUserTable.TABLE_NAME, contentValues, where, null);
            } else {
                insert(db);
                if (rowId != 0) {
                    count += 1;
                }
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
        return count;
    }
}
