package com.vdt.aiops.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the prose-tolerant JSON-array extractor used to parse LLM output. */
class JsonExtractorTest {

    @Test
    void extractsPlainArray() {
        assertThat(JsonExtractor.extractArray("[{\"a\":1}]")).isEqualTo("[{\"a\":1}]");
    }

    @Test
    void unwrapsMarkdownFence() {
        String fenced = "```json\n[{\"service\":\"redis\"}]\n```";
        assertThat(JsonExtractor.extractArray(fenced)).isEqualTo("[{\"service\":\"redis\"}]");
    }

    @Test
    void skipsProseBracketsBeforeTheArray() {
        // "[POSTGRES ERROR]" is prose, not an object array -> must be skipped.
        String raw = "Logs show [POSTGRES ERROR] then. Result: [{\"x\":1}]";
        assertThat(JsonExtractor.extractArray(raw)).isEqualTo("[{\"x\":1}]");
    }

    @Test
    void ignoresBracketsInsideStringValues() {
        String raw = "[{\"msg\":\"failed [E01] retry\"}]";
        assertThat(JsonExtractor.extractArray(raw)).isEqualTo(raw);
    }

    @Test
    void keepsNestedArraysInsideObjects() {
        String raw = "[{\"ids\":[1,2,3]}]";
        assertThat(JsonExtractor.extractArray(raw)).isEqualTo(raw);
    }

    @Test
    void dropsTrailingProseAfterArray() {
        String raw = "[{\"a\":1}] — that is my final answer.";
        assertThat(JsonExtractor.extractArray(raw)).isEqualTo("[{\"a\":1}]");
    }

    @Test
    void returnsEmptyArrayForEmptyInput() {
        String empty = "[]";
        assertThat(JsonExtractor.extractArray(empty)).isEqualTo("[]");
    }

    @Test
    void returnsEmptyArrayWhenNoArrayPresent() {
        assertThat(JsonExtractor.extractArray("no json here at all")).isEqualTo("[]");
    }

    @Test
    void returnsEmptyArrayForNullOrBlank() {
        assertThat(JsonExtractor.extractArray(null)).isEqualTo("[]");
        assertThat(JsonExtractor.extractArray("   ")).isEqualTo("[]");
    }

    @Test
    void returnsEmptyArrayForUnclosedArray() {
        assertThat(JsonExtractor.extractArray("[{\"a\":1}")).isEqualTo("[]");
    }
}
