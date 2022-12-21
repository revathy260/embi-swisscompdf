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

import com.swisscom.ais.client.AisClientException;
import com.swisscom.ais.client.utils.ConfigurationProvider;
import com.swisscom.ais.client.utils.ConfigurationProviderPropertiesImpl;
import com.swisscom.ais.client.utils.Trace;
import com.swisscom.ais.client.utils.Utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

import static com.swisscom.ais.client.utils.Utils.getStringNotNull;
import static com.swisscom.ais.client.utils.Utils.valueNotNull;

@SuppressWarnings("unused")
public class UserData {

    private String transactionId;

    private String claimedIdentityName;
    private String claimedIdentityKey;
    private String distinguishedName;

    private String stepUpLanguage;
    private String stepUpMsisdn;
    private String stepUpMessage;
    private String stepUpSerialNumber;

    private String signatureName;
    private String signatureReason;
    private String signatureLocation;
    private String signatureContactInfo;

    private ConsentUrlCallback consentUrlCallback;

    private boolean addTimestamp = true;
    private RevocationInformation addRevocationInformation = RevocationInformation.DEFAULT;
    private SignatureStandard signatureStandard = SignatureStandard.DEFAULT;

    // ----------------------------------------------------------------------------------------------------

    public UserData() {
        setTransactionIdToRandomUuid();
    }

    public UserData(String transactionId) {
        this.transactionId = transactionId;
    }

    // ----------------------------------------------------------------------------------------------------

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public void setTransactionIdToRandomUuid() {
        this.transactionId = UUID.randomUUID().toString();
    }

    public String getClaimedIdentityName() {
        return claimedIdentityName;
    }

    public void setClaimedIdentityName(String claimedIdentityName) {
        this.claimedIdentityName = claimedIdentityName;
    }

    public String getStepUpLanguage() {
        return stepUpLanguage;
    }

    public void setStepUpLanguage(String stepUpLanguage) {
        this.stepUpLanguage = stepUpLanguage;
    }

    public String getStepUpMsisdn() {
        return stepUpMsisdn;
    }

    public void setStepUpMsisdn(String stepUpMsisdn) {
        this.stepUpMsisdn = stepUpMsisdn;
    }

    public String getStepUpMessage() {
        return stepUpMessage;
    }

    public void setStepUpMessage(String stepUpMessage) {
        this.stepUpMessage = stepUpMessage;
    }

    public String getDistinguishedName() {
        return distinguishedName;
    }

    public void setDistinguishedName(String distinguishedName) {
        this.distinguishedName = distinguishedName;
    }

    public ConsentUrlCallback getConsentUrlCallback() {
        return consentUrlCallback;
    }

    public void setConsentUrlCallback(ConsentUrlCallback consentUrlCallback) {
        this.consentUrlCallback = consentUrlCallback;
    }

    public boolean isAddTimestamp() {
        return addTimestamp;
    }

    public void setAddTimestamp(boolean addTimestamp) {
        this.addTimestamp = addTimestamp;
    }

    public RevocationInformation getAddRevocationInformation() {
        return addRevocationInformation;
    }

    public void setAddRevocationInformation(RevocationInformation addRevocationInformation) {
        this.addRevocationInformation = addRevocationInformation;
    }

    public SignatureStandard getSignatureStandard() {
        return signatureStandard;
    }

    public void setSignatureStandard(SignatureStandard signatureStandard) {
        this.signatureStandard = signatureStandard;
    }

    public String getClaimedIdentityKey() {
        return claimedIdentityKey;
    }

    public void setClaimedIdentityKey(String claimedIdentityKey) {
        this.claimedIdentityKey = claimedIdentityKey;
    }

    public String getStepUpSerialNumber() {
        return stepUpSerialNumber;
    }

    public void setStepUpSerialNumber(String stepUpSerialNumber) {
        this.stepUpSerialNumber = stepUpSerialNumber;
    }

