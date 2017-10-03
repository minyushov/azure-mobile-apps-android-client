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
 * MobileServiceConnection.java
 */
package com.microsoft.windowsazure.mobileservices.http;

import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.MobileServiceException;

import io.reactivex.Single;

/**
 * Class for handling communication with Microsoft Azure Mobile Services REST APIs
 */
public class MobileServiceConnection {

    /**
     * Header value to represent JSON content-type
     */
    public static final String JSON_CONTENTTYPE = "application/json";

    /**
     * Request header to indicate the Mobile Service application key
     */
    private static final String X_ZUMO_APPLICATION_HEADER = "X-ZUMO-APPLICATION";

    /**
     * Request header to indicate the Mobile Service Installation ID
     */
    private static final String X_ZUMO_INSTALLATION_ID_HEADER = "X-ZUMO-INSTALLATION-ID";

    /**
     * Request header to indicate the Mobile Service user authentication token
     */
    private static final String X_ZUMO_AUTH_HEADER = "X-ZUMO-AUTH";

    /**
     * Name of the zumo version header.
     */
    private static final String X_ZUMO_VERSION_HEADER = "X-ZUMO-VERSION";

    /**
     * Header value to represent GZIP content-encoding
     */
    private static final String GZIP_CONTENTENCODING = "gzip";
    /**
     * Current SDK version
     */
    private static final String SDK_VERSION = "2.0.2";
    /**
     * The MobileServiceClient used for communication with the Mobile Service
     */
    private MobileServiceClient mClient;

    /**
     * Constructor for the MobileServiceConnection
     *
     * @param client The client used for communication with the Mobile Service
     */
    public MobileServiceConnection(MobileServiceClient client) {
        mClient = client;
    }

    /**
     * Generates the User-Agent
     */
    static String getUserAgent() {
        // FIXME: HARDCODE
        String userAgent = String.format("ZUMO/1.0 (lang=%s; os=%s; os_version=%s; arch=%s; version=%s)", "Java", "Android", "5.0", "arm", SDK_VERSION);

        return userAgent;
    }

    /**
     * Execute a request-response operation with a Mobile Service
     *
     * @param request The request to execute
     */
    public Single<ServiceFilterResponse> start(ServiceFilterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request can not be null");
        }

        // Set the request's headers
        configureHeadersOnRequest(request);

        return Single
                .fromCallable(() -> {
                    ServiceFilterResponse response = null;
                    try {
                        response = request.execute();
                        int statusCode = response.getStatus().code;

                        // If the response has error throw exception
                        if (statusCode < 200 || statusCode >= 300) {
                            String responseContent = response.getContent();
                            if (responseContent != null && !responseContent.trim().equals("")) {
                                throw new MobileServiceException(responseContent, response);
                            } else {
                                throw new MobileServiceException(String.format("{'code': %d}", statusCode), response);
                            }
                        }

                        return response;
                    } catch (MobileServiceException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new MobileServiceException("Error while processing request.", e, response);
                    }
                });
    }

    /**
     * Configures the HttpRequestBase to execute a request with a Mobile Service
     *
     * @param request The request to configure
     */
    private void configureHeadersOnRequest(ServiceFilterRequest request) {
        // Add the authentication header if the user is logged in

        request.addHeader(X_ZUMO_VERSION_HEADER, SDK_VERSION);

        // Set the User Agent header
        request.addHeader("User-Agent", getUserAgent());

        // Set the special Application key header, if present
        String appKey = mClient.getAppKey();
        if (appKey != null && appKey.trim().length() > 0) {
            request.addHeader(X_ZUMO_APPLICATION_HEADER, mClient.getAppKey());
        }

        if (!requestContainsHeader(request, "Accept")) {
            request.addHeader("Accept", JSON_CONTENTTYPE);
        }

        if (!requestContainsHeader(request, "Accept-Encoding")) {
            request.addHeader("Accept-Encoding", GZIP_CONTENTENCODING);
        }
    }

    /**
     * Verifies if the request contains the specified header
     *
     * @param request    The request to verify
     * @param headerName The header name to find
     * @return True if the header is present, false otherwise
     */
    private boolean requestContainsHeader(ServiceFilterRequest request, String headerName) {

        String value = request.getHeaders().get(headerName);

        return (value != null);
    }
}
