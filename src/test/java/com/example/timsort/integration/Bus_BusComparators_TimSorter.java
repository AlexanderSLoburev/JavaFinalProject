package com.example.timsort.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;
import com.example.timsort.sort.BusComparators;
import com.example.timsort.sort.BusField;
import com.example.timsort.sort.TimSorter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Tests for {@link TimSorter} over Bus elements and {@link BusComparators}. */
class TimSorterIntegrationTest {

  private static final Comparator<Bus> BY_ROUTE =
      BusComparators.byField(BusField.ROUTE_NUMBER);
  private static final Comparator<Bus> BY_MODEL =
      BusComparators.byField(BusField.MODEL);
  private static final Comparator<Bus> BY_MILEAGE =
      BusComparators.byField(BusField.MILEAGE);

  // ------------------------------------------------------------------
  // Correctness per field
  // ------------------------------------------------------------------

  @Test
  void when_sortedByRouteNumber_then_resultIsInAscendingRouteOrder() {
    List<Bus> input =
        Arrays.asList(bus(10, "LiAZ", 1_000), bus(2, "Ikarus", 500),
                      bus(33, "PAZ", 7_500), bus(7, "GAZ", 0));

    assertEquals(Arrays.asList(bus(2, "Ikarus", 500), bus(7, "GAZ", 0),
                               bus(10, "LiAZ", 1_000), bus(33, "PAZ", 7_500)),
                 new TimSorter<>(input, BY_ROUTE).sort());
  }

  @Test
  void when_sortedByModel_then_resultIsInLexicographicModelOrder() {
    List<Bus> input = Arrays.asList(bus(3, "Volvo", 900), bus(1, "GAZ", 100),
                                    bus(2, "Ikarus", 500));

    assertEquals(Arrays.asList(bus(1, "GAZ", 100), bus(2, "Ikarus", 500),
                               bus(3, "Volvo", 900)),
                 new TimSorter<>(input, BY_MODEL).sort());
  }

  @Test
  void when_sortedByMileage_then_resultIsInAscendingMileageOrder() {
    List<Bus> input = Arrays.asList(bus(1, "Volvo", 900), bus(2, "GAZ", 100),
                                    bus(3, "Ikarus", 500));

    assertEquals(Arrays.asList(bus(2, "GAZ", 100), bus(3, "Ikarus", 500),
                               bus(1, "Volvo", 900)),
                 new TimSorter<>(input, BY_MILEAGE).sort());
  }

  @Test
  void when_fieldValuesSpanTheirFullRanges_then_sortedCorrectly() {
    // No naive subtraction anywhere: the extreme values of int and long must
    // order by value, not by overflow artefacts.
    List<Bus> input =
        Arrays.asList(bus(Integer.MAX_VALUE, "LiAZ", Long.MAX_VALUE),
                      bus(0, "GAZ", 0), bus(Integer.MAX_VALUE, "Ikarus", 0));

    List<Bus> expected =
        Arrays.asList(bus(0, "GAZ", 0), bus(Integer.MAX_VALUE, "Ikarus", 0),
                      bus(Integer.MAX_VALUE, "LiAZ", Long.MAX_VALUE));

    assertEquals(expected, new TimSorter<>(input, BY_ROUTE).sort());
    assertEquals(expected, new TimSorter<>(input, BY_MILEAGE).sort());
  }

  // ------------------------------------------------------------------
  // Tie-breaks of the registered comparators
  // ------------------------------------------------------------------

  @Test
  void when_sortedByRouteNumber_then_modelBreaksTiesThenMileage() {
    List<Bus> tieByModel =
        Arrays.asList(bus(5, "LiAZ", 100), bus(5, "Ikarus", 100));
    assertEquals(Arrays.asList(bus(5, "Ikarus", 100), bus(5, "LiAZ", 100)),
                 new TimSorter<>(tieByModel, BY_ROUTE).sort());

    List<Bus> tieByMileage =
        Arrays.asList(bus(5, "Ikarus", 200), bus(5, "Ikarus", 100));
    assertEquals(Arrays.asList(bus(5, "Ikarus", 100), bus(5, "Ikarus", 200)),
                 new TimSorter<>(tieByMileage, BY_ROUTE).sort());
  }

