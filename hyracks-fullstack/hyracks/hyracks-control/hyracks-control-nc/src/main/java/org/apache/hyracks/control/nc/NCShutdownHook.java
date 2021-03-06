/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.hyracks.control.nc;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shutdown hook that invokes {@link NodeControllerService#stop() stop} method.
 * This shutdown hook must have a failsafe mechanism to halt the process in case the shutdown
 * operation is hanging for any reason
 */
public class NCShutdownHook extends Thread {

    public static final int FAILED_TO_STARTUP_EXIT_CODE = 2;
    private static final Logger LOGGER = Logger.getLogger(NCShutdownHook.class.getName());
    private static final long SHUTDOWN_WAIT_TIME = 10 * 60 * 1000L;
    private final Thread watchDog;
    private final NodeControllerService nodeControllerService;
    private volatile Thread shutdownHookThread;

    public NCShutdownHook(NodeControllerService nodeControllerService) {
        super("ShutdownHook-" + nodeControllerService.getId());
        this.nodeControllerService = nodeControllerService;
        watchDog = new Thread(watch(), "ShutdownHookWatchDog-" + nodeControllerService.getId());
    }

    private Runnable watch() {
        return () -> {
            try {
                shutdownHookThread.join(SHUTDOWN_WAIT_TIME); // 10 min
                if (shutdownHookThread.isAlive()) {
                    try {
                        LOGGER.info("Watchdog is angry. Killing shutdown hook");
                    } finally {
                        Runtime.getRuntime().halt(66); // NOSONAR last resort
                    }
                }
            } catch (Throwable th) { // NOSONAR must catch them all
                Runtime.getRuntime().halt(77); // NOSONAR last resort
            }
        };
    }

    @Override
    public void run() {
        try {
            try {
                LOGGER.info("Shutdown hook called");
            } catch (Throwable th) {//NOSONAR
            }
            shutdownHookThread = Thread.currentThread();
            watchDog.start();
            nodeControllerService.stop();
        } catch (Throwable th) { // NOSONAR... This is fine since this is shutdown hook
            LOGGER.log(Level.WARNING, "Exception in executing shutdown hook", th);
        }
    }
}