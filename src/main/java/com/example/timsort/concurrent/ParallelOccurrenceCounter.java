package com.example.timsort.concurrent;

import com.example.timsort.collection.CustomArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;


/**
 * Counts occurrences in parallel: splits the data into chunks and scans
 * them concurrently on the injected executor.
 *
 * <p>Instances are stateless (the executor is final and only borrowed),
 * so one counter may be shared and called from several threads.</p>
 *
 * <p>WHY an injected executor: the pool lifecycle (creation, shutdown)
 * belongs to the composition root; this class never closes it, which
 * also lets tests plug in their own pool.</p>
 */
public final class ParallelOccurrenceCounter<T>
    implements OccurrenceCounter<T> {

  private final ExecutorService executor;

  public ParallelOccurrenceCounter(ExecutorService executor) {
    this.executor =
        Objects.requireNonNull(executor, "executor must not be null");
  }

  @Override
  public long count(List<T> data, T target) {
    Objects.requireNonNull(data, "data must not be null");
    if (data.isEmpty()) {
      return 0;
    }

    // ceil(size / threads) == (size + threads - 1) / threads;
    // it yields roughly `threads` chunks, so every core gets one task.
    int threads = Runtime.getRuntime().availableProcessors();
    int chunkSize = (data.size() + threads - 1) / threads;

    List<CompletableFuture<Long>> futures = new CustomArrayList<>();
    for (int from = 0; from < data.size(); from += chunkSize) {
      int to = Math.min(from + chunkSize, data.size());
      List<T> chunk = data.subList(from, to);
      futures.add(CompletableFuture.supplyAsync(
          () -> countManually(chunk, target), executor));
    }

    long total = 0;
    for (CompletableFuture<Long> future : futures) {
      total += future.join();
    }
    return total;
  }

  /**
   * Manual per-chunk count: a plain loop with {@code equals} — no
   * ready-made counting utilities (Collections.frequency, stream
   * filters) per the task.
   *
   * <p>WHY the null-aware match: a null target counts null elements,
   * keeping the "match by equality" semantics uniform.</p>
   */
  private static <T> long countManually(List<T> chunk, T target) {
    long matches = 0;
    for (T item : chunk) {
      if (target == null ? item == null : target.equals(item)) {
        matches++;
      }
    }
    return matches;
  }
}
