/*
 * Copyright 2025 VMware, Inc.
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

import io.micrometer.core.instrument.util.IOUtils;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

/**
 * HttpSender implementation that uses a custom self-signed certificate for HTTPS requests.
 * Only requests made by this sender use the custom certificate.
 */
public class CustomSslCertHttpSender implements HttpSender {
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 1000;

    private static final int DEFAULT_READ_TIMEOUT_MS = 10000;

    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final SSLSocketFactory sslSocketFactory;
    private final HostnameVerifier hostnameVerifier;
    private final Set<String> allowedHostnames;

    /**
     * @param certFilePath   Path to the self-signed certificate file (PEM or DER encoded)
     * @param connectTimeout connect timeout
     * @param readTimeout    read timeout
     * @throws Exception if certificate loading fails
     */
    public CustomSslCertHttpSender(String certFilePath, Duration connectTimeout, Duration readTimeout) throws Exception {
        if (certFilePath.isEmpty()) {
            throw new IllegalArgumentException("certFilePath cannot be empty");
        }

        this.connectTimeoutMs = (int) connectTimeout.toMillis();
        this.readTimeoutMs = (int) readTimeout.toMillis();

        CertificateHolder holder = loadCertificate(certFilePath);
        this.allowedHostnames = holder.getAllowedHostnames();
        this.sslSocketFactory = holder.getSslSocketFactory();
        // this is a fallback for when the SSL handshake fails.
        // HostnameVerifier::verify is not called if the handshake succeeds with the read cert.
        this.hostnameVerifier = this::verifyHostname;
    }

    public CustomSslCertHttpSender(String certFilePath) throws Exception {
        this(certFilePath, Duration.ofMillis(DEFAULT_CONNECT_TIMEOUT_MS), Duration.ofMillis(DEFAULT_READ_TIMEOUT_MS));
    }

    private boolean verifyHostname(String hostname, SSLSession session) {
        // log.info("Fallback hostname verification: " + hostname + " | allowed Hostnames: " + String.join(", ", allowedHostnames));
        return allowedHostnames.contains(hostname);
    }

    @Override
    public Response send(Request request) throws IOException {
        URL url = request.getUrl();
        if ("https".equalsIgnoreCase(url.getProtocol())) {
            return sendHttps(request);
        } else {
            return sendHttp(request);
        }
    }

    private Response sendHttp(Request request) throws IOException {
        HttpURLConnection con = (HttpURLConnection) request.getUrl().openConnection();
        return doSend(request, con);
    }

    private Response doSend(Request request, HttpURLConnection con) throws IOException {
        try {
            con.setConnectTimeout(connectTimeoutMs);
            con.setReadTimeout(readTimeoutMs);
            Method method = request.getMethod();
            con.setRequestMethod(method.name());

            for (Map.Entry<String, String> header : request.getRequestHeaders().entrySet()) {
                con.setRequestProperty(header.getKey(), header.getValue());
            }

            if (method != Method.GET) {
                con.setDoOutput(true);
                try (OutputStream os = con.getOutputStream()) {
                    os.write(request.getEntity());
                    os.flush();
                }
            }

            int status = con.getResponseCode();

            String body = null;
            try {
                if (con.getErrorStream() != null) {
                    body = IOUtils.toString(con.getErrorStream());
                } else if (con.getInputStream() != null) {
                    body = IOUtils.toString(con.getInputStream());
                }
            } catch (IOException ignored) {
            }

            return new Response(status, body);
        } finally {
            try {
                if (con != null) {
                    con.disconnect();
                }
            } catch (Exception ignore) {
            }
        }
    }

    private Response sendHttps(Request request) throws IOException {
        HttpsURLConnection con = (HttpsURLConnection) request.getUrl().openConnection();
        con.setSSLSocketFactory(sslSocketFactory);
        con.setHostnameVerifier(hostnameVerifier);
        return doSend(request, con);
    }

    private static Set<String> extractAllowedHostnames(X509Certificate certificate) {
        Set<String> hostnames = new HashSet<>();

        // Extract Common Name (CN)
        String dn = certificate.getSubjectX500Principal().getName();
        String[] dnParts = dn.split(",");
        for (String part : dnParts) {
            part = part.trim();
            if (part.startsWith("CN=")) {
                hostnames.add(part.substring(3));
            }
        }

        // Extract Subject Alternative Names (SANs)
        try {
            Collection<List<?>> sanEntries = certificate.getSubjectAlternativeNames();
            if (sanEntries != null) {
                for (List<?> sanEntry : sanEntries) {
                    Integer type = (Integer) sanEntry.get(0);
                    if (type == 2) { // DNS Name
                        String dnsName = (String) sanEntry.get(1);
                        hostnames.add(dnsName);
                    }
                }
            }
        } catch (CertificateParsingException e) {
            // Ignore parsing exceptions for SANs
        }

        return hostnames;
    }

    private static class CertificateHolder {
        private final X509Certificate certificate;
        private final SSLSocketFactory sslSocketFactory;
        private final Set<String> allowedHostnames;

        public CertificateHolder(X509Certificate certificate, SSLSocketFactory sslSocketFactory,
                                 Set<String> allowedHostnames) {
            this.certificate = certificate;
            this.sslSocketFactory = sslSocketFactory;
            this.allowedHostnames = allowedHostnames;
        }

        public X509Certificate getCertificate() {
            return certificate;
        }

        public SSLSocketFactory getSslSocketFactory() {
            return sslSocketFactory;
        }

        public Set<String> getAllowedHostnames() {
            return allowedHostnames;
        }
    }

    private static CertificateHolder loadCertificate(String certFilePath) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        try (InputStream caInput = Files.newInputStream(new File(certFilePath).toPath())) {
            X509Certificate caCert = (X509Certificate) cf.generateCertificate(caInput);
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("caCert", caCert);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), null);

            return new CertificateHolder(caCert, context.getSocketFactory(), extractAllowedHostnames(caCert));
        }
    }
}

