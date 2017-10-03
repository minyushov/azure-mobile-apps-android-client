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
 * MobileServiceClient.java
 */
package com.microsoft.windowsazure.mobileservices;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;
import com.microsoft.windowsazure.mobileservices.http.HttpConstants;
import com.microsoft.windowsazure.mobileservices.http.MobileServiceConnection;
import com.microsoft.windowsazure.mobileservices.http.MobileServiceHttpClient;
import com.microsoft.windowsazure.mobileservices.http.OkHttpClientFactory;
import com.microsoft.windowsazure.mobileservices.http.OkHttpClientFactoryImpl;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterResponse;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceJsonTable;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;
import com.microsoft.windowsazure.mobileservices.table.serialization.DateSerializer;
import com.microsoft.windowsazure.mobileservices.table.serialization.JsonEntityParser;
import com.microsoft.windowsazure.mobileservices.table.serialization.LongSerializer;
import com.microsoft.windowsazure.mobileservices.util.Pair;

import io.reactivex.Single;

/**
 * Entry point for Microsoft Azure Mobile app interactions
 */
public class MobileServiceClient {
    /**
     * UTF-8 encoding
     */
    public static final String UTF8_ENCODING = "UTF-8";
    /**
     * Custom API Url
     */
    private static final String CUSTOM_API_URL = "api/";

    /**
     * Mobile Service application key
     */
    private String mAppKey;
    /**
     * Mobile Service URL
     */
    private URL mAppUrl;
    /**
     * GsonBuilder used to in JSON Serialization/Deserialization
     */
    private GsonBuilder mGsonBuilder;
    /**
     * AndroidHttpClientFactory used for request execution
     */
    private OkHttpClientFactory mOkHttpClientFactory;

    private static URL normalizeUrl(URL appUrl) {
        URL normalizedAppURL = appUrl;

        if (normalizedAppURL.getPath().isEmpty()) {
            try {
                normalizedAppURL = new URL(appUrl.toString() + "/");
            } catch (MalformedURLException e) {
                // This exception won't happen, since it's just adding a
                // trailing "/" to a valid URL
            }
        }
        return normalizedAppURL;
    }

    /**
     * Constructor for the MobileServiceClient
     *
     * @param client An existing MobileServiceClient
     */
    private MobileServiceClient(MobileServiceClient client) {
        initialize(client.getAppUrl(), client.getAppKey(), client.getGsonBuilder(), client.getOkHttpClientFactory());
    }

    /**
     * Constructor for the MobileServiceClient
     *
     * @param appUrl  Mobile Service URL
     * @throws java.net.MalformedURLException
     */
    public MobileServiceClient(String appUrl, String appKey) throws MalformedURLException {
        this(new URL(appUrl), appKey);
    }

    /**
     * Constructor for the MobileServiceClient
     *
     * @param appUrl  Mobile Service URL
     */
    public MobileServiceClient(URL appUrl, String appKey) {
        GsonBuilder gsonBuilder = createMobileServiceGsonBuilder();
        initialize(appUrl, appKey, gsonBuilder, new OkHttpClientFactoryImpl());
    }

    /**
     * Constructor for the MobileServiceClient
     *
     * @param appUrl  Mobile Service URL
     * @throws java.net.MalformedURLException
     */
    public MobileServiceClient(String appUrl, String appKey, GsonBuilder gsonBuilder) throws MalformedURLException {
        this(new URL(appUrl), appKey, gsonBuilder);
    }

    /**
     * Constructor for the MobileServiceClient
     *
     * @param appUrl  Mobile Service URL
     */
    public MobileServiceClient(URL appUrl, String appKey, GsonBuilder gsonBuilder) {
        initialize(appUrl, appKey, gsonBuilder, new OkHttpClientFactoryImpl());
    }

