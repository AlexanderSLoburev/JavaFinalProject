package com.example.timsort.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.example.timsort.model.Bus;
import com.example.timsort.sort.TimParitySorter;
import com.example.timsort.sort.TimSorter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;
import org.junit.jupiter.api.Test;

class TimParitySorterIntegrationTest {

  private static final ToLongFunction<Bus> ROUTE_KEY = Bus::routeNumber;
  private static final Comparator<Bus> BY_ROUTE = Comparator.comparingLong(ROUTE_KEY);

  @Test
  void when_mixedEvenAndOddKeys_then_onlyEvenElementsSorted() {
    List<Bus> input =
        Arrays.asList(bus(2, "LiAZ", 200), bus(5, "Volvo", 500),
                      bus(1, "GAZ", 100), bus(4, "PAZ", 400),
                      bus(3, "Ikarus", 300));

    List<Bus> result = newTimParitySorter().sort(input);

    assertEquals(bus(2, "LiAZ", 200), result.get(0));
    assertEquals(bus(5, "Volvo", 500), result.get(1));
    assertEquals(bus(1, "GAZ", 100), result.get(2));
    assertEquals(bus(4, "PAZ", 400), result.get(3));
    assertEquals(bus(3, "Ikarus", 300), result.get(4));
  }

  @Test
  void when_oddElementsPresent_then_theyRemainAtOriginalPositions() {
    List<Bus> input = Arrays.asList(bus(1, "GAZ", 100), bus(3, "PAZ", 300),
                                    bus(5, "Volvo", 500));

    List<Bus> result = newTimParitySorter().sort(input);

    assertEquals(input, result);
  }

  @Test
  void when_evenElementsSorted_then_ascendingByKey() {
    List<Bus> input = Arrays.asList(bus(6, "LiAZ", 600), bus(2, "GAZ", 200),
                                    bus(4, "PAZ", 400));

    List<Bus> result = newTimParitySorter().sort(input);

    assertEquals(Arrays.asList(bus(2, "GAZ", 200), bus(4, "PAZ", 400),
                               bus(6, "LiAZ", 600)),
                 result);
  }

  @Test
  void when_equalEvenKeys_then_stableOrderPreserved() {
    List<Bus> input =
        Arrays.asList(bus(2, "LiAZ", 200), bus(2, "GAZ", 200),
                      bus(2, "PAZ", 200), bus(1, "Volvo", 100));

    List<Bus> result = newTimParitySorter().sort(input);

    assertEquals(bus(2, "LiAZ", 200), result.get(0));
    assertEquals(bus(2, "GAZ", 200), result.get(1));
    assertEquals(bus(2, "PAZ", 200), result.get(2));
    assertEquals(bus(1, "Volvo", 100), result.get(3));
  }

  @Test
  void when_emptyList_then_emptyResult() {
    List<Bus> input = List.of();
    assertEquals(List.of(), newTimParitySorter().sort(input));
  }

  @Test
  void when_singleEvenElement_then_sortedListWithSameElement() {
    List<Bus> input = List.of(bus(2, "LiAZ", 200));
    assertEquals(input, newTimParitySorter().sort(input));
  }

  @Test
  void when_singleOddElement_then_listUnchanged() {
    List<Bus> input = List.of(bus(1, "GAZ", 100));
    assertEquals(input, newTimParitySorter().sort(input));
  }

  @Test
  void when_allOddKeys_then_listUnchanged() {
    List<Bus> input = Arrays.asList(bus(5, "Volvo", 500), bus(3, "PAZ", 300),
                                    bus(1, "GAZ", 100));

    assertEquals(input, newTimParitySorter().sort(input));
  }

  @Test
  void when_allEvenKeys_then_fullySorted() {
    List<Bus> input = Arrays.asList(bus(4, "PAZ", 400), bus(2, "GAZ", 200),
                                    bus(6, "LiAZ", 600));

    List<Bus> result = newTimParitySorter().sort(input);

    assertEquals(Arrays.asList(bus(2, "GAZ", 200), bus(4, "PAZ", 400),
                               bus(6, "LiAZ", 600)),
                 result);
  }

  @Test
  void when_sortCompletes_then_sourceListUnchanged() {
    List<Bus> input = new ArrayList<>(Arrays.asList(bus(2, "LiAZ", 200),
                                                    bus(5, "Volvo", 500),
                                                    bus(1, "GAZ", 100)));
    List<Bus> snapshot = new ArrayList<>(input);

    newTimParitySorter().sort(input);

    assertEquals(snapshot, input);
  }

  @Test
  void when_resultIsNewList_then_notSameAsInput() {
    List<Bus> input =
        Arrays.asList(bus(2, "LiAZ", 200), bus(5, "Volvo", 500));

    List<Bus> result = newTimParitySorter().sort(input);
    assertNotSame(input, result);
  }

  @Test
  void when_zeroRouteNumber_then_treatedAsEven() {
    List<Bus> input = Arrays.asList(bus(4, "PAZ", 400), bus(0, "GAZ", 0),
                                    bus(2, "LiAZ", 200));

    List<Bus> result = newTimParitySorter().sort(input);

    assertEquals(Arrays.asList(bus(0, "GAZ", 0), bus(2, "LiAZ", 200),
                               bus(4, "PAZ", 400)),
                 result);
  }

  private static TimParitySorter<Bus> newTimParitySorter() {
    TimSorter<Bus> delegate = new TimSorter<>(List.of(), BY_ROUTE);
    return new TimParitySorter<>(delegate, ROUTE_KEY);
  }

  private static Bus bus(int routeNumber, String model, long mileage) {
    return Bus.builder()
        .routeNumber(routeNumber)
        .model(model)
        .mileage(mileage)
        .build();
  }
}
