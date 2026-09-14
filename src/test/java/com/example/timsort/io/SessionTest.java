package com.example.timsort.io;

import static org.junit.jupiter.api.Assertions.*;

import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionTest {

  private Session session;

  @BeforeEach
  void setUp() {
    session = new Session();
  }

  @Test
  @DisplayName("Should have empty optional for current collection initially")
  void shouldHaveEmptyCurrentInitially() {
    assertTrue(session.getCurrent().isEmpty());
  }

  @Test
  @DisplayName("Should have empty optional for last result initially")
  void shouldHaveEmptyLastResultInitially() {
    assertTrue(session.getLastResult().isEmpty());
  }

  @Test
  @DisplayName("Should store and retrieve current collection")
  void shouldStoreCurrentCollection() {
    CustomArrayList<Bus> collection = new CustomArrayList<>();
    collection.add(
        Bus.builder().routeNumber(1).model("A").mileage(100).build());

    session.setCurrent(collection);

    assertTrue(session.getCurrent().isPresent());
    assertEquals(collection, session.getCurrent().get());
  }

  @Test
  @DisplayName("Should store and retrieve last result")
  void shouldStoreLastResult() {
    List<Bus> result =
        List.of(Bus.builder().routeNumber(1).model("A").mileage(100).build());

    session.setLastResult(result);

    assertTrue(session.getLastResult().isPresent());
    assertEquals(result, session.getLastResult().get());
  }

  @Test
  @DisplayName("Should allow overwriting current collection")
  void shouldAllowOverwritingCurrent() {
    CustomArrayList<Bus> first = new CustomArrayList<>();
    CustomArrayList<Bus> second = new CustomArrayList<>();
    session.setCurrent(first);

    session.setCurrent(second);

    assertEquals(second, session.getCurrent().get());
  }

  @Test
  @DisplayName("Should allow overwriting last result")
  void shouldAllowOverwritingLastResult() {
    List<Bus> first =
        List.of(Bus.builder().routeNumber(1).model("A").mileage(100).build());
    List<Bus> second =
        List.of(Bus.builder().routeNumber(2).model("B").mileage(200).build());
    session.setLastResult(first);

    session.setLastResult(second);

    assertEquals(second, session.getLastResult().get());
  }

  @Test
  @DisplayName("Should handle null current collection gracefully")
  void shouldHandleNullCurrent() {
    session.setCurrent(null);

    assertTrue(session.getCurrent().isEmpty());
  }

  @Test
  @DisplayName("Should handle null last result gracefully")
  void shouldHandleNullLastResult() {
    session.setLastResult(null);

    assertTrue(session.getLastResult().isEmpty());
  }
}