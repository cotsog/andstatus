/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.msg;

import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.UserInTimeline;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.List;

public class MessageEditorData {
    public volatile long msgId = 0;
    public DownloadStatus status = DownloadStatus.DRAFT;
    public String messageText = "";
    public DownloadData image = DownloadData.EMPTY;
    /**
     * Id of the Message to which we are replying.
     *  0 - This message is not a Reply.
     * -1 - is non-existent id.
     */
    public long inReplyToId = 0;
    boolean mReplyAll = false; 
    public long recipientId = 0;
    public MyAccount ma = MyAccount.getEmpty(MyContextHolder.get(), "");

    private MessageEditorData(MyAccount myAccount) {
        ma = myAccount;
    }
    
    public static MessageEditorData newEmpty(MyAccount myAccount) {
        return new MessageEditorData(
                myAccount == null ? MyAccount.getEmpty(MyContextHolder.get(), "") : myAccount);
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((ma == null) ? 0 : ma.hashCode());
        result = prime * result + image.getUri().hashCode();
        result = prime * result + ((messageText == null) ? 0 : messageText.hashCode());
        result = prime * result + (int) (recipientId ^ (recipientId >>> 32));
        result = prime * result + (int) (inReplyToId ^ (inReplyToId >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MessageEditorData other = (MessageEditorData) obj;
        if (ma == null) {
            if (other.ma != null)
                return false;
        } else if (!ma.equals(other.ma))
            return false;
        if (!image.getUri().equals(other.image.getUri()))
            return false;
        if (messageText == null) {
            if (other.messageText != null)
                return false;
        } else if (!messageText.equals(other.messageText))
            return false;
        if (recipientId != other.recipientId)
            return false;
        if (inReplyToId != other.inReplyToId)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "MessageEditorData{" +
                "msgId=" + msgId +
                ", status=" + status +
                ", messageText='" + messageText + '\'' +
                ", image=" + image +
                ", inReplyToId=" + inReplyToId +
                ", mReplyAll=" + mReplyAll +
                ", recipientId=" + recipientId +
                ", ma=" + ma +
                '}';
    }

    static MessageEditorData load() {
        long msgId = MyPreferences.getLong(MyPreferences.KEY_DRAFT_MESSAGE_ID);
        if (msgId != 0) {
            DownloadStatus status = DownloadStatus.load(MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.MSG_STATUS, msgId));
            if (status != DownloadStatus.DRAFT) {
                msgId = 0;
            }
        }
        MessageEditorData data;
        if (msgId != 0) {
            MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(
                    MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.SENDER_ID, msgId));
            data = new MessageEditorData(ma);
            data.msgId = msgId;
            data.messageText = MyQuery.msgIdToStringColumnValue(MyDatabase.Msg.BODY, msgId);
            data.image = DownloadData.getSingleForMessage(msgId, MyContentType.IMAGE, Uri.EMPTY);
            data.inReplyToId = MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.IN_REPLY_TO_MSG_ID, msgId);
            data.recipientId = MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.RECIPIENT_ID, msgId);
        } else {
            data = new MessageEditorData(MyContextHolder.get().persistentAccounts().getCurrentAccount());
        }
        return data;
    }

    MyAccount getMyAccount() {
        return ma;
    }
    
    boolean isEmpty() {
        return TextUtils.isEmpty(messageText) && UriUtils.isEmpty(image.getUri());
    }

    public MessageEditorData setMessageText(String textInitial) {
        messageText = textInitial;
        return this;
    }

    public MessageEditorData setMediaUri(Uri mediaUri) {
        image = DownloadData.getSingleForMessage(msgId, MyContentType.IMAGE, mediaUri);
        return this;
    }
    
    public MessageEditorData setInReplyToId(long msgId) {
        inReplyToId = msgId;
        return this;
    }
    
    public MessageEditorData setReplyAll(boolean replyAll) {
        mReplyAll = replyAll;
        return this;
    }

    public MessageEditorData addMentionsToText() {
        if (inReplyToId != 0) {
            if (mReplyAll) {
                addConversationMembersToText();
            } else {
                addMentionedAuthorOfMessageToText(inReplyToId);
            }
        }
        return this;
    }
    
    private void addConversationMembersToText() {
        if (!ma.isValid()) {
            return;
        }
        ConversationLoader<ConversationMemberItem> loader = new ConversationLoader<ConversationMemberItem>(
                ConversationMemberItem.class,
                MyContextHolder.get().context(), ma, inReplyToId);
        loader.load(null);
        List<Long> mentioned = new ArrayList<Long>();
        mentioned.add(ma.getUserId());  // Skip an author of this message
        long authorWhomWeReply = getAuthorWhomWeReply(loader);
        mentioned.add(authorWhomWeReply);
        for(ConversationMemberItem item : loader.getMsgs()) {
            mentionConversationMember(mentioned, item);
        }
        addMentionedUserToText(authorWhomWeReply);  // He will be mentioned first
    }

    private long getAuthorWhomWeReply(ConversationLoader<ConversationMemberItem> loader) {
        for(ConversationMemberItem item : loader.getMsgs()) {
            if (item.getMsgId() == inReplyToId) {
                return item.authorId;
            }
        }
        return 0;
    }

    private void mentionConversationMember(List<Long> mentioned, ConversationMemberItem item) {
        if (!mentioned.contains(item.authorId)) {
            addMentionedUserToText(item.authorId);
            mentioned.add(item.authorId);
        }
    }

    public MessageEditorData addMentionedUserToText(long mentionedUserId) {
        String name = MyQuery.userIdToName(mentionedUserId, getUserInTimeline());
        addMentionedUsernameToText(name);
        return this;
    }

    private UserInTimeline getUserInTimeline() {
        return ma
                .getOrigin().isMentionAsWebFingerId() ? UserInTimeline.WEBFINGER_ID
                : UserInTimeline.USERNAME;
    }

    private void addMentionedAuthorOfMessageToText(long messageId) {
        String name = MyQuery.msgIdToUsername(MyDatabase.Msg.AUTHOR_ID, messageId, getUserInTimeline());
        addMentionedUsernameToText(name);
    }
    
    private void addMentionedUsernameToText(String name) {
        if (!TextUtils.isEmpty(name)) {
            String messageText1 = "@" + name + " ";
            if (!TextUtils.isEmpty(messageText)) {
                messageText1 += messageText;
            }
            messageText = messageText1;
        }
    }

    public MessageEditorData setRecipientId(long userId) {
        recipientId = userId;
        return this;
    }

    public boolean sameContext(MessageEditorData dataIn) {
        return inReplyToId == dataIn.inReplyToId
                && recipientId == dataIn.recipientId
                && getMyAccount().getAccountName()
                        .compareTo(dataIn.getMyAccount().getAccountName()) == 0
                && mReplyAll == dataIn.mReplyAll;
    }
}
