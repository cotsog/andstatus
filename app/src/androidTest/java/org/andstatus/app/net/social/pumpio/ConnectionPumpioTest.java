/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social.pumpio;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import org.andstatus.app.account.AccountDataReaderEmpty;
import org.andstatus.app.account.AccountName;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.context.Travis;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnectionMock;
import org.andstatus.app.net.http.OAuthClientKeys;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbTimelineItem;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.net.social.pumpio.ConnectionPumpio.ConnectionAndUrl;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RawResourceUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.Calendar;
import java.util.List;

@Travis
public class ConnectionPumpioTest extends InstrumentationTestCase {
    private Context context;
    private ConnectionPumpio connection;
    private URL originUrl = UrlUtils.fromString("https://identi.ca");
    private HttpConnectionMock httpConnectionMock;
    private OriginConnectionData connectionData;

    private String keyStored;
    private String secretStored;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = TestSuite.initializeWithData(this);

        TestSuite.setHttpConnectionMockClass(HttpConnectionMock.class);
        connectionData = OriginConnectionData.fromAccountName( AccountName.fromOriginAndUserName(
                MyContextHolder.get().persistentOrigins().fromName(TestSuite.PUMPIO_ORIGIN_NAME), ""),
                TriState.UNKNOWN);
        connectionData.setDataReader(new AccountDataReaderEmpty());
        connection = (ConnectionPumpio) connectionData.newConnection();
        httpConnectionMock = connection.getHttpMock();

        httpConnectionMock.data.originUrl = originUrl;
        httpConnectionMock.data.oauthClientKeys = OAuthClientKeys.fromConnectionData(httpConnectionMock.data);
        keyStored = httpConnectionMock.data.oauthClientKeys.getConsumerKey();
        secretStored = httpConnectionMock.data.oauthClientKeys.getConsumerSecret();

