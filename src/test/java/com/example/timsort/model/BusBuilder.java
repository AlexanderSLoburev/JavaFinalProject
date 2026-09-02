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

    assertNotNull(bus, "build() should return a non-null Bus");
    assertEquals(ROUTE_NUMBER, bus.routeNumber());
    assertEquals(MODEL, bus.model());
    assertEquals(MILEAGE, bus.mileage());
  }

  // === Missing fields should throw IllegalStateException ===

  @Test
  void when_routeNumberNotSet_then_buildThrowsIllegalStateException() {
    BusBuilder builder = new BusBuilder().model(MODEL).mileage(MILEAGE);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, builder::build);

    assertTrue(exception.getMessage().contains("routeNumber"),
               "Exception message should mention the missing field");
  }

  @Test
  void when_modelNotSet_then_buildThrowsIllegalStateException() {
    BusBuilder builder =
        new BusBuilder().routeNumber(ROUTE_NUMBER).mileage(MILEAGE);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, builder::build);

    assertTrue(exception.getMessage().contains("model"),
               "Exception message should mention the missing field");
  }

  @Test
  void when_mileageNotSet_then_buildThrowsIllegalStateException() {
    BusBuilder builder =
        new BusBuilder().routeNumber(ROUTE_NUMBER).model(MODEL);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, builder::build);

    assertTrue(exception.getMessage().contains("mileage"),
               "Exception message should mention the missing field");
  }

  @Test
  void when_noFieldsSet_then_buildThrowsIllegalStateException() {
    BusBuilder builder = new BusBuilder();

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, builder::build);

    assertNotNull(exception.getMessage(),
                  "Exception message should not be null");
  }

  // === Fluent API behavior ===

  @Test
  void when_chainMethods_then_returnsSameBuilderInstance() {
    BusBuilder builder = new BusBuilder();

    BusBuilder afterRoute = builder.routeNumber(ROUTE_NUMBER);
    BusBuilder afterModel = afterRoute.model(MODEL);
    BusBuilder afterMileage = afterModel.mileage(MILEAGE);

    // Each setter must return the SAME instance (fluent interface)
    assertSame(builder, afterRoute, "routeNumber() should return 'this'");
    assertSame(builder, afterModel, "model() should return 'this'");
    assertSame(builder, afterMileage, "mileage() should return 'this'");
  }

  @Test
  void when_buildCalledTwice_then_returnsDifferentInstances() {
    BusBuilder builder = new BusBuilder()
                             .routeNumber(ROUTE_NUMBER)
                             .model(MODEL)
                             .mileage(MILEAGE);

    Bus first = builder.build();
    Bus second = builder.build();

    // Different object references
    assertNotSame(first, second,
                  "Each build() call should create a new instance");

    // But logically equal because fields are the same
    assertEquals(first, second, "Both buses should be equal by values");
    assertEquals(first.hashCode(), second.hashCode(),
                 "Equal buses must have the same hashCode");
  }
}