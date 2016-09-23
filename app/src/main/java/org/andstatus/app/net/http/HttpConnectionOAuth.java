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

package org.andstatus.app.net.http;

import android.text.TextUtils;

import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.util.MyLog;
import org.json.JSONException;
import org.json.JSONObject;

abstract class HttpConnectionOAuth extends HttpConnection implements OAuthConsumerAndProvider {
    private static final String TAG = HttpConnectionOAuth.class.getSimpleName();
    public boolean logMe = false;

    private String userTokenKey() {
        return "user_token";
    }
    
    private String userSecretKey() {
        return "user_secret";
    }
    
    private String userToken;
    private String userSecret;

    @Override
    public void setConnectionData(HttpConnectionData connectionData) {
        super.setConnectionData(connectionData);
        connectionData.oauthClientKeys = OAuthClientKeys.fromConnectionData(connectionData);
        // We look for saved user keys
        if (connectionData.dataReader.dataContains(userTokenKey()) && connectionData.dataReader.dataContains(userSecretKey())) {
            userToken = connectionData.dataReader.getDataString(userTokenKey(), null);
            userSecret = connectionData.dataReader.getDataString(userSecretKey(), null);
            setUserTokenWithSecret(userToken, userSecret);
        }
    }  
    
    @Override
    public boolean getCredentialsPresent() {
        boolean yes = false;
        if (data.oauthClientKeys.areKeysPresent() 
            && !TextUtils.isEmpty(userToken) 
            && !TextUtils.isEmpty(userSecret)) {
                yes = true;
        }
        if (!yes && logMe) {
            MyLog.v(this, "Credentials presence: clientKeys:" + data.oauthClientKeys.areKeysPresent() + "; userKeys:" + !TextUtils.isEmpty(userToken) + "," + !TextUtils.isEmpty(userSecret));
        }
        return yes;
    }

    protected String getApiUrl(ApiRoutineEnum routine) throws ConnectionException {
        String url;
        switch(routine) {
            case OAUTH_ACCESS_TOKEN:
                url =  data.getOauthPath() + "/access_token";
                break;
            case OAUTH_AUTHORIZE:
                url = data.getOauthPath() + "/authorize";
                break;
            case OAUTH_REQUEST_TOKEN:
                url = data.getOauthPath() + "/request_token";
                break;
            case OAUTH_REGISTER_CLIENT:
                url = data.getBasicPath() +
                        "/client/register";
                break;
            default:
                url = "";
                break;
        }
        if (!TextUtils.isEmpty(url)) {
            url = pathToUrlString(url);
        }
        return url;
    }

    /**
     * @param token null means to clear the old values
     * @param secret
     */
    @Override
    public void setUserTokenWithSecret(String token, String secret) {
        synchronized (this) {
            userToken = token;
            userSecret = secret;
        }
        if (logMe) {
            MyLog.v(this, "Credentials set?: " + !TextUtils.isEmpty(token) + ", " + !TextUtils.isEmpty(secret));
        }
    }

    @Override
    String getUserToken() {
        return userToken;
    }

    @Override
    String getUserSecret() {
        return userSecret;
    }

    /* (non-Javadoc)
     * @see org.andstatus.app.net.Connection#save(android.content.SharedPreferences, android.content.SharedPreferences.Editor)
     */
    @Override
    public boolean save(AccountDataWriter dw) {
        boolean changed = super.save(dw);

        if ( !TextUtils.equals(userToken, dw.getDataString(userTokenKey(), null)) ||
                !TextUtils.equals(userSecret, dw.getDataString(userSecretKey(), null)) 
                ) {
            changed = true;

            if (TextUtils.isEmpty(userToken)) {
                dw.setDataString(userTokenKey(), null);
                if (logMe) {
                    MyLog.d(TAG, "Clearing OAuth Token");
                }
            } else {
                dw.setDataString(userTokenKey(), userToken);
                if (logMe) {
                    MyLog.d(TAG, "Saving OAuth Token: " + userToken);
                }
            }
            if (TextUtils.isEmpty(userSecret)) {
                dw.setDataString(userSecretKey(), null);
                if (logMe) {
                    MyLog.d(TAG, "Clearing OAuth Secret");
                }
            } else {
                dw.setDataString(userSecretKey(), userSecret);
                if (logMe) {
                    MyLog.d(TAG, "Saving OAuth Secret: " + userSecret);
                }
            }
        }
        return changed;
    }

    @Override
    public boolean save(JSONObject jso) throws JSONException {
        boolean changed = super.save(jso);

        if ( !TextUtils.equals(userToken, jso.optString(userTokenKey(), null)) ||
                !TextUtils.equals(userSecret, jso.optString(userSecretKey(), null)) 
                ) {
            changed = true;

            if (TextUtils.isEmpty(userToken)) {
                jso.remove(userTokenKey());
                if (logMe) {
                    MyLog.d(TAG, "Clearing OAuth Token");
                }
            } else {
                jso.put(userTokenKey(), userToken);
                if (logMe) {
                    MyLog.d(TAG, "Saving OAuth Token: " + userToken);
                }
            }
            if (TextUtils.isEmpty(userSecret)) {
                jso.remove(userSecretKey());
                if (logMe) {
                    MyLog.d(TAG, "Clearing OAuth Secret");
                }
            } else {
                jso.put(userSecretKey(), userSecret);
                if (logMe) {
                    MyLog.d(TAG, "Saving OAuth Secret: " + userSecret);
                }
            }
        }
        return changed;
    }
    
    @Override
    public void clearAuthInformation() {
        setUserTokenWithSecret(null, null);
    }
}
