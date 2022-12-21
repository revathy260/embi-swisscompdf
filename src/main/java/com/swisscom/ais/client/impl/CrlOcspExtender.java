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

import com.swisscom.ais.client.AisClientException;
import com.swisscom.ais.client.rest.model.SignatureType;
import com.swisscom.ais.client.utils.Loggers;
import com.swisscom.ais.client.utils.Trace;
import com.swisscom.ais.client.utils.Utils;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSUpdateInfo;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.util.*;
import java.util.stream.Collectors;

public class CrlOcspExtender {

    private static final Logger logPdfProcessing = LoggerFactory.getLogger(Loggers.PDF_PROCESSING);

    private static final COSName COSNAME_DSS;
    private static final COSName COSNAME_VRI;
    private static final COSName COSNAME_OCSPS;
    private static final COSName COSNAME_OCSP_SINGLE;
    private static final COSName COSNAME_CRLS;
    private static final COSName COSNAME_CRL_SINGLE;
    private static final COSName COSNAME_CERTS;
    private static final COSName COSNAME_CERT_SINGLE;

    static {
        COSNAME_DSS = COSName.getPDFName("DSS");
        COSNAME_VRI = COSName.getPDFName("VRI");
        COSNAME_OCSPS = COSName.getPDFName("OCSPs");
        COSNAME_OCSP_SINGLE = COSName.getPDFName("OCSP");
        COSNAME_CRLS = COSName.getPDFName("CRLs");
        COSNAME_CRL_SINGLE = COSName.getPDFName("CRL");
        COSNAME_CERTS = COSName.getPDFName("Certs");
        COSNAME_CERT_SINGLE = COSName.getPDFName("Cert");
    }

    // ----------------------------------------------------------------------------------------------------

    private final Trace trace;
    private final PDDocument pdDocument;
    private final byte[] documentBytes;

    public CrlOcspExtender(PDDocument pdDocument, byte[] documentBytes, Trace trace) {
        this.pdDocument = pdDocument;
        this.documentBytes = documentBytes;
        this.trace = trace;
    }

    public void extendPdfWithCrlAndOcsp(List<byte[]> crlEntries, List<byte[]> ocspEntries) {
        try {
            PDDocumentCatalog pdDocumentCatalog = pdDocument.getDocumentCatalog();
            COSDictionary cosDocumentCatalog = pdDocumentCatalog.getCOSObject();
            cosDocumentCatalog.setNeedToBeUpdated(true);

            addExtensions(pdDocumentCatalog);

            List<byte[]> encodedCrlEntries = getCrlEncodedForm(crlEntries);
            List<byte[]> encodedOcspEntries = getOcspEncodedForm(ocspEntries);

            Map<COSName, ValidationData> validationMap = new HashMap<>();

            PDSignature lastSignature = getLastRelevantSignature(pdDocument);
            if (lastSignature == null) {
                throw new AisClientException("Cannot extend PDF with CRL and OCSP data. No signature was found in the PDF");
            }

            ValidationData vData = new ValidationData();
            for (byte[] ocsp : encodedOcspEntries) {
                vData.ocsps.add(buildOCSPResponse(ocsp));
            }
            vData.crls.addAll(encodedCrlEntries);
            validationMap.put(COSName.getPDFName(getSignatureHashKey(lastSignature)), vData);

            // ----------------------------------------------------------------------------------------------------
            COSDictionary pdDssDict = getOrCreateDictionaryEntry(COSDictionary.class, cosDocumentCatalog, COSNAME_DSS);
            COSDictionary pdVriMapDict = getOrCreateDictionaryEntry(COSDictionary.class, pdDssDict, COSNAME_VRI);
            COSArray ocsps = getOrCreateDictionaryEntry(COSArray.class, pdDssDict, COSNAME_OCSPS);
            COSArray crls = getOrCreateDictionaryEntry(COSArray.class, pdDssDict, COSNAME_CRLS);
            COSArray certs = getOrCreateDictionaryEntry(COSArray.class, pdDssDict, COSNAME_CERTS);

            for (Map.Entry<COSName, ValidationData> validationEntry : validationMap.entrySet()) {
                ValidationData validationData = validationEntry.getValue();
                COSDictionary vriDict = new COSDictionary();
                COSArray vriOcsps = new COSArray();
                COSArray vriCrls = new COSArray();
                COSArray vriCerts = new COSArray();
                for (byte[] ocspBytes : validationData.ocsps) {
                    COSStream stream = createStream(ocspBytes);
                    ocsps.add(stream);
                    vriOcsps.add(stream);
                }
                for (byte[] crlBytes : validationData.crls) {
                    COSStream stream = createStream(crlBytes);
                    crls.add(stream);
                    vriCrls.add(stream);
                }
                for (byte[] certBytes : validationData.certs) {
                    COSStream stream = createStream(certBytes);
                    certs.add(stream);
                    vriCerts.add(stream);
                }
                if (vriOcsps.size() > 0) {
                    vriDict.setItem(COSNAME_OCSP_SINGLE, vriOcsps);
                }
                if (vriCrls.size() > 0) {
                    vriDict.setItem(COSNAME_CRL_SINGLE, vriCrls);
                }
                if (vriCerts.size() > 0) {
                    vriDict.setItem(COSNAME_CERT_SINGLE, vriCerts);
                }
                pdVriMapDict.setItem(validationEntry.getKey(), vriDict);
            }

            if (ocsps.size() > 0) {
                pdDssDict.setItem(COSNAME_OCSPS, ocsps);
            } else {
                pdDssDict.removeItem(COSNAME_OCSPS);
            }
            if (crls.size() > 0) {
                pdDssDict.setItem(COSNAME_CRLS, crls);
            } else {
                pdDssDict.removeItem(COSNAME_CRLS);
            }
            if (certs.size() > 0) {
                pdDssDict.setItem(COSNAME_CERTS, certs);
            } else {
                pdDssDict.removeItem(COSNAME_CERTS);
            }
            pdDssDict.setItem(COSNAME_VRI, pdVriMapDict);
            cosDocumentCatalog.setItem(COSNAME_DSS, pdDssDict);
        } catch (Exception e) {
            throw new AisClientException("An error occurred processing the signature and embedding CRL and OCSP data", e);
        }
    }

