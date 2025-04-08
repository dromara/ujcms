package com.ujcms.commons.lucene;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.FloatDocValues;

import java.io.IOException;
import java.util.Map;

/**
 * Exponential decay function.
 *
 * @author PONY
 */
public class ExpDecayValueSource extends ValueSource {
    /**
     * Source of timestamp values
     */
    private final ValueSource source;
    /**
     * "now" in milliseconds
     */
    private final long origin;
    /**
     * Decay scale (e.g., 7 days in milliseconds)
     */
    private final long scale;
    /**
     * Offset (e.g., 0 days in milliseconds)
     */
    private final long offset;
    /**
     * Decay factor (e.g., 0.5)
     */
    private final double decay;

    public ExpDecayValueSource(ValueSource source, long origin, long scale, long offset, double decay) {
        this.source = source;
        this.origin = origin;
        this.scale = scale;
        this.offset = offset;
        this.decay = decay;
    }

    @Override
    public FunctionValues getValues(Map context, LeafReaderContext readerContext) throws IOException {
        final FunctionValues vals =  source.getValues(context, readerContext);

        return new FloatDocValues(this) {
            @Override
            public float floatVal(int doc) throws IOException {
                long timestamp = vals.longVal(doc);
                // Distance from origin
                long distance = Math.abs(origin - timestamp);
                if (distance <= offset) {
                    // Within offset, full score
                    return 1.0f;
                }
                double normalizedDistance = (double) (distance - offset) / scale;
                // Exponential decay
                return (float) Math.pow(decay, normalizedDistance);
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExpDecayValueSource)) {
            return false;
        }
        ExpDecayValueSource other = (ExpDecayValueSource) o;
        return source.equals(other.source) &&
                origin == other.origin &&
                scale == other.scale &&
                offset == other.offset &&
                Double.compare(decay, other.decay) == 0;
    }

    @Override
    public int hashCode() {
        int result = source.hashCode();
        result = 31 * result + Long.hashCode(origin);
        result = 31 * result + Long.hashCode(scale);
        result = 31 * result + Long.hashCode(offset);
        result = 31 * result + Double.hashCode(decay);
        return result;
    }

    @Override
    public String description() {
        return "expDecay(" + source.description() + ")";
    }
}