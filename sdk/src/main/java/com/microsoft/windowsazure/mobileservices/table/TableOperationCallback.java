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
 * TableOperationCallback.java
 */
package com.microsoft.windowsazure.mobileservices.table;

import com.microsoft.windowsazure.mobileservices.http.ServiceFilterResponse;

/**
 * Callback used after a TableOperation is executed
 *
 * @param <E> The table's entity
 */
public interface TableOperationCallback<E> {
    /**
     * Method to call if the operation finishes successfully
     *
     * @param entity    The obtained entity
     * @param exception An exception representing the error, in case there was one
     * @param response  Response object
     */
    void onCompleted(E entity, Exception exception, ServiceFilterResponse response);
}