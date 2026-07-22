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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OtlpTlsConfigurer}.
 *
 * @author Joao Grassi
 */
class OtlpTlsConfigurerTest {

    @TempDir
    Path tempDir;

    @Test
    void createSslSocketFactoryFromValidCertificate() throws IOException {
        Path certificatePath = copyClasspathCertificateToTempDir();
        SSLSocketFactory sslSocketFactory = OtlpTlsConfigurer
            .createSslSocketFactory(certificatePath.toAbsolutePath().toString());
        assertThat(sslSocketFactory).isNotNull();
    }

    @Test
    void createSslSocketFactoryWhenFileDoesNotExist() {
        String missingPath = tempDir.resolve("does-not-exist.pem").toAbsolutePath().toString();
        assertThatThrownBy(() -> OtlpTlsConfigurer.createSslSocketFactory(missingPath))
            .isInstanceOf(InvalidConfigurationException.class)
            .hasMessageContaining(missingPath);
    }

    @Test
    void createSslSocketFactoryWhenContentIsNotAValidCertificate() throws IOException {
        Path invalidCertificatePath = tempDir.resolve("invalid-cert.pem");
        Files.write(invalidCertificatePath, "not a certificate".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(
                () -> OtlpTlsConfigurer.createSslSocketFactory(invalidCertificatePath.toAbsolutePath().toString()))
            .isInstanceOf(InvalidConfigurationException.class);
    }

    private Path copyClasspathCertificateToTempDir() throws IOException {
        Path certificatePath = tempDir.resolve("test-ca.pem");
        try (InputStream certificateStream = getClass().getResourceAsStream("/otlp/test-ca.pem")) {
            Files.copy(certificateStream, certificatePath);
        }
        return certificatePath;
    }

}
