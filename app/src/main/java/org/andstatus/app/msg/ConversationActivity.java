/* 
 * Copyright (c) 2012-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.QueueViewer;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.widget.MyBaseAdapter;

/**
 * One selected message and, optionally, the whole conversation
 * 
 * @author yvolk@yurivolkov.com
 */
public class ConversationActivity extends LoadableListActivity implements ActionableMessageList {
    private MessageContextMenu mContextMenu;
    private MessageEditor mMessageEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.my_list_swipe;
        super.onCreate(savedInstanceState);

        mMessageEditor = new MessageEditor(this);
        mContextMenu = new MessageContextMenu(this);
    }

    @Override
    public boolean canSwipeRefreshChildScrollUp() {
        if (mMessageEditor != null && mMessageEditor.isVisible()) {
            return true;
        }
        return super.canSwipeRefreshChildScrollUp();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_ACCOUNT_TO_ACT_AS:
                if (resultCode == RESULT_OK) {
                    MyAccount myAccount = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.ACCOUNT_NAME.key));
                    if (myAccount.isValid()) {
                        mContextMenu.setAccountUserIdToActAs(myAccount.getUserId());
                        mContextMenu.showContextMenu();
                    }
                }
                break;
            case ATTACH:
                if (resultCode == RESULT_OK && data != null) {
                    Uri uri = UriUtils.notNull(data.getData());
                    if (!UriUtils.isEmpty(uri)) {
                        UriUtils.takePersistableUriPermission(getActivity(), uri, data.getFlags());
                        mMessageEditor.startEditingCurrentWithAttachedMedia(uri);
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    protected void onPause() {
        mMessageEditor.saveAsBeingEditedAndHide();
        super.onPause();
    }

    @Override
    protected void onReceiveAfterExecutingCommand(CommandData commandData) {
        super.onReceiveAfterExecutingCommand(commandData);
        switch (commandData.getCommand()) {
            case UPDATE_STATUS:
                mMessageEditor.loadCurrentDraft();
                break;
            default:
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMessageEditor.loadCurrentDraft();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        mContextMenu.onContextItemSelected(item);
        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversation, menu);
        if (mMessageEditor != null) {
            mMessageEditor.onCreateOptionsMenu(menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mMessageEditor != null) {
            mMessageEditor.onPrepareOptionsMenu(menu);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.commands_queue_id:
                startActivity(new Intent(getActivity(), QueueViewer.class));
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public LoadableListActivity getActivity() {
        return this;
    }

    @Override
    public MessageEditor getMessageEditor() {
        return mMessageEditor;
    }

    @Override
    public void onMessageEditorVisibilityChange() {
        invalidateOptionsMenu();
    }

    @Override
    public Timeline getTimeline() {
        return Timeline.getTimeline(myContext, 0, TimelineType.MESSAGES_TO_ACT,
                getCurrentMyAccount(), 0, getCurrentMyAccount().getOrigin(), "");
    }

    @SuppressWarnings("unchecked")
    private ConversationLoader<ConversationViewItem> getListLoader() {
        return ((ConversationLoader<ConversationViewItem>)getLoaded());
    }
    
    @Override
    protected SyncLoader newSyncLoader(Bundle args) {
        return new ConversationLoader<>(ConversationViewItem.class,
                getMyContext(), getCurrentMyAccount(), centralItemId);
    }

    @Override
    protected MyBaseAdapter newListAdapter() {
        return new ConversationViewAdapter(mContextMenu, centralItemId, getListLoader().getList());
    }

    @Override
    protected CharSequence getCustomTitle() {
        final StringBuilder title = new StringBuilder(
                getText(getListData().size() > 1 ? R.string.label_conversation : R.string.message));
        I18n.appendWithSpace(title, getText(R.string.combined_timeline_off_origin));
        I18n.appendWithSpace(title, getCurrentMyAccount().getOrigin().getName());
        return title;
    }
}
