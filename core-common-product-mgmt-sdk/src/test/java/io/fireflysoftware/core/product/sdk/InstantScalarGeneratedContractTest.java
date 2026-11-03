package io.fireflysoftware.core.product.sdk;

import static org.junit.jupiter.api.Assertions.*;

import io.fireflysoftware.core.product.sdk.invoker.ApiClient;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.*;
import java.util.*;
import java.util.stream.Stream;

class InstantScalarGeneratedContractTest {
    private static final String[][] FIELDS = {
        {"ReadObservation", "databaseRecordedAt"},
        {"ReadObservation", "admissionValidUntil"},
        {"RootState", "createdAt"},
        {"RetainedRevision", "createdAt"},
        {"RetainedPublication", "createdAt"},
        {"ProductDefinitionCostView", "observedAt"},
        {"ProductDefinitionReviewSelectionView", "observedAt"}
    };

    static Stream<Arguments> instantVectors() {
        return Arrays.stream(FIELDS)
                .flatMap(
                        field ->
                                Stream.of(
                                                "2026-10-25T01:02:03.123456789Z",
                                                "2026-10-25T06:32:03.123456789+05:30",
                                                "2026-10-24T18:02:03.123456789-07:00")
                                        .map(value -> Arguments.of(field[0], field[1], value)));
    }

    static Stream<Arguments> noOffsetVectors() {
        return Arrays.stream(FIELDS).map(f -> Arguments.of(f[0], f[1]));
    }

    static Stream<Arguments> legacyVectors() {
        return Stream.of(
                Arguments.of("ProductVersionDTO", "effectiveDate"),
                Arguments.of("ProductVersionDTO", "dateCreated"),
                Arguments.of("ProductVersionDTO", "dateUpdated"),
                Arguments.of("ProductDocumentationDTO", "dateAdded"),
                Arguments.of("ProductDocumentationDTO", "dateCreated"),
                Arguments.of("ProductDocumentationDTO", "dateUpdated"));
    }

    private Class<?> type(String model) throws Exception {
        return Class.forName("io.fireflysoftware.core.product.sdk.model." + model);
    }

    private String getter(String field) {
        return "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }

    private String wire(String field, String value) {
        return "{\"" + field + "\":\"" + value + "\"}";
    }

    @ParameterizedTest
    @MethodSource("instantVectors")
    void actualDefaultMapperRetainsTheExactInstantInBothDirections(
            String model, String field, String value) throws Exception {
        var mapper = new ApiClient().getObjectMapper();
        var decoded = mapper.readValue(wire(field, value), type(model));
        var actual = type(model).getMethod(getter(field)).invoke(decoded);
        assertEquals(Instant.class, type(model).getMethod(getter(field)).getReturnType());
        assertEquals(
                OffsetDateTime.parse(value).toInstant(), assertInstanceOf(Instant.class, actual));
        var encoded = mapper.readTree(mapper.writeValueAsBytes(decoded)).get(field);
        assertTrue(encoded.isTextual());
        assertEquals(actual, Instant.parse(encoded.textValue()));
        assertTrue(encoded.textValue().endsWith("Z"));
    }

    @ParameterizedTest
    @MethodSource("noOffsetVectors")
    void instantWithoutOffsetDoesNotAcquireAGuessedTimezone(String model, String field) {
        var mapper = new ApiClient().getObjectMapper();
        assertThrows(
                com.fasterxml.jackson.databind.JsonMappingException.class,
                () -> mapper.readValue(wire(field, "2026-10-25T01:02:03.123456789"), type(model)));
    }

    @ParameterizedTest
    @MethodSource("legacyVectors")
    void legacyLocalDateTimeAbiAndValuesRemainUnchanged(String model, String field)
            throws Exception {
        var mapper = new ApiClient().getObjectMapper();
        String value = "2026-10-25T01:02:03.123456789";
        var decoded = mapper.readValue(wire(field, value), type(model));
        assertEquals(LocalDateTime.class, type(model).getMethod(getter(field)).getReturnType());
        assertEquals(
                LocalDateTime.parse(value), type(model).getMethod(getter(field)).invoke(decoded));
        assertEquals(
                value, mapper.readTree(mapper.writeValueAsBytes(decoded)).get(field).textValue());
    }
}