  @Test
  void when_sortedByModel_then_routeNumberBreaksTiesThenMileage() {
    List<Bus> tieByRoute =
        Arrays.asList(bus(9, "Ikarus", 50), bus(3, "Ikarus", 50));
    assertEquals(Arrays.asList(bus(3, "Ikarus", 50), bus(9, "Ikarus", 50)),
                 new TimSorter<>(tieByRoute, BY_MODEL).sort());

    List<Bus> tieByMileage =
        Arrays.asList(bus(5, "Ikarus", 200), bus(5, "Ikarus", 100));
    assertEquals(Arrays.asList(bus(5, "Ikarus", 100), bus(5, "Ikarus", 200)),
                 new TimSorter<>(tieByMileage, BY_MODEL).sort());
  }

  @Test
  void when_sortedByMileage_then_routeNumberBreaksTiesThenModel() {
    List<Bus> tieByRoute = Arrays.asList(bus(9, "PAZ", 50), bus(3, "LiAZ", 50));
    assertEquals(Arrays.asList(bus(3, "LiAZ", 50), bus(9, "PAZ", 50)),
                 new TimSorter<>(tieByRoute, BY_MILEAGE).sort());

    List<Bus> tieByModel =
        Arrays.asList(bus(5, "LiAZ", 50), bus(5, "Ikarus", 50));
    assertEquals(Arrays.asList(bus(5, "Ikarus", 50), bus(5, "LiAZ", 50)),
                 new TimSorter<>(tieByModel, BY_MILEAGE).sort());
  }

  // ------------------------------------------------------------------
  // Source contract: untouched, snapshot-per-call, independent result
  // ------------------------------------------------------------------

  @Test
  void when_sortCompletes_then_sourceListUnchanged() {
    List<Bus> source = new ArrayList<>(50);
    for (int i = 0; i < 50; i++) {
      source.add(bus(i, "LiAZ", i));
    }
    Collections.shuffle(source, new Random(3));
    List<Bus> snapshot = new ArrayList<>(source);

    new TimSorter<>(source, BY_MILEAGE).sort();
    new TimSorter<>(source, BY_MODEL).sort();
    new TimSorter<>(source, BY_ROUTE).sort();

    assertEquals(snapshot, source);
  }

  @Test
  void when_sortCompletes_then_resultIsIndependentOfSource() {
    List<Bus> source =
        new ArrayList<>(Arrays.asList(bus(2, "B", 20), bus(1, "A", 10)));
    TimSorter<Bus> sorter = new TimSorter<>(source, BY_ROUTE);

    List<Bus> result = sorter.sort();
    assertNotSame(source, result);

    result.clear();                        // the result is ours to mutate...
    assertEquals(2, source.size());        // ...the source feels nothing
    assertEquals(2, sorter.sort().size()); // ...and future sorts neither
  }

  @Test
  void when_sourceMutatedBetweenSorts_then_eachCallSortsCurrentContents() {
    List<Bus> source =
        new ArrayList<>(Arrays.asList(bus(2, "B", 20), bus(1, "A", 10)));
    TimSorter<Bus> sorter = new TimSorter<>(source, BY_ROUTE);

    List<Bus> first = sorter.sort();
    assertEquals(Arrays.asList(bus(1, "A", 10), bus(2, "B", 20)), first);

    source.add(bus(0, "C", 30));
    List<Bus> second = sorter.sort();
    assertEquals(
        Arrays.asList(bus(0, "C", 30), bus(1, "A", 10), bus(2, "B", 20)),
        second);
    assertEquals(2, first.size()); // earlier results are frozen snapshots
  }

  @Test
  void when_sourceIsImmutable_then_sortSucceedsWithoutTouchingIt() {
    // List.of rejects any mutation: an in-place implementation would fail
    // here with UnsupportedOperationException.
    List<Bus> immutable =
        List.of(bus(3, "Volvo", 30), bus(1, "GAZ", 10), bus(2, "Ikarus", 20));

    assertEquals(Arrays.asList(bus(1, "GAZ", 10), bus(2, "Ikarus", 20),
                               bus(3, "Volvo", 30)),
                 new TimSorter<>(immutable, BY_ROUTE).sort());
  }

  @Test
  void when_sourceIsLinkedList_then_resultSortedCorrectly() {
    List<Bus> source = new LinkedList<>();
    source.add(bus(5, "Volvo", 50));
    source.add(bus(1, "GAZ", 10));
    source.add(bus(3, "Ikarus", 30));

    assertEquals(referenceSorted(source, BY_ROUTE),
                 new TimSorter<>(source, BY_ROUTE).sort());
  }

