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
package org.apache.asterix.external.input.stream.factory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.asterix.common.exceptions.AsterixException;
import org.apache.asterix.external.api.AsterixInputStream;
import org.apache.asterix.external.api.IInputStreamFactory;
import org.apache.asterix.external.api.INodeResolver;
import org.apache.asterix.external.api.INodeResolverFactory;
import org.apache.asterix.external.input.stream.LocalFSInputStream;
import org.apache.asterix.external.util.ExternalDataConstants;
import org.apache.asterix.external.util.ExternalDataUtils;
import org.apache.asterix.external.util.FeedUtils;
import org.apache.asterix.external.util.NodeResolverFactory;
import org.apache.hyracks.algebricks.common.constraints.AlgebricksAbsolutePartitionConstraint;
import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.api.io.FileReference;
import org.apache.hyracks.dataflow.std.file.FileSplit;

public class LocalFSInputStreamFactory implements IInputStreamFactory {

    private static final long serialVersionUID = 1L;

    protected static final INodeResolver DEFAULT_NODE_RESOLVER = new NodeResolverFactory().createNodeResolver();
    protected static final Logger LOGGER = Logger.getLogger(LocalFSInputStreamFactory.class.getName());
    protected static INodeResolver nodeResolver;
    protected Map<String, String> configuration;
    protected FileSplit[] inputFileSplits;
    protected FileSplit[] feedLogFileSplits; // paths where instances of this feed can use as log storage
    protected boolean isFeed;
    protected String expression;
    // transient fields (They don't need to be serialized and transferred)
    private transient AlgebricksAbsolutePartitionConstraint constraints;

    @Override
    public AsterixInputStream createInputStream(IHyracksTaskContext ctx, int partition) throws HyracksDataException {
        try {
            return new LocalFSInputStream(inputFileSplits, ctx, configuration, partition, expression, isFeed);
        } catch (IOException e) {
            throw new HyracksDataException(e);
        }
    }

    @Override
    public DataSourceType getDataSourceType() {
        return DataSourceType.STREAM;
    }

    @Override
    public boolean isIndexible() {
        return false;
    }

    @Override
    public void configure(Map<String, String> configuration) throws AsterixException {
        this.configuration = configuration;
        String[] splits = configuration.get(ExternalDataConstants.KEY_PATH).split(",");
        configureFileSplits(splits);
        configurePartitionConstraint();
        this.isFeed = ExternalDataUtils.isFeed(configuration) && ExternalDataUtils.keepDataSourceOpen(configuration);
        if (isFeed) {
            feedLogFileSplits = FeedUtils.splitsForAdapter(ExternalDataUtils.getDataverse(configuration),
                    ExternalDataUtils.getFeedName(configuration), constraints);
        }
        this.expression = configuration.get(ExternalDataConstants.KEY_EXPRESSION);
    }

    @Override
    public AlgebricksAbsolutePartitionConstraint getPartitionConstraint() {
        return constraints;
    }

    private void configureFileSplits(String[] splits) throws AsterixException {
        if (inputFileSplits == null) {
            inputFileSplits = new FileSplit[splits.length];
            String nodeName;
            String nodeLocalPath;
            int count = 0;
            String trimmedValue;
            for (String splitPath : splits) {
                trimmedValue = splitPath.trim();
                if (!trimmedValue.contains("://")) {
                    throw new AsterixException(
                            "Invalid path: " + splitPath + "\nUsage- path=\"Host://Absolute File Path\"");
                }
                nodeName = trimmedValue.split(":")[0];
                nodeLocalPath = trimmedValue.split("://")[1];
                FileSplit fileSplit = new FileSplit(nodeName, new FileReference(new File(nodeLocalPath)));
                inputFileSplits[count++] = fileSplit;
            }
        }
    }

    private void configurePartitionConstraint() throws AsterixException {
        String[] locs = new String[inputFileSplits.length];
        String location;
        for (int i = 0; i < inputFileSplits.length; i++) {
            location = getNodeResolver().resolveNode(inputFileSplits[i].getNodeName());
            locs[i] = location;
        }
        constraints = new AlgebricksAbsolutePartitionConstraint(locs);
    }

    protected INodeResolver getNodeResolver() {
        if (nodeResolver == null) {
            synchronized (DEFAULT_NODE_RESOLVER) {
                if (nodeResolver == null) {
                    nodeResolver = initializeNodeResolver();
                }
            }
        }
        return nodeResolver;
    }

    private static INodeResolver initializeNodeResolver() {
        INodeResolver nodeResolver = null;
        String configuredNodeResolverFactory = System.getProperty(ExternalDataConstants.NODE_RESOLVER_FACTORY_PROPERTY);
        if (configuredNodeResolverFactory != null) {
            try {
                nodeResolver = ((INodeResolverFactory) (Class.forName(configuredNodeResolverFactory).newInstance()))
                        .createNodeResolver();

            } catch (Exception e) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING, "Unable to create node resolver from the configured classname "
                            + configuredNodeResolverFactory + "\n" + e.getMessage());
                }
                nodeResolver = DEFAULT_NODE_RESOLVER;
            }
        } else {
            nodeResolver = DEFAULT_NODE_RESOLVER;
        }
        return nodeResolver;
    }
}