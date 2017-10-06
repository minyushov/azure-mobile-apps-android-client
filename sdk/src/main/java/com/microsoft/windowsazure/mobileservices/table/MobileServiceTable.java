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
 * MobileServiceTable.java
 */
package com.microsoft.windowsazure.mobileservices.table;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.MobileServiceFeatures;
import com.microsoft.windowsazure.mobileservices.MobileServiceList;
import com.microsoft.windowsazure.mobileservices.table.query.ExecutableQuery;
import com.microsoft.windowsazure.mobileservices.table.query.Query;
import com.microsoft.windowsazure.mobileservices.table.query.QueryOrder;
import com.microsoft.windowsazure.mobileservices.table.serialization.JsonEntityParser;
import com.microsoft.windowsazure.mobileservices.util.Pair;

import io.reactivex.Completable;
import io.reactivex.Single;

/**
 * Represents a Mobile Service Table, Provides operations on a table for a Mobile Service.
 */
public final class MobileServiceTable<E> extends MobileServiceTableBase {

    private MobileServiceJsonTable mInternalTable;

    private Class<E> mClazz;

    /**
     * Constructor for MobileServiceTable
     *
     * @param name   The name of the represented table
     * @param client The MobileServiceClient used to invoke table operations
     * @param clazz  The class used for data serialization
     */
    public MobileServiceTable(String name, MobileServiceClient client, Class<E> clazz) {
        super(name, client);
        mFeatures.add(MobileServiceFeatures.TypedTable);

        mInternalTable = new MobileServiceJsonTable(name, client);
        mInternalTable.mFeatures = EnumSet.of(MobileServiceFeatures.TypedTable);
        mClazz = clazz;

        mSystemProperties = getSystemProperties(clazz);
        mInternalTable.setSystemProperties(mSystemProperties);
    }

    public EnumSet<MobileServiceSystemProperty> getSystemProperties() {
        return mInternalTable.getSystemProperties();
    }

    public void setSystemProperties(EnumSet<MobileServiceSystemProperty> systemProperties) {
        this.mSystemProperties = systemProperties;
        this.mInternalTable.setSystemProperties(systemProperties);
    }

    /**
     * Executes a query to retrieve all the table rows
     */
    public Single<MobileServiceList<E>> execute() {
        return mInternalTable
                .execute()
                .map(response -> {
                    if (response.isJsonObject()) {
                        JsonObject jsonObject = response.getAsJsonObject();

                        int count = jsonObject.get("count").getAsInt();
                        JsonElement elements = jsonObject.get("results");

                        List<E> list = parseResults(elements);
                        return new MobileServiceList<E>(list, count);
                    } else {
                        List<E> list = parseResults(response);
                        return new MobileServiceList<E>(list, -1);
                    }
                });
    }

    /**
     * Executes a query to retrieve all the table rows
     *
     * @param query The Query instance to execute
     */
    public Single<MobileServiceList<E>> execute(Query query) {
        return mInternalTable
                .execute(query)
                .map(this::processQueryResults);
    }

    /**
     * Executes a Next Link to retrieve all the table rows
     *
     * @param nextLink The next link with the page information
     */
    public Single<MobileServiceList<E>> execute(String nextLink) {
        return mInternalTable
                .execute(nextLink)
                .map(this::processQueryResults);
    }

    /**
     * Process the Results of the Query
     *
     * @param result The Json element with the information
     */
    private MobileServiceList<E> processQueryResults(JsonElement result) {
        if (result.isJsonObject()) {
            JsonObject jsonObject = result.getAsJsonObject();

            int count = 0;
            String nextLink = null;

            if (jsonObject.has("count")) {
                count = jsonObject.get("count").getAsInt();
            }

            if (jsonObject.has("nextLink")) {
                nextLink = jsonObject.get("nextLink").getAsString();
            }

            JsonElement elements = jsonObject.get("results");

            List<E> list = parseResults(elements);

            if (nextLink != null) {
                return new MobileServiceList<E>(list, count, nextLink);
            } else {
                return new MobileServiceList<E>(list, count);
            }

        } else {
            List<E> list = parseResults(result);
            return new MobileServiceList<E>(list, list.size());
        }
    }

