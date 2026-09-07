package com.example.timsort.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BusTest {

  private static final int ROUTE_NUMBER = 42;
  private static final String MODEL = "ЛиАЗ-5256";
  private static final long MILEAGE = 250000L;

  private static Bus createBus(int route, String model, long mileage) {
    return Bus.builder()
        .routeNumber(route)
        .model(model)
        .mileage(mileage)
        .build();
  }

  // === equals ===

  @Test
  void when_twoBusesHaveSameFields_then_equalsReturnsTrue() {
    Bus first = createBus(ROUTE_NUMBER, MODEL, MILEAGE);
    Bus second = createBus(ROUTE_NUMBER, MODEL, MILEAGE);

    assertEquals(first, second);
    assertEquals(second, first); // symmetricity
  }

  @Test
  void when_twoBusesHaveDifferentRouteNumber_then_equalsReturnsFalse() {
    Bus first = createBus(42, MODEL, MILEAGE);
    Bus second = createBus(99, MODEL, MILEAGE);

    assertNotEquals(first, second);
  }

  @Test
  void when_twoBusesHaveDifferentModel_then_equalsReturnsFalse() {
    Bus first = createBus(ROUTE_NUMBER, "ЛиАЗ-5256", MILEAGE);
    Bus second = createBus(ROUTE_NUMBER, "КАМАЗ-6282", MILEAGE);

    assertNotEquals(first, second);
  }

  @Test
  void when_twoBusesHaveDifferentMileage_then_equalsReturnsFalse() {
    Bus first = createBus(ROUTE_NUMBER, MODEL, 100000L);
    Bus second = createBus(ROUTE_NUMBER, MODEL, 200000L);

    assertNotEquals(first, second);
  }

  @Test
  void when_compareBusWithNull_then_equalsReturnsFalse() {
    Bus bus = createBus(ROUTE_NUMBER, MODEL, MILEAGE);

    assertFalse(bus.equals(null));
  }

  @Test
  void when_compareBusWithItself_then_equalsReturnsTrue() {
    Bus bus = createBus(ROUTE_NUMBER, MODEL, MILEAGE);

    assertEquals(bus, bus); // reflexivity
  }

  @Test
  void when_compareBusWithDifferentType_then_equalsReturnsFalse() {
    Bus bus = createBus(ROUTE_NUMBER, MODEL, MILEAGE);

    assertNotEquals("not a bus", bus);
    assertNotEquals(42, bus);
  }

  @Test
  void when_equalsIsCalledMultipleTimes_then_returnsConsistentResult() {
    Bus first = createBus(ROUTE_NUMBER, MODEL, MILEAGE);
    Bus second = createBus(ROUTE_NUMBER, MODEL, MILEAGE);
    Bus third = createBus(99, MODEL, MILEAGE);

    assertTrue(first.equals(second));
    assertTrue(first.equals(second));
    assertTrue(first.equals(second));

    assertFalse(first.equals(third));
    assertFalse(first.equals(third));
    assertFalse(first.equals(third));
  }

  @Test
  void when_threeBusesAreEqual_then_equalsIsTransitive() {
    Bus first = createBus(ROUTE_NUMBER, MODEL, MILEAGE);
    Bus second = createBus(ROUTE_NUMBER, MODEL, MILEAGE);
    Bus third = createBus(ROUTE_NUMBER, MODEL, MILEAGE);

    assertTrue(first.equals(second));
    assertTrue(second.equals(third));
    assertTrue(first.equals(third)); // transitivity
  }

  // === hashCode ===

  @Test
  void when_twoEqualBuses_then_hashCodesAreSame() {
    Bus first = createBus(ROUTE_NUMBER, MODEL, MILEAGE);
    Bus second = createBus(ROUTE_NUMBER, MODEL, MILEAGE);

    assertEquals(first.hashCode(), second.hashCode());
  }

  // === toString ===

  @Test
  void when_callToString_then_returnsExactExpectedString() {
    Bus bus = createBus(42, "ЛиАЗ-5256", 250000L);

    assertEquals("Bus{42, ЛиАЗ-5256, 250000}", bus.toString());
  }

  // === Getters ===

  @Test
  void when_getRouteNumber_then_returnsConstructorValue() {
    Bus bus = createBus(42, MODEL, MILEAGE);

    assertEquals(42, bus.routeNumber());
  }

  @Test
  void when_getModel_then_returnsConstructorValue() {
    Bus bus = createBus(ROUTE_NUMBER, "ЛиАЗ-5256", MILEAGE);

    assertEquals("ЛиАЗ-5256", bus.model());
  }

  @Test
  void when_getMileage_then_returnsConstructorValue() {
    Bus bus = createBus(ROUTE_NUMBER, MODEL, 250000L);

    assertEquals(250000L, bus.mileage());
  }
}