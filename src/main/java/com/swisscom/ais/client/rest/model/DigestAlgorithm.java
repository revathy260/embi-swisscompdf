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

public enum DigestAlgorithm {

    SHA256("SHA-256", "http://www.w3.org/2001/04/xmlenc#sha256"),
    SHA384("SHA-384", "http://www.w3.org/2001/04/xmldsig-more#sha384"),
    SHA512("SHA-512", "http://www.w3.org/2001/04/xmlenc#sha512");

    /**
     * Name of the algorithm (to be used with Java CE / security provider).
     */
    private final String digestAlgorithm;

    /**
     * Uri of the algorithm (to be used in the AIS API).
     */
    private final String digestUri;

    /**
     * Set name and uri of hash algorithm
     *
     * @param digestAlgorithm Name of hash algorithm
     * @param digestUri       Uri of hash algorithm
     */
    DigestAlgorithm(String digestAlgorithm, String digestUri) {
        this.digestAlgorithm = digestAlgorithm;
        this.digestUri = digestUri;
    }

    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    public String getDigestUri() {
        return digestUri;
    }

    public static DigestAlgorithm getByDigestAlgorithm(String algo) {
        for (DigestAlgorithm currentAlgo : values()) {
            if (currentAlgo.getDigestAlgorithm().equalsIgnoreCase(algo)) {
                return currentAlgo;
            }
        }
        throw new AisClientException("Invalid digest algorithm: " + algo);
    }

}