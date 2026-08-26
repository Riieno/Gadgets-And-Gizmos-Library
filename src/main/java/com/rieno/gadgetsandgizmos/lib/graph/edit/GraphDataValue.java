package com.rieno.gadgetsandgizmos.lib.graph.edit;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Preserve structured graph data without depending on Minecraft NBT
public sealed interface GraphDataValue permits GraphDataValue.NumericValue,
        GraphDataValue.StringValue, GraphDataValue.ListValue,
        GraphDataValue.CompoundValue, GraphDataValue.ByteArrayValue,
        GraphDataValue.IntArrayValue, GraphDataValue.LongArrayValue {
    // Supported numeric tag kinds
    enum NumberKind {
        BYTE,
        SHORT,
        INT,
        LONG,
        FLOAT,
        DOUBLE
    }

    // Preserve one numeric tag and its exact kind
    record NumericValue(NumberKind kind, Number value) implements GraphDataValue {
        public NumericValue {
            kind = Objects.requireNonNull(kind, "kind");
            value = normalize(kind, Objects.requireNonNull(value, "value"));
        }
    }

    // Preserve one string tag
    record StringValue(String value) implements GraphDataValue {
        public StringValue {
            value = value == null ? "" : value;
        }
    }

    // Preserve one homogeneous list tag
    record ListValue(List<GraphDataValue> values) implements GraphDataValue {
        public ListValue {
            values = List.copyOf(values == null ? List.of() : values);
            if (!values.isEmpty()) {
                String expected = tagKind(values.getFirst());
                for (GraphDataValue value : values) {
                    if (!expected.equals(tagKind(value))) {
                        throw new IllegalArgumentException(
                                "NBT list values must use one tag kind");
                    }
                }
            }
        }
    }

    // Preserve one compound tag
    record CompoundValue(Map<String, GraphDataValue> values) implements GraphDataValue {
        public CompoundValue {
            values = Map.copyOf(values == null ? Map.of() : values);
        }
    }

    // Preserve one byte array tag
    record ByteArrayValue(List<Byte> values) implements GraphDataValue {
        public ByteArrayValue {
            values = List.copyOf(values == null ? List.of() : values);
        }
    }

    // Preserve one integer array tag
    record IntArrayValue(List<Integer> values) implements GraphDataValue {
        public IntArrayValue {
            values = List.copyOf(values == null ? List.of() : values);
        }
    }

    // Preserve one long array tag
    record LongArrayValue(List<Long> values) implements GraphDataValue {
        public LongArrayValue {
            values = List.copyOf(values == null ? List.of() : values);
        }
    }

    // Normalize one numeric value to its declared tag kind
    private static Number normalize(NumberKind kind, Number value) {
        return switch (kind) {
            case BYTE -> new BigDecimal(value.toString()).byteValueExact();
            case SHORT -> new BigDecimal(value.toString()).shortValueExact();
            case INT -> new BigDecimal(value.toString()).intValueExact();
            case LONG -> new BigDecimal(value.toString()).longValueExact();
            case FLOAT -> {
                float result = value.floatValue();
                if (!Float.isFinite(result)) {
                    throw new IllegalArgumentException("A finite float is required");
                }
                yield result;
            }
            case DOUBLE -> {
                double result = value.doubleValue();
                if (!Double.isFinite(result)) {
                    throw new IllegalArgumentException("A finite double is required");
                }
                yield result;
            }
        };
    }

    // Get the NBT-compatible kind for one value
    private static String tagKind(GraphDataValue value) {
        if (value instanceof NumericValue numeric) return numeric.kind().name();
        return value.getClass().getName();
    }
}
