package com.example.timsort.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;


class ValidationResultTest {

  @Test
  void whenOfValidValue_thenHoldsValueAndNoErrors() {
    ValidationResult<String> result = ValidationResult.of("test");
    assertTrue(result.isValid());
    assertEquals(Optional.of("test"), result.value());
    assertTrue(result.errors().isEmpty());
  }

  @Test
  void whenOfNull_thenThrowsNpe() {
    // pins the contract: a valid value is never null
    assertThrows(NullPointerException.class, () -> ValidationResult.of(null));
  }

  @Test
  void whenFailure_thenHoldsErrorsAndNoValue() {
    ValidationResult<String> result =
        ValidationResult.failure(List.of("Ошибка 1", "Ошибка 2"));
    assertFalse(result.isValid());
    assertTrue(result.value().isEmpty());
    assertEquals(List.of("Ошибка 1", "Ошибка 2"), result.errors());
  }

  @Test
  void whenFailureWithNullOrEmptyList_thenThrowsIae() {
    assertThrows(IllegalArgumentException.class,
                 () -> ValidationResult.failure(null));
    assertThrows(IllegalArgumentException.class,
                 () -> ValidationResult.failure(List.of()));
  }

  @Test
  void whenFailureGivenMutableList_thenIsolatedFromSourceChanges() {
    List<String> source = new ArrayList<>(List.of("Ошибка 1"));
    ValidationResult<String> result = ValidationResult.failure(source);
    source.add("Ошибка 2");
    assertEquals(1, result.errors().size(),
                 "result must be isolated from the source list");
  }

  @Test
  void whenErrorsModified_thenUnsupportedOperation() {
    ValidationResult<String> result =
        ValidationResult.failure(List.of("Ошибка"));
    assertThrows(UnsupportedOperationException.class,
                 () -> result.errors().add("новая"));
  }
}