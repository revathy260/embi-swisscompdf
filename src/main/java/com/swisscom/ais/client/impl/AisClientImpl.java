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
package com.swisscom.ais.client.impl;

import com.swisscom.ais.client.AisClient;
import com.swisscom.ais.client.AisClientConfiguration;
import com.swisscom.ais.client.AisClientException;
import com.swisscom.ais.client.model.PdfHandle;
import com.swisscom.ais.client.model.SignatureMode;
import com.swisscom.ais.client.model.SignatureResult;
import com.swisscom.ais.client.model.UserData;
import com.swisscom.ais.client.model.VisibleSignatureDefinition;
import com.swisscom.ais.client.rest.RestClient;
import com.swisscom.ais.client.rest.model.*;
import com.swisscom.ais.client.rest.model.pendingreq.AISPendingRequest;
import com.swisscom.ais.client.rest.model.signreq.AISSignRequest;
import com.swisscom.ais.client.rest.model.signresp.AISSignResponse;
import com.swisscom.ais.client.rest.model.signresp.ResultMessage;
import com.swisscom.ais.client.rest.model.signresp.ScExtendedSignatureObject;
import com.swisscom.ais.client.utils.Loggers;
import com.swisscom.ais.client.utils.Trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AisClientImpl implements AisClient {

    private static final Logger logClient = LoggerFactory.getLogger(Loggers.CLIENT);
    private static final Logger logProtocol = LoggerFactory.getLogger(Loggers.CLIENT_PROTOCOL);
    private static final String MISSING_MSISDN_MESSAGE = "<MSISDN> is missing";

    private RestClient restClient;
    private AisClientConfiguration configuration = new AisClientConfiguration();

    // ----------------------------------------------------------------------------------------------------

    @SuppressWarnings("unused")
    public AisClientImpl() {
        // no code here
    }

    public AisClientImpl(RestClient restClient) {
        this.restClient = restClient;
    }

    public AisClientImpl(AisClientConfiguration configuration, RestClient restClient) {
        this.configuration = configuration;
        this.restClient = restClient;
    }

    // ----------------------------------------------------------------------------------------------------

    @Override
    public SignatureResult signWithStaticCertificate(List<PdfHandle> documentHandles, UserData userData) {
        return performSigning(SignatureMode.STATIC,
                              SignatureType.CMS,
                              userData,
                              documentHandles,
                              false,
                              false,
                              false);
    }

    @Override
    @SuppressWarnings("DuplicatedCode")
    public SignatureResult signWithOnDemandCertificate(List<PdfHandle> documentHandles, UserData userData) {
        return performSigning(SignatureMode.ON_DEMAND,
                              SignatureType.CMS,
                              userData,
                              documentHandles,
                              false,
                              true,
                              false,
                              AdditionalProfile.ON_DEMAND_CERTIFICATE);
    }

    @Override
    @SuppressWarnings("DuplicatedCode")
    public SignatureResult signWithOnDemandCertificateAndStepUp(List<PdfHandle> documentHandles, UserData userData) {
        return performSigning(SignatureMode.ON_DEMAND_STEP_UP,
                              SignatureType.CMS,
                              userData,
                              documentHandles,
                              true,
                              true,
                              true,
                              AdditionalProfile.ON_DEMAND_CERTIFICATE,
                              AdditionalProfile.REDIRECT,
                              AdditionalProfile.ASYNC);
    }

    @Override
    public SignatureResult timestamp(List<PdfHandle> documentHandles, UserData userData) {
        return performSigning(SignatureMode.TIMESTAMP,
                              SignatureType.TIMESTAMP,
                              userData,
                              documentHandles,
                              false,
                              false,
                              false,
                              AdditionalProfile.TIMESTAMP);
    }

    @Override
    public void close() throws IOException {
        if (restClient != null) {
            restClient.close();
        }
    }

    // ----------------------------------------------------------------------------------------------------

    @SuppressWarnings("unused")
    public void setRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @SuppressWarnings("unused")
    public AisClientConfiguration getConfiguration() {
        return configuration;
    }

    @SuppressWarnings("unused")
    public void setConfiguration(AisClientConfiguration configuration) {
        this.configuration = configuration;
    }

    // ----------------------------------------------------------------------------------------------------

    private SignatureResult performSigning(SignatureMode signatureMode,
                                           SignatureType signatureType,
                                           UserData userData,
                                           List<PdfHandle> documentHandles,
                                           boolean withStepUp,
                                           boolean withCertificateRequest,
                                           boolean withPolling,
                                           AdditionalProfile... additionalProfiles) {
        Trace trace = new Trace(userData.getTransactionId());
        userData.validateYourself(signatureMode, trace);
        documentHandles.forEach(handle -> handle.validateYourself(trace));
        // prepare documents
        List<PdfDocument> documentsToSign = prepareMultipleDocumentsForSigning(documentHandles, signatureMode, signatureType, userData, trace);
        // start the signature
        AISSignResponse signResponse;
        try {
            try {
                List<AdditionalProfile> preparedAdditionalProfiles = prepareAdditionalProfiles(documentsToSign, additionalProfiles);
                AISSignRequest signRequest = ModelHelper.buildAisSignRequest(documentsToSign, signatureMode, signatureType, userData,
                                                                             preparedAdditionalProfiles, withStepUp, withCertificateRequest);
                signResponse = restClient.requestSignature(signRequest, trace);
            } catch (Exception e) {
                throw new AisClientException("Failed to communicate with the AIS service and obtain the signature(s) - " + trace.getId(), e);
            }
            if (withPolling) {
                if (!checkThatResponseIsPending(signResponse)) {
                    return selectASignatureResultForResponse(signResponse, trace);
                }
                // poll for signature status
                signResponse = pollUntilSignatureIsComplete(signResponse, userData, trace);
            }
            if (!checkThatResponseIsSuccessful(signResponse)) {
                return selectASignatureResultForResponse(signResponse, trace);
            }
            finishDocumentsSigning(documentsToSign, signResponse, signatureMode, trace);
            return SignatureResult.SUCCESS;
        } finally {
            documentsToSign.forEach(PdfDocument::close);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private List<PdfDocument> prepareMultipleDocumentsForSigning(List<PdfHandle> documentHandles,
                                                                 SignatureMode signatureMode,
                                                                 SignatureType signatureType,
                                                                 UserData userData,
                                                                 Trace trace) {
        return documentHandles
            .stream()
            .map(handle -> prepareOneDocumentForSigning(handle, signatureMode, signatureType, userData, trace))
            .collect(Collectors.toList());
    }

    private PdfDocument prepareOneDocumentForSigning(PdfHandle documentHandle,
                                                     SignatureMode signatureMode,
                                                     SignatureType signatureType,
                                                     UserData userData,
                                                     Trace trace) {
        try {
            logClient.info("Preparing {} signing for document: {} - {}",
                           signatureMode.getFriendlyName(),
                           documentHandle.getInputFromFile(),
                           trace.getId());
            FileInputStream fileIn = new FileInputStream(documentHandle.getInputFromFile());
            FileOutputStream fileOut = new FileOutputStream(documentHandle.getOutputToFile());
            VisibleSignatureDefinition signatureDefinition = documentHandle.getVisibleSignatureDefinition();
           
            PdfDocument newDocument = new PdfDocument(documentHandle.getOutputToFile(), fileIn, fileOut, signatureDefinition, trace);
            newDocument.prepareForSigning(documentHandle.getDigestAlgorithm(), signatureType, userData);
            return newDocument;
        } catch (Exception e) {
            throw new AisClientException("Failed to prepare the document [" +
                                         documentHandle.getInputFromFile() + "] for " +
                                         signatureMode.getFriendlyName() + " signing", e);
        }
    }

    private List<AdditionalProfile> prepareAdditionalProfiles(List<PdfDocument> documentsToSign, AdditionalProfile... extraProfiles) {
        List<AdditionalProfile> additionalProfiles = new ArrayList<>();
        if (documentsToSign.size() > 1) {
            additionalProfiles.add(AdditionalProfile.BATCH);
        }
        additionalProfiles.addAll(Arrays.asList(extraProfiles));
        return additionalProfiles;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean checkThatResponseIsSuccessful(AISSignResponse signResponse) {
        return ResponseHelper.responseIsMajorSuccess(signResponse);
    }

    private boolean checkThatResponseIsPending(AISSignResponse signResponse) {
        return ResponseHelper.responseIsAsyncPending(signResponse);
    }

    private boolean checkForConsentUrlInTheResponse(AISSignResponse response, UserData userData, Trace trace) {
        if (ResponseHelper.responseHasStepUpConsentUrl(response)) {
            if (userData.getConsentUrlCallback() != null) {
                userData.getConsentUrlCallback().onConsentUrlReceived(ResponseHelper.getStepUpConsentUrl(response), userData);
            } else {
                logClient.warn("Consent URL was received from AIS, but no consent URL callback was configured " +
                               "(in UserData). This transaction will probably fail - {}", trace.getId());
            }
            return true;
        }
        return false;
    }

    private SignatureResult selectASignatureResultForResponse(AISSignResponse response, Trace trace) {
        if (response == null ||
            response.getSignResponse() == null ||
            response.getSignResponse().getResult() == null ||
            response.getSignResponse().getResult().getResultMajor() == null) {
            throw new AisClientException("Incomplete response received from the AIS service: " + response + " - " + trace.getId());
        }
        ResultMajorCode majorCode = ResultMajorCode.getByUri(response.getSignResponse().getResult().getResultMajor());
        ResultMinorCode minorCode = ResultMinorCode.getByUri(response.getSignResponse().getResult().getResultMinor());
        if (majorCode != null) {
            switch (majorCode) {
                case SUCCESS: {
                    return SignatureResult.SUCCESS;
                }
                case PENDING: {
                    return SignatureResult.USER_TIMEOUT;
                }
                case REQUESTER_ERROR: // falls through
                case SUBSYSTEM_ERROR: {
                    if (minorCode != null) {
                        switch (minorCode) {
                            case SERIAL_NUMBER_MISMATCH:
                                return SignatureResult.SERIAL_NUMBER_MISMATCH;
                            case STEPUP_TIMEOUT:
                                return SignatureResult.USER_TIMEOUT;
                            case STEPUP_CANCEL:
                                return SignatureResult.USER_CANCEL;
                            case INSUFFICIENT_DATA:
                                if (response.getSignResponse().getResult().getResultMessage() != null) {
                                    ResultMessage resultMessage = response.getSignResponse().getResult().getResultMessage();
                                    if (resultMessage.get$() != null && resultMessage.get$().contains(MISSING_MSISDN_MESSAGE)) {
                                        logClient.error(
                                            "The required MSISDN parameter was missing in the request. This can happen sometimes in the context of the"
                                            + " on-demand flow, depending on the user's server configuration. As an alternative, the on-demand with"
                                            + " step-up flow can be used instead.");
                                        return SignatureResult.INSUFFICIENT_DATA_WITH_ABSENT_MSISDN;
                                    }
                                }
                                break;
                            case SERVICE_ERROR:
                                if (response.getSignResponse().getResult().getResultMessage() != null) {
                                    ResultMessageCode messageCode = ResultMessageCode.getByUri(
                                        response.getSignResponse().getResult().getResultMessage().get$());
                                    if (messageCode != null) {
                                        switch (messageCode) {
                                            case INVALID_PASSWORD: // falls through
                                            case INVALID_OTP:
                                                return SignatureResult.USER_AUTHENTICATION_FAILED;
                                        }
                                    }
                                }
                                break;
                        }
                    }
                    break;
                }
            }
        }
        throw new AisClientException("Failure response received from AIS service: " +
                                     ResponseHelper.getResponseResultSummary(response) + " - " + trace.getId());
    }

    private AISSignResponse pollUntilSignatureIsComplete(AISSignResponse signResponse, UserData userData, Trace trace) {
        AISSignResponse localResponse = signResponse;
        try {
            if (checkForConsentUrlInTheResponse(localResponse, userData, trace)) {
                TimeUnit.SECONDS.sleep(configuration.getSignaturePollingIntervalInSeconds());
            }
            for (int round = 0; round < configuration.getSignaturePollingRounds(); round++) {
                logProtocol.debug("Polling for signature status, round {}/{} - {}",
                                  round + 1, configuration.getSignaturePollingRounds(), trace.getId());
                AISPendingRequest pendingRequest = ModelHelper.buildAisPendingRequest(ResponseHelper.getResponseId(localResponse), userData);
                localResponse = restClient.pollForSignatureStatus(pendingRequest, trace);
                checkForConsentUrlInTheResponse(localResponse, userData, trace);
                if (ResponseHelper.responseIsAsyncPending(localResponse)) {
                    TimeUnit.SECONDS.sleep(configuration.getSignaturePollingIntervalInSeconds());
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            throw new AisClientException("Failed to poll AIS for the status of the signature(s) - " + trace.getId(), e);
        }
        return localResponse;
    }

    private void finishDocumentsSigning(List<PdfDocument> documentsToSign, AISSignResponse signResponse,
                                        SignatureMode signatureMode, Trace trace) {
        List<String> base64EncodedCrls = ResponseHelper.getResponseScCrlList(signResponse);
        List<byte[]> crlEntries = null;
        if (base64EncodedCrls.size() > 0) {
            crlEntries = base64EncodedCrls.stream().map(crl -> Base64.getDecoder().decode(crl)).collect(Collectors.toList());
        }
        List<String> base64EncodedOcsps = ResponseHelper.getResponseScOcspList(signResponse);
        List<byte[]> ocspEntries = null;
        if (base64EncodedOcsps.size() > 0) {
            ocspEntries = base64EncodedOcsps.stream().map(ocsp -> Base64.getDecoder().decode(ocsp)).collect(Collectors.toList());
        }

        if (signatureMode == SignatureMode.TIMESTAMP) {
            if (documentsToSign.size() == 1) {
                PdfDocument document = documentsToSign.get(0);
                logClient.info("Finalizing the timestamping for document: {} - {}", document.getName(), trace.getId());
                String base64TimestampToken = signResponse.getSignResponse().getSignatureObject().getTimestamp().getRFC3161TimeStampToken();
                document.finishSignature(Base64.getDecoder().decode(base64TimestampToken), crlEntries, ocspEntries);
            } else {
                for (PdfDocument document : documentsToSign) {
                    logClient.info("Finalizing the timestamping for document: {} - {}", document.getName(), trace.getId());
                    ScExtendedSignatureObject signatureObject = ResponseHelper.getSignatureObjectByDocumentId(document.getId(), signResponse);
                    document.finishSignature(Base64.getDecoder().decode(signatureObject.getTimestamp().getRFC3161TimeStampToken()),
                                             crlEntries, ocspEntries);
                }
            }
        } else {
            if (documentsToSign.size() == 1) {
                PdfDocument document = documentsToSign.get(0);
                logClient.info("Finalizing the signature for document: {} - {}", document.getName(), trace.getId());
                document.finishSignature(
                    Base64.getDecoder().decode(signResponse.getSignResponse().getSignatureObject().getBase64Signature().get$()),
                    crlEntries, ocspEntries);
            } else {
                for (PdfDocument document : documentsToSign) {
                    logClient.info("Finalizing the signature for document: {} - {}", document.getName(), trace.getId());
                    ScExtendedSignatureObject signatureObject = ResponseHelper.getSignatureObjectByDocumentId(document.getId(), signResponse);
                    document.finishSignature(Base64.getDecoder().decode(signatureObject.getBase64Signature().get$()),
                                             crlEntries, ocspEntries);
                }
            }
        }
    }

}
