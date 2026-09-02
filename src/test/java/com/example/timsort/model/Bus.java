package com.example.timsort.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BusTest {

  private static final int ROUTE_NUMBER = 42;
  private static final String MODEL = "ЛиАЗ-5256";
  private static final long MILEAGE = 250000L;

  // === equals ===

  @Test
  void when_twoBusesHaveSameFields_then_equalsReturnsTrue() {
    Bus first = new Bus(ROUTE_NUMBER, MODEL, MILEAGE);
    Bus second = new Bus(ROUTE_NUMBER, MODEL, MILEAGE);

    assertEquals(first, second);
    assertEquals(second, first); // symmetry
  }

  @Test
  void when_twoBusesHaveDifferentRouteNumber_then_equalsReturnsFalse() {
    Bus first = new Bus(42, MODEL, MILEAGE);
    Bus second = new Bus(99, MODEL, MILEAGE);

    assertNotEquals(first, second);
  }

  @Test
  void when_twoBusesHaveDifferentModel_then_equalsReturnsFalse() {
    Bus first = new Bus(ROUTE_NUMBER, "ЛиАЗ-5256", MILEAGE);
    Bus second = new Bus(ROUTE_NUMBER, "КАМАЗ-6282", MILEAGE);

    assertNotEquals(first, second);
  }

  @Test
  void when_twoBusesHaveDifferentMileage_then_equalsReturnsFalse() {
    Bus first = new Bus(ROUTE_NUMBER, MODEL, 100000L);
    Bus second = new Bus(ROUTE_NUMBER, MODEL, 200000L);

    assertNotEquals(first, second);
  }

  @Test
  void when_compareBusWithNull_then_equalsReturnsFalse() {
    Bus bus = new Bus(ROUTE_NUMBER, MODEL, MILEAGE);

    assertNotEquals(null, bus);
    assertFalse(bus.equals(null));
  }

  @Test
  void when_compareBusWithItself_then_equalsReturnsTrue() {
    Bus bus = new Bus(ROUTE_NUMBER, MODEL, MILEAGE);

    assertEquals(bus, bus); // reflexivity
  }

  @Test
  void when_compareBusWithDifferentType_then_equalsReturnsFalse() {
    Bus bus = new Bus(ROUTE_NUMBER, MODEL, MILEAGE);

    assertNotEquals("not a bus", bus);
    assertNotEquals(42, bus);
  }

  @Test
  void when_equalsIsCalledMultipleTimes_then_returnsConsistentResult() {
    Bus first = new Bus(ROUTE_NUMBER, MODEL, MILEAGE);
    Bus second = new Bus(ROUTE_NUMBER, MODEL, MILEAGE);
    Bus third = new Bus(99, MODEL, MILEAGE);

    // consistency: same result on multiple calls
    assertTrue(first.equals(second));
    assertTrue(first.equals(second));
    assertTrue(first.equals(second));

    assertFalse(first.equals(third));
    assertFalse(first.equals(third));
    assertFalse(first.equals(third));
  }

  @Test
  void when_threeBusesAreEqual_then_equalsIsTransitive() {
    Bus first = new Bus(ROUTE_NUMBER, MODEL, MILEAGE);
    Bus second = new Bus(ROUTE_NUMBER, MODEL, MILEAGE);
    Bus third = new Bus(ROUTE_NUMBER, MODEL, MILEAGE);

    // transitivity: if first == second and second == third, then first == third
    assertTrue(first.equals(second));
    assertTrue(second.equals(third));
    assertTrue(first.equals(third));
  }

  // === hashCode ===

  @Test
  void when_twoEqualBuses_then_hashCodesAreSame() {
    Bus first = new Bus(ROUTE_NUMBER, MODEL, MILEAGE);
    Bus second = new Bus(ROUTE_NUMBER, MODEL, MILEAGE);

    assertEquals(first.hashCode(), second.hashCode());
  }

  // === toString ===

  @Test
  void when_callToString_then_containsAllFields() {
    Bus bus = new Bus(42, "ЛиАЗ-5256", 250000L);

    String result = bus.toString();

    assertTrue(result.contains("42"), "toString should contain routeNumber");
    assertTrue(result.contains("ЛиАЗ-5256"), "toString should contain model");
    assertTrue(result.contains("250000"), "toString should contain mileage");
  }

  // === Getters ===

  @Test
  void when_getRouteNumber_then_returnsConstructorValue() {
    Bus bus = new Bus(42, MODEL, MILEAGE);

    assertEquals(42, bus.routeNumber());
  }

  @Test
  void when_getModel_then_returnsConstructorValue() {
    Bus bus = new Bus(ROUTE_NUMBER, "ЛиАЗ-5256", MILEAGE);

    assertEquals("ЛиАЗ-5256", bus.model());
  }

  @Test
  void when_getMileage_then_returnsConstructorValue() {
    Bus bus = new Bus(ROUTE_NUMBER, MODEL, 250000L);

    assertEquals(250000L, bus.mileage());
  }
}