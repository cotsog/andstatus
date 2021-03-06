package org.andstatus.app.timeline;

import android.support.annotation.NonNull;
import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.context.Travis;
import org.andstatus.app.net.social.TimelinePosition;

import java.util.Arrays;

@Travis
public class LatestTimelineItemTest extends InstrumentationTestCase {

    public static final int LATEST_ITEM_MILLIS_AGO = 10000;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
    }

    public void testGnuSocialTimeline() {
        testTimelineForAccount(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME);
    }

    public void testTwitterTimeline() {
        testTimelineForAccount(TestSuite.TWITTER_TEST_ACCOUNT_NAME);
    }

    private void testTimelineForAccount(String accountName) {
        oneTimelineType(TimelineType.PUBLIC, accountName);
        oneTimelineType(TimelineType.HOME, accountName);
        oneTimelineType(TimelineType.EVERYTHING, accountName);
    }

    private void oneTimelineType(TimelineType timelineType, String accountName) {
        MyContext myContext = MyContextHolder.get();
        MyAccount ma = myContext.persistentAccounts().fromAccountName(accountName);
        assertTrue(ma.isValid());
        assertEquals("Account was found", ma.getAccountName(), accountName);
        Timeline timeline = getTimeline(myContext, timelineType, ma);
        if (timelineType.isAtOrigin()) {
            if (timeline.getOrigin().getOriginType().isTimelineTypeSyncable(timelineType)) {
                assertEquals("Timeline persistence " + timeline,
                        Arrays.asList(TimelineType.getDefaultOriginTimelineTypes()).contains(timelineType)
                        , timeline.getId() != 0);
            }
        } else {
            assertEquals("Timeline persistence " + timeline,
                    Arrays.asList(TimelineType.getDefaultMyAccountTimelineTypes()).contains(timelineType)
                    , timeline.getId() != 0);
        }
        long time1 = System.currentTimeMillis();
        LatestTimelineItem latest = new LatestTimelineItem(timeline);
        latest.onTimelineDownloaded();
        latest.onNewMsg(
                new TimelinePosition("position_" + timelineType.save() + "_" + accountName),
                System.currentTimeMillis() - LATEST_ITEM_MILLIS_AGO);
        latest.save();
        timeline.save(myContext);

        timeline = getTimeline(myContext, timelineType, ma);

        latest = new LatestTimelineItem(timeline);
        long time2 = System.currentTimeMillis();
        if (timeline.getId() == 0) {
            assertEquals("Remembered timeline dates for " + timeline, 0, latest.getTimelineItemDate());
            assertEquals("Remembered timeline dates for " + timeline, latest.getTimelineDownloadedDate(), 0);
        } else {
            assertTrue(timeline.toString() + " was downloaded " + latest.toString(),
                    latest.getTimelineDownloadedDate() >= time1);
            assertTrue(timeline.toString() + " was downloaded " + latest.toString(),
                    latest.getTimelineDownloadedDate() <= time2);
            assertTrue(timeline.toString() + " latest item " + latest.toString(),
                    latest.getTimelineItemDate() >= time1 - LATEST_ITEM_MILLIS_AGO);
            assertTrue(timeline.toString() + " latest item " + latest.toString(),
                    latest.getTimelineItemDate() <= time2 - LATEST_ITEM_MILLIS_AGO);
        }
    }

    @NonNull
    private Timeline getTimeline(MyContext myContext, TimelineType timelineType, MyAccount ma) {
        return Timeline.getTimeline(myContext, 0, timelineType, ma, 0, null, "");
    }
}
