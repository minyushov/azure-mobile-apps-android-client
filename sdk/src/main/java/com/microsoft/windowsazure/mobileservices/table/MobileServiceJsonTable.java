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
 * MobileServiceJsonTable.java
 */
package com.microsoft.windowsazure.mobileservices.table;


import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.MobileServiceException;
import com.microsoft.windowsazure.mobileservices.MobileServiceFeatures;
import com.microsoft.windowsazure.mobileservices.http.HttpConstants;
import com.microsoft.windowsazure.mobileservices.http.MobileServiceConnection;
import com.microsoft.windowsazure.mobileservices.http.MobileServiceHttpClient;
import com.microsoft.windowsazure.mobileservices.http.Request;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterRequest;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterRequestImpl;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterResponse;
import com.microsoft.windowsazure.mobileservices.table.query.ExecutableJsonQuery;
import com.microsoft.windowsazure.mobileservices.table.query.Query;
import com.microsoft.windowsazure.mobileservices.table.query.QueryODataWriter;
import com.microsoft.windowsazure.mobileservices.table.query.QueryOrder;
import com.microsoft.windowsazure.mobileservices.util.Pair;
import com.microsoft.windowsazure.mobileservices.util.Uri;

import io.reactivex.Completable;
import io.reactivex.Single;
import okhttp3.Headers;

/**
 * Represents a Mobile Service Table, Provides operations on a table using a JsonObject
 */
public final class MobileServiceJsonTable extends MobileServiceTableBase {

    /**
     * Constructor for MobileServiceJsonTable
     *
     * @param name   The name of the represented table
     * @param client The MobileServiceClient used to invoke table operations
     */
    public MobileServiceJsonTable(String name, MobileServiceClient client) {
        super(name, client);
        mFeatures.add(MobileServiceFeatures.UntypedTable);
    }

    /**
     * Updates the Version System Property in the Json Object with the ETag
     * information
     *
     * @param response The response containing the ETag Header
     * @param json     The JsonObject to modify
     */
    private static void updateVersionFromETag(ServiceFilterResponse response, JsonObject json) {
        if (response != null && response.getHeaders() != null) {
            String etag = response.getHeaders().get("ETag");

            if (etag != null) {
                json.remove(VersionSystemPropertyName);
                json.addProperty(VersionSystemPropertyName, getValueFromEtag(etag));
            }
        }
    }

    /**
     * Executes a query to retrieve all the table rows
     */
    public Single<JsonElement> execute() {
        return executeInternal();
    }

    /**
     * Executes a query to retrieve all the table rows
     */
    protected Single<JsonElement> executeInternal() {
        return execute(where());
    }

    /**
     * Retrieves a set of rows from the table using a query
     *
     * @param query The query used to retrieve the rows
     */
    public Single<JsonElement> execute(Query query) {
        String url;
        try {
            String filtersUrl = QueryODataWriter.getRowFilter(query);
            url = mClient.getAppUrl().toString() + TABLES_URL + URLEncoder.encode(mTableName, MobileServiceClient.UTF8_ENCODING);

            if (filtersUrl.length() > 0) {
                url += "?$filter=" + filtersUrl + QueryODataWriter.getRowSetModifiers(query, this);
            } else {
                String rowSetModifiers = QueryODataWriter.getRowSetModifiers(query, this);

                if (rowSetModifiers.length() > 0) {
                    url += "?" + QueryODataWriter.getRowSetModifiers(query, this).substring(1);
                }
            }
        } catch (UnsupportedEncodingException e) {
            return Single.error(e);
        }

        EnumSet<MobileServiceFeatures> features = mFeatures.clone();
        if (query != null) {
            List<Pair<String, String>> userParameters = query.getUserDefinedParameters();
            if (userParameters != null && userParameters.size() > 0) {
                features.add(MobileServiceFeatures.AdditionalQueryParameters);
            }
        }

        return executeUrlQuery(url, features);
    }

