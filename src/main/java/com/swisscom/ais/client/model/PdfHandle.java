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

import com.swisscom.ais.client.rest.model.DigestAlgorithm;
import com.swisscom.ais.client.utils.Trace;

import static com.swisscom.ais.client.utils.Utils.valueNotEmpty;
import static com.swisscom.ais.client.utils.Utils.valueNotNull;

public class PdfHandle {

    private String inputFromFile;

    private String outputToFile;

    private DigestAlgorithm digestAlgorithm = DigestAlgorithm.SHA512;

    private VisibleSignatureDefinition visibleSignatureDefinition = null;

    public String getInputFromFile() {
        return inputFromFile;
    }

    public void setInputFromFile(String inputFromFile) {
        this.inputFromFile = inputFromFile;
    }

    public String getOutputToFile() {
        return outputToFile;
    }

    public void setOutputToFile(String outputToFile) {
        this.outputToFile = outputToFile;
    }

    public DigestAlgorithm getDigestAlgorithm() {
        return digestAlgorithm;
    }

    public void setDigestAlgorithm(DigestAlgorithm digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }

    public VisibleSignatureDefinition getVisibleSignatureDefinition() {
        return visibleSignatureDefinition;
    }

    public void setVisibleSignatureDefinition(VisibleSignatureDefinition definition) {
        this.visibleSignatureDefinition = definition;
    }

    public void validateYourself(Trace trace) {
        valueNotEmpty(inputFromFile, "The inputFromFile cannot be null or empty", trace);
        valueNotEmpty(outputToFile, "The outputToFile cannot be null or empty", trace);
        valueNotNull(digestAlgorithm, "The digest algorithm for a PDF handle cannot be NULL", trace);
    }

}
