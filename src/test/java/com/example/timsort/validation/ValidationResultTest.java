package com.example.timsort.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;


class ValidationResultTest {

  @Test
  void when_failureWithEmptyList_then_throwsException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> {
      ValidationResult.failure(List.of());
    }, "failure с пустым списком должен бросить исключение");
  }

  @Test
  void when_failureWithNullList_then_throwsException() {
    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> {
      ValidationResult.failure(null);
    }, "failure с null должен бросить исключение");
  }

  @Test
  void when_ofValidValue_then_isValidReturnsTrue() {
    // Arrange
    String value = "test";

    // Act
    ValidationResult<String> result = ValidationResult.of(value);

    // Assert
    assertTrue(result.isValid());
    assertEquals(value, result.value().orElse(null));
    assertTrue(result.errors().isEmpty());
  }

  @Test
  void when_failureWithErrors_then_isValidReturnsFalse() {
    // Arrange
    List<String> errors = List.of("Ошибка 1", "Ошибка 2");

    // Act
    ValidationResult<String> result = ValidationResult.failure(errors);

    // Assert
    assertFalse(result.isValid());
    assertEquals(2, result.errors().size());
    assertTrue(result.value().isEmpty());
  }

  @Test
  void when_failureReturnsUnmodifiableList_then_cannotModify() {
    // Arrange
    List<String> errors = List.of("Ошибка");
    ValidationResult<String> result = ValidationResult.failure(errors);

    // Act & Assert
    assertThrows(UnsupportedOperationException.class, () -> {
      result.errors().add("Новая ошибка");
    }, "Список ошибок должен быть неизменяемым");
  }
}