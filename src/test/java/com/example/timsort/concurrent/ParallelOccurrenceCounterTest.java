package com.example.timsort.concurrent;

import static org.junit.jupiter.api.Assertions.*;

import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for ParallelOccurrenceCounter on the domain type Bus: exact
 * counts for known data (matches go through Bus.equals, not identity),
 * agreement with an independent sequential reference on various sizes,
 * null semantics, correct executor usage and pool lifecycle in tests.
 *
 * <p>The counter is an aggregation layer, not a semantic one: the data
 * does not have to pass BusValidator.</p>
 */
class ParallelOccurrenceCounterTest {

  private static final int TARGET_ROUTE = 42;
  private static final String TARGET_MODEL = "ЛиАЗ-5292";
  private static final long TARGET_MILEAGE = 150_000L;

  private ExecutorService pool;
  private ParallelOccurrenceCounter<Bus> counter;

  @BeforeEach
  void setUp() {
    // Tests supply their own pool — playing the same role that the composition
    // root plays in production.
    pool = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors());
    counter = new ParallelOccurrenceCounter<>(pool);
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    // Pool is properly shut down by its owner — in tests the owner is the test
    pool.shutdown();
    if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
      pool.shutdownNow();
    }
  }

  // -------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------

  /**
   * A bus equal to the search target. A fresh instance on every call:
   * the count must rely on {@code equals}, not on reference identity.
   */
  private static Bus targetBus() {
    return Bus.builder()
        .routeNumber(TARGET_ROUTE)
        .model(TARGET_MODEL)
        .mileage(TARGET_MILEAGE)
        .build();
  }

  /**
   * A bus distinct from the target in every field (route >= 1000,
   * unique model, different mileage) — the value never collides.
   */
  private static Bus otherBus(int i) {
    return Bus.builder()
        .routeNumber(1000 + i)
        .model("МАЗ-" + i)
        .mileage(i)
        .build();
  }

  /** Every period-th element is a fresh target instance, the rest are other buses. */
  private static CustomArrayList<Bus> busesWithPeriod(int size, int period) {
    CustomArrayList<Bus> data = new CustomArrayList<>();
    for (int i = 0; i < size; i++) {
      data.add(i % period == 0 ? targetBus() : otherBus(i));
    }
    return data;
  }

  /** Independent sequential reference: also a manual loop. */
  private static long sequentialCount(List<Bus> data, Bus target) {
    long count = 0;
    for (Bus item : data) {
      if (target == null ? item == null : target.equals(item)) {
        count++;
      }
    }
    return count;
  }

  /** Count of indices i in [0, size) with i % period == 0 — ceil(size / period). */
  private static long expectedPeriodMatches(int size, int period) {
    return (size + period - 1) / period;
  }

  // -------------------------------------------------------------------
  // Exact values
  // -------------------------------------------------------------------

  @Test
  void when_emptyCollection_then_countIsZero() {
    assertEquals(0, counter.count(new CustomArrayList<>(), targetBus()));
  }

  @Test
  void when_singleElementEqualsTarget_then_countIsOne() {
    CustomArrayList<Bus> data = new CustomArrayList<>();
    data.add(targetBus());
    assertEquals(1, counter.count(data, targetBus()));
  }

  @Test
  void when_singleElementDiffers_then_countIsZero() {
    CustomArrayList<Bus> data = new CustomArrayList<>();
    data.add(otherBus(0));
    assertEquals(0, counter.count(data, targetBus()));
  }

  @Test
  void when_smallCollectionWithKnownMatches_then_exactCount() {
    // 10 elements, target at every 3rd position: indices 0, 3, 6, 9
    assertEquals(4, counter.count(busesWithPeriod(10, 3), targetBus()));
  }

  @Test
  void when_largeCollectionWithKnownMatches_then_exactCount() {
    // 10 000 elements, target at every 7th position: ceil(10000 / 7) = 1429
    assertEquals(1_429, counter.count(busesWithPeriod(10_000, 7), targetBus()));
  }

  @Test
  void when_allElementsMatchTarget_then_countEqualsSize() {
    // All 10 000 are distinct instances, equal by value:
    // aggregation must work through equals, not references
    CustomArrayList<Bus> data = new CustomArrayList<>();
    for (int i = 0; i < 10_000; i++) {
      data.add(targetBus());
    }
    assertEquals(10_000, counter.count(data, targetBus()));
  }

  @Test
  void when_targetAbsent_then_countIsZero() {
    CustomArrayList<Bus> data = new CustomArrayList<>();
    for (int i = 0; i < 10_000; i++) {
      data.add(otherBus(i));
    }
    assertEquals(0, counter.count(data, targetBus()));
  }

  // -------------------------------------------------------------------
  // Comparison with sequential reference at various sizes
  // -------------------------------------------------------------------

  @ParameterizedTest(name = "size = {0}")
  @ValueSource(ints = {0, 1, 2, 3, 5, 17, 100, 1_000, 10_000})
  void when_anySize_then_resultMatchesSequentialCount(int size) {
    CustomArrayList<Bus> data = busesWithPeriod(size, 3);
    long parallel = counter.count(data, targetBus());
    assertEquals(sequentialCount(data, targetBus()), parallel,
                 "Parallel count must match sequential count");
    // sanity: the reference counts what we intended
    assertEquals(expectedPeriodMatches(size, 3), parallel);
  }

  // -------------------------------------------------------------------
  // Value-semantics of Bus (including "wide" instances with null fields)
  // -------------------------------------------------------------------

  @Test
  void when_equalBusesByValue_then_allCounted() {
    CustomArrayList<Bus> data = new CustomArrayList<>();
    data.add(targetBus());
    data.add(otherBus(1));
    data.add(targetBus());
    data.add(otherBus(2));
    assertEquals(2, counter.count(data, targetBus()),
                 "Distinct instances with identical fields count as occurrences");
  }

  @Test
  void when_busModelIsNull_then_stillComparedByValue() {
    // Bus is a wide holder: null model is allowed, equals uses
    // Objects.equals, so two such instances are equal by value
    CustomArrayList<Bus> data = new CustomArrayList<>();
    data.add(Bus.builder().routeNumber(1).model(null).mileage(5).build());
    data.add(Bus.builder().routeNumber(2).model("X").mileage(5).build());
    data.add(Bus.builder().routeNumber(1).model(null).mileage(5).build());
    Bus nullModelTarget =
        Bus.builder().routeNumber(1).model(null).mileage(5).build();

    assertEquals(2, counter.count(data, nullModelTarget));
  }

  // -------------------------------------------------------------------
  // null-semantics of the list itself
  // -------------------------------------------------------------------

  @Test
  void when_nullTarget_then_countsNullElements() {
    // WHY java.util.ArrayList: contract — java.util.List; some
    // implementations may not allow null elements
    List<Bus> withNulls = new ArrayList<>();
    withNulls.add(otherBus(1));
    withNulls.add(null);
    withNulls.add(null);
    withNulls.add(otherBus(2));
    assertEquals(2, counter.count(withNulls, null));
  }

  @Test
  void when_nullElementsButNonNullTarget_then_nullsNotCounted() {
    List<Bus> withNulls = new ArrayList<>();
    withNulls.add(null);
    withNulls.add(targetBus());
    assertEquals(1, counter.count(withNulls, targetBus()));
  }

  // -------------------------------------------------------------------
  // Executor: counting runs on pool threads
  // (not on Bus: the test needs an instrumented equals)
  // -------------------------------------------------------------------

  /**
   * Element whose equals records the thread that performed the comparison.
   */
  private static final class RecordingElement {
    final Set<String> threads;

    RecordingElement(Set<String> threads) { this.threads = threads; }

    @Override
    public boolean equals(Object o) {
      threads.add(Thread.currentThread().getName());
      return o instanceof RecordingElement;
    }

    @Override
    public int hashCode() {
      return 1; // not used by the counter, kept for the equals contract
    }
  }

  @Test
  void when_countRuns_then_comparisonsHappenOnExecutorThreads() {
    Set<String> threads = ConcurrentHashMap.newKeySet(); // concurrent writers
    CustomArrayList<RecordingElement> data = new CustomArrayList<>();
    for (int i = 0; i < 10_000; i++) {
      data.add(new RecordingElement(threads));
    }
    RecordingElement target = new RecordingElement(threads);

    // WHY a local counter: the field is typed with the domain Bus, and
    // this test needs its own instrumented element type
    ParallelOccurrenceCounter<RecordingElement> recordingCounter =
        new ParallelOccurrenceCounter<>(pool);

    assertEquals(10_000, recordingCounter.count(data, target));
    // join guarantees happens-before: all writes are visible after count.
    // Comparisons ran in the pool, not in the test thread:
    assertFalse(threads.contains(Thread.currentThread().getName()),
                "Counting must run on executor threads, not on the " +
                "calling thread");
  }

  @Test
  void when_singleThreadExecutor_then_resultIsStillCorrect()
      throws InterruptedException {
    // Robustness: algorithm is correct for any pool size
    ExecutorService single = Executors.newSingleThreadExecutor();
    try {
      ParallelOccurrenceCounter<Bus> singleCounter =
          new ParallelOccurrenceCounter<>(single);
      CustomArrayList<Bus> data = busesWithPeriod(10_000, 5);
      assertEquals(sequentialCount(data, targetBus()),
                   singleCounter.count(data, targetBus()));
    } finally {
      single.shutdown();
      assertTrue(single.awaitTermination(5, TimeUnit.SECONDS),
                 "Pool must terminate after shutdown");
    }
  }

  // -------------------------------------------------------------------
  // Contracts
  // -------------------------------------------------------------------

  @Test
  void when_nullData_then_throwsNpe() {
    assertThrows(NullPointerException.class,
                 () -> counter.count(null, targetBus()));
  }

  @Test
  void when_nullExecutorInConstructor_then_throwsNpe() {
    assertThrows(NullPointerException.class,
                 () -> new ParallelOccurrenceCounter<Bus>(null));
  }
}
