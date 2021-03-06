/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
Apache 2.0 License
 
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
 
     http://www.apache.org/licenses/LICENSE-2.0
 
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 
See the Apache Version 2.0 License for specific language governing permissions and limitations under the License.
 */

/**
 * ServiceFilterRequestImpl.java
 */
package com.microsoft.windowsazure.mobileservices.http;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;

import com.microsoft.windowsazure.mobileservices.MobileServiceClient;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ServiceFilterRequest implementation
 */
public class ServiceFilterRequestImpl implements ServiceFilterRequest {

    /**
     * The request to execute
     */
    private Request mRequest;

    /**
     * The request content
     */
    private byte[] mContent;

    private OkHttpClientFactory mOkHttpClientFactory;

    private static final MediaType JSON
            = MediaType.parse(MobileServiceConnection.JSON_CONTENTTYPE);

    /**
     * Constructor
     *
     * @param request The request to use
     * @param factory The AndroidHttpClientFactory instance used to create
     *                AndroidHttpClient objects
     */
    private ServiceFilterRequestImpl(Request request, OkHttpClientFactory factory) {
        mRequest = request;
        mOkHttpClientFactory = factory;
    }

    private ServiceFilterRequestImpl(Request request, OkHttpClientFactory factory, byte[] content) {
        mRequest = request;
        mOkHttpClientFactory = factory;
        mContent = content;
    }

    public static ServiceFilterRequestImpl post(OkHttpClientFactory factory, String url, byte[] content) {

        if (content == null) {
            content = "".getBytes();
        }

        RequestBody requestBody = RequestBody.create(JSON, content);

        Request request = getBaseRequestBuilder(url)
                .post(requestBody).build();

        return new ServiceFilterRequestImpl(request, factory, content);
    }

    public static ServiceFilterRequestImpl put(OkHttpClientFactory factory, String url, byte[] content) {

        if (content == null) {
            content = "".getBytes();
        }

        RequestBody requestBody = RequestBody.create(JSON, content);

        Request request = getBaseRequestBuilder(url)
                .put(requestBody).build();

        return new ServiceFilterRequestImpl(request, factory, content);
    }

    public static ServiceFilterRequestImpl patch(OkHttpClientFactory factory, String url, byte[] content) {

        if (content == null) {
            content = "".getBytes();
        }

        RequestBody requestBody = RequestBody.create(JSON, content);

        Request request = getBaseRequestBuilder(url)
                .patch(requestBody).build();

        return new ServiceFilterRequestImpl(request, factory, content);
    }

    public static ServiceFilterRequestImpl get(OkHttpClientFactory factory, String url) {

        Request request = getBaseRequestBuilder(url).get().build();

        return new ServiceFilterRequestImpl(request, factory);
    }

    public static ServiceFilterRequestImpl delete(OkHttpClientFactory factory, String url) {
        return delete(factory, url, (byte[]) null);
    }

    public static ServiceFilterRequestImpl delete(OkHttpClientFactory factory, String url, byte[] content) {

        Request.Builder requestBuilder = getBaseRequestBuilder(url);

        if (content != null) {
            RequestBody requestBody = RequestBody.create(JSON, content);
            requestBuilder = requestBuilder.delete(requestBody);
        } else {
            requestBuilder = requestBuilder.delete();
        }

        return new ServiceFilterRequestImpl(requestBuilder.build(), factory, content);
    }

    private static Request.Builder getBaseRequestBuilder(String url) {
        return new Request.Builder().url(url);
    }

    @Override
    public ServiceFilterResponse execute() throws Exception {
        OkHttpClient client = mOkHttpClientFactory.createOkHttpClient();
        Response response = client.newCall(mRequest).execute();
        return new ServiceFilterResponseImpl(response);
    }

    @Override
    public Headers getHeaders() {
        return mRequest.headers();
    }

    @Override
    public void addHeader(String name, String val) {
        mRequest = mRequest.newBuilder().addHeader(name, val).build();
    }

    @Override
    public void removeHeader(String name) {
        mRequest = mRequest.newBuilder().removeHeader(name).build();
    }


    @Override
    public String getContent() {
        if (mContent != null) {
            String content = null;
            try {
                content = new String(mContent, MobileServiceClient.UTF8_ENCODING);
            } catch (UnsupportedEncodingException e) {
            }
            return content;
        } else {
            return null;
        }
    }

    @Override
    public byte[] getRawContent() {
        return mContent;
    }

    @Override
    public String getUrl() {
        return mRequest.url().toString();
    }

    @Override
    public void setUrl(String url) throws URISyntaxException {
        mRequest = mRequest.newBuilder().url(url).build();
    }

    @Override
    public String getMethod() {
        return mRequest.method();
    }
}