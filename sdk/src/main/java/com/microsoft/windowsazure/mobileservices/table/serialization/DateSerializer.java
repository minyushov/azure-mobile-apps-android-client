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
 * DateSerializer.java
 */
package com.microsoft.windowsazure.mobileservices.table.serialization;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Date Serializer/Deserializer to make Mobile Services and Java dates
 * compatible
 */
public class DateSerializer implements JsonSerializer<Date>, JsonDeserializer<Date> {

    /**
     * Deserializes an ISO-8601 formatted date
     */
    public static Date deserialize(String strVal) throws ParseException {

        String s = strVal;

        // Parse the well-formatted date string
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'.'SSSZ");
        dateFormat.setTimeZone(TimeZone.getDefault());

        try {

            String[] splittedByDot = strVal.split("\\.");

            if (splittedByDot.length == 1) {
                //We need to add the miliseconds
                s = s.replace("Z", ".000Z");
            } else {

                String miliseconds = splittedByDot[splittedByDot.length - 1].replace("Z", "");

                if (miliseconds.length() == 1) {
                    miliseconds = miliseconds + "00";
                } else if (miliseconds.length() == 2) {
                    miliseconds = miliseconds + "0";
                }

                s = splittedByDot[0] + "." + miliseconds + "Z";
            }

            // Change Z to +0000 to adapt the string to a format
            // that can be parsed in Java
            s = s.replace("Z", "+0000");

        } catch (IndexOutOfBoundsException e) {
            throw new JsonParseException("Invalid length");
        }

        return dateFormat.parse(s);
    }

    /**
     * Serializes a Date object to an ISO-8601 formatted date string
     */
    public static String serialize(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'.'SSS'Z'", Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(date);
    }

    /**
     * Deserializes a JsonElement containing an ISO-8601 formatted date
     */
    @Override
    public Date deserialize(JsonElement element, Type type, JsonDeserializationContext ctx) throws JsonParseException {
        String strVal = element.getAsString();

        try {
            return deserialize(strVal);
        } catch (ParseException e) {
            throw new JsonParseException(e);
        }

    }

    /**
     * Serializes a Date to a JsonElement containing a ISO-8601 formatted date
     */
    @Override
    public JsonElement serialize(Date date, Type type, JsonSerializationContext ctx) {
        return new JsonPrimitive(serialize(date));
    }
}