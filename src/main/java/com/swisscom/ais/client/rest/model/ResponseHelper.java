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

import com.swisscom.ais.client.AisClientException;
import com.swisscom.ais.client.rest.model.signresp.AISSignResponse;
import com.swisscom.ais.client.rest.model.signresp.Result;
import com.swisscom.ais.client.rest.model.signresp.ScExtendedSignatureObject;
import com.swisscom.ais.client.rest.model.signresp.ScSignatureObjects;

import java.util.ArrayList;
import java.util.List;

public class ResponseHelper {

    public static boolean responseIsAsyncPending(AISSignResponse response) {
        if (response != null && response.getSignResponse() != null && response.getSignResponse().getResult() != null) {
            return ResultMajorCode.PENDING.getUri().equals(response.getSignResponse().getResult().getResultMajor());
        }
        return false;
    }

    public static boolean responseIsMajorSuccess(AISSignResponse response) {
        return response != null &&
               response.getSignResponse() != null &&
               response.getSignResponse().getResult() != null &&
               ResultMajorCode.SUCCESS.getUri().equals(response.getSignResponse().getResult().getResultMajor());
    }

    public static String getResponseResultSummary(AISSignResponse response) {
        Result result = response.getSignResponse().getResult();
        return "Major=[" + result.getResultMajor() + "], "
               + "Minor=[" + result.getResultMinor() + "], "
               + "Message=[" + result.getResultMessage() + ']';
    }

    public static boolean responseHasStepUpConsentUrl(AISSignResponse response) {
        return response != null &&
               response.getSignResponse() != null &&
               response.getSignResponse().getOptionalOutputs() != null &&
               response.getSignResponse().getOptionalOutputs().getScStepUpAuthorisationInfo() != null &&
               response.getSignResponse().getOptionalOutputs().getScStepUpAuthorisationInfo().getScResult() != null &&
               response.getSignResponse().getOptionalOutputs().getScStepUpAuthorisationInfo().getScResult().getScConsentURL() != null;
    }

    public static String getStepUpConsentUrl(AISSignResponse response) {
        return response.getSignResponse().getOptionalOutputs().getScStepUpAuthorisationInfo().getScResult().getScConsentURL();
    }

    public static String getResponseId(AISSignResponse response) {
        return response.getSignResponse().getOptionalOutputs().getAsyncResponseID();
    }

    public static ScExtendedSignatureObject getSignatureObjectByDocumentId(String documentId, AISSignResponse signResponse) {
        ScSignatureObjects signatureObjects = signResponse.getSignResponse().getSignatureObject().getOther().getScSignatureObjects();
        for (ScExtendedSignatureObject seSignatureObject : signatureObjects.getScExtendedSignatureObject()) {
            if (documentId.equals(seSignatureObject.getWhichDocument())) {
                return seSignatureObject;
            }
        }
        throw new AisClientException("Invalid AIS response. Cannot find the extended signature object for document with ID=[" + documentId + "]");
    }

    public static List<String> getResponseScCrlList(AISSignResponse response) {
        List<String> result = new ArrayList<>();
        if (response != null &&
            response.getSignResponse() != null &&
            response.getSignResponse().getOptionalOutputs() != null &&
            response.getSignResponse().getOptionalOutputs().getScRevocationInformation() != null &&
            response.getSignResponse().getOptionalOutputs().getScRevocationInformation().getScCRLs() != null) {

            List<String> crlList = response.getSignResponse().getOptionalOutputs().getScRevocationInformation().getScCRLs().getScCRL();
            if (crlList != null) {
                result.addAll(crlList);
            }
        }
        return result;
    }

    public static List<String> getResponseScOcspList(AISSignResponse response) {
        List<String> result = new ArrayList<>();
        if (response != null &&
            response.getSignResponse() != null &&
            response.getSignResponse().getOptionalOutputs() != null &&
            response.getSignResponse().getOptionalOutputs().getScRevocationInformation() != null &&
            response.getSignResponse().getOptionalOutputs().getScRevocationInformation().getScOCSPs() != null) {

            List<String> ocspList = response.getSignResponse().getOptionalOutputs().getScRevocationInformation().getScOCSPs().getScOCSP();
            if (ocspList != null) {
                result.addAll(ocspList);
            }
        }
        return result;
    }

}
