package com.ujcms.commons.misc;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * @author PONY
 */
public class LongArrayToStringSerializer extends JsonSerializer<long[]> {
    public static final LongArrayToStringSerializer INSTANCE = new LongArrayToStringSerializer();

    @Override
    public final void serialize(long[] value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        gen.writeStartArray();
        for (long val : value) {
            gen.writeString(String.valueOf(val));
        }
        gen.writeEndArray();
    }
}
