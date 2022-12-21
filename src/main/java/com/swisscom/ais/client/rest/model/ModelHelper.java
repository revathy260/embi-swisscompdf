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
package com.swisscom.ais.client.rest.model;

import com.swisscom.ais.client.impl.PdfDocument;
import com.swisscom.ais.client.model.RevocationInformation;
import com.swisscom.ais.client.model.SignatureMode;
import com.swisscom.ais.client.model.SignatureStandard;
import com.swisscom.ais.client.model.UserData;
import com.swisscom.ais.client.rest.model.pendingreq.AISPendingRequest;
import com.swisscom.ais.client.rest.model.pendingreq.AsyncPendingRequest;
import com.swisscom.ais.client.rest.model.signreq.*;
import com.swisscom.ais.client.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModelHelper {

    private static final String SWISSCOM_BASIC_PROFILE = "http://ais.swisscom.ch/1.1";

    public static AISSignRequest buildAisSignRequest(List<PdfDocument> documents,
                                                     SignatureMode signatureMode,
                                                     SignatureType signatureType,
                                                     UserData userData,
                                                     List<AdditionalProfile> additionalProfiles,
                                                     boolean withStepUp,
                                                     boolean withCertificateRequest) {
        // Input documents --------------------------------------------------------------------------------
        List<DocumentHash> documentHashes = new ArrayList<>();
        for (PdfDocument document : documents) {
            DocumentHash newDocumentHash = new DocumentHash();
            newDocumentHash.setId(document.getId());
            newDocumentHash.setDsigDigestMethod(new DsigDigestMethod().withAlgorithm(document.getDigestAlgorithm().getDigestUri()));
            newDocumentHash.setDsigDigestValue(document.getBase64HashToSign());
            documentHashes.add(newDocumentHash);
        }
        InputDocuments inputDocuments = new InputDocuments();
        inputDocuments.setDocumentHash(documentHashes);

        // Optional inputs --------------------------------------------------------------------------------
        AddTimestamp addTimestamp = null;
        if (userData.isAddTimestamp()) {
            addTimestamp = new AddTimestamp();
            addTimestamp.setType(SignatureType.TIMESTAMP.getUri());
        }

        ClaimedIdentity claimedIdentity = new ClaimedIdentity();
        if (signatureMode != SignatureMode.TIMESTAMP && Utils.notEmpty(userData.getClaimedIdentityKey())) {
            claimedIdentity.setName(userData.getClaimedIdentityName() + ":" + userData.getClaimedIdentityKey());
        } else {
            claimedIdentity.setName(userData.getClaimedIdentityName());
        }

        ScCertificateRequest certificateRequest = null;
        if (withCertificateRequest) {
            certificateRequest = new ScCertificateRequest();
            certificateRequest.setScDistinguishedName(userData.getDistinguishedName());
        }

        if (withStepUp) {
            if (certificateRequest == null) {
                certificateRequest = new ScCertificateRequest();
            }

            ScPhone phone = new ScPhone();
            phone.setScLanguage(userData.getStepUpLanguage());
            phone.setScMSISDN(userData.getStepUpMsisdn());
            phone.setScMessage(userData.getStepUpMessage());
            phone.setScSerialNumber(userData.getStepUpSerialNumber());

            ScStepUpAuthorisation stepUpAuthorisation = new ScStepUpAuthorisation();
            stepUpAuthorisation.setScPhone(phone);

            certificateRequest.setScStepUpAuthorisation(stepUpAuthorisation);
        }

        ScAddRevocationInformation addRevocationInformation = new ScAddRevocationInformation();
        if (userData.getAddRevocationInformation() == RevocationInformation.DEFAULT) {
            if (signatureMode == SignatureMode.TIMESTAMP) {
                addRevocationInformation.setType(RevocationInformation.BOTH.getValue());
            } else {
                addRevocationInformation.setType(null);
            }
        } else {
            addRevocationInformation.setType(userData.getAddRevocationInformation().getValue());
        }

        OptionalInputs optionalInputs = new OptionalInputs();
        optionalInputs.setAddTimestamp(addTimestamp);
        optionalInputs.setAdditionalProfile(additionalProfiles.stream().map(AdditionalProfile::getUri).collect(Collectors.toList()));
        optionalInputs.setClaimedIdentity(claimedIdentity);
        optionalInputs.setSignatureType(signatureType.getUri());
        optionalInputs.setScCertificateRequest(certificateRequest);
        optionalInputs.setScAddRevocationInformation(null);
        optionalInputs.setScAddRevocationInformation(addRevocationInformation);
        if (signatureMode != SignatureMode.TIMESTAMP && userData.getSignatureStandard() != SignatureStandard.DEFAULT) {
            optionalInputs.setScSignatureStandard(userData.getSignatureStandard().getValue());
        }

        // Sign request --------------------------------------------------------------------------------
        SignRequest request = new SignRequest();
        request.setRequestID(Utils.generateRequestId());
        request.setProfile(SWISSCOM_BASIC_PROFILE);
        request.setInputDocuments(inputDocuments);
        request.setOptionalInputs(optionalInputs);

        AISSignRequest requestWrapper = new AISSignRequest();
        requestWrapper.setSignRequest(request);
        return requestWrapper;
    }

    public static AISPendingRequest buildAisPendingRequest(String responseId, UserData userData) {
        com.swisscom.ais.client.rest.model.pendingreq.ClaimedIdentity claimedIdentity =
            new com.swisscom.ais.client.rest.model.pendingreq.ClaimedIdentity();
        claimedIdentity.setName(userData.getClaimedIdentityName());

        com.swisscom.ais.client.rest.model.pendingreq.OptionalInputs optionalInputs =
            new com.swisscom.ais.client.rest.model.pendingreq.OptionalInputs();
        optionalInputs.setAsyncResponseID(responseId);
        optionalInputs.setClaimedIdentity(claimedIdentity);

        AsyncPendingRequest request = new AsyncPendingRequest();
        request.setProfile(SWISSCOM_BASIC_PROFILE);
        request.setOptionalInputs(optionalInputs);

        AISPendingRequest requestWrapper = new AISPendingRequest();
        requestWrapper.setAsyncPendingRequest(request);
        return requestWrapper;
    }

}
