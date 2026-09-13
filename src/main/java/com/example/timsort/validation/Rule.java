package com.example.timsort.validation;

import java.util.Optional;

/**
 * Functional interface of a single validation rule: takes an object and
 * returns an Optional holding the error message when the rule is violated.
 *
 * @param <T> type of the validated object
 */
@FunctionalInterface
public interface Rule<T> {

  /**
   * Applies the rule to the object.
   *
   * @param value object to check
   * @return Optional with an error message if the rule is violated;
   *         an empty Optional otherwise
   */
  Optional<String> apply(T value);
}