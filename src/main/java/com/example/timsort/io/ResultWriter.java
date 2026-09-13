package com.example.timsort.io;

import java.util.List;

/**
 * Strategy for writing out the results of collection processing.
 *
 * <p>Implementations may persist data to a file, send it over the
 * network, print it to the console, etc. The contract of
 * {@link #appendAll(List)} is <b>appending</b>, not overwriting:
 * repeated calls accumulate the data.</p>
 *
 * @param <T> the type of the written elements
 */
public interface ResultWriter<T> {

  /**
   * Writes the given elements by adding them to the already
   * existing data (APPEND mode).
   *
   * <p>An empty list produces no data lines; whether a block header
   * is emitted is implementation-specific.</p>
   *
   * @param items the elements to write; must not be {@code null}
   * @throws NullPointerException if {@code items} is {@code null}
   */
  void appendAll(List<T> items);
}