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
package com.swisscom.ais.client.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swisscom.ais.client.AisClientException;
import com.swisscom.ais.client.rest.model.pendingreq.AISPendingRequest;
import com.swisscom.ais.client.rest.model.signreq.AISSignRequest;
import com.swisscom.ais.client.rest.model.signresp.AISSignResponse;
import com.swisscom.ais.client.utils.Loggers;
import com.swisscom.ais.client.utils.Trace;
import com.swisscom.ais.client.utils.Utils;
import org.apache.commons.codec.CharEncoding;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.ssl.PrivateKeyStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import static com.swisscom.ais.client.utils.Utils.closeResource;

public class RestClientImpl implements RestClient {

    private static final Logger logClient = LoggerFactory.getLogger(Loggers.CLIENT);
    private static final Logger logProtocol = LoggerFactory.getLogger(Loggers.CLIENT_PROTOCOL);
    private static final Logger logReqResp = LoggerFactory.getLogger(Loggers.REQUEST_RESPONSE);
    private static final Logger logFullReqResp = LoggerFactory.getLogger(Loggers.FULL_REQUEST_RESPONSE);

    private RestClientConfiguration config;
    private ObjectMapper jacksonMapper;
    private CloseableHttpClient httpClient;

    // ----------------------------------------------------------------------------------------------------

    public void setConfiguration(RestClientConfiguration config) {
        this.config = config;
        Security.addProvider(new BouncyCastleProvider());
        jacksonMapper = new ObjectMapper();
        jacksonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        jacksonMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        jacksonMapper.setSerializationInclusion(JsonInclude.Include.USE_DEFAULTS);

        SSLConnectionSocketFactory sslConnectionSocketFactory;
        try {
            SSLContextBuilder sslContextBuilder = SSLContexts.custom()
                .loadKeyMaterial(produceTheKeyStore(config),
                                 keyToCharArray(config.getClientKeyPassword()), produceAPrivateKeyStrategy());
            if (Utils.notEmpty(config.getServerCertificateFile())) {
                sslContextBuilder.loadTrustMaterial(produceTheTrustStore(config), null);
            }
            sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContextBuilder.build());
        } catch (Exception e) {
            throw new AisClientException("Failed to configure the TLS/SSL connection factory for the AIS client", e);
        }

        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(config.getMaxTotalConnections())
            .setMaxConnPerRoute(config.getMaxConnectionsPerRoute())
            .setSSLSocketFactory(sslConnectionSocketFactory)
            .build();
        RequestConfig httpClientRequestConfig = RequestConfig.custom()
            .setConnectTimeout(config.getConnectionTimeoutInSec(), TimeUnit.SECONDS)
            .setResponseTimeout(config.getResponseTimeoutInSec(), TimeUnit.SECONDS)
            .build();

