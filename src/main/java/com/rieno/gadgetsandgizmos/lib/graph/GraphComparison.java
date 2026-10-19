package com.rieno.gadgetsandgizmos.lib.graph;

import java.util.Locale;

// Compare graph values by their meaning instead of their serialized representation
public final class GraphComparison{
    private GraphComparison(){}

    // Match a second operand to the first operand's wire or authored literal
    public static String operandType(String connectedType, String literalType){
        String type = connectedType == null || connectedType.isBlank() || "any".equals(connectedType)
                ? literalType : connectedType;
        if(type == null || type.isBlank() || "exec".equals(type)) return "any";
        return switch(type){
            case "integer", "int", "float", "double" -> "number";
            default -> type;
        };
    }

    // Normalize the supported symbolic and named comparison operators
    public static String normalizeOperator(String operator){
        if(operator == null) return "";
        String op = operator.trim().toLowerCase(Locale.ROOT);
        if(!op.matches("[<>=!~]+")) op = op.replace('-', ' ').replace('_', ' ').replaceAll("\\s+", " ");
        return switch(op){
            case "equal", "equals", "equal to", "eq", "is", "==", "===" -> "==";
            case "not equal", "not equals", "not equal to", "ne", "is not", "!=", "!==", "~=" -> "!=";
            case "less", "lt", "less than", "<" -> "<";
            case "less equal", "less or equal", "less than or equal", "less than or equal to", "less than or equals", "lte", "<=" -> "<=";
            case "greater", "gt", "greater than", ">" -> ">";
            case "greater equal", "greater or equal", "greater than or equal", "greater than or equal to", "greater than or equals", "gte", ">=" -> ">=";
            default -> "";
        };
    }

    // Compare numeric inputs, treating signed zero equally and NaN as unordered
    public static boolean numbers(double a, double b, String operator){
        String op = normalizeOperator(operator);
        if(Double.isNaN(a) || Double.isNaN(b)) return "!=".equals(op);
        return matches(op, a == b, a == b ? 0 : Double.compare(a, b));
    }

    // Apply an operator to equality and ordering supplied by a host's value model
    public static boolean matches(String operator, boolean equal, int ordering){
        return switch(normalizeOperator(operator)){
            case "==" -> equal;
            case "!=" -> !equal;
            case "<" -> ordering < 0;
            case "<=" -> ordering <= 0;
            case ">" -> ordering > 0;
            case ">=" -> ordering >= 0;
            default -> false;
        };
    }
}
