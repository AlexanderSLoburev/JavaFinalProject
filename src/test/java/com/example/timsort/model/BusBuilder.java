package com.example.timsort.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BusBuilderTest {

  private static final int ROUTE_NUMBER = 42;
  private static final String MODEL = "LiAZ-5256";
  private static final long MILEAGE = 250000L;

  // === Successful build ===

  @Test
  void when_allFieldsSet_then_buildReturnsValidBus() {
    Bus bus = Bus.builder()
                  .routeNumber(ROUTE_NUMBER)
                  .model(MODEL)
                  .mileage(MILEAGE)
                  .build();

    assertNotNull(bus);
    assertEquals(ROUTE_NUMBER, bus.routeNumber());
    assertEquals(MODEL, bus.model());
    assertEquals(MILEAGE, bus.mileage());
  }

  // === Raw values are accepted: business rules live in BusValidator ===
  // Why: if the builder rejected them, those values could never reach
  // the validator and its rules would become untestable.

  @Test
  void when_routeNumberNegative_then_builderAcceptsIt() {
    Bus bus =
        Bus.builder().routeNumber(-1).model(MODEL).mileage(MILEAGE).build();
    assertEquals(-1, bus.routeNumber());
  }

  @Test
  void when_modelNull_then_builderAcceptsIt() {
    Bus bus = Bus.builder()
                  .routeNumber(ROUTE_NUMBER)
                  .model(null)
                  .mileage(MILEAGE)
                  .build();
    assertNull(bus.model());
  }

  @Test
  void when_modelBlank_then_builderAcceptsIt() {
    Bus bus = Bus.builder()
                  .routeNumber(ROUTE_NUMBER)
                  .model("   ")
                  .mileage(MILEAGE)
                  .build();
    assertEquals("   ", bus.model());
  }

  @Test
  void when_mileageNegative_then_builderAcceptsIt() {
    Bus bus = Bus.builder()
                  .routeNumber(ROUTE_NUMBER)
                  .model(MODEL)
                  .mileage(-1L)
                  .build();
    assertEquals(-1L, bus.mileage());
  }

  // === Missed fields ===

  @Test
  void when_routeNumberNotSet_then_buildThrowsIllegalStateException() {
    Bus.BusBuilder builder = Bus.builder().model(MODEL).mileage(MILEAGE);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, builder::build);

    assertTrue(exception.getMessage().contains("routeNumber"));
    assertFalse(exception.getMessage().contains("model"));
    assertFalse(exception.getMessage().contains("mileage"));
  }

  @Test
  void when_modelNotSet_then_buildThrowsIllegalStateException() {
    Bus.BusBuilder builder =
        Bus.builder().routeNumber(ROUTE_NUMBER).mileage(MILEAGE);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, builder::build);

    assertTrue(exception.getMessage().contains("model"));
    assertFalse(exception.getMessage().contains("routeNumber"));
    assertFalse(exception.getMessage().contains("mileage"));
  }

  @Test
  void when_mileageNotSet_then_buildThrowsIllegalStateException() {
    Bus.BusBuilder builder =
        Bus.builder().routeNumber(ROUTE_NUMBER).model(MODEL);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, builder::build);

    assertTrue(exception.getMessage().contains("mileage"));
    assertFalse(exception.getMessage().contains("routeNumber"));
    assertFalse(exception.getMessage().contains("model"));
  }

  @Test
  void when_noFieldsSet_then_buildThrowsIllegalStateException() {
    Bus.BusBuilder builder = Bus.builder();

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, builder::build);

    assertNotNull(exception.getMessage());
    assertTrue(exception.getMessage().contains("routeNumber"));
    assertTrue(exception.getMessage().contains("model"));
    assertTrue(exception.getMessage().contains("mileage"));
  }

  @Test
  void when_multipleFieldsMissing_then_messageContainsAllMissingFields() {
    Bus.BusBuilder builder = Bus.builder().model(MODEL);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, builder::build);

    String message = exception.getMessage();
    assertTrue(message.contains("routeNumber"));
    assertTrue(message.contains("mileage"));
    assertFalse(message.contains("model"));
  }

  // === Field redefinition ===

  @Test
  void when_fieldSetTwice_then_lastValueWins() {
    Bus bus = Bus.builder()
                  .routeNumber(10)
                  .model("Old model")
                  .mileage(100L)
                  .routeNumber(ROUTE_NUMBER)
                  .model(MODEL)
                  .mileage(MILEAGE)
                  .build();

    assertEquals(ROUTE_NUMBER, bus.routeNumber());
    assertEquals(MODEL, bus.model());
    assertEquals(MILEAGE, bus.mileage());
  }

  // === Fluent API ===

  @Test
  void when_chainMethods_then_returnsSameBuilderInstance() {
    Bus.BusBuilder builder = Bus.builder();

    Bus.BusBuilder afterRoute = builder.routeNumber(ROUTE_NUMBER);
    Bus.BusBuilder afterModel = afterRoute.model(MODEL);
    Bus.BusBuilder afterMileage = afterModel.mileage(MILEAGE);

    assertSame(builder, afterRoute);
    assertSame(builder, afterModel);
    assertSame(builder, afterMileage);
  }

  @Test
  void when_buildCalledTwice_then_returnsDifferentInstances() {
    Bus.BusBuilder builder =
        Bus.builder().routeNumber(ROUTE_NUMBER).model(MODEL).mileage(MILEAGE);

    Bus first = builder.build();
    Bus second = builder.build();

    assertNotSame(first, second);
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }

  @Test
  void when_builderCalledMultipleTimes_then_returnsNewBuilderInstance() {
    Bus.BusBuilder builder1 = Bus.builder();
    Bus.BusBuilder builder2 = Bus.builder();

    assertNotSame(builder1, builder2,
                  "Each call to builder() must create a new instance");
  }
}