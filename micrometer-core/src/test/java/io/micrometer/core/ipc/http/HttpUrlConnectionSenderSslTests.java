/*
 * Copyright 2026 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.ipc.http;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link HttpUrlConnectionSender} using a custom {@link SSLSocketFactory}.
 *
 * @author Joao Grassi
 */
class HttpUrlConnectionSenderSslTests {

    @RegisterExtension
    static final WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig()
            .dynamicPort()
            .dynamicHttpsPort()
            .keystorePath(classpathResourcePath("/ssl/localhost.jks"))
            .keystorePassword("changeit")
            .keyManagerPassword("changeit"))
        .build();

    @Test
    void customSslSocketFactoryTrustsProvidedCertificate() throws Throwable {
        wireMock.stubFor(any(urlEqualTo("/metrics")).willReturn(ok()));
        SSLSocketFactory sslSocketFactory = trustingSslSocketFactory("/ssl/localhost-ca.pem");
        HttpSender sender = new HttpUrlConnectionSender(Duration.ofSeconds(1), Duration.ofSeconds(1), null,
                sslSocketFactory);

        HttpSender.Response response = sender.post(wireMock.getRuntimeInfo().getHttpsBaseUrl() + "/metrics").send();

        assertThat(response.code()).isEqualTo(200);
    }

    @Test
    void defaultSslSocketFactoryDoesNotTrustSelfSignedCertificate() {
        wireMock.stubFor(any(urlEqualTo("/metrics")).willReturn(ok()));
        HttpSender sender = new HttpUrlConnectionSender();

        assertThatExceptionOfType(SSLHandshakeException.class)
            .isThrownBy(() -> sender.post(wireMock.getRuntimeInfo().getHttpsBaseUrl() + "/metrics").send());
    }

    private static SSLSocketFactory trustingSslSocketFactory(String caCertificateClasspathResource) throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Certificate certificate;
        try (InputStream in = HttpUrlConnectionSenderSslTests.class
            .getResourceAsStream(caCertificateClasspathResource)) {
            certificate = certificateFactory.generateCertificate(in);
        }
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca", certificate);

        TrustManagerFactory trustManagerFactory = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return sslContext.getSocketFactory();
    }

    private static String classpathResourcePath(String classpathResource) {
        try {
            return Paths
                .get(HttpUrlConnectionSenderSslTests.class.getResource(classpathResource).toURI())
                .toAbsolutePath()
                .toString();
        }
        catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

}
