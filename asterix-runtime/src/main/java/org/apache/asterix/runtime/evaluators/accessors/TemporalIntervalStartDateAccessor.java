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
package org.apache.asterix.runtime.evaluators.accessors;

import java.io.DataOutput;
import java.io.IOException;

import org.apache.asterix.dataflow.data.nontagged.serde.AIntervalSerializerDeserializer;
import org.apache.asterix.formats.nontagged.AqlSerializerDeserializerProvider;
import org.apache.asterix.om.base.ADate;
import org.apache.asterix.om.base.AMutableDate;
import org.apache.asterix.om.base.ANull;
import org.apache.asterix.om.functions.AsterixBuiltinFunctions;
import org.apache.asterix.om.functions.IFunctionDescriptor;
import org.apache.asterix.om.functions.IFunctionDescriptorFactory;
import org.apache.asterix.om.types.ATypeTag;
import org.apache.asterix.om.types.BuiltinType;
import org.apache.asterix.om.types.EnumDeserializer;
import org.apache.asterix.runtime.evaluators.base.AbstractScalarFunctionDynamicDescriptor;
import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import org.apache.hyracks.algebricks.runtime.base.IScalarEvaluator;
import org.apache.hyracks.algebricks.runtime.base.IScalarEvaluatorFactory;
import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.api.dataflow.value.ISerializerDeserializer;
import org.apache.hyracks.data.std.api.IPointable;
import org.apache.hyracks.data.std.primitive.VoidPointable;
import org.apache.hyracks.data.std.util.ArrayBackedValueStorage;
import org.apache.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

public class TemporalIntervalStartDateAccessor extends AbstractScalarFunctionDynamicDescriptor {

    private static final long serialVersionUID = 1L;
    private static final FunctionIdentifier FID = AsterixBuiltinFunctions.ACCESSOR_TEMPORAL_INTERVAL_START_DATE;
    public static final IFunctionDescriptorFactory FACTORY = new IFunctionDescriptorFactory() {

        @Override
        public IFunctionDescriptor createFunctionDescriptor() {
            return new TemporalIntervalStartDateAccessor();
        }
    };

    @Override
    public IScalarEvaluatorFactory createEvaluatorFactory(final IScalarEvaluatorFactory[] args)
            throws AlgebricksException {
        return new IScalarEvaluatorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public IScalarEvaluator createScalarEvaluator(IHyracksTaskContext ctx) throws AlgebricksException {
                return new IScalarEvaluator() {
                    private final ArrayBackedValueStorage resultStorage = new ArrayBackedValueStorage();
                    private final DataOutput out = resultStorage.getDataOutput();
                    private final IPointable argPtr = new VoidPointable();
                    private final IScalarEvaluator eval = args[0].createScalarEvaluator(ctx);

                    // possible output
                    @SuppressWarnings("unchecked")
                    private final ISerializerDeserializer<ADate> dateSerde = AqlSerializerDeserializerProvider.INSTANCE
                            .getSerializerDeserializer(BuiltinType.ADATE);
                    private final AMutableDate aDate = new AMutableDate(0);
                    @SuppressWarnings("unchecked")
                    private final ISerializerDeserializer<ANull> nullSerde = AqlSerializerDeserializerProvider.INSTANCE
                            .getSerializerDeserializer(BuiltinType.ANULL);

                    @Override
                    public void evaluate(IFrameTupleReference tuple, IPointable result) throws AlgebricksException {
                        eval.evaluate(tuple, argPtr);
                        byte[] bytes = argPtr.getByteArray();
                        int startOffset = argPtr.getStartOffset();

                        resultStorage.reset();
                        try {
                            if (bytes[startOffset] == ATypeTag.SERIALIZED_NULL_TYPE_TAG) {
                                nullSerde.serialize(ANull.NULL, out);
                                result.set(resultStorage);
                                return;
                            } else if (bytes[startOffset] == ATypeTag.SERIALIZED_INTERVAL_TYPE_TAG) {
                                byte timeType = AIntervalSerializerDeserializer.getIntervalTimeType(bytes,
                                        startOffset + 1);
                                long startTime = AIntervalSerializerDeserializer.getIntervalStart(bytes,
                                        startOffset + 1);
                                if (timeType == ATypeTag.SERIALIZED_DATE_TYPE_TAG) {
                                    aDate.setValue((int) (startTime));
                                    dateSerde.serialize(aDate, out);
                                } else {
                                    throw new AlgebricksException(FID.getName()
                                            + ": expects NULL/INTERVAL(of DATE), but got INTERVAL(of "
                                            + EnumDeserializer.ATYPETAGDESERIALIZER.deserialize(timeType) + ")");
                                }
                            } else {
                                throw new AlgebricksException(FID.getName()
                                        + ": expects NULL/INTERVAL(of DATE), but got "
                                        + EnumDeserializer.ATYPETAGDESERIALIZER.deserialize(bytes[startOffset]));
                            }
                        } catch (IOException e) {
                            throw new AlgebricksException(e);
                        }
                        result.set(resultStorage);
                    }
                };
            }
        };
    }

    /* (non-Javadoc)
     * @see org.apache.asterix.om.functions.AbstractFunctionDescriptor#getIdentifier()
     */
    @Override
    public FunctionIdentifier getIdentifier() {
        return FID;
    }

}
