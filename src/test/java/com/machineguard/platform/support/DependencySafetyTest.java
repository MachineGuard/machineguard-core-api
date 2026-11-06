package com.machineguard.platform.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.message.MapMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Library-level advisory regressions, without claiming an exposed application sink. */
class DependencySafetyTest {
    @Test void excessiveArrayDimensionsFailWithoutRecursiveExhaustion() {
        assertThrows(IllegalArgumentException.class,
                () -> ClassUtils.getClass("java.lang.String" + "[]".repeat(10_000)));
        assertEquals(String[].class, assertDoesNotThrow(() -> ClassUtils.getClass("java.lang.String[]")));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void mapMessageNonFiniteValuesRemainParseableJson(double value) {
        var message = new MapMessage<>().with("reading", value).with("source", "synthetic");
        var json = message.getFormattedMessage(new String[]{"JSON"});
        var tree = assertDoesNotThrow(() -> new ObjectMapper().readTree(json));
        assertEquals("synthetic", tree.get("source").asText());
        assertTrue(tree.has("reading"));
    }

    @Test void mapMessageFiniteValuesKeepTheirMeaning() throws Exception {
        var message = new MapMessage<>().with("reading", 42.5).with("source", "synthetic");
        var tree = new ObjectMapper().readTree(message.getFormattedMessage(new String[]{"JSON"}));
        assertEquals(42.5, tree.get("reading").asDouble());
        assertEquals("synthetic", tree.get("source").asText());
    }
}
