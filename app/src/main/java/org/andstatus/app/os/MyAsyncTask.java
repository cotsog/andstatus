/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.os;

import android.os.AsyncTask;
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;

/**
 * @author yvolk@yurivolkov.com
 */
public abstract class MyAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {
    public static final long MAX_WAITING_BEFORE_EXECUTION_SECONDS = 600;
    public static final long MAX_COMMAND_EXECUTION_SECONDS = 600;
    public static final long MAX_EXECUTION_AFTER_CANCEL_SECONDS = 300;
    protected static final long DELAY_AFTER_EXECUTOR_ENDED_SECONDS = 1;

    private final String taskId;
    protected final long createdAt = MyLog.uniqueCurrentTimeMS();
    protected final long instanceId = InstanceId.next();
    private boolean singleInstance = true;

    protected volatile long backgroundStartedAt;
    protected volatile long backgroundEndedAt;
    /** This allows to control execution time of single steps/commands by this AsyncTask */
    protected volatile long currentlyExecutingSince = 0;

    protected volatile long cancelledAt = 0;

    public enum PoolEnum {
        SYNC(2),
        FILE_DOWNLOAD(1),
        QUICK_UI(1),
        LONG_UI(1),
        DEFAULT(0);

        protected int corePoolSize;

        PoolEnum(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }
    }

    public final PoolEnum pool;
    public boolean isSingleInstance() {
        return singleInstance;
    }

    public void setSingleInstance(boolean singleInstance) {
        this.singleInstance = singleInstance;
    }

    public MyAsyncTask(PoolEnum pool) {
        this.taskId = this.getClass().getName();
        this.pool = pool;
    }

    public MyAsyncTask(@NonNull String taskId, PoolEnum pool) {
        this.taskId = taskId;
        this.pool = pool;
    }

    @Override
    protected final Result doInBackground(Params... params) {
        backgroundStartedAt = System.currentTimeMillis();
        currentlyExecutingSince = backgroundStartedAt;
        try {
            if (isCancelled()) {
                return null;
            } else {
                return doInBackground2(params);
            }
        } catch (Exception e) {
            String msgLog = MyContextHolder.getSystemInfo(MyContextHolder.get().context());
            MyLog.e(this, msgLog, e);
            throw new IllegalStateException(msgLog, e);
        } finally {
            backgroundEndedAt = System.currentTimeMillis();
        }
    }

    protected abstract Result doInBackground2(Params... params);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MyAsyncTask<?, ?, ?> that = (MyAsyncTask<?, ?, ?>) o;

        return taskId.equals(that.taskId);
    }

    @Override
    public int hashCode() {
        return taskId.hashCode();
    }

    public boolean isBackgroundStarted() {
        return backgroundStartedAt > 0;
    }

    public boolean isBackgroundCompleted() {
        return backgroundEndedAt > 0;
    }

    @Override
    public String toString() {
        return taskId + " on " + pool.name()
                + "; age " + RelativeTime.secondsAgo(createdAt) + "sec"
                + "; " + stateSummary()
                + "; instanceId=" + instanceId + "; " + super.toString();
    }

    private String stateSummary() {
        String summary = "";
        switch (getStatus()) {
            case PENDING:
                summary = "PENDING " + RelativeTime.secondsAgo(createdAt) + "sec ago";
                break;
            case FINISHED:
                if (backgroundEndedAt == 0) {
                    summary = "FINISHED, but didn't complete";
                } else {
                    summary = "FINISHED " + RelativeTime.secondsAgo(backgroundEndedAt) + "sec ago";
                }
                break;
            default:
                if (backgroundStartedAt == 0) {
                    summary = "QUEUED " + RelativeTime.secondsAgo(createdAt) + "sec ago";
                } else if (backgroundEndedAt == 0) {
                    summary = "RUNNING " + RelativeTime.secondsAgo(backgroundStartedAt) + "sec";
                } else {
                    summary = "FINISHING " +  RelativeTime.secondsAgo(backgroundEndedAt) + "sec ago";
                }
                break;
        }
        if (isCancelled()) {
            if (cancelledAt == 0) {
                cancelledAt = System.currentTimeMillis();
            }
            summary += ", cancelled " + RelativeTime.secondsAgo(cancelledAt) + "sec ago";
        }
        return summary;
    }

    public boolean isReallyWorking() {
        return needsBackgroundWork() && !isStalled();
    }

    private boolean isStalled() {
        return RelativeTime.wasButMoreSecondsAgoThan(backgroundEndedAt, DELAY_AFTER_EXECUTOR_ENDED_SECONDS)
                || RelativeTime.wasButMoreSecondsAgoThan(currentlyExecutingSince, MAX_COMMAND_EXECUTION_SECONDS)
                || RelativeTime.wasButMoreSecondsAgoThan(cancelledAt, MAX_EXECUTION_AFTER_CANCEL_SECONDS)
                || (getStatus() == Status.PENDING
                && RelativeTime.wasButMoreSecondsAgoThan(createdAt, MAX_WAITING_BEFORE_EXECUTION_SECONDS));
    }

    public boolean needsBackgroundWork() {
        switch (getStatus()) {
            case PENDING:
                return true;
            case FINISHED:
                return false;
            default:
                return backgroundEndedAt == 0;
        }
    }

    public boolean cancelLogged(boolean mayInterruptIfRunning) {
        if (cancelledAt == 0) {
            cancelledAt = System.currentTimeMillis();
        }
        return super.cancel(mayInterruptIfRunning);
    }
}
