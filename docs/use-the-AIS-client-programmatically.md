# Use the AIS client programmatically
The client library can be used as a normal project dependency, allowing your project to access
the document signing and timestamping features provided by the AIS service.

## Dependency configuration
For Maven projects, add the following in your _POM_ file:
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <!-- ... -->
    
    <repositories>
        <repository>
            <id>swisscom-ais-pdfbox-client</id>
            <name>Swisscom AIS PDFBox client</name>
            <url>https://raw.githubusercontent.com/SwisscomTrustServices/pdfbox-ais/main/repository</url>
        </repository>
    </repositories>
    
    <!-- ... -->

    <dependencies>
        <dependency>
            <groupId>com.swisscom.ais</groupId>
            <artifactId>pdfbox-ais</artifactId>
            <version>1.2.3</version>
        </dependency>
    </dependencies>
</project>
```

For Gradle projects, add the following in your _build.gradle_ file:
```groovy
plugins {
    id 'java'
}

// ...

repositories {
    mavenCentral()
    maven {
        url 'https://raw.githubusercontent.com/SwisscomTrustServices/pdfbox-ais/main/repository'
    }
}

dependencies {
    compile 'com.swisscom.ais:pdfbox-ais:1.2.3'
    // ...
}
```

## Using the library
This section describes the usage of the library in code. See the sample files 
in the [root source folder](../src/main/java/com/swisscom/ais) for complete examples of how to use the library in code.

First create the configuration objects, one for the REST client 
([RestClientConfiguration](../src/main/java/com/swisscom/ais/client/rest/RestClientConfiguration.java)) and one for the AIS client 
([AISClientConfiguration](../src/main/java/com/swisscom/ais/client/AisClientConfiguration.java)). This needs to be done once per application
lifetime, as the AIS client, once it is created and properly configured, can be reused over and over for each incoming request. It is implemented
in a thread-safe way and makes use of proper HTTP connection pooling in order to correctly reuse resources.

Configure the REST client:
```java
RestClientConfiguration restConfig = new RestClientConfiguration();
restConfig.setRestServiceSignUrl("https://ais.swisscom.com/AIS-Server/rs/v1.0/sign");
restConfig.setRestServicePendingUrl("https://ais.swisscom.com/AIS-Server/rs/v1.0/pending");
restConfig.setServerCertificateFile("/home/user/ais-server.crt");
restConfig.setClientKeyFile("/home/user/ais-client.key");
restConfig.setClientKeyPassword("secret");
restConfig.setClientCertificateFile("/home/user/ais-client.crt");

RestClientImpl restClient = new RestClientImpl();
restClient.setConfiguration(restConfig);
```

Then configure the AIS client:
```java
AisClientConfiguration aisConfig = new AisClientConfiguration();
aisConfig.setSignaturePollingIntervalInSeconds(10);
aisConfig.setSignaturePollingRounds(10);
```

Finally, create the AIS client with these objects:
```java
try (AisClientImpl aisClient = new AisClientImpl(aisConfig, restClient)){
    // use the client here
}
```

The above example makes use of Java's _try-with-resources_ feature. If you don't use the client like this, just make sure you call its _close()_
method once you are done with it (e.g. at the shutdown of your application). Don't call this method after each request!

Once the client is up and running, you can request it to sign and/or timestamp documents. For this, a 
[UserData](../src/main/java/com/swisscom/ais/client/model/UserData.java) object is needed, to specify all the details required for the signature
or timestamp.

```java
UserData userData = new UserData();
userData.setClaimedIdentityName("ais-90days-trial");
userData.setClaimedIdentityKey("keyEntity");
userData.setDistinguishedName("cn=TEST User, givenname=Max, surname=Maximus, c=US, serialnumber=abcdefabcdefabcdefabcdefabcdef");

userData.setStepUpLanguage("en");
userData.setStepUpMessage("Please confirm the signing of the document");
userData.setStepUpMsisdn("40799999999");

userData.setSignatureReason("For testing purposes");
userData.setSignatureLocation("Topeka, Kansas");
userData.setSignatureContactInfo("test@test.com");

userData.setAddRevocationInformation(RevocationInformation.PADES);
userData.setSignatureStandard(SignatureStandard.PADES);

userData.setConsentUrlCallback((consentUrl, userData1) -> System.out.println("Consent URL: " + consentUrl));
```

The last line is quite interesting. If you go with the _On Demand signature with Step Up_, there is a Consent URL that is generated and that
needs to be passed to the mobile user, so that he or she can access it, authenticate there and confirm the signature. The _UserData_ class
allows you to define a callback object that is invoked as soon as the URL is generated and received by the client. In the example above
the URL is just printed in the _STDOUT_ stream, but in your case you might want to display it to the user by other means (web, mobile UI, etc).
Keep in mind that this callback is performed EACH TIME the consent URL is received. For a signature request that goes into pending/polling mode,
this will happen each time the response comes back from the server. 

Third, you need one object (or more) that identifies the document to sign and/or timestamp. More than one document can be signed/timestamped at
a time.

```java
PdfHandle document = new PdfHandle();
document.setInputFromFile("/home/user/input.pdf");
document.setOutputToFile("/home/user/signed-output.pdf");
document.setDigestAlgorithm(DigestAlgorithm.SHA256);
```

Finally, use all these objects to create the signature:

```java
SignatureResult result = aisClient.signWithOnDemandCertificateAndStepUp(Collections.singletonList(document), userData);
if (result == SignatureResult.SUCCESS) {
    // yay!
}
```

The [returned result](../src/main/java/com/swisscom/ais/client/model/SignatureResult.java) is a coder-friendly way of finding how the signature went. 
As long as the signature terminates as caused by the mobile user (success, user cancel, user timeout) then the AIS client gracefully returns a result. 
If some other error is encountered, the client throws
an [AisClientException](../src/main/java/com/swisscom/ais/client/AisClientException.java).
