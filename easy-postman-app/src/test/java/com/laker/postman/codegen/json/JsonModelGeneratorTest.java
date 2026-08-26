package com.laker.postman.codegen.json;

import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

public class JsonModelGeneratorTest {
    private static final String SAMPLE = """
            {"user_id": 7, "profile": {"display-name": "Ada"}, "active": true}
            """;

    @Test
    public void shouldGenerateDependencyFreeJavaModel() {
        String code = JsonModelGenerator.generate(SAMPLE, "UserResponse", JsonModelLanguage.JAVA);

        assertTrue(code.contains("public class UserResponse"));
        assertTrue(!code.contains("com.fasterxml.jackson"));
        assertTrue(!code.contains("@JsonProperty"));
        assertTrue(code.contains("private Long userId"));
        assertTrue(code.contains("class UserResponseProfile"));
        assertTrue(code.contains("public Long getUserId() {\n        return userId;\n    }"));
        assertTrue(code.contains("public void setUserId(Long userId) {\n        this.userId = userId;\n    }"));
    }

    @Test
    public void shouldGenerateTypeScriptAndCSharpModels() {
        String typeScript = JsonModelGenerator.generate(SAMPLE, "UserResponse", JsonModelLanguage.TYPESCRIPT);
        String csharp = JsonModelGenerator.generate(SAMPLE, "UserResponse", JsonModelLanguage.CSHARP);

        assertTrue(typeScript.contains("export interface UserResponse"));
        assertTrue(typeScript.contains("'display-name': string"));
        assertTrue(csharp.contains("public class UserResponse"));
        assertTrue(csharp.contains("[JsonPropertyName(\"user_id\")]"));
    }

    @Test
    public void shouldDescribeTheRootTypeForAnArray() {
        String code = JsonModelGenerator.generate("[{\"id\": 1}, {\"id\": 2}]", "User", JsonModelLanguage.TYPESCRIPT);

        assertTrue(code.contains("export type UserItemList = UserItem[]"));
        assertTrue(code.contains("export interface UserItem"));
    }

    @Test
    public void shouldMergeFieldsFromArrayObjectSamplesAndEscapeJavaKeywords() {
        String code = JsonModelGenerator.generate("[{\"id\": 1, \"class\": \"a\"}, {\"id\": 2, \"name\": \"b\"}]",
                "User", JsonModelLanguage.JAVA);

        assertTrue(code.contains("private Long id"));
        assertTrue(code.contains("private String class_"));
        assertTrue(code.contains("private String name"));
        assertTrue(!code.contains("class UserItem2"));
    }

    @Test
    public void shouldMarkFieldsMissingFromSomeArraySamplesAsOptionalInTypeScript() {
        String code = JsonModelGenerator.generate("[{\"id\": 1}, {\"id\": 2, \"name\": \"Ada\"}]",
                "User", JsonModelLanguage.TYPESCRIPT);

        assertTrue(code.contains("name?: string"));
    }
}
