/*
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

package org.andstatus.app.service;

import android.text.TextUtils;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.LatestUserMessages;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.social.MbTimelineItem;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.timeline.LatestTimelineItem;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

class TimelineDownloaderOther extends TimelineDownloader {
    private static final int MAXIMUM_NUMBER_OF_MESSAGES_TO_DOWNLOAD = 200;

    @Override
    public void download() throws ConnectionException {
        if (!getTimeline().isSyncable()) {
            throw new IllegalArgumentException("Timeline cannot be synced: " + getTimeline());
        }

        LatestTimelineItem latestTimelineItem = new LatestTimelineItem(getTimeline());
        long hours = MyPreferences.getDontSynchronizeOldMessages();
        boolean downloadingLatest = false;
        if (hours > 0 && RelativeTime.moreSecondsAgoThan(latestTimelineItem.getTimelineDownloadedDate(),
                TimeUnit.HOURS.toSeconds(hours))) {
            downloadingLatest = true;
            latestTimelineItem.clearPosition();
        } else if (latestTimelineItem.getPosition().isEmpty()) {
            downloadingLatest = true;
        }
        
        if (MyLog.isLoggable(this, MyLog.DEBUG)) {
            String strLog = "Loading "
            + (downloadingLatest ? "latest " : "")
            + execContext.getCommandData().toCommandSummary(execContext.getMyContext());
            if (latestTimelineItem.getTimelineItemDate() > 0) { strLog +=
                "; last Timeline item at=" + (new Date(latestTimelineItem.getTimelineItemDate()).toString())
                + "; last time downloaded at=" +  (new Date(latestTimelineItem.getTimelineDownloadedDate()).toString());
            }
            MyLog.d(this, strLog);
        }
        String userOid =  MyQuery.idToOid(OidEnum.USER_OID, execContext.getCommandData().getUserId(), 0);
        if (TextUtils.isEmpty(userOid) && getTimeline().getTimelineType().isForUser()) {
            throw new ConnectionException("User oId is not found for id=" + execContext.getCommandData().getUserId());
        }
        int toDownload = MAXIMUM_NUMBER_OF_MESSAGES_TO_DOWNLOAD;
        TimelinePosition lastPosition = latestTimelineItem.getPosition();
        LatestUserMessages latestUserMessages = new LatestUserMessages();

        latestTimelineItem.onTimelineDownloaded();

        DataInserter di = new DataInserter(execContext);
        for (int loopCounter=0; loopCounter < 100; loopCounter++ ) {
            try {
                int limit = execContext.getMyAccount().getConnection().fixedDownloadLimitForApiRoutine(
                        toDownload, getTimeline().getTimelineType().getConnectionApiRoutine());
                List<MbTimelineItem> messages;
                switch (getTimeline().getTimelineType()) {
                    case SEARCH:
                        messages = execContext.getMyAccount().getConnection().search(lastPosition, limit, getTimeline().getSearchQuery());
                        break;
                    default:
                        messages = execContext.getMyAccount().getConnection().getTimeline(
                                getTimeline().getTimelineType().getConnectionApiRoutine(), lastPosition, limit, userOid);
                        break;
                }
                for (MbTimelineItem item : messages) {
                    toDownload--;
                    latestTimelineItem.onNewMsg(item.timelineItemPosition, item.timelineItemDate);
                    switch (item.getType()) {
                        case MESSAGE:
                            di.insertOrUpdateMsg(item.mbMessage, latestUserMessages);
                            break;
                        case USER:
                            di.insertOrUpdateUser(item.mbUser);
                            break;
                        default:
                            break;
                    }
                }
                if (toDownload <= 0
                        || lastPosition == latestTimelineItem.getPosition()) {
                    break;
                } else {
                    lastPosition = latestTimelineItem.getPosition();
                }
            } catch (ConnectionException e) {
                if (e.getStatusCode() != StatusCode.NOT_FOUND) {
                    throw e;
                }
                if (lastPosition.isEmpty()) {
                    throw ConnectionException.hardConnectionException("No last position", e);
                }
                MyLog.d(this, "The timeline was not found, last position='" + lastPosition +"'", e);
                lastPosition = TimelinePosition.getEmpty();
            }
        }
        latestUserMessages.save();
        latestTimelineItem.save();
    }

}