  @Test
  void when_sourceIsCustomArrayList_then_resultSortedAndSourceUntouched() {
    CustomArrayList<Bus> source = new CustomArrayList<>();
    for (int i = 0; i < 40; i++) {
      source.add(
          bus((i * 17) % 40, "LiAZ", i)); // a permutation of routes 0..39
    }
    List<Bus> snapshot = new ArrayList<>(source);

    assertEquals(referenceSorted(snapshot, BY_ROUTE),
                 new TimSorter<>(source, BY_ROUTE).sort());
    assertEquals(snapshot, source);
  }

  // ------------------------------------------------------------------
  // Stability
  // ------------------------------------------------------------------

  @Test
  void when_comparatorReportsEqualElements_then_originalOrderPreserved() {
    // byModelOnly is NOT a total order: equal models compare 0 while the
    // routeNumbers still distinguish the elements, making order violations
    // visible to assertEquals.
    Comparator<Bus> byModelOnly = Comparator.comparing(Bus::model);
    List<Bus> input =
        Arrays.asList(bus(1, "B", 0), bus(2, "A", 0), bus(3, "B", 0),
                      bus(4, "C", 0), bus(5, "A", 0), bus(6, "B", 0));

    assertEquals(Arrays.asList(bus(2, "A", 0),
                               bus(5, "A", 0), // A group in insertion order
                               bus(1, "B", 0), bus(3, "B", 0),
                               bus(6, "B", 0), // B group in insertion order
                               bus(4, "C", 0)),
                 new TimSorter<>(input, byModelOnly).sort());
  }

  @Test
  void when_descendingRunEndsInEqualElements_then_tieKeepsOriginalOrder() {
    // The strictly-descending run [D, C, B(3)] must stop BEFORE B(4):
    // an implementation that reverses a merely weakly-descending run would
    // swap the two equal-by-comparator B's (route 4 before route 3).
    Comparator<Bus> byModelOnly = Comparator.comparing(Bus::model);
    List<Bus> input =
        Arrays.asList(bus(1, "D", 0), bus(2, "C", 0), bus(3, "B", 0),
                      bus(4, "B", 0), bus(5, "A", 0));

    assertEquals(Arrays.asList(bus(5, "A", 0), bus(3, "B", 0), bus(4, "B", 0),
                               bus(2, "C", 0), bus(1, "D", 0)),
                 new TimSorter<>(input, byModelOnly).sort());
  }

  @Test
  @Timeout(60)
  void when_randomTieHeavyInput_then_stableOrderMatchesReferenceSort() {
    Comparator<Bus> byModelOnly = Comparator.comparing(Bus::model);
    Random random = new Random(777);
    for (int trial = 0; trial < 30; trial++) {
      List<Bus> input = randomBuses(random, 1 + random.nextInt(1_200));
      assertEquals(referenceSorted(input, byModelOnly),
                   new TimSorter<>(input, byModelOnly).sort());
    }
  }

  // ------------------------------------------------------------------
  // Edge sizes, structured patterns, randomized cross-check
  // ------------------------------------------------------------------

  @Test
  void when_sourceIsEmpty_then_resultIsEmpty() {
    List<Bus> empty = List.of();
    List<Bus> result = new TimSorter<>(empty, BY_ROUTE).sort();
    assertTrue(result.isEmpty());
    assertEquals(empty, result);
  }

  @Test
  void when_sourceHasSingleElement_then_resultContainsOnlyIt() {
    List<Bus> single = List.of(bus(42, "Solo", 1_000));
    assertEquals(single, new TimSorter<>(single, BY_MODEL).sort());
  }

  @Test
  void when_sourceHasTwoElements_then_orderKeptOrFixed() {
    List<Bus> inOrder = Arrays.asList(bus(1, "LiAZ", 0), bus(2, "Ikarus", 0));
    assertEquals(inOrder, new TimSorter<>(inOrder, BY_ROUTE).sort());

    List<Bus> swapped = Arrays.asList(bus(2, "LiAZ", 0), bus(1, "Ikarus", 0));
    assertEquals(Arrays.asList(bus(1, "Ikarus", 0), bus(2, "LiAZ", 0)),
                 new TimSorter<>(swapped, BY_ROUTE).sort());
  }

