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
 * MobileServiceHttpClient.java
 */
package com.microsoft.windowsazure.mobileservices.http;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.MobileServiceFeatures;
import com.microsoft.windowsazure.mobileservices.util.Pair;
import com.microsoft.windowsazure.mobileservices.util.Uri;

import io.reactivex.Single;

/**
 * Utility class which centralizes the HTTP requests sent by the
 * mobile services client.
 */
public class MobileServiceHttpClient {

    /**
     * Request header to indicate the features in this SDK used by the request.
     */
    public final static String X_ZUMO_FEATURES = "X-ZUMO-FEATURES";

    /**
     * The client associated with this HTTP caller.
     */
    MobileServiceClient mClient;

    /**
     * Get client associated with this HTTP caller.
     * @return MobileServiceClient
     */
    public MobileServiceClient getClient(){
        return mClient;
    }
    /**
     * Constructor
     *
     * @param client The client associated with this HTTP caller.
     */
    public MobileServiceHttpClient(MobileServiceClient client) {
        this.mClient = client;
    }

    /**
     * Makes a request over HTTP
     *
     * @param path           The path of the request URI
     * @param content        The byte array to send as the request body
     * @param httpMethod     The HTTP Method used to invoke the API
     * @param requestHeaders The extra headers to send in the request
     * @param parameters     The query string parameters sent in the request
     */
    public Single<ServiceFilterResponse> request(String path, byte[] content, String httpMethod,
                                                           List<Pair<String, String>> requestHeaders, List<Pair<String, String>> parameters) {
        return request(path, content, httpMethod, requestHeaders, parameters, EnumSet.noneOf(MobileServiceFeatures.class));
    }

    /**
     * Makes a request over HTTP
     *
     * @param path           The path of the request URI
     * @param content        The string to send as the request body
     * @param httpMethod     The HTTP Method used to invoke the API
     * @param requestHeaders The extra headers to send in the request
     * @param parameters     The query string parameters sent in the request
     * @param features       The features used in the request
     */
    public Single<ServiceFilterResponse> request(String path, String content, String httpMethod,
                                                           List<Pair<String, String>> requestHeaders, List<Pair<String, String>> parameters,
                                                           EnumSet<MobileServiceFeatures> features) {
        try {
            byte[] byteContent = null;

            if (content != null) {
                byteContent = content.getBytes(MobileServiceClient.UTF8_ENCODING);
            }

            return request(path, byteContent, httpMethod, requestHeaders, parameters, features);
        } catch (UnsupportedEncodingException e) {
            return Single.error(e);
        }
    }

    /**
     * Makes a request over HTTP
     *
     * @param path           The path of the request URI
     * @param content        The byte array to send as the request body
     * @param httpMethod     The HTTP Method used to invoke the API
     * @param requestHeaders The extra headers to send in the request
     * @param parameters     The query string parameters sent in the request
     * @param features       The features used in the request
     */
    public Single<ServiceFilterResponse> request(String path, byte[] content, String httpMethod,
                                                 List<Pair<String, String>> requestHeaders, List<Pair<String, String>> parameters,
                                                 EnumSet<MobileServiceFeatures> features) {

        if (path == null || path.trim().equals("")) {
            return Single.error(new IllegalArgumentException("request path cannot be null"));
        }

        if (httpMethod == null || httpMethod.trim().equals("")) {
            return Single.error(new IllegalArgumentException("httpMethod cannot be null"));
        }

        Uri.Builder uriBuilder = Uri.parse(mClient.getAppUrl().toString()).buildUpon();
        uriBuilder.path(path);

        if (parameters != null && parameters.size() > 0) {
            for (Pair<String, String> parameter : parameters) {
                uriBuilder.appendQueryParameter(parameter.first, parameter.second);
            }
        }

        ServiceFilterRequestImpl request;
        String url = uriBuilder.build().toString();


        if (httpMethod.equalsIgnoreCase(HttpConstants.GetMethod)) {
            request = ServiceFilterRequestImpl.get(mClient.getOkHttpClientFactory(), url);
        } else if (httpMethod.equalsIgnoreCase(HttpConstants.PostMethod)) {
            request = ServiceFilterRequestImpl.post(mClient.getOkHttpClientFactory(), url, content);
        } else if (httpMethod.equalsIgnoreCase(HttpConstants.PutMethod)) {
            request = ServiceFilterRequestImpl.put(mClient.getOkHttpClientFactory(), url, content);
        } else if (httpMethod.equalsIgnoreCase(HttpConstants.PatchMethod)) {
            request = ServiceFilterRequestImpl.patch(mClient.getOkHttpClientFactory(), url, content);
        } else if (httpMethod.equalsIgnoreCase(HttpConstants.DeleteMethod)) {
            request = ServiceFilterRequestImpl.delete(mClient.getOkHttpClientFactory(), url, content);
        } else {
            return Single.error(new IllegalArgumentException("httpMethod not supported"));
        }

        String featuresHeader = MobileServiceFeatures.featuresToString(features);
        if (featuresHeader != null) {
            if (requestHeaders == null) {
                requestHeaders = new ArrayList<>();
            }

            boolean containsFeatures = false;
            for (Pair<String, String> header : requestHeaders) {
                if (header.first.equals(X_ZUMO_FEATURES)) {
                    containsFeatures = true;
                    break;
                }
            }

            if (!containsFeatures) {
                // Clone header list to prevent changing user's list
                requestHeaders = new ArrayList<>(requestHeaders);
                requestHeaders.add(new Pair<>(X_ZUMO_FEATURES, featuresHeader));
            }
        }

        if (requestHeaders != null && requestHeaders.size() > 0) {
            for (Pair<String, String> header : requestHeaders) {
                request.addHeader(header.first, header.second);
            }
        }

        MobileServiceConnection conn = mClient.createConnection();
        return Request.create(request, conn);
    }
}
