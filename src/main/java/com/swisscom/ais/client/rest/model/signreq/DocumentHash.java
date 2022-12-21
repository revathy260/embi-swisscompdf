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
package com.swisscom.ais.client.rest.model.signreq;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "@ID",
    "dsig.DigestMethod",
    "dsig.DigestValue"
})
public class DocumentHash {

    @JsonProperty("@ID")
    private String id;
    @JsonProperty("dsig.DigestMethod")
    private DsigDigestMethod dsigDigestMethod;
    @JsonProperty("dsig.DigestValue")
    private String dsigDigestValue;

    @JsonProperty("@ID")
    public String getId() {
        return id;
    }

    @JsonProperty("@ID")
    public void setId(String id) {
        this.id = id;
    }

    public DocumentHash withId(String id) {
        this.id = id;
        return this;
    }

    @JsonProperty("dsig.DigestMethod")
    public DsigDigestMethod getDsigDigestMethod() {
        return dsigDigestMethod;
    }

    @JsonProperty("dsig.DigestMethod")
    public void setDsigDigestMethod(DsigDigestMethod dsigDigestMethod) {
        this.dsigDigestMethod = dsigDigestMethod;
    }

    public DocumentHash withDsigDigestMethod(DsigDigestMethod dsigDigestMethod) {
        this.dsigDigestMethod = dsigDigestMethod;
        return this;
    }

    @JsonProperty("dsig.DigestValue")
    public String getDsigDigestValue() {
        return dsigDigestValue;
    }

    @JsonProperty("dsig.DigestValue")
    public void setDsigDigestValue(String dsigDigestValue) {
        this.dsigDigestValue = dsigDigestValue;
    }

    public DocumentHash withDsigDigestValue(String dsigDigestValue) {
        this.dsigDigestValue = dsigDigestValue;
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(DocumentHash.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("id");
        sb.append('=');
        sb.append(((this.id == null)?"<null>":this.id));
        sb.append(',');
        sb.append("dsigDigestMethod");
        sb.append('=');
        sb.append(((this.dsigDigestMethod == null)?"<null>":this.dsigDigestMethod));
        sb.append(',');
        sb.append("dsigDigestValue");
        sb.append('=');
        sb.append(((this.dsigDigestValue == null)?"<null>":this.dsigDigestValue));
        sb.append(',');
        if (sb.charAt((sb.length()- 1)) == ',') {
            sb.setCharAt((sb.length()- 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

}