    /**
     * Starts a filter to query the table
     *
     * @return The ExecutableQuery representing the filter
     */
    public ExecutableQuery<E> where() {
        ExecutableQuery<E> query = new ExecutableQuery<E>();
        query.setTable(this);
        return query;
    }

    /**
     * Starts a filter to query the table with an existing filter
     *
     * @param query The existing filter
     * @return The ExecutableQuery representing the filter
     */
    public ExecutableQuery<E> where(Query query) {
        if (query == null) {
            throw new IllegalArgumentException("Query must not be null");
        }

        ExecutableQuery<E> baseQuery = new ExecutableQuery<E>(query);
        baseQuery.setTable(this);

        return baseQuery;
    }

    /**
     * Adds a new user-defined parameter to the query
     *
     * @param parameter The parameter name
     * @param value     The parameter value
     * @return ExecutableQuery
     */
    public ExecutableQuery<E> parameter(String parameter, String value) {
        return where().parameter(parameter, value);
    }

    /**
     * Creates a query with the specified order
     *
     * @param field Field name
     * @param order Sorting order
     * @return ExecutableQuery
     */
    public ExecutableQuery<E> orderBy(String field, QueryOrder order) {
        return where().orderBy(field, order);
    }

    /**
     * Sets the number of records to return
     *
     * @param top Number of records to return
     * @return ExecutableQuery
     */
    public ExecutableQuery<E> top(int top) {
        return where().top(top);
    }

    /**
     * Sets the number of records to skip over a given number of elements in a
     * sequence and then return the remainder.
     *
     * @param skip
     * @return ExecutableQuery
     */
    public ExecutableQuery<E> skip(int skip) {
        return where().skip(skip);
    }

    /**
     * Specifies the fields to retrieve
     *
     * @param fields Names of the fields to retrieve
     * @return ExecutableQuery
     */
    public ExecutableQuery<E> select(String... fields) {
        return where().select(fields);
    }

    /**
     * Include a property with the number of records returned.
     *
     * @return ExecutableQuery
     */
    public ExecutableQuery<E> includeInlineCount() {
        return where().includeInlineCount();
    }

    /**
     * Include a the soft deleted items on  records returned.
     *
     * @return ExecutableQuery
     */
    public ExecutableQuery<E> includeDeleted() {
        return where().includeDeleted();
    }

    /**
     * Looks up a row in the table.
     *
     * @param id The id of the row
     */
    public Single<E> lookUp(Object id) {
        return lookUp(id, null);
    }

    /**
     * Looks up a row in the table.
     *
     * @param id         The id of the row
     * @param parameters A list of user-defined parameters and values to include in the
     *                   request URI query string
     */
    public Single<E> lookUp(Object id, List<Pair<String, String>> parameters) {
        return mInternalTable
                .lookUp(id, parameters)
                .onErrorResumeNext(throwable -> Single.error(transformToTypedException(throwable)))
                .map(response -> parseResults(response).get(0));
    }

    /**
     * Inserts an entity into a Mobile Service Table
     *
     * @param element The entity to insert
     */
    public Single<E> insert(E element) {
        return insert(element, null);
    }

    /**
     * Inserts an entity into a Mobile Service Table
     *
     * @param element    The entity to insert
     * @param parameters A list of user-defined parameters and values to include in the
     *                   request URI query string
     */
    public Single<E> insert(E element, List<Pair<String, String>> parameters) {
        JsonObject json;
        try {
            json = mClient.getGsonBuilder().create().toJsonTree(element).getAsJsonObject();
        } catch (IllegalArgumentException e) {
            return Single.error(e);
        }

        Class<?> idClazz = getIdPropertyClass(element.getClass());

        if (idClazz != null && !isIntegerClass(idClazz)) {
            json = removeSystemProperties(json);
        }

        return mInternalTable
                .insert(json, parameters)
                .onErrorResumeNext(throwable -> Single.error(transformToTypedException(throwable)))
                .map(response -> {
                    E entity = null;
                    if (response != null) {
                        entity = parseResults(response).get(0);
                        if (entity != null) {
                            copyFields(entity, element);
                            entity = element;
                        }
                    }
                    return entity;
                });
    }

    /**
     * Updates an entity from a Mobile Service Table
     *
     * @param element The entity to update
     */
    public Single<E> update(E element) {
        return update(element, null);
    }

