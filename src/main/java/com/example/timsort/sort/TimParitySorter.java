package com.example.timsort.sort;

import com.example.timsort.collection.CustomArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.ToLongFunction;

public class TimParitySorter<T> implements ParitySorter<T> {

  private final Sorter<T> delegate;
  private final ToLongFunction<T> keyExtractor;
  private final Comparator<T> comparator;

  public TimParitySorter(Sorter<T> delegate, ToLongFunction<T> keyExtractor) {
    this.delegate = Objects.requireNonNull(delegate);
    this.keyExtractor = Objects.requireNonNull(keyExtractor);
    this.comparator = Comparator.comparingLong(keyExtractor);
  }

  @Override
  public List<T> sort(List<T> data) {
    Objects.requireNonNull(data);

    List<T> result = new CustomArrayList<>(data);

    CustomArrayList<Integer> evenIndices = new CustomArrayList<>();
    CustomArrayList<T> evenElements = new CustomArrayList<>();

    for (int i = 0; i < result.size(); i++) {
      T element = result.get(i);
      if (keyExtractor.applyAsLong(element) % 2 == 0) {
        evenIndices.add(i);
        evenElements.add(element);
      }
    }

    if (evenElements.isEmpty()) {
      return result;
    }

    List<T> sortedEven = new TimSorter<>(evenElements, comparator).sort();

    for (int i = 0; i < evenIndices.size(); i++) {
      result.set(evenIndices.get(i), sortedEven.get(i));
    }

    return result;
  }
}