    // ----------------------------------------------------------------------------------------------------

    private PDSignature getLastRelevantSignature(PDDocument document) throws IOException {
        SortedMap<Integer, PDSignature> sortedMap = new TreeMap<>();
        for (PDSignature signature : document.getSignatureDictionaries()) {
            int sigOffset = signature.getByteRange()[1];
            sortedMap.put(sigOffset, signature);
        }
        if (sortedMap.size() > 0) {
            PDSignature lastSignature = sortedMap.get(sortedMap.lastKey());
            COSBase type = lastSignature.getCOSObject().getItem(COSName.TYPE);
            if (type.equals(COSName.SIG) || type.equals(COSName.DOC_TIME_STAMP)) {
                return lastSignature;
            }
        }
        return null;
    }

    private void addExtensions(PDDocumentCatalog catalog) {
        COSDictionary dssExtensions = new COSDictionary();
        dssExtensions.setDirect(true);
        catalog.getCOSObject().setItem("Extensions", dssExtensions);

        COSDictionary adbeExtension = new COSDictionary();
        adbeExtension.setDirect(true);
        dssExtensions.setItem("ADBE", adbeExtension);

        adbeExtension.setName("BaseVersion", "1.7");
        adbeExtension.setInt("ExtensionLevel", 5);

        catalog.setVersion("1.7");
    }

    private List<byte[]> getCrlEncodedForm(List<byte[]> crlEntries) {
        if (crlEntries == null) {
            return Collections.emptyList();
        }
        return crlEntries.stream().map(crl -> {
            try {
                X509CRL x509crl = (X509CRL) CertificateFactory.getInstance("X.509").generateCRL(new ByteArrayInputStream(crl));
                if (logPdfProcessing.isDebugEnabled()) {
                    String message = "\nEmbedding CRL..."
                                     + "\nIssuer DN                   : " + x509crl.getIssuerDN()
                                     + "\nThis update                 : " + x509crl.getThisUpdate()
                                     + "\nNext update                 : " + x509crl.getNextUpdate()
                                     + "\nNo. of revoked certificates : " + ((x509crl.getRevokedCertificates() == null) ?
                                                                             "0" : x509crl.getRevokedCertificates().size());
                    logPdfProcessing.debug(message + " - " + trace.getId());
                }
                return x509crl.getEncoded();
            } catch (Exception e) {
                throw new AisClientException("Failed to generate X509CRL from CRL content received from AIS", e);
            }
        }).collect(Collectors.toList());
    }