    /**
     * Updates an entity from a Mobile Service Table
     *
     * @param element    The entity to update
     * @param parameters A list of user-defined parameters and values to include in the
     *                   request URI query string
     */
    public Single<E> update(E element, List<Pair<String, String>> parameters) {
        JsonObject json;

        try {
            json = mClient.getGsonBuilder().create().toJsonTree(element).getAsJsonObject();
        } catch (IllegalArgumentException e) {
            return Single.error(e);
        }

        return mInternalTable
                .update(json, parameters)
                .onErrorResumeNext(throwable -> {
                    return Single.error(transformToTypedException(throwable));
                })
                .map(response -> {
                    E entity = parseResults(response).get(0);
                    if (entity != null && element != null) {
                        copyFields(entity, element);
                        entity = element;
                    }
                    return entity;
                });
    }

    /**
     * Undelete an entity from a Mobile Service Table
     *
     * @param element The entity to Undelete
     */
    public Single<E> undelete(E element) {
        return undelete(element, null);
    }

    /**
     * Undelete an entity from a Mobile Service Table
     *
     * @param element    The entity to Undelete
     * @param parameters A list of user-defined parameters and values to include in the
     *                   request URI query string
     */
    public Single<E> undelete(E element, List<Pair<String, String>> parameters) {
        JsonObject json;

        try {
            json = mClient.getGsonBuilder().create().toJsonTree(element).getAsJsonObject();
        } catch (IllegalArgumentException e) {
            return Single.error(e);
        }

        return mInternalTable
                .undelete(json, parameters)
                .onErrorResumeNext(throwable -> Single.error(transformToTypedException(throwable)))
                .map(response -> {
                    E entity = parseResults(response).get(0);
                    if (entity != null && element != null) {
                        copyFields(entity, element);
                        entity = element;
                    }
                    return entity;
                });
    }

    /**
     * Delete an element from a Mobile Service Table
     *
     * @param element The JsonObject to delete
     */
    public Completable delete(E element) {
        return this.delete(element, null);
    }

    /**
     * Delete an entity from a Mobile Service Table
     *
     * @param element    The entity to Undelete
     * @param parameters A list of user-defined parameters and values to include in the
     *                   request URI query string
     */
    public Completable delete(E element, List<Pair<String, String>> parameters) {
        validateId(element);

        JsonObject json;

        try {
            json = mClient.getGsonBuilder().create().toJsonTree(element).getAsJsonObject();
        } catch (IllegalArgumentException e) {
            return Completable.error(e);
        }

        return mInternalTable
                .delete(json, parameters)
                .onErrorResumeNext(throwable -> Completable.error(transformToTypedException(throwable)));
    }

    /**
     * Parses the JSON object to a typed list
     *
     * @param results JSON results
     * @return List of entities
     */
    private List<E> parseResults(JsonElement results) {
        Gson gson = mClient.getGsonBuilder().create();
        return JsonEntityParser.parseResults(results, gson, mClazz);
    }

    /**
     * Copy object field values from source to target object
     *
     * @param source The object to copy the values from
     * @param target The destination object
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     */
    private void copyFields(Object source, Object target) throws IllegalArgumentException, IllegalAccessException {
        if (source != null && target != null) {
            for (Field field : source.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                field.set(target, field.get(source));
            }
        }
    }

    private Throwable transformToTypedException(Throwable exc) {

        if (exc instanceof MobileServicePreconditionFailedExceptionJson) {
            MobileServicePreconditionFailedExceptionJson ex = (MobileServicePreconditionFailedExceptionJson) exc;

            E entity = parseResultsForTypedException(ex);

            return new MobileServicePreconditionFailedException(ex, entity);

        } else if (exc instanceof MobileServiceConflictExceptionJson) {
            MobileServiceConflictExceptionJson ex = (MobileServiceConflictExceptionJson) exc;

            E entity = parseResultsForTypedException(ex);

            return new MobileServiceConflictException(ex, entity);
        }

        return exc;
    }

    private E parseResultsForTypedException(MobileServiceExceptionBase ex) {
        E entity = null;

        try {
            entity = parseResults(ex.getValue()).get(0);
        } catch (Exception e) {
        }

        return entity;
    }
}