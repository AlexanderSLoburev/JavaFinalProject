package com.example.timsort.sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.timsort.model.Bus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;


/**
 * Tests for {@link BusComparators}.
 *
 * <p>Method names follow the {@code when_Condition_then_Result} convention,
 * so each test name already states its single scenario; no separate
 * display names are needed.
 */
class BusComparatorsTest {

  /** Convenience factory: keeps each test focused on the compared values. */
  private static Bus bus(int routeNumber, String model, long mileage) {
    return Bus.builder()
        .routeNumber(routeNumber)
        .model(model)
        .mileage(mileage)
        .build();
  }

  // ------------------------------------------------------------- registry

  @Test
  void when_byField_isCalledWithEachConstant_then_comparatorIsReturned() {
    for (BusField field : BusField.values()) {
      assertNotNull(BusComparators.byField(field),
                    () -> "missing comparator for " + field);
    }
  }

  @Test
  void
  when_byField_isCalledRepeatedlyForSameField_then_sameInstanceIsReturned() {
    for (BusField field : BusField.values()) {
      assertSame(BusComparators.byField(field), BusComparators.byField(field));
    }
    assertNotSame(BusComparators.byField(BusField.ROUTE_NUMBER),
                  BusComparators.byField(BusField.MODEL));
  }

  @Test
  void when_byField_isCalledWithNull_then_NullPointerExceptionIsThrown() {
    NullPointerException npe = assertThrows(NullPointerException.class,
                                            () -> BusComparators.byField(null));
    assertEquals("field must not be null", npe.getMessage());
  }

  // --------------------------------------------------------- ROUTE_NUMBER

  @Test
  void when_routeNumbersDiffer_then_busWithSmallerNumberComparesLess() {
    Comparator<Bus> cmp = BusComparators.byField(BusField.ROUTE_NUMBER);
    Bus smaller = bus(10, "Volvo", 1_000L);
    Bus larger = bus(20, "Volvo", 1_000L);

    assertTrue(cmp.compare(smaller, larger) < 0);
    assertTrue(cmp.compare(larger, smaller) > 0);
  }

  @Test
  void when_routeNumbersAreEqual_then_modelDecidesOrder() {
    Comparator<Bus> cmp = BusComparators.byField(BusField.ROUTE_NUMBER);

    Bus liaz = bus(7, "LiAZ", 500L);
    Bus volvo = bus(7, "Volvo", 500L);
    assertTrue(cmp.compare(liaz, volvo) < 0);
    assertTrue(cmp.compare(volvo, liaz) > 0);
  }

  @Test
  void when_routeNumberAndModelAreEqual_then_mileageDecidesOrder() {
    Comparator<Bus> cmp = BusComparators.byField(BusField.ROUTE_NUMBER);

    Bus worn = bus(7, "Volvo", 500L);
    Bus fresh = bus(7, "Volvo", 900L);
    assertTrue(cmp.compare(worn, fresh) < 0);
  }

  // ----------------------------------------------------------------- MODEL

  @Test
  void when_modelsDiffer_then_lexicographicallySmallerModelComparesLess() {
    Comparator<Bus> cmp = BusComparators.byField(BusField.MODEL);
    Bus ikarus = bus(1, "Ikarus", 100L);
    Bus volvo = bus(1, "Volvo", 100L);

    assertTrue(cmp.compare(ikarus, volvo) < 0);
    assertTrue(cmp.compare(volvo, ikarus) > 0);
  }

  @Test
  void when_modelsAreEqual_then_routeNumberDecidesOrder() {
    Comparator<Bus> cmp = BusComparators.byField(BusField.MODEL);

    Bus route3 = bus(3, "Volvo", 100L);
    Bus route4 = bus(4, "Volvo", 100L);
    assertTrue(cmp.compare(route3, route4) < 0);
  }

  @Test
  void when_modelAndRouteNumberAreEqual_then_mileageDecidesOrder() {
    Comparator<Bus> cmp = BusComparators.byField(BusField.MODEL);

    Bus worn = bus(3, "Volvo", 100L);
    Bus fresh = bus(3, "Volvo", 300L);
    assertTrue(cmp.compare(worn, fresh) < 0);
  }

