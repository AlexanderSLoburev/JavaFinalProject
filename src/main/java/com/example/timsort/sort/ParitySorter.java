package com.example.timsort.sort;

import java.util.List;

/**
 * Reorders a list "by parity of key": only elements whose numeric key is even
 * take part in ordering and are written back into their original indices;
 * elements with an odd key stay anchored to their positions.
 *
 * @param <T> element type
 */
public interface ParitySorter<T> {

  /**
   * Returns a new list of the same size and content as {@code data} where the
   * even-key elements are ordered by ascending key and occupy their original
   * indices; odd-key elements are untouched.
   *
   * <p>Contract:
   * <ul>
   *   <li>{@code data} is never modified (it may be immutable);</li>
   *   <li>the result is a fresh, independent copy;</li>
   *   <li>{@code data} must not contain nulls — the key extractor would fail
   *       with a NullPointerException, but that is already assumed to be
   *       invariant;</li>
   *    <li>a null {@code data} yields a NullPointerException.</li>
   * </ul>
   *
   * @param data source list
   * @return reordered copy of {@code data}
   */
  List<T> sort(List<T> data);
}