    /**
     * Retrieves a set of rows using the Next Link Url (Continuation Token)
     *
     * @param nextLink The Next Link to make the request
     */
    public Single<JsonElement> execute(String nextLink) {
        return executeUrlQuery(nextLink, mFeatures.clone());
    }

    /**
     * Make the request to the mobile service with the query URL
     *
     * @param url      The query url
     * @param features The features used in the request
     */
    private Single<JsonElement> executeUrlQuery(String url, EnumSet<MobileServiceFeatures> features) {
        return executeGetRecords(url, features)
                .map(response -> {
                    String nextLinkHeaderValue = getHeaderValue(response.second.getHeaders(), "Link");

                    if (nextLinkHeaderValue != null) {
                        JsonObject jsonResult = new JsonObject();

                        String nextLink = nextLinkHeaderValue.replace("; rel=next", "");

                        jsonResult.addProperty("nextLink", nextLink);
                        jsonResult.add("results", response.first);

                        return jsonResult;
                    } else {
                        return response.first;
                    }
                });
    }

    /**
     * Starts a filter to query the table
     *
     * @return The ExecutableJsonQuery representing the filter
     */
    public ExecutableJsonQuery where() {
        ExecutableJsonQuery query = new ExecutableJsonQuery();
        query.setTable(this);
        return query;
    }

    /**
     * Starts a filter to query the table with an existing filter
     *
     * @param query The existing filter
     * @return The ExecutableJsonQuery representing the filter
     */
    public ExecutableJsonQuery where(Query query) {
        if (query == null) {
            throw new IllegalArgumentException("Query must not be null");
        }

        ExecutableJsonQuery baseQuery = new ExecutableJsonQuery(query);
        baseQuery.setTable(this);
        return baseQuery;
    }

    /**
     * Adds a new user-defined parameter to the query
     *
     * @param parameter The parameter name
     * @param value     The parameter value
     * @return ExecutableJsonQuery
     */
    public ExecutableJsonQuery parameter(String parameter, String value) {
        return this.where().parameter(parameter, value);
    }

    /**
     * Creates a query with the specified order
     *
     * @param field Field name
     * @param order Sorting order
     * @return ExecutableJsonQuery
     */
    public ExecutableJsonQuery orderBy(String field, QueryOrder order) {
        return this.where().orderBy(field, order);
    }

    /**
     * Sets the number of records to return
     *
     * @param top Number of records to return
     * @return ExecutableQuery
     */
    public ExecutableJsonQuery top(int top) {
        return this.where().top(top);
    }

    /**
     * Sets the number of records to skip over a given number of elements in a
     * sequence and then return the remainder.
     *
     * @param skip
     * @return ExecutableJsonQuery
     */
    public ExecutableJsonQuery skip(int skip) {
        return this.where().skip(skip);
    }

    /**
     * Specifies the fields to retrieve
     *
     * @param fields Names of the fields to retrieve
     * @return ExecutableJsonQuery
     */
    public ExecutableJsonQuery select(String... fields) {
        return this.where().select(fields);
    }

    /**
     * Include a property with the number of records returned.
     *
     * @return ExecutableJsonQuery
     */
    public ExecutableJsonQuery includeInlineCount() {
        return this.where().includeInlineCount();
    }

    /**
     * Include the soft deleted records on the query result.
     *
     * @return ExecutableJsonQuery
     */
    public ExecutableJsonQuery includeDeleted() {
        return this.where().includeDeleted();
    }

    /**
     * Looks up a row in the table and retrieves its JSON value.
     *
     * @param id The id of the row
     */
    public Single<JsonObject> lookUp(Object id) {
        return lookUp(id, null);
    }

