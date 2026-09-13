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
    // Тесты подставляют собственный пул — ту же роль, что composition
    // root играет в проде.
    pool = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors());
    counter = new ParallelOccurrenceCounter<>(pool);
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    // Пул корректно завершается владельцем — в тестах владельцем является тест
    pool.shutdown();
    if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
      pool.shutdownNow();
    }
  }

  // -------------------------------------------------------------------
  // Хелперы
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

  /** Каждый period-й элемент — новый экземпляр target, остальные — прочие. */
  private static CustomArrayList<Bus> busesWithPeriod(int size, int period) {
    CustomArrayList<Bus> data = new CustomArrayList<>();
    for (int i = 0; i < size; i++) {
      data.add(i % period == 0 ? targetBus() : otherBus(i));
    }
    return data;
  }

  /** Независимый последовательный эталон: тоже ручной цикл. */
  private static long sequentialCount(List<Bus> data, Bus target) {
    long count = 0;
    for (Bus item : data) {
      if (target == null ? item == null : target.equals(item)) {
        count++;
      }
    }
    return count;
  }

  /** Число индексов i в [0, size) с i % period == 0 — ceil(size / period). */
  private static long expectedPeriodMatches(int size, int period) {
    return (size + period - 1) / period;
  }

  // -------------------------------------------------------------------
  // Точные значения
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
    // 10 элементов, target на каждом 3-м месте: индексы 0, 3, 6, 9
    assertEquals(4, counter.count(busesWithPeriod(10, 3), targetBus()));
  }

  @Test
  void when_largeCollectionWithKnownMatches_then_exactCount() {
    // 10 000 элементов, target на каждом 7-м месте: ceil(10000 / 7) = 1429
    assertEquals(1_429, counter.count(busesWithPeriod(10_000, 7), targetBus()));
  }

  @Test
  void when_allElementsMatchTarget_then_countEqualsSize() {
    // Все 10 000 — разные экземпляры, равные по значению:
    // агрегация обязана работать через equals, а не ссылки
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
  // Сверка с последовательным эталоном на разных размерах
  // -------------------------------------------------------------------

  @ParameterizedTest(name = "size = {0}")
  @ValueSource(ints = {0, 1, 2, 3, 5, 17, 100, 1_000, 10_000})
  void when_anySize_then_resultMatchesSequentialCount(int size) {
    CustomArrayList<Bus> data = busesWithPeriod(size, 3);
    long parallel = counter.count(data, targetBus());
    assertEquals(sequentialCount(data, targetBus()), parallel,
                 "Параллельный подсчёт должен совпасть с последовательным");
    // sanity: сам эталон считает то, что мы задумали
    assertEquals(expectedPeriodMatches(size, 3), parallel);
  }

  // -------------------------------------------------------------------
  // Value-семантика Bus (включая «широкие» экземпляры с null-полями)
  // -------------------------------------------------------------------

  @Test
  void when_equalBusesByValue_then_allCounted() {
    CustomArrayList<Bus> data = new CustomArrayList<>();
    data.add(targetBus());
    data.add(otherBus(1));
    data.add(targetBus());
    data.add(otherBus(2));
    assertEquals(2, counter.count(data, targetBus()),
                 "Разные экземпляры с одинаковыми полями — это вхождения");
  }

  @Test
  void when_busModelIsNull_then_stillComparedByValue() {
    // Bus — широкий носитель: null-модель допустима, equals использует
    // Objects.equals, поэтому два таких экземпляра равны по значению
    CustomArrayList<Bus> data = new CustomArrayList<>();
    data.add(Bus.builder().routeNumber(1).model(null).mileage(5).build());
    data.add(Bus.builder().routeNumber(2).model("X").mileage(5).build());
    data.add(Bus.builder().routeNumber(1).model(null).mileage(5).build());
    Bus nullModelTarget =
        Bus.builder().routeNumber(1).model(null).mileage(5).build();

    assertEquals(2, counter.count(data, nullModelTarget));
  }

  // -------------------------------------------------------------------
  // null-семантика самого списка
  // -------------------------------------------------------------------

  @Test
  void when_nullTarget_then_countsNullElements() {
    // WHY java.util.ArrayList: контракт — java.util.List; некоторые
    // реализации могут не допускать null-элементов
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
  // Executor: подсчёт выполняется на потоках пула
  // (не на Bus: тесту нужен инструментированный equals)
  // -------------------------------------------------------------------

  /**
   * Элемент, чей equals фиксирует поток, в котором выполнялось сравнение.
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
    // join гарантирует happens-before: все записи видны после count.
    // Сравнения выполнялись в пуле, а не в потоке теста:
    assertFalse(threads.contains(Thread.currentThread().getName()),
                "Подсчёт должен выполняться на потоках executor'а, а не " +
                "вызывающего потока");
  }

  @Test
  void when_singleThreadExecutor_then_resultIsStillCorrect()
      throws InterruptedException {
    // Робастность: алгоритм корректен при любом размере пула
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
                 "Пул должен завершиться после shutdown");
    }
  }

  // -------------------------------------------------------------------
  // Контракты
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
