package com.example.timsort.validation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable validation result: holds either a valid value or a list of errors.
 *
 * <p>Contract: a valid value implies an empty error list and vice versa.
 * A valid value is never null.</p>
 *
 * @param <T> type of the validated value
 */
public final class ValidationResult<T> {

  private final Optional<T> value;
  private final List<String> errors;

  private ValidationResult(Optional<T> value, List<String> errors) {
    this.value = Objects.requireNonNull(value);
    this.errors = List.copyOf(errors);
  }

  /**
   * Creates a successful result.
   *
   * @param value the valid value
   * @throws NullPointerException if value is null — a valid value is
   *         never null by contract
   */
  public static <T> ValidationResult<T> of(T value) {
    Objects.requireNonNull(value, "a valid value must not be null");

    return new ValidationResult<>(Optional.of(value), List.of());
  }

  /**
   * Creates a failed result.
   *
   * @param errors violations found; must be non-empty
   * @throws IllegalArgumentException if errors is null or empty
   */
  public static <T> ValidationResult<T> failure(List<String> errors) {
    if (errors == null || errors.isEmpty()) {
      throw new IllegalArgumentException(
          "failure requires a non-empty error list");
    }
    return new ValidationResult<>(Optional.empty(), errors);
  }

  /** @return true when validation succeeded */
  public boolean isValid() { return errors.isEmpty(); }

  /** @return unmodifiable error list; empty when valid */
  public List<String> errors() { return errors; }

  /** @return the valid value; an empty Optional when invalid */
  public Optional<T> value() { return value; }

  @Override
  public String toString() {
    return "ValidationResult[" +
        (isValid() ? "valid: " + value.map(String::valueOf).orElse("?")
                   : "errors: " + errors) +
        "]";
  }
}