    /**
     * Looks up a row in the table and retrieves its JSON value.
     *
     * @param id         The id of the row
     * @param parameters A list of user-defined parameters and values to include in the
     *                   request URI query string
     */
    public Single<JsonObject> lookUp(Object id, List<Pair<String, String>> parameters) {
        try {
            validateId(id);
        } catch (Exception e) {
            return Single.error(e);
        }

        String url;

        Uri.Builder uriBuilder = Uri.parse(mClient.getAppUrl().toString()).buildUpon();
        uriBuilder.path(TABLES_URL);
        uriBuilder.appendPath(mTableName);
        uriBuilder.appendPath(id.toString());

        EnumSet<MobileServiceFeatures> features = mFeatures.clone();
        if (parameters != null && parameters.size() > 0) {
            features.add(MobileServiceFeatures.AdditionalQueryParameters);
        }

        parameters = addSystemProperties(mSystemProperties, parameters);

        if (parameters != null && parameters.size() > 0) {
            for (Pair<String, String> parameter : parameters) {
                uriBuilder.appendQueryParameter(parameter.first, parameter.second);
            }
        }

        url = uriBuilder.build().toString();

        return executeGetRecords(url, features)
                .map(response -> {
                    if (!response.first.isJsonObject()) {
                        // empty result
                        throw new MobileServiceException("A record with the specified Id cannot be found", response.second);
                    } else {
                        // Lookup result
                        JsonObject patchedJson = response.first.getAsJsonObject();

                        updateVersionFromETag(response.second, patchedJson);

                        return patchedJson;
                    }
                });
    }

    /**
     * Inserts a JsonObject into a Mobile Service table
     *
     * @param element The JsonObject to insert
     * @throws IllegalArgumentException if the element has an id property set with a numeric value
     *                                  other than default (0), or an invalid string value
     */
    public Single<JsonObject> insert(JsonObject element) {
        return insert(element, null);
    }

    /**
     * Inserts a JsonObject into a Mobile Service Table
     *
     * @param element    The JsonObject to insert
     * @param parameters A list of user-defined parameters and values to include in the
     *                   request URI query string
     * @throws IllegalArgumentException if the element has an id property set with a numeric value
     *                                  other than default (0), or an invalid string value
     */
    public Single<JsonObject> insert(JsonObject element, List<Pair<String, String>> parameters) {
        try {
            validateIdOnInsert(element);
        } catch (Exception e) {
            return Single.error(e);
        }

        String content = element.toString();

        EnumSet<MobileServiceFeatures> features = mFeatures.clone();
        if (parameters != null && parameters.size() > 0) {
            features.add(MobileServiceFeatures.AdditionalQueryParameters);
        }

        parameters = addSystemProperties(mSystemProperties, parameters);

        return executeTableOperation(TABLES_URL + mTableName, content, HttpConstants.PostMethod, null, parameters, features)
                .map(response -> {
                    if (response == null) {
                        return new JsonObject();
                    } else {
                        JsonObject patchedJson = patchOriginalEntityWithResponseEntity(element, response.first);
                        updateVersionFromETag(response.second, patchedJson);
                        return patchedJson;
                    }
                });
    }

    /**
     * Updates an element from a Mobile Service Table
     *
     * @param element The JsonObject to update
     */
    public Single<JsonObject> update(JsonObject element) {
        return update(element, null);
    }

    /**
     * Updates an element from a Mobile Service Table
     *
     * @param element    The JsonObject to update
     * @param parameters A list of user-defined parameters and values to include in the
     *                   request URI query string
     */
    public Single<JsonObject> update(JsonObject element, List<Pair<String, String>> parameters) {
        Object id;
        String version = null;
        String content;

        try {
            id = validateId(element);
        } catch (Exception e) {
            return Single.error(e);
        }

        if (!isNumericType(id)) {
            version = getVersionSystemProperty(element);
            content = removeSystemProperties(element).toString();
        } else {
            content = element.toString();
        }

        EnumSet<MobileServiceFeatures> features = mFeatures.clone();
        if (parameters != null && parameters.size() > 0) {
            features.add(MobileServiceFeatures.AdditionalQueryParameters);
        }

        parameters = addSystemProperties(mSystemProperties, parameters);

        List<Pair<String, String>> requestHeaders = null;
        if (version != null) {
            requestHeaders = new ArrayList<>();
            requestHeaders.add(new Pair<>("If-Match", getEtagFromValue(version)));
            features.add(MobileServiceFeatures.OpportunisticConcurrency);
        }

        return executeTableOperation(TABLES_URL + mTableName + "/" + id.toString(), content, HttpConstants.PatchMethod, requestHeaders, parameters, features)
                .map(response -> {
                    JsonObject patchedJson = patchOriginalEntityWithResponseEntity(element, response.first);
                    updateVersionFromETag(response.second, patchedJson);
                    return patchedJson;
                });
    }

