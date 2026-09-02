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
    Bus bus = new BusBuilder()
                  .routeNumber(ROUTE_NUMBER)
                  .model(MODEL)
                  .mileage(MILEAGE)
                  .build();

    assertNotNull(bus);
    assertEquals(ROUTE_NUMBER, bus.routeNumber());
    assertEquals(MODEL, bus.model());
    assertEquals(MILEAGE, bus.mileage());
  }

  // === Parameter validation in setters ===

  @Test
  void when_routeNumberNegative_then_throwsIllegalArgumentException() {
    BusBuilder builder = new BusBuilder();
    assertThrows(IllegalArgumentException.class, () -> builder.routeNumber(-1));
  }

  @Test
  void when_modelNull_then_throwsIllegalArgumentException() {
    BusBuilder builder = new BusBuilder();
    assertThrows(IllegalArgumentException.class, () -> builder.model(null));
  }

  @Test
  void when_modelBlank_then_throwsIllegalArgumentException() {
    BusBuilder builder = new BusBuilder();
    assertThrows(IllegalArgumentException.class, () -> builder.model("   "));
  }

  @Test
  void when_mileageNegative_then_throwsIllegalArgumentException() {
    BusBuilder builder = new BusBuilder();
    assertThrows(IllegalArgumentException.class, () -> builder.mileage(-1L));
  }

  // === Missing fields ===

  @Test
  void when_routeNumberNotSet_then_buildThrowsIllegalStateException() {
    BusBuilder builder = new BusBuilder().model(MODEL).mileage(MILEAGE);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, builder::build);

    assertTrue(exception.getMessage().contains("routeNumber"));
    assertFalse(exception.getMessage().contains("model"));
    assertFalse(exception.getMessage().contains("mileage"));
  }

  @Test
  void when_modelNotSet_then_buildThrowsIllegalStateException() {
    BusBuilder builder =
        new BusBuilder().routeNumber(ROUTE_NUMBER).mileage(MILEAGE);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, builder::build);

    assertTrue(exception.getMessage().contains("model"));
    assertFalse(exception.getMessage().contains("routeNumber"));
    assertFalse(exception.getMessage().contains("mileage"));
  }

  @Test
  void when_mileageNotSet_then_buildThrowsIllegalStateException() {
    BusBuilder builder =
        new BusBuilder().routeNumber(ROUTE_NUMBER).model(MODEL);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, builder::build);

    assertTrue(exception.getMessage().contains("mileage"));
    assertFalse(exception.getMessage().contains("routeNumber"));
    assertFalse(exception.getMessage().contains("model"));
  }

  @Test
  void when_noFieldsSet_then_buildThrowsIllegalStateException() {
    BusBuilder builder = new BusBuilder();

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, builder::build);

    assertNotNull(exception.getMessage());
    assertTrue(exception.getMessage().contains("routeNumber"));
    assertTrue(exception.getMessage().contains("model"));
    assertTrue(exception.getMessage().contains("mileage"));
  }

  @Test
  void when_multipleFieldsMissing_then_messageContainsAllMissingFields() {

    BusBuilder builder = new BusBuilder().model(MODEL);

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
    Bus bus = new BusBuilder()
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
    BusBuilder builder = new BusBuilder();

    BusBuilder afterRoute = builder.routeNumber(ROUTE_NUMBER);
    BusBuilder afterModel = afterRoute.model(MODEL);
    BusBuilder afterMileage = afterModel.mileage(MILEAGE);

    assertSame(builder, afterRoute);
    assertSame(builder, afterModel);
    assertSame(builder, afterMileage);
  }

  @Test
  void when_buildCalledTwice_then_returnsDifferentInstances() {
    BusBuilder builder = new BusBuilder()
                             .routeNumber(ROUTE_NUMBER)
                             .model(MODEL)
                             .mileage(MILEAGE);

    Bus first = builder.build();
    Bus second = builder.build();

    assertNotSame(first, second);
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }
}