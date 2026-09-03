package com.example.timsort.codec;

import com.example.timsort.model.Bus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BusCodecTest {

  private final BusCodec codec = new BusCodec();

  @Test
  void shouldDecodeValidString() {
    String csv = "42;ЛиАЗ-5292;150000";

    Optional<Bus> result = codec.decode(csv);

    assertTrue(result.isPresent());
    Bus bus = result.get();
    assertEquals(42, bus.routeNumber());
    assertEquals("ЛиАЗ-5292", bus.model());
    assertEquals(150000L, bus.mileage());
  }

  @Test
  void shouldReturnEmptyWhenLessThanThreeParts() {
    String csv = "42;ЛиАЗ-5292";

    Optional<Bus> result = codec.decode(csv);

    assertTrue(result.isEmpty());
  }

  @Test
  void shouldReturnEmptyWhenMoreThanThreeParts() {
    String csv = "42;ЛиАЗ-5292;150000;extra";

    Optional<Bus> result = codec.decode(csv);

    assertTrue(result.isEmpty());
  }

  @Test
  void shouldReturnEmptyWhenRouteNumberIsNotNumeric() {
    String csv = "abc;ЛиАЗ-5292;150000";

    Optional<Bus> result = codec.decode(csv);

    assertTrue(result.isEmpty());
  }

  @Test
  void shouldReturnEmptyWhenMileageIsNotNumeric() {
    String csv = "42;ЛиАЗ-5292;invalid";

    Optional<Bus> result = codec.decode(csv);

    assertTrue(result.isEmpty());
  }

  @Test
  void shouldReturnEmptyWhenRouteNumberExceedsIntRange() {
    String csv = "99999999999;ЛиАЗ-5292;150000";

    Optional<Bus> result = codec.decode(csv);

    assertTrue(result.isEmpty());
  }

  @Test
  void shouldReturnEmptyWhenMileageExceedsLongRange() {
    String csv = "42;ЛиАЗ-5292;99999999999999999999999999999999999999";

    Optional<Bus> result = codec.decode(csv);

    assertTrue(result.isEmpty());
  }

  @Test
  void shouldEncodeBusToCsvString() {
    Bus bus = Bus.builder()
            .routeNumber(42)
            .model("ЛиАЗ-5292")
            .mileage(150000L)
            .build();

    String result = codec.encode(bus);

    assertEquals("42;ЛиАЗ-5292;150000", result);
  }

  @Test
  void shouldRoundTripBus() {
    Bus original = Bus.builder()
            .routeNumber(73)
            .model("КАМАЗ-6282")
            .mileage(250000L)
            .build();

    String encoded = codec.encode(original);
    Optional<Bus> decoded = codec.decode(encoded);

    assertTrue(decoded.isPresent());
    assertEquals(original, decoded.get());
  }

  @Test
  void shouldRoundTripMultipleBuses() {
    Bus[] buses = {
            Bus.builder().routeNumber(1).model("ПАЗ-3204").mileage(10000L).build(),
            Bus.builder().routeNumber(15).model("ЛиАЗ-5292").mileage(50000L).build(),
            Bus.builder().routeNumber(99).model("Волгабас-5270").mileage(999999L).build(),
            Bus.builder().routeNumber(150).model("МАЗ-206").mileage(0L).build()
    };

    for (Bus original : buses) {
      String encoded = codec.encode(original);
      Optional<Bus> decoded = codec.decode(encoded);

      assertTrue(decoded.isPresent(), "Should decode successfully");
      assertEquals(original, decoded.get(), "Should round-trip correctly");
    }
  }

  @Test
  void shouldHandleEmptyString() {
    String csv = "";

    Optional<Bus> result = codec.decode(csv);

    assertTrue(result.isEmpty());
  }

  @Test
  void shouldHandleNullInputGracefully() {
    String csv = null;

    Optional<Bus> result = codec.decode(csv);

    assertTrue(result.isEmpty());
  }

  @Test
  void shouldPreserveModelWithSpecialCharacters() {
    Bus bus = Bus.builder()
            .routeNumber(7)
            .model("Model;With;Semicolons")
            .mileage(1000L)
            .build();

    String encoded = codec.encode(bus);
    Optional<Bus> decoded = codec.decode(encoded);

    assertTrue(decoded.isPresent());
    assertEquals(bus, decoded.get());
  }
}