/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
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
package com.hazelcast.simulator.worker.tasks;

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.simulator.probes.Probe;
import com.hazelcast.simulator.probes.impl.ThroughputProbe;
import com.hazelcast.simulator.test.TestContext;
import com.hazelcast.simulator.test.annotations.InjectMetronome;
import com.hazelcast.simulator.test.annotations.InjectProbe;
import com.hazelcast.simulator.test.annotations.InjectTestContext;
import com.hazelcast.simulator.worker.metronome.EmptyMetronome;
import com.hazelcast.simulator.worker.metronome.Metronome;
import com.hazelcast.simulator.worker.selector.OperationSelector;
import com.hazelcast.simulator.worker.selector.OperationSelectorBuilder;

import java.util.Random;

/**
 * Base implementation of {@link IWorker} which is returned by {@link com.hazelcast.simulator.test.annotations.RunWithWorker}
 * annotated test methods.
 *
 * Implicitly measures throughput and latency with a built-in {@link Probe}.
 * The operation counter is automatically increased after each call of {@link #timeStep(Enum)}.
 *
 * @param <O> Type of {@link Enum} used by the {@link com.hazelcast.simulator.worker.selector.OperationSelector}
 */
public abstract class AbstractWorker<O extends Enum<O>> implements IWorker {

    protected static final ILogger LOGGER = Logger.getLogger(AbstractWorker.class);

    private final Random random = new Random();
    private final OperationSelector<O> selector;

    @InjectTestContext
    private TestContext testContext;
    @InjectProbe(name = IWorker.DEFAULT_WORKER_PROBE_NAME, useForThroughput = true)
    private Probe workerProbe;
    @InjectMetronome
    private Metronome workerMetronome;

    private long iteration;
    private boolean isWorkerStopped;

    public AbstractWorker(OperationSelectorBuilder<O> operationSelectorBuilder) {
        this.selector = operationSelectorBuilder.build();
    }

    /**
     * This constructor is for inherited classes which don't use the {@link OperationSelectorBuilder}.
     */
    AbstractWorker() {
        this.selector = null;
    }

    @Override
    public void beforeRun() throws Exception {
    }

    @Override
    public final void run() throws Exception {
        if (workerMetronome.getClass() == EmptyMetronome.class && workerProbe.getClass() == ThroughputProbe.class) {
            while ((!testContext.isStopped() && !isWorkerStopped)) {
                timeStep(selector.select());
                increaseIteration();
                workerProbe.recordValue(0);
            }
        } else {
            while ((!testContext.isStopped() && !isWorkerStopped)) {
                workerMetronome.waitForNext();
                doRun();
            }
        }
    }

    protected void doRun() throws Exception {
        long started = System.nanoTime();
        timeStep(selector.select());
        workerProbe.recordValue(System.nanoTime() - started);

        increaseIteration();
    }

    /**
     * This method is called for each iteration of {@link #run()}.
     *
     * Won't be called if an error occurs in {@link #beforeRun()}.
     *
     * @param operation The selected operation for this iteration
     * @throws Exception is allowed to throw exceptions which are automatically reported as failure
     */
    protected abstract void timeStep(O operation) throws Exception;

    @Override
    public void afterRun() throws Exception {
    }

    /**
     * Stops the local worker, regardless of the {@link TestContext} stopped status.
     *
     * Calling this method will not affect the {@link TestContext} or other workers. It will just stop the local worker.
     */
    protected final void stopWorker() {
        isWorkerStopped = true;
    }

    /**
     * Stops the {@link TestContext}.
     *
     * Calling this method will affect all workers of the {@link TestContext}.
     */
    protected final void stopTestContext() {
        testContext.stop();
    }


    @Override
    public void afterCompletion() throws Exception {
    }

    /**
     * Returns the test ID from the {@link TestContext}.
     *
     * @return the test ID
     */
    protected String getTestId() {
        return testContext.getTestId();
    }

    /**
     * Calls {@link Random#nextInt()} on an internal Random instance.
     *
     * @return the next pseudo random, uniformly distributed {@code int} value from this random number generator's sequence
     */
    protected final int randomInt() {
        return random.nextInt();
    }

    /**
     * Calls {@link Random#nextInt(int)} on an internal Random instance.
     *
     * @param upperBond the bound on the random number to be returned.  Must be
     *                  positive.
     * @return the next pseudo random, uniformly distributed {@code int} value between {@code 0} (inclusive) and {@code n}
     * (exclusive) from this random number generator's sequence
     */
    protected final int randomInt(int upperBond) {
        return random.nextInt(upperBond);
    }

    /**
     * Returns the inner {@link Random} instance to call methods which are not implemented.
     *
     * @return the {@link Random} instance of the worker
     */
    protected Random getRandom() {
        return random;
    }

    /**
     * Returns the iteration count of the worker.
     *
     * @return iteration count
     */
    protected long getIteration() {
        return iteration;
    }

    protected void increaseIteration() {
        iteration++;
    }

    O getRandomOperation() {
        return selector.select();
    }

    Probe getWorkerProbe() {
        return workerProbe;
    }

    protected Metronome getWorkerMetronome() {
        return workerMetronome;
    }
}
