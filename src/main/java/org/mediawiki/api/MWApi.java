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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import in.yuvi.http.fluent.Http.HttpRequestBuilder;
import in.yuvi.http.fluent.*;

import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;

public class MWApi {
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

    private AbstractHttpClient client;
    private String apiURL;
    public boolean isLoggedIn;
    private String authCookie = null;
    private String userName = null;
    private String userID = null;
    private String editToken = null;

    public MWApi(String apiURL, AbstractHttpClient client) {
        this.apiURL = apiURL;
        this.client = client;
    }

    public RequestBuilder action(String action) {
        RequestBuilder builder = new RequestBuilder(this);
        builder.param("action", action);
        return builder;
    }
    
    public String getAuthCookie() {
        if(authCookie == null){
            authCookie = "";
            List<Cookie> cookies = client.getCookieStore().getCookies();
            for(Cookie cookie: cookies) {
                authCookie += cookie.getName() + "=" + cookie.getValue() + ";";
            }
        }
        return authCookie;
    }
    
    public void setAuthCookie(String authCookie) {
        this.authCookie = authCookie;
        this.isLoggedIn = true;
        String[] cookies = authCookie.split(";");
        String domain;
        try {
            domain = new URL(apiURL).getHost();
        } catch (MalformedURLException e) {
            // Mighty well better not happen!
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        // This works because I know which cookies are going to be set by MediaWiki, and they don't contain a = or ; in them :D
        for(String cookie: cookies) {
            String[] parts = cookie.split("=");
            BasicClientCookie c = new BasicClientCookie(parts[0], parts[1]);
            c.setDomain(domain);
            client.getCookieStore().addCookie(c);
        }
    }

    public boolean validateLogin() throws IOException {
        ApiResult userMeta = this.action("query").param("meta", "userinfo").get();
        this.userID = userMeta.getString("/api/query/userinfo/@id");
        this.userName = userMeta.getString("/api/query/userinfo/@name");
        return !userID.equals("0");
    }
    
    public String getUserID() throws IOException {
        if(this.userID == null || this.userID == "0") {
            this.validateLogin();
        }
        return userID;
    }
    
    public String getUserName() throws IOException {
        if(this.userID == null || this.userID == "0") {
            this.validateLogin();
        }
        return userName;
    }
    
    public String login(String username, String password) throws IOException {
        ApiResult tokenData = this.action("login").param("lgname", username).param("lgpassword", password).post();
        String result = tokenData.getString("/api/login/@result");
        if (result.equals("NeedToken")) {
            String token = tokenData.getString("/api/login/@token");
            ApiResult confirmData = this.action("login").param("lgname", username).param("lgpassword", password).param("lgtoken", token).post();
            String finalResult = confirmData.getString("/api/login/@result");
            if(finalResult.equals("Success")) {
                isLoggedIn = true;
            }
            return finalResult;
        } else {
            return result;
        }
    }

    public ApiResult upload(String filename, InputStream file, long length, String text, String comment) throws IOException {
        return this.upload(filename, file, length, text, comment, false, null);
    }
    
    public ApiResult upload(String filename, InputStream file, String text, String comment) throws IOException {
        return this.upload(filename, file, -1, text, comment, false, null);
    }
    
    public ApiResult upload(String filename, InputStream file, long length, String text, String comment, boolean watch, ProgressListener uploadProgressListener) throws IOException {
        String token = this.getEditToken(false);
        HttpRequestBuilder builder = Http.multipart(apiURL)
                .data("action", "upload")
                .data("token", token)
                .data("text", text)
                .data("ignorewarnings", "1")
                .data("comment", comment)
                .data("filename", filename)
                .sendProgressListener(uploadProgressListener);
        if (watch) {
          builder.data("watchlist", "watch");
        } else {
          builder.data("watchlist", "nochange");
        }
        if(length != -1) {
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

    public String getEditToken() throws IOException {
      return this.getEditToken(true);
    }

    public String getEditToken(final boolean renew) throws IOException {
        synchronized (this) {
          if (renew || this.editToken == null) {
            ApiResult result = this.action("tokens").param("type", "edit").get();
            this.editToken = result.getString("/api/tokens/@edittoken");
          }
          return editToken;
        }
    }

    private ApiResult makeRequest(String method, HashMap<String, Object> params) throws IOException {
        HttpRequestBuilder builder;
        if (method == "POST") {
            builder = Http.post(apiURL);
        } else {
            builder = Http.get(apiURL);
        }
        builder.data(params);
        return ApiResult.fromRequestBuilder(builder, client);
    }
}
;