    /**
     * Delete an element from a Mobile Service Table
     *
     * @param element The JsonObject to delete
     */
    public Completable delete(JsonObject element) {
        return delete(element, null);
    }

    /**
     * Delete an element from a Mobile Service Table
     *
     * @param element    The JsonObject to undelete
     * @param parameters A list of user-defined parameters and values to include in the
     *                   request URI query string
     */
    public Completable delete(JsonObject element, List<Pair<String, String>> parameters) {
        validateId(element);

        Object id;
        String version = null;

        try {
            id = validateId(element);
        } catch (Exception e) {
            return Completable.error(e);
        }

        if (!isNumericType(id)) {
            version = getVersionSystemProperty(element);
        }

        EnumSet<MobileServiceFeatures> features = mFeatures.clone();
        if (parameters != null && parameters.size() > 0) {
            features.add(MobileServiceFeatures.AdditionalQueryParameters);
        }

        parameters = addSystemProperties(mSystemProperties, parameters);

        List<Pair<String, String>> requestHeaders = null;
        if (version != null) {
            requestHeaders = new ArrayList<>();
            requestHeaders.add(new Pair<>("If-Match", getEtagFromValue(version)));
            features.add(MobileServiceFeatures.OpportunisticConcurrency);
        }

        return executeTableOperation(TABLES_URL + mTableName + "/" + id.toString(), null, HttpConstants.DeleteMethod, requestHeaders, parameters, features)
                .toCompletable();
    }

    /**
     * Undelete an element from a Mobile Service Table
     *
     * @param element The JsonObject to update
     */
    public Single<JsonObject> undelete(JsonObject element) {
        return undelete(element, null);
    }

    /**
     * Undelete an element from a Mobile Service Table
     *
     * @param element    The JsonObject to undelete
     * @param parameters A list of user-defined parameters and values to include in the
     *                   request URI query string
     */
    public Single<JsonObject> undelete(JsonObject element, List<Pair<String, String>> parameters) {
        Object id;
        String version = null;

        try {
            id = validateId(element);
        } catch (Exception e) {
            return Single.error(e);
        }

        if (!isNumericType(id)) {
            version = getVersionSystemProperty(element);
        }

        EnumSet<MobileServiceFeatures> features = mFeatures.clone();
        if (parameters != null && parameters.size() > 0) {
            features.add(MobileServiceFeatures.AdditionalQueryParameters);
        }

        parameters = addSystemProperties(mSystemProperties, parameters);

        List<Pair<String, String>> requestHeaders = null;
        if (version != null) {
            requestHeaders = new ArrayList<>();
            requestHeaders.add(new Pair<>("If-Match", getEtagFromValue(version)));
            features.add(MobileServiceFeatures.OpportunisticConcurrency);
        }

        return executeTableOperation(TABLES_URL + mTableName + "/" + id.toString(), null, HttpConstants.PostMethod, requestHeaders, parameters, features)
                .map(response -> {
                    JsonObject patchedJson = patchOriginalEntityWithResponseEntity(element, response.first);
                    updateVersionFromETag(response.second, patchedJson);
                    return patchedJson;
                });
    }

