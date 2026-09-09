package com.example.timsort.sort;

import com.example.timsort.collection.CustomArrayList;
import java.util.Collection;
import java.util.Objects;

public class TimSorter<T> {
  private static final int MIN_MERGE_SIZE = 32;
  private CustomArrayList<T> data;
  private int minRunLength;

  private int calcMinRunLength(int size) {
    if (size < MIN_MERGE_SIZE) {
      // A trivial case: Sort the entire array using insertion sort.
      return 1;
    }
    return 1;
  }

  public TimSorter(Collection<? extends T> collection) {
    Objects.requireNonNull(collection);
    data = new CustomArrayList<>(collection);
  }
}