    private List<byte[]> getOcspEncodedForm(List<byte[]> ocspEntries) {
        if (ocspEntries == null) {
            return Collections.emptyList();
        }
        return ocspEntries.stream().map(ocsp -> {
            try {
                OCSPResp ocspResp = new OCSPResp(new ByteArrayInputStream(ocsp));
                BasicOCSPResp basicResp = (BasicOCSPResp) ocspResp.getResponseObject();
                if (logPdfProcessing.isDebugEnabled()) {
                    String certificateId = basicResp.getResponses()[0].getCertID().getSerialNumber().toString() + " (" +
                                           basicResp.getResponses()[0].getCertID().getSerialNumber().toString(16).toUpperCase() + ")";
                    String message = "\nEmbedding OCSP Response..."
                                     + "\nStatus                : " + ((ocspResp.getStatus() == 0) ? "GOOD" : "BAD")
                                     + "\nProduced at           : " + basicResp.getProducedAt()
                                     + "\nThis update           : " + basicResp.getResponses()[0].getThisUpdate()
                                     + "\nNext update           : " + basicResp.getResponses()[0].getNextUpdate()
                                     + "\nX509 Cert issuer      : " + basicResp.getCerts()[0].getIssuer()
                                     + "\nX509 Cert subject     : " + basicResp.getCerts()[0].getSubject()
                                     + "\nCertificate ID        : " + certificateId;
                    logPdfProcessing.debug(message + " - " + trace.getId());
                }
                return basicResp.getEncoded(); // Add Basic OCSP Response to Collection (ASN.1 encoded representation of this object)
            } catch (Exception e) {
                throw new AisClientException("Failed to generate X509CRL from CRL content received from AIS", e);
            }
        }).collect(Collectors.toList());

    }

    /**
     * Gets or creates a dictionary entry. If existing checks for the type and sets need to be
     * updated.
     *
     * @param clazz  the class of the dictionary entry, must implement COSUpdateInfo
     * @param parent where to find the element
     * @param name   of the element
     * @return a Element of given class, new or existing
     * @throws IOException when the type of the element is wrong
     */
    private static <T extends COSBase & COSUpdateInfo> T getOrCreateDictionaryEntry(Class<T> clazz,
                                                                                    COSDictionary parent,
                                                                                    COSName name) throws IOException {
        T result;
        COSBase element = parent.getDictionaryObject(name);
        if (clazz.isInstance(element)) {
            result = clazz.cast(element);
            result.setNeedToBeUpdated(true);
        } else if (element != null) {
            throw new IOException("Element " + name + " from dictionary is not of type " + clazz.getCanonicalName());
        } else {
            try {
                result = clazz.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                throw new IOException("Failed to create new instance of " + clazz.getCanonicalName(), ex);
            }
            result.setDirect(false);
            parent.setItem(name, result);
        }
        return result;
    }

    private COSStream createStream(byte[] data) throws IOException {
        COSStream stream = pdDocument.getDocument().createCOSStream();
        try (OutputStream unfilteredStream = stream.createOutputStream(COSName.FLATE_DECODE)) {
            unfilteredStream.write(data);
        }
        return stream;
    }

    private byte[] buildOCSPResponse(byte[] content) throws IOException {
        DEROctetString derOctet = new DEROctetString(content);
        ASN1EncodableVector v2 = new ASN1EncodableVector();
        v2.add(OCSPObjectIdentifiers.id_pkix_ocsp_basic);
        v2.add(derOctet);
        ASN1Enumerated den = new ASN1Enumerated(0);
        ASN1EncodableVector v3 = new ASN1EncodableVector();
        v3.add(den);
        v3.add(new DERTaggedObject(true, 0, new DERSequence(v2)));
        DERSequence seq = new DERSequence(v3);
        return seq.getEncoded();
    }

    private String getSignatureHashKey(PDSignature signature) throws NoSuchAlgorithmException, IOException {
        byte[] contentToConvert = signature.getContents(documentBytes);
        if (SignatureType.TIMESTAMP.getUri().equals(signature.getSubFilter())) {
            ASN1InputStream din = new ASN1InputStream(new ByteArrayInputStream(contentToConvert));
            ASN1Primitive pkcs = din.readObject();
            contentToConvert = pkcs.getEncoded();
        }
        return Utils.convertToHexString(Utils.hashBytesWithSha1(contentToConvert));
    }

    // ----------------------------------------------------------------------------------------------------

    private static class ValidationData {
        public List<byte[]> crls = new ArrayList<>();
        public List<byte[]> ocsps = new ArrayList<>();
        public List<byte[]> certs = new ArrayList<>();
    }

}
