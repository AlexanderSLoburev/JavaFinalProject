package com.example.timsort.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.timsort.model.Bus;
import com.example.timsort.sort.Sorter;
import com.example.timsort.sort.SorterFactory;
import com.example.timsort.sort.TimParitySorter;
import com.example.timsort.sort.TimSorter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimParitySorterIntegrationTest {

  private RecordingSorterFactory<Bus> factory;
  private TimParitySorter<Bus> sorter;

  @BeforeEach
  void setUp() {
    factory = new RecordingSorterFactory<Bus>(TimSorter::new);
    sorter = new TimParitySorter<>(factory, Bus::routeNumber);
  }

  // ---------- contract of a constructor ----------

  @Test
  void when_sorterFactoryIsNull_then_constructorThrowsNpe() {
    assertThrows(NullPointerException.class,
                 () -> new TimParitySorter<Bus>(null, Bus::routeNumber));
  }

  @Test
  void when_keyExtractorIsNull_then_constructorThrowsNpe() {
    assertThrows(NullPointerException.class,
                 () -> new TimParitySorter<Bus>(factory, null));
  }

  // ---------- contract of sort ----------

  @Test
  void when_dataIsNull_then_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> sorter.sort(null));
  }

  @Test
  void when_listIsEmpty_then_returnsNewEmptyListWithoutDelegateCreation() {
    List<Bus> input = new ArrayList<>();

    List<Bus> result = sorter.sort(input);

    assertTrue(result.isEmpty());
    assertNotSame(input, result);
    assertEquals(0, factory.numCreatedSorters());
  }

  @Test
  void when_allKeysAreOdd_then_returnsEqualCopyWithoutDelegateCreation() {
    List<Bus> input =
        List.of(bus(1, "A"), bus(3, "B"), bus(5, "C"), bus(7, "D"));

    List<Bus> result = sorter.sort(input);

    assertEquals(input, result);
    assertEquals(0, factory.numCreatedSorters());
  }

  @Test
  void when_exactlyOneEvenKey_then_returnsEqualCopy() {
    List<Bus> input = List.of(bus(1, "A"), bus(2, "B"), bus(9, "C"));

    assertEquals(input, sorter.sort(input));
  }

  @Test
  void when_inputIsMutable_then_sortDoesNotModifyIt() {
    List<Bus> input =
        new ArrayList<>(List.of(bus(10, "A"), bus(3, "B"), bus(2, "C")));
    List<Bus> snapshot = List.copyOf(input);

    sorter.sort(input);

    assertEquals(snapshot, input);
  }

  @Test
  void when_resultIsMutated_then_inputListIsNotAffected() {
    List<Bus> input = new ArrayList<>(List.of(bus(2, "A"), bus(4, "B")));

    List<Bus> result = sorter.sort(input);

    assertNotSame(input, result);
    result.set(0, bus(8, "X"));
    assertEquals(List.of(bus(2, "A"), bus(4, "B")), input);
  }

  // ---------- general semantics ----------

  @Test
  void when_twoEvenKeysAreOutOfOrder_then_onlyThosePositionsSwap() {
    List<Bus> input =
        List.of(bus(1, "A"), bus(10, "B"), bus(3, "C"), bus(2, "D"));

    List<Bus> expected =
        List.of(bus(1, "A"), bus(2, "D"), bus(3, "C"), bus(10, "B"));

    assertEquals(expected, sorter.sort(input));
  }

  @Test
  void when_mixedParityRouteNumbers_then_evenKeysSortedOddKeysStayAnchored() {
    List<Bus> input = List.of(bus(3, "Volvo"), bus(10, "MAN"), bus(7, "Scania"),
                              bus(2, "Ikarus"), bus(8, "Liaz"), bus(5, "MAZ"));

    List<Bus> expected =
        List.of(bus(3, "Volvo"), bus(2, "Ikarus"), bus(7, "Scania"),
                bus(8, "Liaz"), bus(10, "MAN"), bus(5, "MAZ"));

    List<Bus> result = sorter.sort(input);

    assertEquals(expected, result);
    assertEquals(oracle(input, Bus::routeNumber), result);
  }

  @Test
  void when_allKeysAreEven_then_resultMatchesStandardLibrarySort() {
    List<Bus> input =
        List.of(bus(10, "A"), bus(2, "B"), bus(8, "C"), bus(4, "D"));

    List<Bus> expected = new ArrayList<>(input);
    expected.sort(Comparator.comparingInt(Bus::routeNumber)); // оракул — stdlib

    assertEquals(expected, sorter.sort(input));
  }

  @Test
  void when_evenKeysAreEqual_then_relativeOrderOfEqualKeysIsPreserved() {
    List<Bus> input =
        List.of(bus(1, "odd1"), bus(4, "a"), bus(4, "b"), bus(2, "x"),
                bus(2, "y"), bus(4, "c"), bus(3, "odd2"));

    List<Bus> expected =
        List.of(bus(1, "odd1"), bus(2, "x"), bus(2, "y"), bus(4, "a"),
                bus(4, "b"), bus(4, "c"), bus(3, "odd2"));

    assertEquals(expected, sorter.sort(input));
  }

  @Test
  void when_evenKeysFormStrictlyDescendingRun_then_resultIsAscending() {
    List<Bus> input = List.of(bus(1, "A"), bus(8, "B"), bus(6, "C"),
                              bus(4, "D"), bus(2, "E"), bus(9, "F"));

    List<Bus> expected = List.of(bus(1, "A"), bus(2, "E"), bus(4, "D"),
                                 bus(6, "C"), bus(8, "B"), bus(9, "F"));

    assertEquals(expected, sorter.sort(input));
  }

  // ---------- oracle: standard sort ----------

  @Test
  void when_randomizedLists_then_resultsAlwaysMatchStandardLibraryOracle() {
    TimParitySorter<Integer> longSorter = new TimParitySorter<>(
        new RecordingSorterFactory<Integer>(TimSorter::new), Integer::intValue);
    Random random = new Random(20240517L);

    // Sizes from 0 to 299 cover all TimSort branches: <2, <MIN_MERGE(32),
    // >=MIN_MERGE.
    for (int trial = 0; trial < 300; trial++) {
      assertOneTrialMatchesOracle(longSorter, random, trial);
    }
  }

  @Test
  void when_largeInputWithTies_then_matchesStandardLibraryOracle() {
    // ~2,500 even-numbered elements: full merger mechanics and galloping.
    TimParitySorter<Integer> longSorter = new TimParitySorter<>(
        new RecordingSorterFactory<Integer>(TimSorter::new), Integer::intValue);
    Random random = new Random(42L);
    List<Integer> input = new ArrayList<>(5000);
    for (int i = 0; i < 5000; i++) {
      input.add((int)(random.nextInt(2001) - 1000)); // Many duplicates
    }

    assertEquals(oracle(input, Integer::intValue), longSorter.sort(input));
  }

  // ---------- interaction with a delegate ----------

  @Test
  void when_mixedKeys_then_delegateReceivesOnlyEvenElementsAndKeyComparator() {
    List<Bus> input = List.of(bus(3, "A"), bus(10, "B"), bus(7, "C"),
                              bus(2, "D"), bus(4, "E"));

    sorter.sort(input);

    assertEquals(1, factory.numCreatedSorters());
    assertEquals(List.of(bus(10, "B"), bus(2, "D"), bus(4, "E")),
                 factory.lastData());

    Comparator<? super Bus> order = factory.lastOrder();
    assertNotNull(order);
    assertTrue(order.compare(bus(2, "x"), bus(10, "y")) < 0);
    assertEquals(0, order.compare(bus(4, "x"), bus(4, "y")));
  }

  @Test
  void when_sorterFactoryReturnsNullSorter_then_throwsNullPointerException() {
    SorterFactory<Bus> nullSorterFactory = (data, order) -> null;
    TimParitySorter<Bus> broken =
        new TimParitySorter<Bus>(nullSorterFactory, Bus::routeNumber);

    assertThrows(
        NullPointerException.class,
        () -> broken.sort(List.of(bus(2, "A"), bus(4, "B"), bus(6, "C"))));
  }

  @Test
  void when_delegateReturnsNullList_then_illegalStateException() {
    SorterFactory<Bus> nullResult = (data, order) -> () -> null;
    TimParitySorter<Bus> broken =
        new TimParitySorter<Bus>(nullResult, Bus::routeNumber);

    assertThrows(
        IllegalStateException.class,
        () -> broken.sort(List.of(bus(2, "A"), bus(4, "B"), bus(6, "C"))));
  }

  @Test
  void when_delegateReturnsWrongSizedList_then_illegalStateException() {
    SorterFactory<Bus> emptyResult = (data, order) -> () -> List.of();
    TimParitySorter<Bus> broken =
        new TimParitySorter<Bus>(emptyResult, Bus::routeNumber);

    assertThrows(
        IllegalStateException.class,
        () -> broken.sort(List.of(bus(2, "A"), bus(4, "B"), bus(6, "C"))));
  }

  // ---------- helpers ----------

  /**
   * One randomized trial: the result against the stdlib oracle, plus the
   * "input is never mutated" contract. Why a method: trial is a parameter
   * here — never reassigned, hence effectively final, so the
   * failure-message lambdas may capture it.
   */
  private static void
  assertOneTrialMatchesOracle(TimParitySorter<Integer> sorter, Random random,
                              int trial) {
    List<Integer> input = randomIntegerList(random, random.nextInt(300));
    List<Integer> snapshot = List.copyOf(input);
    List<Integer> expected = oracle(input, Integer::intValue);

    List<Integer> actual = sorter.sort(input);

    assertEquals(expected, actual, () -> "trial " + trial + ", input=" + input);
    assertEquals(snapshot, input, () -> "input mutated, trial " + trial);
  }

  private static List<Integer> randomIntegerList(Random random, int size) {
    List<Integer> list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      list.add((int)(random.nextInt(21) - 10)); // [-10..10]: дубли и оба знака
    }
    return list;
  }

  private static Bus bus(int routeNumber, String model) {
    return Bus.builder()
        .routeNumber(routeNumber)
        .model(model)
        .mileage(0L)
        .build();
  }

  /**
   * Reference implementation: same semantics, but the ordering is
   * done by the standard library and the structure (plain loops, ArrayList)
   * deliberately differs from the production code so that bugs do not
   * correlate. The independent expected-list tests above double-check the spec.
   */
  private static <T> List<T> oracle(List<T> input,
                                    ToIntFunction<T> keyExtractor) {
    List<T> result = new ArrayList<>(input);
    List<Integer> evenIndices = new ArrayList<>();
    List<T> evenElements = new ArrayList<>();

    for (int i = 0; i < input.size(); i++) {
      T element = input.get(i);
      if (keyExtractor.applyAsInt(element) % 2 == 0) {
        evenIndices.add(i);
        evenElements.add(element);
      }
    }

    evenElements.sort(Comparator.comparingInt(keyExtractor));

    for (int k = 0; k < evenIndices.size(); k++) {
      result.set(evenIndices.get(k), evenElements.get(k));
    }

    return result;
  }

  /**
   * <p> A spy: wraps the production sorter factory and passes every {@code
   * create} call through to it unchanged, while recording the arguments for
   * later interaction assertions.
   *
   * <p> The wrapped factory (in practice {@code TimSorter::new})
   * keeps doing the real sorting, so result/oracle assertions still exercise
   * the genuine production path; the recording adds the observability the
   * real factory cannot offer — WHICH elements were handed to the delegate,
   * WITH which comparator, and WHETHER the delegate was invoked at all.</p>
   *
   * <p>{@code TimParitySorter} receives this class through the injected {@code
   * SorterFactory} seam and must not be able to tell it apart from the real
   * wiring — otherwise the tests would exercise a distorted path. Hence {@code
   * create} forwards the ORIGINAL arguments, never the recorded snapshot.</p>
   *
   * <p>The journals are instance state and a fresh double is created in
   * {@code @BeforeEach}, so no recording leaks between tests.
   */
  private static final class RecordingSorterFactory<T>
      implements SorterFactory<T> {

    /**
     * The factory every call is forwarded to ({@code TimSorter::new} in the
     * tests).
     */
    private final SorterFactory<T> backing;

    /**
     * Journal of the {@code data} argument of every call, one snapshot per
     * call. The OUTER list is the journal (one entry per call), the INNER list
     * is a defensive copy of a single call's argument.
     */
    private final List<List<T>> receivedData = new ArrayList<>();

    /**
     * Journal of the {@code order} argument of every call. Index-synchronized
     * with {@link #receivedData}: entry k of both journals belongs to the
     * same k-th call.
     */
    private final List<Comparator<? super T>> receivedOrders =
        new ArrayList<>();

    /**
     * Package-private on purpose: the double is an implementation detail of
     * this test class and must not leak anywhere else.
     */
    RecordingSorterFactory(SorterFactory<T> backing) { this.backing = backing; }

    @Override
    public Sorter<T> create(List<T> data, Comparator<? super T> order) {
      // Snapshot the data argument. Why a copy and not the reference:
      // the journal must show the argument AS IT WAS at call time; a live
      // reference would expose whatever the caller left there by the time of
      // the assertion, silently checking the wrong state.
      receivedData.add(new ArrayList<>(data));

      // Record the comparator by reference.
      receivedOrders.add(order);

      // Forward the ORIGINAL data, never the snapshot: the real
      // sorter must see exactly what it would see in
      // the chain.
      return backing.create(data, order);
    }

    /**
     * Number of sorters created so far. Every call appends exactly one
     * journal entry, so the journal size equals the call count.
     */
    int numCreatedSorters() { return receivedData.size(); }

    /**
     * The {@code data} argument of the most recent call.
     *
     * Contract: valid only after at least one call.
     */
    List<T> lastData() { return receivedData.get(receivedData.size() - 1); }

    /**
     * The {@code order} argument of the most recent call; the same validity
     * contract as {@link #lastData()}.
     */
    Comparator<? super T> lastOrder() {
      return receivedOrders.get(receivedOrders.size() - 1);
    }
  }
}