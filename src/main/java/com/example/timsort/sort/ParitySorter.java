package com.example.timsort.sort;

import java.util.List;

public interface ParitySorter<T> {
  List<T> sort(List<T> data);
}
