/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.net.Uri;
import android.test.InstrumentationTestCase;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.context.Travis;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.user.UserListType;

@Travis
public class ParsedUriTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
    }

    public void testUserList() {
        assertOneUserList(TestSuite.getConversationOriginId());
        assertOneUserList(0);
    }

    private void assertOneUserList(long originId) {
        long userId = 5;
        long msgId = 2;
        Uri uri = MatchedUri.getUserListUri(userId, UserListType.USERS_OF_MESSAGE, originId, msgId);
        ParsedUri parsedUri = ParsedUri.fromUri(uri);
        String msgLog = parsedUri.toString();
        assertEquals(TimelineType.UNKNOWN, parsedUri.getTimelineType());
        assertEquals(msgLog, UserListType.USERS_OF_MESSAGE, parsedUri.getUserListType());
        assertEquals(msgLog, userId, parsedUri.getAccountUserId());
        assertEquals(msgLog, originId, parsedUri.getOriginId());
        assertEquals(msgLog, msgId, parsedUri.getMessageId());
        assertEquals(msgLog, msgId, parsedUri.getItemId());
        assertEquals(msgLog, 0, parsedUri.getUserId());
    }
}
