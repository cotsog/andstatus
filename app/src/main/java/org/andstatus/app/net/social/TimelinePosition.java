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

package org.andstatus.app.net.social;

import android.text.TextUtils;

/**
 * Since introducing support for Pump.Io it appeared that 
 * Position in the Timeline and Id of the Message may be different things.
 * @author yvolk@yurivolkov.com
 */
public class TimelinePosition {
    private final String position;

    public TimelinePosition(String position) {
        if (TextUtils.isEmpty(position)) {
            this.position = "";
        } else {
            this.position = position;
        }
    }

    public String getPosition() {
        return position;
    }

    @Override
    public String toString() {
        return position;
    }

    public static TimelinePosition getEmpty() {
        return new TimelinePosition("");
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(position);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof TimelinePosition)) {
            return false;
        }
        return hashCode() == ((TimelinePosition) o).hashCode();
    }

    @Override
    public int hashCode() {
        return position.hashCode();
    }
}