        if (!httpConnectionMock.data.oauthClientKeys.areKeysPresent()) {
            httpConnectionMock.data.oauthClientKeys.setConsumerKeyAndSecret("keyForThetestGetTimeline", "thisIsASecret02341");
        }
        TestSuite.setHttpConnectionMockClass(null);
    }
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (!TextUtils.isEmpty(keyStored)) {
            httpConnectionMock.data.oauthClientKeys.setConsumerKeyAndSecret(keyStored, secretStored);        
        }
    }

    public void testOidToObjectType() {
        String oids[] = {"https://identi.ca/api/activity/L4v5OL93RrabouQc9_QGfg",
                "https://identi.ca/api/comment/ibpUqhU1TGCE2yHNbUv54g",
                "https://identi.ca/api/note/nlF5jl1HQciIs_zP85EeYg",
                "https://identi.ca/obj/ibpcomment",
                "http://identi.ca/notice/95772390",
                "acct:t131t@identi.ca",
                "http://identi.ca/user/46155",
                "https://identi.ca/api/user/andstatus/followers",
                ActivitySender.PUBLIC_COLLECTION_ID};
        String objectTypes[] = {"activity",
                "comment",
                "note",
                "unknown object type: https://identi.ca/obj/ibpcomment",
                "note",
                "person",
                "person",
                "collection",
                "collection"};
        for (int ind=0; ind < oids.length; ind++) {
            String oid = oids[ind];
            String objectType = objectTypes[ind];
            assertEquals("Expecting'" + oid + "' to be '" + objectType + "'", objectType, connection.oidToObjectType(oid));
        }
    }

    public void testUsernameToHost() {
        String usernames[] = {"t131t@identi.ca", 
                "somebody@example.com",
                "https://identi.ca/api/note/nlF5jl1HQciIs_zP85EeYg",
                "example.com",
                "@somewhere.com"};
        String hosts[] = {"identi.ca", 
                "example.com", 
                "",
                "",
                "somewhere.com"};
        for (int ind=0; ind < usernames.length; ind++) {
            assertEquals("Expecting '" + hosts[ind] + "'", hosts[ind], connection.usernameToHost(usernames[ind]));
        }
    }
    
    public void testGetConnectionAndUrl() throws ConnectionException {
        String userOids[] = {"acct:t131t@identi.ca", 
                "somebody@identi.ca"};
        String urls[] = {"api/user/t131t/profile", 
                "api/user/somebody/profile"};
        String hosts[] = {"identi.ca", 
                "identi.ca"};
        for (int ind=0; ind < userOids.length; ind++) {
            ConnectionAndUrl conu = connection.getConnectionAndUrl(ApiRoutineEnum.GET_USER, userOids[ind]);
            assertEquals("Expecting '" + urls[ind] + "'", urls[ind], conu.url);
            assertEquals("Expecting '" + hosts[ind] + "'", hosts[ind], conu.httpConnection.data.originUrl.getHost());
        }
    }
    
    public void testGetTimeline() throws IOException {
        String sinceId = originUrl.toExternalForm() + "/activity/frefq3232sf";

        String jso = RawResourceUtils.getString(this.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.user_t131t_inbox);
        httpConnectionMock.setResponse(jso);
        
        List<MbTimelineItem> timeline = connection.getTimeline(ApiRoutineEnum.STATUSES_HOME_TIMELINE,
                new TimelinePosition(sinceId), 20, "acct:t131t@" + originUrl.getHost());
        assertNotNull("timeline returned", timeline);
        int size = 6;
        assertEquals("Number of items in the Timeline", size, timeline.size());

        int ind = 0;
        assertEquals("Posting image", MbTimelineItem.ItemType.MESSAGE, timeline.get(ind).getType());
        MbMessage mbMessage = timeline.get(ind).mbMessage;
        assertTrue("Message body: '" + mbMessage.getBody() + "'", mbMessage.getBody().contains("Fantastic wheel stand"));
        assertEquals("Message sent date: " + mbMessage.sentDate, TestSuite.utcTime(2013, Calendar.SEPTEMBER, 13, 1, 8, 32).getTime(), mbMessage.sentDate);
        assertEquals("Sender's oid", "acct:jpope@io.jpope.org", mbMessage.sender.oid);
        assertEquals("Sender's username", "jpope@io.jpope.org", mbMessage.sender.getUserName());
        assertEquals("Sender's Display name", "jpope", mbMessage.sender.getRealName());
        assertEquals("Sender's profile image URL", "https://io.jpope.org/uploads/jpope/2013/7/8/LPyLPw_thumb.png", mbMessage.sender.avatarUrl);
        assertEquals("Sender's profile URL", "https://io.jpope.org/jpope", mbMessage.sender.getProfileUrl());
        assertEquals("Sender's Homepage", "https://io.jpope.org/jpope", mbMessage.sender.getHomepage());
        assertEquals("Sender's WebFinger ID", "jpope@io.jpope.org", mbMessage.sender.getWebFingerId());
        assertEquals("Description", "Does the Pope shit in the woods?", mbMessage.sender.getDescription());
        assertEquals("Messages count", 0, mbMessage.sender.msgCount);
        assertEquals("Favorites count", 0, mbMessage.sender.favoritesCount);
        assertEquals("Following (friends) count", 0, mbMessage.sender.followingCount);
        assertEquals("Followers count", 0, mbMessage.sender.followersCount);
        assertEquals("Location", "/dev/null", mbMessage.sender.location);
        assertEquals("Created at", 0, mbMessage.sender.getCreatedDate());
        assertEquals("Updated at", connection.parseDate("2013-09-12T17:10:44Z"), mbMessage.sender.getUpdatedDate());

        ind++;
        assertEquals("Other User", MbTimelineItem.ItemType.USER, timeline.get(ind).getType());
        MbUser mbUser = timeline.get(ind).mbUser;
        assertEquals("Other actor", "acct:jpope@io.jpope.org", mbUser.actor.oid);
        assertEquals("WebFinger ID", "jpope@io.jpope.org", mbUser.actor.getWebFingerId());
        assertEquals("Following", TriState.TRUE, mbUser.followedByActor);

        ind++;
        assertEquals("User", MbTimelineItem.ItemType.USER, timeline.get(ind).getType());
        mbUser = timeline.get(ind).mbUser;
        assertEquals("Url of the actor", "https://identi.ca/t131t", mbUser.actor.getProfileUrl());
        assertEquals("WebFinger ID", "t131t@identi.ca", mbUser.actor.getWebFingerId());
        assertEquals("Following", TriState.TRUE, mbUser.followedByActor);
        assertEquals("Url of the user", "https://fmrl.me/grdryn", mbUser.getProfileUrl());

        ind++;
        mbMessage = timeline.get(ind).mbMessage;
        assertEquals("Favorited by someone else", TriState.TRUE, mbMessage.favoritedByActor);
        assertEquals("Actor -someone else", "acct:jpope@io.jpope.org" , mbMessage.actor.oid);
        assertTrue("Does not have a recipient", mbMessage.recipient == null);
        assertEquals("Url of the message", "https://fmrl.me/lostson/note/Dp-njbPQSiOfdclSOuAuFw", mbMessage.url);

        ind++;
        mbMessage = timeline.get(ind).mbMessage;
        assertTrue("Have a recipient", mbMessage.recipient != null);
        assertEquals("Directed to yvolk", "acct:yvolk@identi.ca" , mbMessage.recipient.oid);

        ind++;
        mbMessage = timeline.get(ind).mbMessage;
        assertEquals(mbMessage.isSubscribed(), TriState.UNKNOWN);
        assertTrue("Is a reply", mbMessage.inReplyToMessage != null);
        assertEquals("Is a reply to this user", mbMessage.inReplyToMessage.sender.getUserName(), "jankusanagi@identi.ca");
        assertEquals(mbMessage.inReplyToMessage.isSubscribed(), TriState.FALSE);
    }

    public void testGetUsersFollowedBy() throws IOException {
        String jso = RawResourceUtils.getString(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.user_t131t_following);
        httpConnectionMock.setResponse(jso);
        
        assertTrue(connection.isApiSupported(ApiRoutineEnum.GET_FRIENDS));        
        assertTrue(connection.isApiSupported(ApiRoutineEnum.GET_FRIENDS_IDS));        
        
        List<MbUser> users = connection.getFriends("acct:t131t@" + originUrl.getHost());
        assertNotNull("List of users returned", users);
        int size = 5;
        assertEquals("Response for t131t", size, users.size());

        assertEquals("Does the Pope shit in the woods?", users.get(1).getDescription());
        assertEquals("gitorious@identi.ca", users.get(2).getUserName());
        assertEquals("acct:ken@coding.example", users.get(3).oid);
        assertEquals("Yuri Volkov", users.get(4).getRealName());
    }
    
    public void testUpdateStatus() throws ConnectionException, JSONException {
        String body = "@peter Do you think it's true?";
        String inReplyToId = "https://identi.ca/api/note/94893FsdsdfFdgtjuk38ErKv";
        httpConnectionMock.setResponse("");
        connection.getData().setAccountUserOid("acct:mytester@" + originUrl.getHost());
        connection.updateStatus(body, inReplyToId, null);
        JSONObject activity = httpConnectionMock.getPostedJSONObject();
        assertTrue("Object present", activity.has("object"));
        JSONObject obj = activity.getJSONObject("object");
        assertEquals("Message content", body, obj.getString("content"));
        assertEquals("Reply is comment", ObjectType.COMMENT.id(), obj.getString("objectType"));
        
        assertTrue("InReplyTo is present", obj.has("inReplyTo"));
        JSONObject inReplyToObject = obj.getJSONObject("inReplyTo");
        assertEquals("Id of the in reply to object", inReplyToId, inReplyToObject.getString("id"));

        body = "Testing the application...";
        inReplyToId = "";
        connection.updateStatus(body, inReplyToId, null);
        activity = httpConnectionMock.getPostedJSONObject();
        assertTrue("Object present", activity.has("object"));
        obj = activity.getJSONObject("object");
        assertEquals("Message content", body, obj.getString("content"));
        assertEquals("Message without reply is a note", ObjectType.NOTE.id(), obj.getString("objectType"));

        JSONArray recipients = activity.optJSONArray("to");
        assertEquals("To Public collection", ActivitySender.PUBLIC_COLLECTION_ID, ((JSONObject) recipients.get(0)).get("id"));

        assertTrue("InReplyTo is not present", !obj.has("inReplyTo"));
    }

    public void testReblog() throws ConnectionException, JSONException {
        String rebloggedId = "https://identi.ca/api/note/94893FsdsdfFdgtjuk38ErKv";
        httpConnectionMock.setResponse("");
        connection.getData().setAccountUserOid("acct:mytester@" + originUrl.getHost());
        connection.postReblog(rebloggedId);
        JSONObject activity = httpConnectionMock.getPostedJSONObject();
        assertTrue("Object present", activity.has("object"));
        JSONObject obj = activity.getJSONObject("object");
        assertEquals("Sharing a note", ObjectType.NOTE.id(), obj.getString("objectType"));
        assertEquals("Nothing in TO", null, activity.optJSONArray("to"));
        assertEquals("No followers in CC", null, activity.optJSONArray("cc"));
    }

    public void testUnfollowUser() throws IOException {
        String jso = RawResourceUtils.getString(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.unfollow_pumpio);
        httpConnectionMock.setResponse(jso);
        connection.getData().setAccountUserOid("acct:t131t@" + originUrl.getHost());
        String userOid = "acct:evan@e14n.com";
        MbUser user = connection.followUser(userOid, false);
        assertTrue("User is present", !user.isEmpty());
        assertEquals("Our account acted", connection.getData().getAccountUserOid(), user.actor.oid);
        assertEquals("Object of action", userOid, user.oid);
        assertEquals("Unfollowed", TriState.FALSE, user.followedByActor);
    }

    public void testParseDate() {
        String stringDate = "Wed Nov 27 09:27:01 -0300 2013";
        assertEquals("Bad date shouldn't throw (" + stringDate + ")", 0, connection.parseDate(stringDate) );
    }
    
    public void testDestroyStatus() throws IOException {
        String jso = RawResourceUtils.getString(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.destroy_status_response_pumpio);
        httpConnectionMock.setResponse(jso);
        connection.getData().setAccountUserOid(TestSuite.CONVERSATION_ACCOUNT_USER_OID);
        assertTrue("Success", connection.destroyStatus("https://identi.ca.example.com/api/comment/xf0WjLeEQSlyi8jwHJ0ttre"));

        boolean thrown = false;
        try {
            connection.destroyStatus("");
        } catch (IllegalArgumentException e) {
            MyLog.v(this, e);
            thrown = true;
        }
        assertTrue(thrown);
    }
    
    public void testPostWithMedia() throws IOException {
        String jso = RawResourceUtils.getString(this.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.pumpio_activity_with_image);
        httpConnectionMock.setResponse(jso);
        
        connection.getData().setAccountUserOid("acct:mymediatester@" + originUrl.getHost());
        MbMessage message2 = connection.updateStatus("Test post message with media", "", TestSuite.LOCAL_IMAGE_TEST_URI);
        message2.setPublic(true); 
        assertEquals("Message returned", privateGetMessageWithAttachment(this.getInstrumentation().getContext(), false), message2);
    }
    
    private MbMessage privateGetMessageWithAttachment(Context context, boolean uniqueUid) throws IOException {
        String jso = RawResourceUtils.getString(context,
                org.andstatus.app.tests.R.raw.pumpio_activity_with_image);
        httpConnectionMock.setResponse(jso);

        MbMessage msg = connection.getMessage("w9wME-JVQw2GQe6POK7FSQ");
        if (uniqueUid) {
            msg.oid += "_" + TestSuite.TESTRUN_UID;
        }
        assertNotNull("message returned", msg);
        assertEquals("has attachment", msg.attachments.size(), 1);
        MbAttachment attachment = MbAttachment.fromUrlAndContentType(new URL(
                "https://io.jpope.org/uploads/jpope/2014/8/18/m1o1bw.jpg"), MyContentType.IMAGE);
        assertEquals("attachment", attachment, msg.attachments.get(0));
        assertEquals("Body text", "<p>Hanging out up in the mountains.</p>", msg.getBody());
        return msg;
    }

    public void testGetMessageWithAttachment() throws IOException {
        privateGetMessageWithAttachment(this.getInstrumentation().getContext(), true);    
    }

    public void testGetMessageWithReplies() throws IOException {
        String jso = RawResourceUtils.getString(this.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.pumpio_note_self);
        httpConnectionMock.setResponse(jso);

        final String msgOid = "https://identi.ca/api/note/Z-x96Q8rTHSxTthYYULRHA";
        MbMessage msg = connection.getMessage(msgOid);
        assertNotNull("message returned", msg);
        assertEquals("Message oid", msgOid, msg.oid);
        assertEquals("Number of replies", 2, msg.replies.size());
        MbMessage reply = msg.replies.get(0);
        assertEquals("Reply oid", "https://identi.ca/api/comment/cJdi4cGWQT-Z9Rn3mjr5Bw", reply.oid);
        assertEquals("Is a Reply to", msgOid, reply.inReplyToMessage.oid);
    }
}