    /**
     * Creates a GsonBuilder with custom serializers to use with Microsoft Azure
     * Mobile Services
     *
     * @return
     */
    private static GsonBuilder createMobileServiceGsonBuilder() {
        GsonBuilder gsonBuilder = new GsonBuilder();

        // Register custom date serializer/deserializer
        gsonBuilder.registerTypeAdapter(Date.class, new DateSerializer());
        LongSerializer longSerializer = new LongSerializer();
        gsonBuilder.registerTypeAdapter(Long.class, longSerializer);
        gsonBuilder.registerTypeAdapter(long.class, longSerializer);

        gsonBuilder.excludeFieldsWithModifiers(Modifier.FINAL, Modifier.TRANSIENT, Modifier.STATIC);
        gsonBuilder.serializeNulls(); // by default, add null serialization

        return gsonBuilder;
    }

    /**
     * Returns the Mobile Service application key
     */
    public String getAppKey() {
        return mAppKey;
    }

    /**
     * @return The Mobile Service URL
     */
    public URL getAppUrl() {
        return mAppUrl;
    }

    /**
     * Creates a MobileServiceJsonTable
     *
     * @param name Table name
     * @return MobileServiceJsonTable with the given name
     */
    public MobileServiceJsonTable getTable(String name) {
        return new MobileServiceJsonTable(name, this);
    }

    /**
     * Creates a MobileServiceTable
     *
     * @param clazz The class used for table name and data serialization
     * @return MobileServiceTable with the given name
     */
    public <E> MobileServiceTable<E> getTable(Class<E> clazz) {
        return this.getTable(clazz.getSimpleName(), clazz);
    }

    /**
     * Creates a MobileServiceTable
     *
     * @param name  Table name
     * @param clazz The class used for data serialization
     * @return MobileServiceTable with the given name
     */
    public <E> MobileServiceTable<E> getTable(String name, Class<E> clazz) {
        validateClass(clazz);
        return new MobileServiceTable<E>(name, this, clazz);
    }

    /**
     * Invokes a custom API using POST HTTP method
     *
     * @param apiName The API name
     * @param clazz   The API result class
     */
    public <E> Single<E> invokeApi(String apiName, Class<E> clazz) {
        return invokeApi(apiName, null, HttpConstants.PostMethod, null, clazz);
    }

    /**
     * Invokes a custom API using POST HTTP method
     *
     * @param apiName The API name
     * @param body    The object to send as the request body
     * @param clazz   The API result class
     */
    public <E> Single<E> invokeApi(String apiName, Object body, Class<E> clazz) {
        return invokeApi(apiName, body, HttpConstants.PostMethod, null, clazz);
    }

    /**
     * Invokes a custom API
     *
     * @param apiName    The API name
     * @param httpMethod The HTTP Method used to invoke the API
     * @param parameters The query string parameters sent in the request
     * @param clazz      The API result class
     */
    public <E> Single<E> invokeApi(String apiName, String httpMethod, List<Pair<String, String>> parameters, Class<E> clazz) {
        return invokeApi(apiName, null, httpMethod, parameters, clazz);
    }

    /**
     * Invokes a custom API
     *
     * @param apiName    The API name
     * @param body       The object to send as the request body
     * @param httpMethod The HTTP Method used to invoke the API
     * @param parameters The query string parameters sent in the request
     * @param clazz      The API result class
     */
    public <E> Single<E> invokeApi(String apiName, Object body, String httpMethod, List<Pair<String, String>> parameters, Class<E> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz cannot be null");
        }

        JsonElement json = null;
        if (body != null) {
            if (body instanceof JsonElement) {
                json = (JsonElement) body;
            } else {
                json = getGsonBuilder().create().toJsonTree(body);
            }
        }