  @Test
  void when_inputAlreadySorted_then_resultEqualsInput() {
    List<Bus> input = new ArrayList<>(50);
    for (int i = 0; i < 50; i++) {
      input.add(bus(i, "LiAZ", i));
    }
    assertEquals(input, new TimSorter<>(input, BY_ROUTE).sort());
  }

  @Test
  void when_inputStrictlyDescending_then_resultAscending() {
    List<Bus> input = new ArrayList<>(50);
    List<Bus> expected = new ArrayList<>(50);
    for (int i = 0; i < 50; i++) {
      input.add(bus(49 - i, "LiAZ", i));    // routes 49..0
      expected.add(bus(i, "LiAZ", 49 - i)); // routes 0..49, matching mileages
    }
    assertEquals(expected, new TimSorter<>(input, BY_ROUTE).sort());
  }

  @Test
  void when_allElementsCompareEqual_then_resultEqualsInputAndSizePreserved() {
    List<Bus> input = new ArrayList<>(100);
    for (int i = 0; i < 100; i++) {
      input.add(bus(7, "LiAZ", 100));
    }
    List<Bus> result = new TimSorter<>(input, BY_ROUTE).sort();
    assertEquals(100, result.size());
    assertEquals(input, result);
  }

  @Test
  void when_structuredPatternsInput_then_matchReferenceSort() {
    List<List<Bus>> patterns = Arrays.asList(
        sawtooth(300),     // repeated ascending ramps of 50
        pipeOrgan(300),    // ascending half mirrored into a descending half
        fewDistinct(300)); // 24 distinct combinations, heavy ties
    for (List<Bus> pattern : patterns) {
      for (Comparator<Bus> comparator :
           Arrays.asList(BY_ROUTE, BY_MODEL, BY_MILEAGE)) {
        assertEquals(referenceSorted(pattern, comparator),
                     new TimSorter<>(pattern, comparator).sort());
      }
    }
  }

  @Test
  @Timeout(60)
  void when_sizesSpanMinMergeBoundaries_then_matchReferenceSort() {
    // 31/32/33 straddle MIN_MERGE=32; 119/120 and 1541/1542 straddle the
    // run-stack sizing thresholds; 2^k-1/2^k pairs stress merge balance.
    int[] sizes = {0,   1,   2,   3,   5,   31,  32,  33,  63,   64,  65,
                   119, 120, 121, 127, 128, 255, 256, 999, 1541, 1542};
    Random random = new Random(99);
    for (int size : sizes) {
      List<Bus> input = randomBuses(random, size);
      assertEquals(referenceSorted(input, BY_ROUTE),
                   new TimSorter<>(input, BY_ROUTE).sort());
      assertEquals(referenceSorted(input, BY_MODEL),
                   new TimSorter<>(input, BY_MODEL).sort());
      assertEquals(referenceSorted(input, BY_MILEAGE),
                   new TimSorter<>(input, BY_MILEAGE).sort());
    }
  }

  @Test
  @Timeout(120)
  void
  when_randomInputSortedByEveryField_then_matchReferenceAndSourceUntouched() {
    Random random = new Random(20240601);
    for (int trial = 0; trial < 40; trial++) {
      List<Bus> input = randomBuses(random, random.nextInt(1_500));
      List<Bus> inputSnapshot = new ArrayList<>(input);

      for (BusField field : BusField.values()) {
        Comparator<Bus> comparator = BusComparators.byField(field);
        assertEquals(referenceSorted(input, comparator),
                     new TimSorter<>(input, comparator).sort());
      }
      assertEquals(inputSnapshot, input); // three sorts later, still untouched
    }
  }

  // ------------------------------------------------------------------
  // Adaptivity (the property that makes this TimSort, not just a sort)
  // ------------------------------------------------------------------

  @Test
  void when_inputSortedOrReversed_then_comparisonCountStaysNearLinear() {
    int n = 2_000;
    List<Bus> ascending = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      ascending.add(bus(i, "LiAZ", i));
    }

    ComparisonCounter ascendingCounter = new ComparisonCounter(BY_ROUTE);
    assertEquals(ascending,
                 new TimSorter<>(ascending, ascendingCounter).sort());
    assertTrue(ascendingCounter.calls <= 2L * n,
               "one detected run, no merges expected; comparisons=" +
                   ascendingCounter.calls);

