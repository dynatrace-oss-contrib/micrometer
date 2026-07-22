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
package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.config.InvalidConfigurationException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;

/**
 * Builds an {@link SSLSocketFactory} that trusts the CA certificate(s) found in a given
 * PEM file, for verifying an OTLP server's TLS certificate.
 *
 * @author Joao Grassi
 * @since 1.18.0
 */
final class OtlpTlsConfigurer {

    private OtlpTlsConfigurer() {
    }

    static SSLSocketFactory createSslSocketFactory(String caCertificatePath) {
        try (InputStream certificateStream = Files.newInputStream(Paths.get(caCertificatePath))) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certificates = certificateFactory
                .generateCertificates(certificateStream);
            if (certificates.isEmpty()) {
                throw new InvalidConfigurationException(
                        "No certificates found in the file provided for OTLP certificate configuration: "
                                + caCertificatePath);
            }

            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            int index = 0;
            for (Certificate certificate : certificates) {
                trustStore.setCertificateEntry("otlp-ca-" + index++, certificate);
            }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext.getSocketFactory();
        }
        catch (IOException | GeneralSecurityException e) {
            throw new InvalidConfigurationException(
                    "Unable to load OTLP CA certificate from path: " + caCertificatePath, e);
        }
    }

}
