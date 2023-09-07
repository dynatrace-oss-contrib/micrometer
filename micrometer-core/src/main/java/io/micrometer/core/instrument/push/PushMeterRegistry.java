/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.core.instrument.push;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.util.TimeUtils;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class PushMeterRegistry extends MeterRegistry {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PushMeterRegistry.class);

    // Schedule publishing in the beginning X percent of the step to avoid spill-over into
    // the next step.
    private static final double PERCENT_RANGE_OF_RANDOM_PUBLISHING_OFFSET = 0.8;

    private final PushRegistryConfig config;

    private final AtomicBoolean publishing = new AtomicBoolean(false);

    private long lastScheduledPublishStartTime = 0L;

    @Nullable
    private ScheduledExecutorService scheduledExecutorService;

    protected PushMeterRegistry(PushRegistryConfig config, Clock clock) {
        super(clock);

        config.requireValid();

        this.config = config;
    }

    protected abstract void publish();

    /**
     * Catch uncaught exceptions thrown from {@link #publish()}.
     */
    // VisibleForTesting
    void publishSafely() {
        if (this.publishing.compareAndSet(false, true)) {
            this.lastScheduledPublishStartTime = clock.wallTime();
            try {
                publish();
            }
            catch (Throwable e) {
                logger.warn("Unexpected exception thrown while publishing metrics for " + getClass().getSimpleName(),
                        e);
            }
            finally {
                this.publishing.set(false);
            }
        }
        else {
            logger.warn("Publishing is already in progress. Skipping duplicate call to publish().");
        }
    }

    /**
     * Returns if scheduled publishing of metrics is in progress.
     * @return if scheduled publishing of metrics is in progress
     * @since 1.11.0
     */
    protected boolean isPublishing() {
        return publishing.get();
    }

    /**
     * Returns the time, in milliseconds, when the last scheduled publish was started by
     * {@link PushMeterRegistry#publishSafely()}.
     * @since 1.11.1
     */
    protected long getLastScheduledPublishStartTime() {
        return lastScheduledPublishStartTime;
    }

    /**
     * @deprecated Use {@link #start(ThreadFactory)} instead.
     */
    @Deprecated
    public final void start() {
        start(Executors.defaultThreadFactory());
    }

    public void start(ThreadFactory threadFactory) {
        if (scheduledExecutorService != null)
            stop();

        if (config.enabled()) {
            logger.info("publishing metrics for " + getClass().getSimpleName() + " every "
                    + TimeUtils.format(config.step()));

            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
            long stepMillis = config.step().toMillis();
            long initialDelayMillis = calculateInitialDelay();
            scheduledExecutorService.scheduleAtFixedRate(this::publishSafely, initialDelayMillis, stepMillis,
                    TimeUnit.MILLISECONDS);
        }
    }

    public void stop() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
            scheduledExecutorService = null;
        }
    }

    private static boolean isDurationPositive(Duration duration) {
        return (!duration.isZero() && !duration.isNegative());
    }

    @Override
    public void close() {
        stop();
        if (config.enabled() && !isClosed()) {
            if (isPublishing() && isDurationPositive(config.overlappingShutdownWaitTimeout())) {
                logger.info(
                        "Publishing is already in progress. "
                                + "Waiting for up to {}ms for the export to finish before shutting down.",
                        config.overlappingShutdownWaitTimeout().toMillis());

                ExecutorService executor = Executors.newSingleThreadExecutor();
                FutureTask futureTask = new FutureTask(() -> {
                    while (isPublishing()) {
                        // check if the export is already finished every 50ms.
                        Thread.sleep(50);
                    }
                    return null;
                });
                executor.submit(futureTask);

                try {
                    futureTask.get(config.overlappingShutdownWaitTimeout().toMillis(), TimeUnit.MILLISECONDS);
                }
                catch (InterruptedException | ExecutionException | TimeoutException e) {
                    logger.info("ending wait for last export to finish, took too long");
                    futureTask.cancel(true);
                }
            }
            else {
                // if publishing is not currently in progress, export one final time on
                // shutdown.
                publishSafely();
            }
        }
        super.close();
    }

    // VisibleForTesting
    long calculateInitialDelay() {
        long stepMillis = config.step().toMillis();
        Random random = new Random();
        // in range of [0, X% of step - 2)
        long randomOffsetWithinStep = Math.max(0,
                (long) (stepMillis * random.nextDouble() * PERCENT_RANGE_OF_RANDOM_PUBLISHING_OFFSET) - 2);
        long offsetToStartOfNextStep = stepMillis - (clock.wallTime() % stepMillis);
        // at least 2ms into step, so it is after StepMeterRegistry's meterPollingService
        return offsetToStartOfNextStep + 2 + randomOffsetWithinStep;
    }

}
