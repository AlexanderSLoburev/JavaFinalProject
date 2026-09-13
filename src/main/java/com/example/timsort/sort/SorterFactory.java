package com.example.timsort.sort;

import java.util.Comparator;
import java.util.List;

/**
 * Builds a {@link Sorter} bound to a concrete list and a concrete order —
 * the narrowest seam through which a parity sorter can delegate the
 * ordering work.
 *
 * Why a factory instead of a ready {@code Sorter} instance: this
 * codebase's Sorter contract is stateful — the data to sort is captured in
 * the sorter's constructor (see {@code TimSorter}). The list to order is,
 * however, different on every parity-sort call and only exists inside it,
 * so the delegate must be created after extraction, under the concrete
 * list and order. Passing the delegate's constructor keeps the delegation
 * itself (any Sorter implementation may be plugged in) while making the
 * per-call rebinding possible; the production wiring is a one-liner:
 * {@code TimSorter::new}.</p>
 *
 * @param <T> element type
 */
@FunctionalInterface
public interface SorterFactory<T> {

  /**
   * Returns a sorter whose {@link Sorter#sort() sort()} will produce a new
   * list with {@code data}'s current elements ordered by {@code order},
   * without modifying {@code data}.
   *
   * @param data the list whose elements the returned sorter will order
   * @param order the comparator defining the order
   * @return a sorter over {@code data} and {@code order}, never null
   */
  Sorter<T> create(List<T> data, Comparator<? super T> order);
}