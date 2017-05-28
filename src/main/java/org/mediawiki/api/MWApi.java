/* Copyright (C) 2012 Yuvi Panda <yuvipanda@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mediawiki.api;

import in.yuvi.http.fluent.Http;
import in.yuvi.http.fluent.Http.HttpRequestBuilder;
import in.yuvi.http.fluent.ProgressListener;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class MWApi {

    private CloseableHttpClient client;
    private String apiURL;
    public boolean isLoggedIn;
    private String userName = null;
    private String userID = null;
    private String csrfToken = null;

    public MWApi(String apiURL, CloseableHttpClient client) {
        this.apiURL = apiURL;
        this.client = client;
    }

    public RequestBuilder action(String action) {
        RequestBuilder builder = new RequestBuilder(this);
        builder.param("action", action);
        return builder;
    }

    public boolean validateLogin() throws IOException {
        ApiResult userMeta = this.action("query").param("meta", "userinfo").get();
        this.userID = userMeta.getString("/api/query/userinfo/@id");
        this.userName = userMeta.getString("/api/query/userinfo/@name");
        return !userID.equals("0");
    }

    public String getUserID() throws IOException {
        if (this.userID == null || "0".equals(this.userID)) {
            this.validateLogin();
        }
        return userID;
    }

    public String getUserName() throws IOException {
        if (this.userID == null || "0".equals(this.userID)) {
            this.validateLogin();
        }
        return userName;
    }

    public String login(String username, String password) throws IOException {
        ApiResult loginApiResult = this.action("login").param("lgname", username).param("lgpassword", password).post();
        String loginResult = loginApiResult.getString("/api/login/@result");
        isLoggedIn = "Success".equals(loginResult);
        if ("NeedToken".equals(loginResult)) {
            ApiResult tokenApiResult = this.action("query").param("meta", "tokens").param("type", "login").get();
            String loginToken = tokenApiResult.getString("/api/query/tokens/@logintoken");
            ApiResult loginConfirmApiResult = this.action("login").param("lgname", username).param("lgpassword", password)
                    .param("lgtoken", loginToken).post();
            String loginConfirmResult = loginConfirmApiResult.getString("/api/login/@result");
            isLoggedIn = "Success".equals(loginConfirmResult);
            return loginConfirmResult;
        } else {
            return loginResult;
        }
    }

    public ApiResult upload(String filename, InputStream file, long length, String text, String comment)
            throws IOException {
        return this.upload(filename, file, length, text, comment, false, null);
    }

    public ApiResult upload(String filename, InputStream file, String text, String comment) throws IOException {
        return this.upload(filename, file, -1, text, comment, false, null);
    }

    public ApiResult upload(String filename, InputStream file, long length, String text, String comment, boolean watch,
                            ProgressListener uploadProgressListener) throws IOException {
        String token = this.getCsrfToken(false);
        HttpRequestBuilder builder = Http.multipart(apiURL).data("action", "upload").data("token", token).data("text", text)
                .data("ignorewarnings", "1").data("comment", comment).data("filename", filename)
                .sendProgressListener(uploadProgressListener);
        if (watch) {
            builder.data("watchlist", "watch");
        } else {
            builder.data("watchlist", "nochange");
        }
        if (length != -1) {
            builder.file("file", filename, file, length);
        } else {
            builder.file("file", filename, file);
        }
        return ApiResult.fromRequestBuilder(builder, client);
    }

    public void logout() throws IOException {
        // I should be doing more validation here, but meh
        isLoggedIn = false;
        this.action("logout").post();
    }

    public String getCsrfToken() throws IOException {
        return this.getCsrfToken(true);
    }

    public String getCsrfToken(final boolean renew) throws IOException {
        synchronized (this) {
            if (renew || this.csrfToken == null) {
                ApiResult result = this.action("query").param("meta", "tokens").param("type", "csrf").get();
                this.csrfToken = result.getString("/api/query/tokens/@csrftoken");
            }
            return csrfToken;
        }
    }

    private ApiResult makeRequest(String method, HashMap<String, Object> params) throws IOException {
        HttpRequestBuilder builder;
        if ("POST".equals(method)) {
            builder = Http.post(apiURL);
        } else {
            builder = Http.get(apiURL);
        }
        builder.data(params);
        return ApiResult.fromRequestBuilder(builder, client);
    }

    public class RequestBuilder {
        private HashMap<String, Object> params;
        private MWApi api;

        RequestBuilder(MWApi api) {
            params = new HashMap<String, Object>();
            this.api = api;
        }

        public RequestBuilder param(String key, Object value) {
            params.put(key, value);
            return this;
        }

        public ApiResult get() throws IOException {
            return api.makeRequest("GET", params);
        }

        public ApiResult post() throws IOException {
            return api.makeRequest("POST", params);
        }
    }
};