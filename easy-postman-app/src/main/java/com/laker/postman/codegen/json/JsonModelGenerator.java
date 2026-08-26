package com.laker.postman.codegen.json;

import tools.jackson.databind.JsonNode;
import com.laker.postman.util.JsonUtil;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Generates editable model definitions from a JSON example. */
@UtilityClass
public class JsonModelGenerator {
    public static String generate(String json, String rootTypeName, JsonModelLanguage language) {
        JsonNode root = JsonUtil.readTree(json);
        Context context = new Context();
        Type rootType = infer(root, context, typeName(rootTypeName, "Response"));
        return switch (language) {
            case JAVA -> renderJava(context.models, rootType);
            case TYPESCRIPT -> renderTypeScript(context.models, rootType);
            case CSHARP -> renderCSharp(context.models, rootType);
        };
    }

    private static Type infer(JsonNode node, Context context, String suggestedName) {
        if (node == null || node.isNull()) return Type.UNKNOWN;
        if (node.isTextual()) return Type.STRING;
        if (node.isBoolean()) return Type.BOOLEAN;
        if (node.isIntegralNumber()) return Type.INTEGER;
        if (node.isNumber()) return Type.NUMBER;
        if (node.isArray()) {
            int limit = Math.min(node.size(), 100);
            Type element = Type.UNKNOWN;
            for (int i = 0; i < limit; i++) {
                element = merge(element, infer(node.get(i), context, suggestedName + "Item"), context);
            }
            return new Type(Kind.ARRAY, null, element, null);
        }
        if (node.isObject()) {
            String name = context.uniqueName(suggestedName);
            Model model = new Model(name);
            context.models.add(model);
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                model.fields.add(new Field(field.getKey(), infer(field.getValue(), context,
                        name + typeName(field.getKey(), "Value")), false));
            }
            return new Type(Kind.OBJECT, name, null, model);
        }
        return Type.UNKNOWN;
    }

    private static Type merge(Type left, Type right, Context context) {
        if (left.kind == Kind.UNKNOWN) return right;
        if (right.kind == Kind.UNKNOWN) return left;
        if (left.kind == Kind.OBJECT && right.kind == Kind.OBJECT) {
            mergeModels(left.model, right.model, context);
            return left;
        }
        if (left.kind == Kind.ARRAY && right.kind == Kind.ARRAY) {
            return new Type(Kind.ARRAY, null, merge(left.element, right.element, context), null);
        }
        if (left.kind == right.kind) return left;
        if ((left.kind == Kind.INTEGER && right.kind == Kind.NUMBER) || (left.kind == Kind.NUMBER && right.kind == Kind.INTEGER)) {
            return Type.NUMBER;
        }
        return Type.UNKNOWN;
    }

    private static void mergeModels(Model target, Model source, Context context) {
        if (target == source) return;
        Set<String> sourceNames = new LinkedHashSet<>();
        for (Field sourceField : source.fields) sourceNames.add(sourceField.jsonName);
        for (int i = 0; i < target.fields.size(); i++) {
            Field targetField = target.fields.get(i);
            if (!sourceNames.contains(targetField.jsonName)) {
                target.fields.set(i, new Field(targetField.jsonName, targetField.type, true));
            }
        }
        for (Field sourceField : source.fields) {
            int targetIndex = target.indexOf(sourceField.jsonName);
            if (targetIndex < 0) {
                target.fields.add(new Field(sourceField.jsonName, sourceField.type, true));
            } else {
                Field targetField = target.fields.get(targetIndex);
                target.fields.set(targetIndex, new Field(targetField.jsonName,
                        merge(targetField.type, sourceField.type, context), targetField.optional || sourceField.optional));
            }
        }
        context.models.remove(source);
    }

    private static String renderJava(List<Model> models, Type root) {
        StringBuilder out = new StringBuilder();
        if (root.kind == Kind.ARRAY) out.append("// Root JSON type: ").append(javaType(root)).append("\n\n");
        for (int modelIndex = 0; modelIndex < models.size(); modelIndex++) {
            Model model = models.get(modelIndex);
            out.append(modelIndex == 0 ? "public class " : "class ").append(model.name).append(" {\n");
            for (Field field : model.fields) {
                String javaName = identifier(field.jsonName, false, "value");
                out.append("    private ").append(javaType(field.type)).append(' ').append(javaName).append(";\n");
            }
            if (!model.fields.isEmpty()) out.append('\n');
            for (Field field : model.fields) {
                String fieldName = identifier(field.jsonName, false, "value");
                String accessor = typeName(fieldName, "Value");
                String type = javaType(field.type);
                out.append("    public ").append(type).append(" get").append(accessor).append("() {\n")
                        .append("        return ").append(fieldName).append(";\n")
                        .append("    }\n\n")
                        .append("    public void set").append(accessor).append('(').append(type).append(' ')
                        .append(fieldName).append(") {\n")
                        .append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n")
                        .append("    }\n");
                if (model.fields.indexOf(field) < model.fields.size() - 1) out.append('\n');
            }
            out.append("}\n\n");
        }
        return out.toString().trim();
    }

    private static String renderTypeScript(List<Model> models, Type root) {
        StringBuilder out = new StringBuilder();
        if (root.kind == Kind.ARRAY) out.append("export type ").append(root.element.name == null ? "Response" : root.element.name)
                .append("List = ").append(tsType(root)).append(";\n\n");
        for (Model model : models) {
            out.append("export interface ").append(model.name).append(" {\n");
            for (Field field : model.fields) {
                String name = identifier(field.jsonName, false, "value");
                String rendered = name.equals(field.jsonName) ? name : "'" + escape(field.jsonName) + "'";
                out.append("  ").append(rendered).append(field.optional ? "?: " : ": ").append(tsType(field.type)).append(";\n");
            }
            out.append("}\n\n");
        }
        return out.toString().trim();
    }

    private static String renderCSharp(List<Model> models, Type root) {
        StringBuilder out = new StringBuilder("using System.Collections.Generic;\nusing System.Text.Json.Serialization;\n\n");
        if (root.kind == Kind.ARRAY) out.append("// Root JSON type: ").append(csharpType(root)).append("\n\n");
        for (Model model : models) {
            out.append("public class ").append(model.name).append("\n{\n");
            for (Field field : model.fields) {
                String name = identifier(field.jsonName, true, "Value");
                if (!name.equals(field.jsonName)) out.append("    [JsonPropertyName(\"").append(escape(field.jsonName)).append("\")]\n");
                out.append("    public ").append(csharpType(field.type)).append(' ').append(name).append(" { get; set; }\n");
            }
            out.append("}\n\n");
        }
        return out.toString().trim();
    }

    private static String javaType(Type type) { return switch (type.kind) {
        case STRING -> "String"; case BOOLEAN -> "Boolean"; case INTEGER -> "Long"; case NUMBER -> "java.math.BigDecimal";
        case OBJECT -> type.name; case ARRAY -> "java.util.List<" + javaType(type.element) + ">"; case UNKNOWN -> "Object"; }; }
    private static String tsType(Type type) { return switch (type.kind) {
        case STRING -> "string"; case BOOLEAN -> "boolean"; case INTEGER, NUMBER -> "number"; case OBJECT -> type.name;
        case ARRAY -> tsType(type.element) + "[]"; case UNKNOWN -> "unknown"; }; }
    private static String csharpType(Type type) { return switch (type.kind) {
        case STRING -> "string"; case BOOLEAN -> "bool"; case INTEGER -> "long"; case NUMBER -> "decimal"; case OBJECT -> type.name;
        case ARRAY -> "List<" + csharpType(type.element) + ">"; case UNKNOWN -> "object"; }; }

    private static String typeName(String value, String fallback) { return identifier(value, true, fallback); }
    private static String identifier(String raw, boolean upperFirst, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String[] parts = raw.replaceAll("([a-z])([A-Z])", "$1 $2").split("[^A-Za-z0-9]+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) if (!part.isBlank()) result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        if (result.isEmpty()) result.append(fallback);
        if (!upperFirst) result.setCharAt(0, Character.toLowerCase(result.charAt(0)));
        if (Character.isDigit(result.charAt(0))) result.insert(0, '_');
        if (!upperFirst && JAVA_KEYWORDS.contains(result.toString())) result.append('_');
        return result.toString();
    }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }

    private enum Kind { STRING, BOOLEAN, INTEGER, NUMBER, OBJECT, ARRAY, UNKNOWN }
    private static final Set<String> JAVA_KEYWORDS = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally",
            "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "try", "void", "volatile", "while");
    private record Type(Kind kind, String name, Type element, Model model) {
        static final Type STRING = new Type(Kind.STRING, null, null, null); static final Type BOOLEAN = new Type(Kind.BOOLEAN, null, null, null);
        static final Type INTEGER = new Type(Kind.INTEGER, null, null, null); static final Type NUMBER = new Type(Kind.NUMBER, null, null, null);
        static final Type UNKNOWN = new Type(Kind.UNKNOWN, null, null, null);
    }
    private static final class Context {
        private final List<Model> models = new ArrayList<>(); private final Set<String> names = new LinkedHashSet<>();
        String uniqueName(String preferred) { String candidate = preferred; int index = 2; while (!names.add(candidate)) candidate = preferred + index++; return candidate; }
    }
    private static final class Model {
        private final String name;
        private final List<Field> fields = new ArrayList<>();
        Model(String name) { this.name = name; }
        int indexOf(String jsonName) {
            for (int i = 0; i < fields.size(); i++) if (fields.get(i).jsonName.equals(jsonName)) return i;
            return -1;
        }
    }
    private record Field(String jsonName, Type type, boolean optional) { }
}