    List<Bus> descending = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      descending.add(bus(n - 1 - i, "LiAZ", i));
    }
    ComparisonCounter descendingCounter = new ComparisonCounter(BY_ROUTE);
    assertEquals(referenceSorted(descending, BY_ROUTE),
                 new TimSorter<>(descending, descendingCounter).sort());
    assertTrue(descendingCounter.calls <= 2L * n,
               "one detected descending run, no merges expected; comparisons=" +
                   descendingCounter.calls);

    List<Bus> shuffled = new ArrayList<>(ascending);
    Collections.shuffle(shuffled, new Random(13));
    ComparisonCounter shuffledCounter = new ComparisonCounter(BY_ROUTE);
    assertEquals(ascending, new TimSorter<>(shuffled, shuffledCounter).sort());
    assertTrue(
        shuffledCounter.calls >= 4L * n,
        "shuffled input must pay for run creation and merges; comparisons=" +
            shuffledCounter.calls);
  }

  // ------------------------------------------------------------------
  // Constructors, nulls, natural ordering
  // ------------------------------------------------------------------

  @Test
  void when_sourceIsNull_then_nullPointerException() {
    assertThrows(NullPointerException.class, () -> new TimSorter<>(null));
    assertThrows(
        NullPointerException.class,
        () -> new TimSorter<>(null, BusComparators.byField(BusField.MODEL)));
  }

  @Test
  void when_naturalOrderWithNonComparableElements_then_classCastException() {
    // Bus does not implement Comparable: the documented contract is CCE at
    // the first comparison.
    List<Bus> buses = Arrays.asList(bus(1, "LiAZ", 0), bus(2, "Ikarus", 0));
    assertThrows(ClassCastException.class, () -> new TimSorter<>(buses).sort());
  }

  @Test
  void
  when_naturalOrderWithSingleNonComparableElement_then_noComparisonHappens() {
    List<Bus> single = List.of(bus(7, "Solo", 42));
    assertEquals(single, new TimSorter<>(single).sort());
  }

  @Test
  void when_naturalOrderWithComparableWrapper_then_resultSortedAscending() {
    List<RouteOrder> input = Arrays.asList(new RouteOrder(bus(9, "LiAZ", 0)),
                                           new RouteOrder(bus(2, "Ikarus", 0)),
                                           new RouteOrder(bus(5, "PAZ", 0)));
    List<RouteOrder> result = new TimSorter<>(input).sort();

    List<Integer> routes = new ArrayList<>();
    for (RouteOrder item : result) {
      routes.add(item.bus.routeNumber());
    }
    assertEquals(Arrays.asList(2, 5, 9), routes);
  }

  @Test
  void
  when_nullElementUnderByFieldComparator_then_nullPointerExceptionPropagates() {
    // Null handling is the comparator's decision; the registered ones
    // reject nulls — the failure must simply propagate.
    List<Bus> withNull = new ArrayList<>(
        Arrays.asList(bus(1, "LiAZ", 0), null, bus(2, "Ikarus", 0)));
    assertThrows(NullPointerException.class,
                 () -> new TimSorter<>(withNull, BY_ROUTE).sort());
  }

  @Test
  void when_comparatorToleratesNullElements_then_nullsSortedLast() {
    Comparator<Bus> nullsLast =
        Comparator.nullsLast(Comparator.comparing(Bus::model));
    List<Bus> input =
        Arrays.asList(bus(1, "LiAZ", 0), null, bus(2, "GAZ", 0), null);

    assertEquals(Arrays.asList(bus(2, "GAZ", 0), bus(1, "LiAZ", 0), null, null),
                 new TimSorter<>(input, nullsLast).sort());
  }

  // ------------------------------------------------------------------
  // Exception behavior of the comparator
  // ------------------------------------------------------------------

  @Test
  void when_comparatorThrows_then_exceptionPropagatesAndSourceUntouched() {
    List<Bus> source = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      source.add(bus(i, "LiAZ", 0));
    }
    List<Bus> snapshot = new ArrayList<>(source);

    Comparator<Bus> exploding = (left, right) -> {
      throw new IllegalStateException("comparator failed");
    };
    IllegalStateException failure =
        assertThrows(IllegalStateException.class,
                     () -> new TimSorter<>(source, exploding).sort());
    assertEquals("comparator failed", failure.getMessage());
    assertEquals(snapshot,
                 source); // the failing sort left no trace in the source
  }

  @Test
  @Timeout(30)
  void
  when_comparatorViolatesItsContract_then_sortCompletesOrReportsViolation() {
    // A random-result comparator breaks antisymmetry and transitivity. Both
    // outcomes are acceptable: a completed sort whose result is still a
    // permutation of the input, or the documented contract-violation report.
    // Anything else (AIOOBE, a hang, lost elements) fails this test.
    Random random = new Random(4242);
    for (int trial = 0; trial < 15; trial++) {
      List<Bus> input = randomBuses(random, 400);
      Comparator<Bus> inconsistent = (left, right) -> random.nextInt(5) - 2;
      try {
        List<Bus> result = new TimSorter<>(input, inconsistent).sort();
        assertEquals(input.size(), result.size());
        assertEquals(referenceSorted(input, BY_ROUTE),
                     referenceSorted(result, BY_ROUTE));
      } catch (IllegalArgumentException violation) {
        assertEquals("Comparison method violates its general contract!",
                     violation.getMessage());
      }
    }
  }

  // ------------------------------------------------------------------
  // Thread-safety of a shared sorter
  // ------------------------------------------------------------------

  @Test
  @Timeout(60)
  void when_sorterSharedByThreads_then_everyCallReturnsCorrectResult()
      throws Exception {
    List<Bus> source = randomBuses(new Random(5150), 300);
    Comparator<Bus> comparator = BY_MODEL;
    List<Bus> expected = referenceSorted(source, comparator);

    TimSorter<Bus> sorter = new TimSorter<>(source, comparator);
    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      List<Future<Void>> checks = new ArrayList<>();
      for (int t = 0; t < 4; t++) {
        checks.add(pool.submit(() -> {
          for (int call = 0; call < 25; call++) {
            assertEquals(expected, sorter.sort());
          }
          return null;
        }));
      }
      for (Future<Void> check : checks) {
        check.get(30,
                  TimeUnit.SECONDS); // rethrows assertion failures from workers
      }
    } finally {
      pool.shutdownNow();
    }
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private static Bus bus(int routeNumber, String model, long mileage) {
    return Bus.builder()
        .routeNumber(routeNumber)
        .model(model)
        .mileage(mileage)
        .build();
  }

  /**
   * Random buses over a deliberately small domain (16 routes x 3 models x 8
   * mileages): many comparator ties and full duplicates — the inputs where a
   * hand-ported TimSort is most likely to break.
   */
  private static List<Bus> randomBuses(Random random, int size) {
    String[] models = {"LiAZ", "Ikarus", "PAZ"};
    List<Bus> result = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      result.add(bus(random.nextInt(16), models[random.nextInt(models.length)],
                     random.nextInt(8) * 1_000L));
    }
    return result;
  }

  /** The oracle: the JDK's own stable sort over a copy of the input. */
  private static List<Bus> referenceSorted(List<Bus> input,
                                           Comparator<Bus> comparator) {
    List<Bus> expected = new ArrayList<>(input);
    expected.sort(comparator);
    return expected;
  }

  private static List<Bus> sawtooth(int n) {
    List<Bus> result = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      result.add(bus(i % 50, "LiAZ", (i % 50) * 10L));
    }
    return result;
  }

  private static List<Bus> pipeOrgan(int n) {
    List<Bus> result = new ArrayList<>(n);
    int half = n / 2;
    for (int i = 0; i < n; i++) {
      int route = (i < half) ? i : n - 1 - i;
      result.add(bus(route, "PAZ", route * 10L));
    }
    return result;
  }

  private static List<Bus> fewDistinct(int n) {
    List<Bus> result = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      result.add(bus(i % 3, (i % 2 == 0) ? "LiAZ" : "PAZ", i % 4));
    }
    return result;
  }

  /** Minimal Comparable wrapper — Bus itself is not Comparable. */
  private static final class RouteOrder implements Comparable<RouteOrder> {
    private final Bus bus;

    private RouteOrder(Bus bus) { this.bus = bus; }

    @Override
    public int compareTo(RouteOrder other) {
      return Integer.compare(bus.routeNumber(), other.bus.routeNumber());
    }

    @Override
    public String toString() {
      return bus.toString();
    }
  }

  /** Comparator decorator counting invocations — the adaptivity probe. */
  private static final class ComparisonCounter implements Comparator<Bus> {
    private final Comparator<Bus> delegate;
    private long calls;

    private ComparisonCounter(Comparator<Bus> delegate) {
      this.delegate = delegate;
    }

    @Override
    public int compare(Bus left, Bus right) {
      calls++;
      return delegate.compare(left, right);
    }
  }
}