        return invokeApiInternal(apiName, json, httpMethod, parameters, EnumSet.of(MobileServiceFeatures.TypedApiCall))
                .map(response -> {
                    Class<?> concreteClass = clazz;
                    if (clazz.isArray()) {
                        concreteClass = clazz.getComponentType();
                    }

                    List<?> entities = JsonEntityParser.parseResults(response, getGsonBuilder().create(), concreteClass);

                    if (clazz.isArray()) {
                        @SuppressWarnings("unchecked")
                        E array = (E) Array.newInstance(concreteClass, entities.size());
                        for (int i = 0; i < entities.size(); i++) {
                            Array.set(array, i, entities.get(i));
                        }

                        return array;
                    } else {
                        //noinspection unchecked
                        return (E) entities.get(0);
                    }
                });
    }

    /**
     * Invokes a custom API using POST HTTP method
     *
     * @param apiName The API name
     */
    public Single<JsonElement> invokeApi(String apiName) {
        return invokeApi(apiName, (JsonElement) null);
    }

    /**
     * Invokes a custom API using POST HTTP method
     *
     * @param apiName The API name
     * @param body    The json element to send as the request body
     */
    public Single<JsonElement> invokeApi(String apiName, JsonElement body) {
        return invokeApi(apiName, body, HttpConstants.PostMethod, null);
    }

    /**
     * Invokes a custom API
     *
     * @param apiName    The API name
     * @param httpMethod The HTTP Method used to invoke the API
     * @param parameters The query string parameters sent in the request
     */
    public Single<JsonElement> invokeApi(String apiName, String httpMethod, List<Pair<String, String>> parameters) {
        return invokeApi(apiName, null, httpMethod, parameters);
    }

    /**
     * Invokes a custom API
     *
     * @param apiName    The API name
     * @param body       The json element to send as the request body
     * @param httpMethod The HTTP Method used to invoke the API
     * @param parameters The query string parameters sent in the request
     */
    public Single<JsonElement> invokeApi(String apiName, JsonElement body, String httpMethod, List<Pair<String, String>> parameters) {
        return invokeApiInternal(apiName, body, httpMethod, parameters, EnumSet.of(MobileServiceFeatures.JsonApiCall));
    }

    /**
     * Invokes a custom API
     *
     * @param apiName    The API name
     * @param body       The json element to send as the request body
     * @param httpMethod The HTTP Method used to invoke the API
     * @param parameters The query string parameters sent in the request
     * @param features   The features used in the request
     */
    private Single<JsonElement> invokeApiInternal(String apiName, JsonElement body, String httpMethod, List<Pair<String, String>> parameters, EnumSet<MobileServiceFeatures> features) {

        byte[] content = null;
        if (body != null) {
            try {
                content = body.toString().getBytes(UTF8_ENCODING);
            } catch (UnsupportedEncodingException e) {
                throw new IllegalArgumentException(e);
            }
        }

        List<Pair<String, String>> requestHeaders = new ArrayList<>();
        if (body != null) {
            requestHeaders.add(new Pair<>(HttpConstants.ContentType, MobileServiceConnection.JSON_CONTENTTYPE));
        }

        if (parameters != null && !parameters.isEmpty()) {
            features.add(MobileServiceFeatures.AdditionalQueryParameters);
        }

        return invokeApiInternal(apiName, content, httpMethod, requestHeaders, parameters, features)
                .map(response -> {
                    String responseContent = response.getContent();
                    if (responseContent == null) {
                        return JsonNull.INSTANCE;
                    }
                    return new JsonParser().parse(responseContent);
                });
    }

    /**
     * Invokes a custom API
     *
     * @param apiName        The API name
     * @param content        The byte array to send as the request body
     * @param httpMethod     The HTTP Method used to invoke the API
     * @param requestHeaders The extra headers to send in the request
     * @param parameters     The query string parameters sent in the request
     */
    public Single<ServiceFilterResponse> invokeApi(String apiName, byte[] content, String httpMethod, List<Pair<String, String>> requestHeaders,
                                                             List<Pair<String, String>> parameters) {
        return invokeApiInternal(apiName, content, httpMethod, requestHeaders, parameters, EnumSet.of(MobileServiceFeatures.GenericApiCall));
    }

    /**
     * Invokes a custom API
     *
     * @param apiName        The API name
     * @param content        The byte array to send as the request body
     * @param httpMethod     The HTTP Method used to invoke the API
     * @param requestHeaders The extra headers to send in the request
     * @param parameters     The query string parameters sent in the request
     * @param features       The SDK features used in the request
     */
    private Single<ServiceFilterResponse> invokeApiInternal(String apiName, byte[] content, String httpMethod,
                                                            List<Pair<String, String>> requestHeaders, List<Pair<String, String>> parameters, EnumSet<MobileServiceFeatures> features) {

        if (apiName == null || apiName.trim().equals("")) {
            return Single.error(new IllegalArgumentException("apiName cannot be null"));
        }

        MobileServiceHttpClient httpClient = new MobileServiceHttpClient(this);
        return httpClient.request(CUSTOM_API_URL + apiName, content, httpMethod, requestHeaders, parameters, features);
    }

    /**
     * Validates the class has an id property defined
     *
     * @param clazz
     */
    private <E> void validateClass(Class<E> clazz) {
        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
            throw new IllegalArgumentException("The class type used for creating a MobileServiceTable must be a concrete class");
        }

        int idPropertyCount = 0;
        for (Field field : clazz.getDeclaredFields()) {
            SerializedName serializedName = field.getAnnotation(SerializedName.class);
            if (serializedName != null) {
                if (serializedName.value().equalsIgnoreCase("id")) {
                    idPropertyCount++;
                }
            } else {
                if (field.getName().equalsIgnoreCase("id")) {
                    idPropertyCount++;
                }
            }
        }

        if (idPropertyCount != 1) {
            throw new IllegalArgumentException("The class representing the MobileServiceTable must have a single id property defined");
        }
    }

    /**
     * Creates a MobileServiceConnection
     *
     * @return MobileServiceConnection
     */
    public MobileServiceConnection createConnection() {
        return new MobileServiceConnection(this);
    }

    /**
     * Initializes the MobileServiceClient
     *
     * @param appUrl      Mobile Service URL
     * @param appKey      Mobile Service application key
     * @param gsonBuilder the GsonBuilder used to in JSON Serialization/Deserialization
     */
    private void initialize(URL appUrl, String appKey, GsonBuilder gsonBuilder, OkHttpClientFactory okHttpClientFactory) {
        if (appUrl == null || appUrl.toString().trim().length() == 0) {
            throw new IllegalArgumentException("Invalid Application URL");
        }

        URL normalizedAppURL = normalizeUrl(appUrl);
        mAppUrl = normalizedAppURL;
        mAppKey = appKey;
        mGsonBuilder = gsonBuilder;
        mOkHttpClientFactory = okHttpClientFactory;
    }

    /**
     * Gets the GsonBuilder used to in JSON Serialization/Deserialization
     */
    public GsonBuilder getGsonBuilder() {
        return mGsonBuilder;
    }

    /**
     * Sets the GsonBuilder used to in JSON Serialization/Deserialization
     *
     * @param gsonBuilder The GsonBuilder to set
     */
    public void setGsonBuilder(GsonBuilder gsonBuilder) {
        mGsonBuilder = gsonBuilder;
    }

    /**
     * Registers a JsonSerializer for the specified type
     *
     * @param type       The type to use in the registration
     * @param serializer The serializer to use in the registration
     */
    public <T> void registerSerializer(Type type, JsonSerializer<T> serializer) {
        mGsonBuilder.registerTypeAdapter(type, serializer);
    }

    /**
     * Registers a JsonDeserializer for the specified type
     *
     * @param type         The type to use in the registration
     * @param deserializer The deserializer to use in the registration
     */
    public <T> void registerDeserializer(Type type, JsonDeserializer<T> deserializer) {
        mGsonBuilder.registerTypeAdapter(type, deserializer);
    }

    /**
     * Gets the AndroidHttpClientFactory
     *
     * @return OkHttp Client Factory
     */
    public OkHttpClientFactory getOkHttpClientFactory() {
        return mOkHttpClientFactory;
    }

    /**
     * Sets the AndroidHttpClientFactory
     */
    public void setAndroidHttpClientFactory(OkHttpClientFactory mOkHttpClientFactory) {
        this.mOkHttpClientFactory = mOkHttpClientFactory;
    }
}
