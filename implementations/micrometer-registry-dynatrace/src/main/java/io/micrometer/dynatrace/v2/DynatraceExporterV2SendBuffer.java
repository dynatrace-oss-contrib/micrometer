/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.dynatrace.v2;

import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.ipc.http.HttpSender;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class DynatraceExporterV2SendBuffer {

    private static final String NEW_LINE = "\n";

    private static final int NEW_LINE_LENGTH = NEW_LINE.getBytes().length;

    private static final Pattern EXTRACT_LINES_OK = Pattern.compile("\"linesOk\":\\s?(\\d+)");

    private static final Pattern EXTRACT_LINES_INVALID = Pattern.compile("\"linesInvalid\":\\s?(\\d+)");

    private static final Pattern IS_NULL_ERROR_RESPONSE = Pattern.compile("\"error\":\\s?null");

    private final String endpoint;

    private final String apiToken;

    private final int maxBatchSize;

    private final long maxBodySizeBytes;

    private final StringBuilder stringBuilder = new StringBuilder();

    private int currentBatchSize = 0;

    private int currentBodySizeBytes = 0;

    private final InternalLogger logger = InternalLoggerFactory.getInstance(DynatraceExporterV2SendBuffer.class);

    private final HttpSender httpSender;

    private final ExportStatistics exportStatistics;

    public DynatraceExporterV2SendBuffer(String endpoint, String apiToken, HttpSender httpSender, int maxBatchSize,
            long maxPayloadSizeBytes) {
        this.endpoint = endpoint;
        this.apiToken = apiToken;
        this.maxBatchSize = maxBatchSize > 0 ? maxBatchSize : 1000;
        this.maxBodySizeBytes = maxPayloadSizeBytes > 0 ? maxPayloadSizeBytes : 1048576;
        this.httpSender = httpSender;
        this.exportStatistics = new ExportStatistics();
    }

    public void putMetricLine(String line) {
        int nextLineSizeBytes = line.getBytes().length + NEW_LINE_LENGTH;

        // if adding the line would exceed the maxBodySize, send the request before adding
        // the next line.
        if ((currentBodySizeBytes + nextLineSizeBytes) > maxBodySizeBytes) {
            sendAndReset();
        }

        stringBuilder.append(line).append(NEW_LINE);

        currentBodySizeBytes = currentBodySizeBytes + line.getBytes().length;
        currentBatchSize++;

        if (currentBatchSize == maxBatchSize) {
            sendAndReset();
        }
        else if (currentBatchSize > maxBatchSize) {
            logger.warn("batch size ({}) larger than max batch size ({}), attempting to send...", currentBatchSize,
                    maxBatchSize);
            sendAndReset();
        }
    }

    ExportStatistics getExportStatistics() {
        return exportStatistics;
    }

    private void reset() {
        stringBuilder.setLength(0);
        currentBatchSize = 0;
        currentBodySizeBytes = 0;
    }

    private void sendAndReset() {
        HttpSender.Request.Builder requestBuilder = httpSender.post(endpoint).withHeader("User-Agent", "micrometer");

        if (null != apiToken) {
            requestBuilder.withAuthentication("Api-Token", apiToken);
            // requestBuilder.withHeader("Authorization", "Api-Token " + apiToken);
        }

        String body = stringBuilder.toString();

        requestBuilder.withPlainText(body);

        if (logger.isDebugEnabled()) {
            logger.debug("Sending lines (payload bytes {})\n{}", currentBodySizeBytes, body);
        }

        try {
            requestBuilder.send()
                .onSuccess(response -> handleSuccess(currentBatchSize, response))
                .onError(response -> logger.error("Failed metric ingestion: Error Code={}, Response Body={}",
                        response.code(), getTruncatedBody(response)));

            exportStatistics.incrementRequestsSent();
            exportStatistics.incrementLinesSent(currentBatchSize);
        }
        catch (Throwable throwable) {
            // log at info to not spam application logs with warnings
            logger.info("Failed metric ingestion: {}", throwable.getMessage());
        }
        reset();
    }

    private void handleSuccess(int totalSent, HttpSender.Response response) {
        if (response.code() == 202) {
            exportStatistics.incrementRequestsOk();
            if (IS_NULL_ERROR_RESPONSE.matcher(response.body()).find()) {
                Matcher linesOkMatchResult = EXTRACT_LINES_OK.matcher(response.body());
                Matcher linesInvalidMatchResult = EXTRACT_LINES_INVALID.matcher(response.body());
                if (linesOkMatchResult.find() && linesInvalidMatchResult.find()) {
                    String linesOkMatch = linesOkMatchResult.group(1);
                    String linesInvalidMatch = linesInvalidMatchResult.group(1);

                    logger.debug("Sent {} metric lines, linesOk: {}, linesInvalid: {}.", totalSent, linesOkMatch,
                            linesInvalidMatch);

                    exportStatistics.incrementLinesOk(tryParseInt(linesOkMatch));
                    exportStatistics.incrementLinesInvalid(tryParseInt(linesInvalidMatch));
                }
                else {
                    logger.warn("Unable to parse response: {}", getTruncatedBody(response));
                }
            }
            else {
                logger.warn("Unable to parse response: {}", getTruncatedBody(response));
            }
        }
        else {
            // common pitfall if URI is supplied in V1 format (without endpoint path)
            logger.error(
                    "Expected status code 202, got {}.\nResponse Body={}\nDid you specify the ingest path (e.g.: /api/v2/metrics/ingest)?",
                    response.code(), getTruncatedBody(response));
        }
    }

    private String getTruncatedBody(HttpSender.Response response) {
        return StringUtils.truncate(response.body(), 1_000, " (truncated)");
    }

    public void flush() {
        if (currentBatchSize > 0) {
            sendAndReset();
        }
    }

    private static int tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        }
        catch (NumberFormatException ignored) {
        }
        return 0;
    }

    static class ExportStatistics {

        private int requestsSent = 0;

        private int requestsOk = 0;

        private int linesSent = 0;

        private int linesOk = 0;

        private int linesInvalid = 0;

        public void incrementRequestsSent() {
            requestsSent++;
        }

        public void incrementRequestsOk() {
            requestsOk++;
        }

        public void incrementLinesSent(int lines) {
            linesSent += lines;
        }

        public void incrementLinesOk(int lines) {
            linesOk += lines;
        }

        public void incrementLinesInvalid(int lines) {
            linesInvalid += lines;
        }

        public int getRequestsSent() {
            return requestsSent;
        }

        public int getRequestsOk() {
            return requestsOk;
        }

        public int getLinesSent() {
            return linesSent;
        }

        public int getLinesOk() {
            return linesOk;
        }

        public int getLinesInvalid() {
            return linesInvalid;
        }

        public void reset() {
            requestsSent = 0;
            requestsOk = 0;
            linesSent = 0;
            linesOk = 0;
            linesInvalid = 0;
        }

    }

}
