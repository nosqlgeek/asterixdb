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
package org.apache.asterix.runtime.evaluators.functions;

import java.io.DataOutput;

import org.apache.asterix.formats.nontagged.AqlSerializerDeserializerProvider;
import org.apache.asterix.om.base.ABoolean;
import org.apache.asterix.om.base.ANull;
import org.apache.asterix.om.types.ATypeTag;
import org.apache.asterix.om.types.BuiltinType;
import org.apache.asterix.om.types.EnumDeserializer;
import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import org.apache.hyracks.algebricks.runtime.base.IScalarEvaluator;
import org.apache.hyracks.algebricks.runtime.base.IScalarEvaluatorFactory;
import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.api.dataflow.value.ISerializerDeserializer;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.data.std.api.IPointable;
import org.apache.hyracks.data.std.primitive.UTF8StringPointable;
import org.apache.hyracks.data.std.primitive.VoidPointable;
import org.apache.hyracks.data.std.util.ArrayBackedValueStorage;
import org.apache.hyracks.dataflow.common.data.accessors.IFrameTupleReference;

public abstract class AbstractTripleStringBoolEval implements IScalarEvaluator {

    private ArrayBackedValueStorage resultStorage = new ArrayBackedValueStorage();
    private DataOutput dout = resultStorage.getDataOutput();
    private IPointable array0 = new VoidPointable();
    private IPointable array1 = new VoidPointable();
    private IPointable array2 = new VoidPointable();
    private IScalarEvaluator eval0;
    private IScalarEvaluator eval1;
    private IScalarEvaluator eval2;

    @SuppressWarnings("rawtypes")
    private ISerializerDeserializer boolSerde = AqlSerializerDeserializerProvider.INSTANCE
            .getSerializerDeserializer(BuiltinType.ABOOLEAN);
    @SuppressWarnings("unchecked")
    private final ISerializerDeserializer<ANull> nullSerde = AqlSerializerDeserializerProvider.INSTANCE
            .getSerializerDeserializer(BuiltinType.ANULL);

    private final FunctionIdentifier funcID;

    private final UTF8StringPointable strPtr1st = new UTF8StringPointable();
    private final UTF8StringPointable strPtr2nd = new UTF8StringPointable();
    private final UTF8StringPointable strPtr3rd = new UTF8StringPointable();

    public AbstractTripleStringBoolEval(IHyracksTaskContext context, IScalarEvaluatorFactory eval0,
            IScalarEvaluatorFactory eval1, IScalarEvaluatorFactory eval2, FunctionIdentifier funcID)
                    throws AlgebricksException {
        this.eval0 = eval0.createScalarEvaluator(context);
        this.eval1 = eval1.createScalarEvaluator(context);
        this.eval2 = eval2.createScalarEvaluator(context);
        this.funcID = funcID;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void evaluate(IFrameTupleReference tuple, IPointable result) throws AlgebricksException {
        eval0.evaluate(tuple, array0);
        eval1.evaluate(tuple, array1);
        eval2.evaluate(tuple, array2);

        resultStorage.reset();
        try {
            if (array0.getByteArray()[array0.getStartOffset()] == ATypeTag.SERIALIZED_NULL_TYPE_TAG
                    || array1.getByteArray()[array1.getStartOffset()] == ATypeTag.SERIALIZED_NULL_TYPE_TAG
                    || array2.getByteArray()[array2.getStartOffset()] == ATypeTag.SERIALIZED_NULL_TYPE_TAG) {
                nullSerde.serialize(ANull.NULL, dout);
                result.set(resultStorage);
                return;
            }

            if (array0.getByteArray()[array0.getStartOffset()] != ATypeTag.SERIALIZED_STRING_TYPE_TAG
                    || array1.getByteArray()[array1.getStartOffset()] != ATypeTag.SERIALIZED_STRING_TYPE_TAG
                    || array2.getByteArray()[array2.getStartOffset()] != ATypeTag.SERIALIZED_STRING_TYPE_TAG) {
                throw new AlgebricksException(
                        funcID.getName() + ": expects iput type (STRING/NULL, STRING/NULL, STRING) but got ("
                                + EnumDeserializer.ATYPETAGDESERIALIZER
                                        .deserialize(array0.getByteArray()[array0.getStartOffset()])
                                + ", "
                                + EnumDeserializer.ATYPETAGDESERIALIZER
                                        .deserialize(array1.getByteArray()[array1.getStartOffset()])
                                + ", " + EnumDeserializer.ATYPETAGDESERIALIZER
                                        .deserialize(array2.getByteArray()[array2.getStartOffset()])
                                + ")");
            }

        } catch (HyracksDataException e) {
            throw new AlgebricksException(e);
        }

        strPtr1st.set(array0.getByteArray(), array0.getStartOffset() + 1, array0.getLength());
        strPtr2nd.set(array1.getByteArray(), array1.getStartOffset() + 1, array1.getLength());
        strPtr3rd.set(array2.getByteArray(), array2.getStartOffset() + 1, array2.getLength());

        ABoolean res = compute(strPtr1st, strPtr2nd, strPtr3rd) ? ABoolean.TRUE : ABoolean.FALSE;
        try {
            boolSerde.serialize(res, dout);
        } catch (HyracksDataException e) {
            throw new AlgebricksException(e);
        }
        result.set(resultStorage);
    }

    protected abstract boolean compute(UTF8StringPointable strPtr1st, UTF8StringPointable strPtr2nd,
            UTF8StringPointable strPtr3rd) throws AlgebricksException;

}
