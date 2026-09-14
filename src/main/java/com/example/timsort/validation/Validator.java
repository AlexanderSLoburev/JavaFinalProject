package com.example.timsort.validation;

import com.example.timsort.collection.CustomArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validator contract with composition support.
 *
 * @param <T> type of the validated object
 */
@FunctionalInterface
public interface Validator<T> {

  /**
   * Validates the object. Implementations are expected to return a
   * failure result for null input instead of throwing.
   */
  ValidationResult<T> validate(T value);

  /**
   * Combines this validator with another one, accumulating errors.
   *
   * <p>Both validators always run, so the caller
   * receives every violation at once. Errors of {@code this} validator
   * come first, then the errors of {@code other}.</p>
   *
   * @param other the other validator
   * @return a new validator applying both
   * @throws NullPointerException if other is null
   */
  default Validator<T> and(Validator<T> other) {
    Objects.requireNonNull(other, "other validator must not be null");

    return value -> {
      ValidationResult<T> first = this.validate(value);
      ValidationResult<T> second = other.validate(value);

      if (first.isValid() && second.isValid()) {
        return first;
      }

      List<String> allErrors = new CustomArrayList<>(first.errors());
      allErrors.addAll(second.errors());
      return ValidationResult.failure(allErrors);
    };
  }
}