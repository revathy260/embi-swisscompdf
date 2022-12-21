/*
 * Copyright 2021 Swisscom Trust Services (Schweiz) AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.swisscom.ais.client.model;

/**
 * Implementations of this interface can be passed to {@link com.swisscom.ais.client.AisClient} via a {@link UserData} instance to
 * define the handling mechanism for Step Up consent URLs. The passed in parameters allow for identifying the exact transaction for
 * which this URL needs to be accessed by the end user.
 */
@FunctionalInterface
public interface ConsentUrlCallback {

    void onConsentUrlReceived(String consentUrl, UserData userData);

}