  // -------------------------------------------------------------- MILEAGE

  @Test
  void when_mileagesDiffer_then_busWithLowerMileageComparesLess() {
    Comparator<Bus> cmp = BusComparators.byField(BusField.MILEAGE);
    Bus smaller = bus(1, "Volvo", 100_000L);
    Bus larger = bus(1, "Volvo", 250_000L);

    assertTrue(cmp.compare(smaller, larger) < 0);
    assertTrue(cmp.compare(larger, smaller) > 0);
  }

  @Test
  void when_mileagesAreEqual_then_routeNumberDecidesOrder() {
    Comparator<Bus> cmp = BusComparators.byField(BusField.MILEAGE);

    Bus route10 = bus(10, "LiAZ", 100_000L);
    Bus route20 = bus(20, "LiAZ", 100_000L);
    assertTrue(cmp.compare(route10, route20) < 0);
  }

  @Test
  void when_mileageAndRouteNumberAreEqual_then_modelDecidesOrder() {
    Comparator<Bus> cmp = BusComparators.byField(BusField.MILEAGE);

    Bus liaz = bus(10, "LiAZ", 100_000L);
    Bus volvo = bus(10, "Volvo", 100_000L);
    assertTrue(cmp.compare(liaz, volvo) < 0);
  }

  // ---------------------------------------------------------- total order

  @Test
  void when_busesAreFullyEqual_then_comparatorReturnsZeroForEveryField() {
    for (BusField field : BusField.values()) {
      Comparator<Bus> cmp = BusComparators.byField(field);
      Bus a = bus(42, "Volvo", 123_456L);
      Bus b = bus(42, "Volvo", 123_456L);

      assertEquals(a, b);
      assertEquals(0, cmp.compare(a, b),
                   () -> field + " comparator disagrees with equals");
    }
  }

  @Test
  void when_busesAreComparedInBothDirections_then_resultsHaveOppositeSigns() {
    Bus a = bus(42, "Volvo", 123_456L);
    Bus b = bus(17, "LiAZ", 999_999L);
    for (BusField field : BusField.values()) {
      Comparator<Bus> cmp = BusComparators.byField(field);
      int forward = Integer.signum(cmp.compare(a, b));
      int backward = Integer.signum(cmp.compare(b, a));
      assertNotEquals(0, forward);
      assertEquals(-forward, backward, () -> field + " is not antisymmetric");
    }
  }

  // ------------------------------------------------------------ end-to-end

  @Test
  void when_listIsSortedByRouteNumber_then_busesAreOrderedByRouteNumber() {
    List<Bus> buses = new ArrayList<>(List.of(bus(30, "Volvo", 3_000L),
                                              bus(10, "LiAZ", 2_000L),
                                              bus(20, "Ikarus", 1_000L)));

    buses.sort(BusComparators.byField(BusField.ROUTE_NUMBER));
    assertEquals(List.of(10, 20, 30),
                 buses.stream().map(Bus::routeNumber).toList());
  }

  @Test
  void when_listIsSortedByModel_then_busesAreOrderedLexicographicallyByModel() {
    List<Bus> buses = new ArrayList<>(List.of(bus(30, "Volvo", 3_000L),
                                              bus(10, "LiAZ", 2_000L),
                                              bus(20, "Ikarus", 1_000L)));

    buses.sort(BusComparators.byField(BusField.MODEL));
    assertEquals(List.of("Ikarus", "LiAZ", "Volvo"),
                 buses.stream().map(Bus::model).toList());
  }

  @Test
  void when_listIsSortedByMileage_then_busesAreOrderedByMileage() {
    List<Bus> buses = new ArrayList<>(List.of(bus(30, "Volvo", 3_000L),
                                              bus(10, "LiAZ", 2_000L),
                                              bus(20, "Ikarus", 1_000L)));

    buses.sort(BusComparators.byField(BusField.MILEAGE));
    assertEquals(List.of(1_000L, 2_000L, 3_000L),
                 buses.stream().map(Bus::mileage).toList());
  }
}
