package com.example.timsort.source;

import com.example.timsort.collection.CustomArrayList;

/**
 * Strategy for filling a collection with data: implementations
 * provide elements of a given type.
 *
 * @param <T> the type of the generated or provided objects
 */
public interface DataSource<T> {

  /**
   * Requests up to {@code count} elements.
   *
   * <p>WHY "up to": implementations differ in how much they can
   * actually deliver. FileBusSource returns as many valid lines as the
   * file holds; ManualBusSource skips elements with exhausted attempts
   * and stops at EOF; RandomBusSource always returns exactly
   * {@code count}. Callers must not rely on the result size equaling
   * count unless the implementation documents that guarantee.</p>
   *
   * @param count the requested number of elements (must be >= 0)
   * @return the provided objects; never null, possibly fewer than requested
   * @throws IllegalArgumentException if count is negative
   */
  CustomArrayList<T> provide(int count);
}