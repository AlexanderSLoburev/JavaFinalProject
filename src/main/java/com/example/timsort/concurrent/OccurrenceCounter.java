package com.example.timsort.concurrent;

import java.util.List;

/**
 * Counts occurrences of a target element in a collection.
 *
 * @param <T> element type; {@code equals} defines the match
 */
public interface OccurrenceCounter<T> {

  /**
   * Counts how many elements of {@code data} are equal to {@code target}.
   *
   * @param data the data to scan; must not be structurally modified
   *        while the count is running (the subList view contract)
   * @param target the element to count; may be null — a null target
   *        counts null elements
   * @return the number of matches; {@code 0} for an empty list
   * @throws NullPointerException if data is null
   */
  long count(List<T> data, T target);
}