        setUpRestClient(config, connectionManager, httpClientRequestConfig);
    }

    private void setUpRestClient(RestClientConfiguration config, PoolingHttpClientConnectionManager connectionManager, RequestConfig httpClientRequestConfig) {
        HttpClientBuilder httpClientBuilder = HttpClients.custom();

        if (config.isEnableProxy()) {
            enableProxy(httpClientBuilder);
        }

        this.httpClient = httpClientBuilder
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(httpClientRequestConfig)
                .build();
    }

    private void enableProxy(HttpClientBuilder httpClientBuilder) {

        String proxyHost = this.getProxyHost();
        int port = this.getProxyPortNumber();

        setRestClientProxy(httpClientBuilder, proxyHost, port);

        if (config.isEnableProxyAuth()) {
            enableProxyAuthentication(httpClientBuilder, proxyHost, port);
        }
    }

    private void enableProxyAuthentication(HttpClientBuilder httpClientBuilder, String proxyHost, int port) {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        String username = this.getProxyUserName();
        char[] password = this.getProxyPassword();

        AuthScope authScope = new AuthScope(proxyHost, port);
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);
        credentialsProvider.setCredentials(authScope, credentials);

        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
    }

    private void setRestClientProxy(HttpClientBuilder httpClientBuilder, String proxyHost, int port) {
        if (config.getProxyUsername() != null && config.getProxyUsername().length() > 0) {
            httpClientBuilder.setProxy(new HttpHost(new URIAuthority(config.getProxyUsername(), proxyHost, port)));
        } else {
            httpClientBuilder.setProxy(new HttpHost(new URIAuthority(proxyHost, port)));
        }
    }

    private String getProxyHost() {
        String proxyHost = config.getProxyHost();
        if (proxyHost == null || proxyHost.length() == 0) {
            throw new IllegalStateException("Invalid configuration. The server proxy host is missing, empty or invalid.");
        }
        return proxyHost;
    }

    private char[] getProxyPassword() {
        String proxyHost = config.getProxyPassword();
        if (proxyHost == null || proxyHost.length() == 0) {
            throw new IllegalStateException("Invalid configuration. The server proxy password is missing or is empty.");
        }
        return proxyHost.toCharArray();
    }

    private String getProxyUserName() {
        String proxyHost = config.getProxyUsername();
        if (proxyHost == null || proxyHost.length() == 0) {
            throw new IllegalStateException("Invalid configuration. The server proxy username is missing or is empty.");
        }
        return proxyHost;
    }

    private Integer getProxyPortNumber() {
        try {
            return Integer.parseInt(config.getProxyPort());
        } catch (Exception e) {
            throw new IllegalStateException("Invalid configuration. The server proxy port number is missing, empty or invalid.");
        }

    }

    @Override
    public void close() throws IOException {
        logClient.debug("Closing the REST client");
        if (httpClient != null) {
            logClient.debug("Closing the embedded HTTP client");
            httpClient.close();
        }
    }

    // ----------------------------------------------------------------------------------------------------

    @Override
    public AISSignResponse requestSignature(AISSignRequest requestWrapper, Trace trace) {
        return sendAndReceive("SignRequest", config.getRestServiceSignUrl(),
                              requestWrapper, AISSignResponse.class, trace);
    }

    @Override
    public AISSignResponse pollForSignatureStatus(AISPendingRequest requestWrapper, Trace trace) {
        return sendAndReceive("PendingRequest", config.getRestServicePendingUrl(),
                              requestWrapper, AISSignResponse.class, trace);
    }

    // ----------------------------------------------------------------------------------------------------

    private <TReq, TResp> TResp sendAndReceive(String operationName,
                                               String serviceUrl,
                                               TReq requestObject,
                                               @SuppressWarnings("SameParameterValue") Class<TResp> responseClass,
                                               Trace trace) {
        logProtocol.debug("{}: Serializing object of type {} to JSON - {}",
                          operationName, requestObject.getClass().getSimpleName(), trace.getId());
        String requestJson;
        try {
            requestJson = jacksonMapper.writeValueAsString(requestObject);
        } catch (JsonProcessingException e) {
            throw new AisClientException("Failed to serialize request object to JSON, for operation " +
                                         operationName + " - " + trace.getId(), e);
        }

        HttpPost httpPost = new HttpPost(serviceUrl);
        httpPost.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON, CharEncoding.UTF_8, false));
        httpPost.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON);
        logProtocol.info("{}: Sending request to: [{}] - {}", operationName, serviceUrl, trace.getId());
        logReqResp.info("{}: Sending JSON to: [{}], content: [{}] - {}", operationName, serviceUrl, requestJson, trace.getId());
        logFullReqResp.info("{}: Sending JSON to: [{}], content: [{}] - {}", operationName, serviceUrl, requestJson, trace.getId());

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            logProtocol.info("{}: Received HTTP status code: {} - {}", operationName, response.getCode(), trace.getId());
            String responseJson;
            try {
                responseJson = EntityUtils.toString(response.getEntity());
            } catch (ParseException e) {
                throw new AisClientException("Failed to interpret the HTTP response content as a string, for operation " +
                                             operationName + " - " + trace.getId(), e);
            }
            if (response.getCode() == 200) {
                if (logReqResp.isInfoEnabled()) {
                    String strippedResponse = Utils.stripInnerLargeBase64Content(responseJson, '"', '"');
                    logReqResp.info("{}: Received JSON content: {} - {}", operationName, strippedResponse, trace.getId());
                }
                if (logFullReqResp.isInfoEnabled()) {
                    logFullReqResp.info("{}: Received JSON content: {} - {}", operationName, responseJson, trace.getId());
                }
                logProtocol.debug("{}: Deserializing JSON to object of type {} - {}", operationName, responseClass.getSimpleName(), trace.getId());
                try {
                    return jacksonMapper.readValue(responseJson, responseClass);
                } catch (JsonProcessingException e) {
                    throw new AisClientException("Failed to deserialize JSON content to object of type " +
                                                 responseClass.getSimpleName() + " for operation " +
                                                 operationName + " - " +
                                                 trace.getId(), e);
                }
            } else {
                throw new AisClientException("Received fault response: HTTP " +
                                             response.getCode() + " " +
                                             response.getReasonPhrase() + " - " + trace.getId());
            }
        } catch (SSLException e) {
            throw new AisClientException("TLS/SSL connection failure for " + operationName + " - " + trace.getId(), e);
        } catch (Exception e) {
            throw new AisClientException("Communication failure for " + operationName + " - " + trace.getId(), e);
        }
    }

    // ----------------------------------------------------------------------------------------------------

    private KeyStore produceTheKeyStore(RestClientConfiguration config) {
        try {
            CertificateFactory fact = CertificateFactory.getInstance("X.509");
            FileInputStream is = new FileInputStream(config.getClientCertificateFile());
            X509Certificate certificate = (X509Certificate) fact.generateCertificate(is);
            PrivateKey privateKey = getPrivateKey(config.getClientKeyFile(), config.getClientKeyPassword());

            KeyStore keyStore = KeyStore.getInstance("jks");
            keyStore.load(null, null);
            keyStore.setKeyEntry("main", privateKey, keyToCharArray(config.getClientKeyPassword()), new Certificate[]{certificate});

            closeResource(is, null);
            return keyStore;
        } catch (Exception e) {
            throw new AisClientException("Failed to initialize the TLS keystore", e);
        }
    }

    private KeyStore produceTheTrustStore(RestClientConfiguration config) {
        try {
            CertificateFactory fact = CertificateFactory.getInstance("X.509");
            FileInputStream is = new FileInputStream(config.getServerCertificateFile());
            X509Certificate certificate = (X509Certificate) fact.generateCertificate(is);

            KeyStore keyStore = KeyStore.getInstance("jks");
            keyStore.load(null, null);
            keyStore.setCertificateEntry("main", certificate);

            closeResource(is, null);
            return keyStore;
        } catch (Exception e) {
            throw new AisClientException("Failed to initialize the TLS truststore", e);
        }
    }

    public static PrivateKey getPrivateKey(String fileName, String keyPassword) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(fileName));
            // if we read a X509 key we will get immediately a PrivateKeyInfo
            // if the key is a RSA key it is necessary to create a PEMKeyPair first
            PrivateKeyInfo privateKeyInfo;
            PEMParser pemParser;
            try {
                pemParser = new PEMParser(br);
                privateKeyInfo = (PrivateKeyInfo) pemParser.readObject();
            } catch (Exception ignored) {
                br.close();
                br = new BufferedReader(new FileReader(fileName));
                pemParser = new PEMParser(br);
                Object pemKeyPair = pemParser.readObject();
                if (pemKeyPair instanceof PEMEncryptedKeyPair) {
                    if (Utils.isEmpty(keyPassword)) {
                        throw new AisClientException("The client private key is encrypted but there is no key password provided " +
                                                     "(check field 'client.auth.keyPassword' from the config.properties or from " +
                                                     "the REST client configuration)");
                    }
                    PEMDecryptorProvider decryptionProv = new JcePEMDecryptorProviderBuilder().build(keyPassword.toCharArray());
                    PEMKeyPair decryptedKeyPair = ((PEMEncryptedKeyPair) pemKeyPair).decryptKeyPair(decryptionProv);
                    privateKeyInfo = decryptedKeyPair.getPrivateKeyInfo();
                } else {
                    privateKeyInfo = ((PEMKeyPair) pemKeyPair).getPrivateKeyInfo();
                }
            }

            pemParser.close();
            br.close();

            JcaPEMKeyConverter jcaPEMKeyConverter = new JcaPEMKeyConverter();
            return jcaPEMKeyConverter.getPrivateKey(privateKeyInfo);
        } catch (Exception e) {
            throw new AisClientException("Failed to initialize the client private key", e);
        }
    }

    private PrivateKeyStrategy produceAPrivateKeyStrategy() {
        return (aliases, sslParameters) -> "main";
    }

    private char[] keyToCharArray(String key) {
        return Utils.isEmpty(key) ? new char[0] : key.toCharArray();
    }

}