    public String getSignatureName() {
        return signatureName;
    }

    public void setSignatureName(String signatureName) {
        this.signatureName = signatureName;
    }

    public String getSignatureReason() {
        return signatureReason;
    }

    public void setSignatureReason(String signatureReason) {
        this.signatureReason = signatureReason;
    }

    public String getSignatureLocation() {
        return signatureLocation;
    }

    public void setSignatureLocation(String signatureLocation) {
        this.signatureLocation = signatureLocation;
    }

    public String getSignatureContactInfo() {
        return signatureContactInfo;
    }

    public void setSignatureContactInfo(String signatureContactInfo) {
        this.signatureContactInfo = signatureContactInfo;
    }

    // ----------------------------------------------------------------------------------------------------

    public void setFromPropertiesClasspathFile(String fileName) {
        Properties properties;
        try {
            properties = new Properties();
            properties.load(this.getClass().getResourceAsStream(fileName));
        } catch (IOException exception) {
            throw new AisClientException("Failed to load user data properties from classpath file: [" + fileName + "]", exception);
        }
        setFromProperties(properties);
    }

    public void setFromPropertiesFile(String fileName) {
        Properties properties;
        try {
            properties = new Properties();
            properties.load(new FileInputStream(fileName));
        } catch (IOException exception) {
            throw new AisClientException("Failed to load user data properties from file: [" + fileName + "]", exception);
        }
        setFromProperties(properties);
    }

    public void setFromProperties(Properties properties) {
        setFromConfigurationProvider(new ConfigurationProviderPropertiesImpl(properties));
    }

    public void setFromConfigurationProvider(ConfigurationProvider provider) {
        claimedIdentityName = getStringNotNull(provider, "signature.claimedIdentityName");
        claimedIdentityKey = provider.getProperty("signature.claimedIdentityKey");
        stepUpLanguage = provider.getProperty("signature.stepUp.language");
        stepUpMsisdn = provider.getProperty("signature.stepUp.msisdn");
        stepUpMessage = provider.getProperty("signature.stepUp.message");
        stepUpSerialNumber = provider.getProperty("signature.stepUp.serialNumber");
        distinguishedName = getStringNotNull(provider, "signature.distinguishedName");
        signatureName = provider.getProperty("signature.name");
        signatureReason = provider.getProperty("signature.reason");
        signatureLocation = provider.getProperty("signature.location");
        signatureContactInfo = provider.getProperty("signature.contactInfo");
        String value = provider.getProperty("signature.standard");
        if (Utils.notEmpty(value)) {
            signatureStandard = SignatureStandard.getByValue(value);
        }
        value = provider.getProperty("signature.revocationInformation");
        if (Utils.notEmpty(value)) {
            addRevocationInformation = RevocationInformation.getByValue(value);
        }
        value = provider.getProperty("signature.addTimestamp");
        if (Utils.notEmpty(value)) {
            addTimestamp = Boolean.parseBoolean(value);
        }
    }

    public void validateYourself(SignatureMode signatureMode, Trace trace) {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new AisClientException("The user data's transactionId cannot be null or empty. For example, you can set it to a new UUID "
                                         + "or to any other value that is unique between requests. This helps with traceability in the logs "
                                         + "generated by the AIS client");
        }
        valueNotNull(claimedIdentityName, "The claimedIdentityName must be provided", trace);
        switch (signatureMode) {
            case STATIC:
                break;
            case ON_DEMAND:
                break;
            case ON_DEMAND_STEP_UP:
                valueNotNull(stepUpLanguage, "The stepUpLanguage must be provided", trace);
                valueNotNull(stepUpMessage, "The stepUpMessage must be provided", trace);
                valueNotNull(stepUpMsisdn, "The stepUpMsisdn must be provided", trace);
                break;
            case TIMESTAMP:
                break;
            default:
                throw new IllegalArgumentException("Invalid signature mode: " + signatureMode);
        }
    }

}
