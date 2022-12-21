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
package com.swisscom.ais.client;

import com.swisscom.ais.client.utils.ConfigurationProvider;
import com.swisscom.ais.client.utils.ConfigurationProviderPropertiesImpl;

import java.io.IOException;
import java.util.Properties;

import static com.swisscom.ais.client.utils.Utils.getIntNotNull;

@SuppressWarnings("unused")
public class AisClientConfiguration {

    private int signaturePollingIntervalInSeconds = 10;

    private int signaturePollingRounds = 10;

    public int getSignaturePollingIntervalInSeconds() {
        return signaturePollingIntervalInSeconds;
    }

    public void setSignaturePollingIntervalInSeconds(int signaturePollingIntervalInSeconds) {
        if (signaturePollingIntervalInSeconds < 1 || signaturePollingIntervalInSeconds > 300) {
            throw new AisClientException("The signaturePollingIntervalInSeconds parameter of the AIS client "
                                         + "configuration must be between 1 and 300 seconds");
        }
        this.signaturePollingIntervalInSeconds = signaturePollingIntervalInSeconds;
    }

    public int getSignaturePollingRounds() {
        return signaturePollingRounds;
    }

    public void setSignaturePollingRounds(int signaturePollingRounds) {
        if (signaturePollingRounds < 1 || signaturePollingRounds > 100) {
            throw new AisClientException("The signaturePollingRounds parameter of the AIS client "
                                         + "configuration must be between 1 and 100 rounds");
        }
        this.signaturePollingRounds = signaturePollingRounds;
    }

    // ----------------------------------------------------------------------------------------------------

    @SuppressWarnings("unused")
    public void setFromPropertiesClasspathFile(String fileName) {
        Properties properties;
        try {
            properties = new Properties();
            properties.load(this.getClass().getResourceAsStream(fileName));
        } catch (IOException exception) {
            throw new AisClientException("Failed to load AIS client properties from classpath file: [" + fileName + "]", exception);
        }
        setFromProperties(properties);
    }

    public void setFromProperties(Properties properties) {
        setFromConfigurationProvider(new ConfigurationProviderPropertiesImpl(properties));
    }

    public void setFromConfigurationProvider(ConfigurationProvider provider) {
        setSignaturePollingIntervalInSeconds(getIntNotNull(provider, "client.poll.intervalInSeconds"));
        setSignaturePollingRounds(getIntNotNull(provider, "client.poll.rounds"));
    }

}
