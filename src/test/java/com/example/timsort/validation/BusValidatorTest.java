package com.example.timsort.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.example.timsort.model.Bus;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;


class BusValidatorTest {

  private final BusValidator validator = new BusValidator();

  private Bus bus(int route, String model, long mileage) {
    return Bus.builder()
        .routeNumber(route)
        .model(model)
        .mileage(mileage)
        .build();
  }

  @Test
  void shouldAcceptValidBus() {
    Bus valid = bus(42, "ЛиАЗ-5292", 150_000);
    ValidationResult<Bus> result = validator.validate(valid);
    assertTrue(result.isValid());
    assertEquals(Optional.of(valid), result.value());
    assertTrue(result.errors().isEmpty());
  }

  @Test
  void shouldAcceptBoundaryValues() {
    assertTrue(validator.validate(bus(1, "AB", 0)).isValid());
    assertTrue(
        validator.validate(bus(999, "A".repeat(30), 2_000_000)).isValid());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 42, 999})
  void shouldAcceptRouteNumberBoundaries(int routeNumber) {
    assertTrue(validator.validate(bus(routeNumber, "ЛиАЗ-5292", 1)).isValid());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -100, 1000, 100_500})
  void shouldRejectRouteNumberOutsideRange(int routeNumber) {
    ValidationResult<Bus> result =
        validator.validate(bus(routeNumber, "ЛиАЗ-5292", 1));
    assertFalse(result.isValid());
    assertTrue(result.errors().stream().anyMatch(
        e -> e.toLowerCase(Locale.ROOT).contains("route")));
  }

  @ParameterizedTest(name = "[{index}] model=\"{0}\"")
  @MethodSource("validModels")
  void shouldAcceptValidModel(String model) {
    assertTrue(validator.validate(bus(42, model, 1)).isValid());
  }

  static Stream<String> validModels() {
    return Stream.of("AB", "ЛиАЗ-5292", "Ё-мобиль", "ё-мобиль", "ЛиАЗ 5292",
                     "A".repeat(30));
  }

  @ParameterizedTest(name = "[{index}] model={0}")
  @MethodSource("invalidModels")
  void shouldRejectInvalidModel(String model) {
    ValidationResult<Bus> result = validator.validate(bus(42, model, 1));
    assertFalse(result.isValid());
    assertTrue(result.errors().stream().anyMatch(
        e -> e.toLowerCase(Locale.ROOT).contains("model")));
  }

  static Stream<String> invalidModels() {
    return Stream.of(null, "", " ", "!", "A", "A".repeat(31), "ЛиАЗ_5292",
                     "model?");
  }

  @Test
  void shouldMeasureModelLengthAfterTrimming() {
    // " A" trims to "A" — a 1-character model, must fail the length check
    ValidationResult<Bus> result = validator.validate(bus(42, " A", 1));
    assertFalse(result.isValid());
    assertTrue(result.errors().stream().anyMatch(
        e -> e.toLowerCase(Locale.ROOT).contains("character")));
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 1, 2_000_000})
  void shouldAcceptMileageBoundaries(long mileage) {
    assertTrue(validator.validate(bus(42, "ЛиАЗ-5292", mileage)).isValid());
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, -100, 2_000_001, Long.MAX_VALUE})
  void shouldRejectMileageOutsideRange(long mileage) {
    ValidationResult<Bus> result =
        validator.validate(bus(42, "ЛиАЗ-5292", mileage));
    assertFalse(result.isValid());
    assertTrue(result.errors().stream().anyMatch(
        e -> e.toLowerCase(Locale.ROOT).contains("mileage")));
  }

  @Test
  void shouldCollectAllFieldErrors() {
    // model "!" violates the length check (1 < 2)
    ValidationResult<Bus> result = validator.validate(bus(0, "!", 2_000_001));
    assertFalse(result.isValid());
    assertEquals(3, result.errors().size());
    assertTrue(
        result.errors().get(0).toLowerCase(Locale.ROOT).contains("route"));
    assertTrue(
        result.errors().get(1).toLowerCase(Locale.ROOT).contains("model"));
    assertTrue(
        result.errors().get(2).toLowerCase(Locale.ROOT).contains("mileage"));
  }

  @Test
  void shouldReportAtMostOneErrorPerField() {
    // "!" violates both length and pattern — only the first is reported
    ValidationResult<Bus> result = validator.validate(bus(42, "!", 1));
    assertEquals(1, result.errors().size());
  }

  @Test
  void shouldFailOnNullBus() {
    ValidationResult<Bus> result = validator.validate(null);
    assertFalse(result.isValid());
    assertTrue(result.errors().stream().anyMatch(e -> e.contains("null")));
  }

  @Test
  void shouldAccumulateErrorsFromComposedValidators() {
    Validator<Bus> extra = other
        -> other.routeNumber() < 100
               ? ValidationResult.failure(
                     List.of("Route numbers below 100 are not served"))
               : ValidationResult.of(other);

    // route 0 is invalid for both: out of 1..999 and below 100
    ValidationResult<Bus> result =
        validator.and(extra).validate(bus(0, "ЛиАЗ-5292", 1));

    assertFalse(result.isValid());
    assertEquals(2, result.errors().size());
    assertTrue(
        result.errors().get(0).toLowerCase(Locale.ROOT).contains("route"));
    assertTrue(
        result.errors().get(1).toLowerCase(Locale.ROOT).contains("below"));
  }
}