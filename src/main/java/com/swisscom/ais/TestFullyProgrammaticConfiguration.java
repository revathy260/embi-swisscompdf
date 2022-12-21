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
package com.swisscom.ais;

import com.swisscom.ais.client.AisClientConfiguration;
import com.swisscom.ais.client.impl.AisClientImpl;
import com.swisscom.ais.client.model.PdfHandle;
import com.swisscom.ais.client.model.RevocationInformation;
import com.swisscom.ais.client.model.SignatureResult;
import com.swisscom.ais.client.model.SignatureStandard;
import com.swisscom.ais.client.model.UserData;
import com.swisscom.ais.client.rest.RestClientConfiguration;
import com.swisscom.ais.client.rest.RestClientImpl;
import com.swisscom.ais.client.rest.model.DigestAlgorithm;

import java.io.IOException;
import java.util.Collections;

/**
 * Test that shows how to configure the REST and AIS clients from the code. This can also be switched to configuration via the Spring framework or
 * other similar DI frameworks.
 */
public class TestFullyProgrammaticConfiguration {

    public static void main(String[] args) throws IOException {
        // configuration for the REST client; this is done once per application lifetime
        RestClientConfiguration restConfig = new RestClientConfiguration();
        restConfig.setRestServiceSignUrl("https://ais.swisscom.com/AIS-Server/rs/v1.0/sign");
        restConfig.setRestServicePendingUrl("https://ais.swisscom.com/AIS-Server/rs/v1.0/pending");
        restConfig.setServerCertificateFile("");
        restConfig.setClientKeyFile("/Users/ranger/Programs/rangerinc/lings/embi/ssl/server.key");
        restConfig.setClientKeyPassword("embi2022");
        restConfig.setClientCertificateFile("/Users/ranger/Programs/rangerinc/lings/embi/ssl/embi19sep22.crt");
        System.out.println("before passing restClient");
        System.out.println(restConfig);
        RestClientImpl restClient = new RestClientImpl();
        restClient.setConfiguration(restConfig);

        // then configure the AIS client; this is done once per application lifetime
        AisClientConfiguration aisConfig = new AisClientConfiguration();
        aisConfig.setSignaturePollingIntervalInSeconds(300);
        aisConfig.setSignaturePollingRounds(100);

        try (AisClientImpl aisClient = new AisClientImpl(aisConfig, restClient)) {
            // third, configure a UserData instance with details about this signature
            // this is done for each signature (can also be created once and cached on a per-user basis)
        	System.out.println("inside aisClient");
            System.out.println(restClient);
            UserData userData = new UserData();
            userData.setClaimedIdentityName("ais-90days-trial-OTP");
            userData.setClaimedIdentityKey("OnDemand-Advanced4");
            userData.setDistinguishedName("cn=TEST User, givenname=Max, surname=Maximus, c=US, serialnumber=abcdefabcdefabcdefabcdefabcdef");

            userData.setStepUpLanguage("en");
            userData.setStepUpMessage("Please confirm the signing of the document ais_input_file");
            userData.setStepUpMsisdn("41797776626");

            userData.setSignatureReason("For testing purposes");
            userData.setSignatureLocation("Topeka, Kansas");
            userData.setSignatureContactInfo("test@test.com");

            userData.setSignatureStandard(SignatureStandard.PDF);

            userData.setConsentUrlCallback((consentUrl, userData1) -> System.out.println("Consent URL: " + consentUrl));

            // fourth, populate a PdfHandle with details about the document to be signed. More than one PdfHandle can be given
            PdfHandle document = new PdfHandle();
            document.setInputFromFile("/Users/ranger/Documents/ais_input_file.pdf");
            document.setOutputToFile("/Users/ranger/Documents/ais_output_file.pdf");
            document.setDigestAlgorithm(DigestAlgorithm.SHA512);
            System.out.println("userdata dai "+userData);
            // finally, do the signature
            SignatureResult result = aisClient.signWithOnDemandCertificateAndStepUp(Collections.singletonList(document), userData);
            // SignatureResult result = aisClient.signWithOnDemandCertificate(Collections.singletonList(document), userData);
            if (result == SignatureResult.SUCCESS) {
                // yay!
            	System.out.println("success");
            } else {
            	System.out.println(result);
            }
        }
    }

}