    /**
     * Executes the query against the table
     *
     * @param path           Request to execute
     * @param content        The content of the request body
     * @param httpMethod     The method of the HTTP request
     * @param requestHeaders Additional request headers used in the HTTP request
     * @param parameters     A list of user-defined parameters and values to include in the
     *                       request URI query string
     * @param features       The features used in the request
     */
    private Single<Pair<JsonObject, ServiceFilterResponse>> executeTableOperation(
            String path, String content, String httpMethod, List<Pair<String, String>> requestHeaders, List<Pair<String, String>> parameters, EnumSet<MobileServiceFeatures> features) {

        MobileServiceHttpClient httpClient = new MobileServiceHttpClient(mClient);
        if (requestHeaders == null) {
            requestHeaders = new ArrayList<>();
        }

        if (content != null) {
            requestHeaders.add(new Pair<>(HttpConstants.ContentType, MobileServiceConnection.JSON_CONTENTTYPE));
        }

        return httpClient
                .request(path, content, httpMethod, requestHeaders, parameters, features)
                .map(response -> {
                    String responseContent = response.getContent();
                    if (responseContent == null) {
                        return new Pair<JsonObject, ServiceFilterResponse>(null, null);
                    } else {
                        if (!responseContent.isEmpty()) {
                            JsonElement json = new JsonParser().parse(responseContent);
                            return json.isJsonObject() ?
                                    Pair.create(json.getAsJsonObject(), response) :
                                    new Pair<JsonObject, ServiceFilterResponse>(null, null);
                        } else {
                            return new Pair<JsonObject, ServiceFilterResponse>(null, null);
                        }
                    }
                })
                .onErrorResumeNext(throwable -> Single.error(transformHttpException(throwable)));
    }

    /**
     * Retrieves a set of rows from using the specified URL
     *
     * @param url      The URL used to retrieve the rows
     * @param features The features used in this request
     */
    private Single<Pair<JsonElement, ServiceFilterResponse>> executeGetRecords(String url, EnumSet<MobileServiceFeatures> features) {
        ServiceFilterRequest request = ServiceFilterRequestImpl.get(mClient.getOkHttpClientFactory(), url);

        String featuresHeader = MobileServiceFeatures.featuresToString(features);
        if (featuresHeader != null) {
            request.addHeader(MobileServiceHttpClient.X_ZUMO_FEATURES, featuresHeader);
        }

        MobileServiceConnection conn = mClient.createConnection();

        return Request
                .create(request, conn)
                .map(response -> {
                    try {
                        // Parse the results using the given Entity class
                        String content = response.getContent();
                        JsonElement json = new JsonParser().parse(content);
                        return Pair.create(json, response);
                    } catch (Exception e) {
                        throw new MobileServiceException("Error while retrieving data from response.", e, response);
                    }
                });
    }

    private String getHeaderValue(Headers headers, String headerName) {
        if (headers == null) {
            return null;
        }

        return headers.get(headerName);
    }

    /**
     * Validates the Id property from a JsonObject on an Insert Action
     *
     * @param json The JsonObject to modify
     */
    private Object validateIdOnInsert(JsonObject json) {
        // Remove id property if exists
        String[] idPropertyNames = new String[]{"id", "Id", "iD", "ID"};

        for (int i = 0; i < 4; i++) {
            String idProperty = idPropertyNames[i];

            if (json.has(idProperty)) {
                JsonElement idElement = json.get(idProperty);

                if (isStringType(idElement)) {
                    String id = getStringValue(idElement);

                    if (!isValidStringId(id)) {
                        throw new IllegalArgumentException("The entity to insert has an invalid string value on " + idProperty + " property.");
                    }

                    return id;
                } else if (isNumericType(idElement)) {
                    long id = getNumericValue(idElement);

                    if (!isDefaultNumericId(id)) {
                        throw new IllegalArgumentException("The entity to insert should not have a numeric " + idProperty + " property defined.");
                    }

                    json.remove(idProperty);

                    return id;

                } else if (idElement.isJsonNull()) {
                    json.remove(idProperty);

                    return null;

                } else {
                    throw new IllegalArgumentException("The entity to insert should not have an " + idProperty + " defined with an invalid value");
                }
            }
        }

        return null;
    }

}

