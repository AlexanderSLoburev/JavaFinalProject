package com.example.timsort.sort;

import com.example.timsort.collection.CustomArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * {@link ParitySorter} that delegates the ordering itself to {@link Sorter}s
 * built per call by an injected {@link SorterFactory} — in production,
 * {@code TimSorter::new}.
 *
 * <p>Algorithm: collect the indices of elements whose key is even, extract
 * those elements, order them by the key with the delegate, and write them
 * back into the same indices. Elements with an odd key never move.
 * Example (keys in brackets): {@code [3, 10, 7, 2, 8, 5]} — even keys 10, 2,
 * 8 live at indices 1, 3, 4; sorted they become 2, 8, 10, so the result is
 * {@code [3, 2, 7, 8, 10, 5]}.</p>
 *
 * <p>Immutability and thread-safety are provided the injected factory: every
 * {@code create()} yields an independent sorter.</p>
 */
public final class TimParitySorter<T> implements ParitySorter<T> {

  private final SorterFactory<T> sorterFactory;
  private final ToIntFunction<T> keyExtractor;
  private final Comparator<T> keyComparator;

  /**
   * Creates a parity sorter that orders even-key elements with sorters from
   * {@code sorterFactory}.
   *
   * @param sorterFactory produces the delegate for each call; the production
   *     wiring is {@code TimSorter::new}
   * @param keyExtractor computes the numeric key that decides parity and
   *     defines the order of the moved elements
   * @throws NullPointerException if any argument is null
   */
  public TimParitySorter(SorterFactory<T> sorterFactory,
                         ToIntFunction<T> keyExtractor) {
    this.sorterFactory =
        Objects.requireNonNull(sorterFactory, "sorterFactory must not be null");
    this.keyExtractor =
        Objects.requireNonNull(keyExtractor, "keyExtractor must not be null");
    // A single comparator instance for all calls; it
    // is exactly the comparator the contract pins —
    // comparingInt(keyExtractor).
    this.keyComparator = Comparator.comparingInt(keyExtractor);
  }

  @Override
  public List<T> sort(List<T> data) {
    Objects.requireNonNull(data, "data must not be null");

    // The input list must never be modified, and the contract promises a fresh
    // result.
    List<T> result = new CustomArrayList<>(data);

    int[] evenIndices = IntStream.range(0, data.size())
                            .filter(i -> isEvenKey(data.get(i)))
                            .toArray();

    if (evenIndices.length < 2) {
      return result;
    }

    List<T> extracted =
        Arrays.stream(evenIndices)
            .mapToObj(data::get)
            .collect(Collectors.toCollection(
                () -> new CustomArrayList<>(evenIndices.length)));

    Sorter<T> delegate = sorterFactory.create(extracted, keyComparator);
    Objects.requireNonNull(delegate,
                           "sorterFactory must not return a null sorter");

    List<T> sorted = delegate.sort();

    if (sorted == null || sorted.size() != extracted.size()) {
      throw new IllegalStateException(
          "delegate broke its contract: expected a list of " +
          extracted.size() + " element(s), got " +
          (sorted == null ? "null" : sorted.size()));
    }

    for (int k = 0; k < evenIndices.length; k++) {
      result.set(evenIndices[k], sorted.get(k));
    }

    return result;
  }

  private boolean isEvenKey(T element) {
    return keyExtractor.applyAsInt(element) % 2 == 0;
  }
}