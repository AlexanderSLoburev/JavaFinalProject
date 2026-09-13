package com.example.timsort.source;

import static org.junit.jupiter.api.Assertions.*;

import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;
import com.example.timsort.validation.BusValidator;
import com.example.timsort.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class RandomBusSourceTest {

  private DataSource<Bus> source;

  @BeforeEach
  void setUp() {
    source = new RandomBusSource(new BusValidator());
  }

  @Test
  void when_provideWithPositiveCount_then_returnsCollectionOfExactSize() {
    // Arrange
    int count = 10;

    // Act
    CustomArrayList<Bus> result = source.provide(count);

    // Assert
    assertEquals(count, result.size(),
                 "The collection size must equal count — guaranteed by this " +
                 "implementation");
  }

  @Test
  void when_provide_then_allElementsAreValid() {
    // Arrange
    int count = 50;
    BusValidator validator = new BusValidator();

    // Act
    CustomArrayList<Bus> result = source.provide(count);

    // Assert
    for (Bus bus : result) {
      ValidationResult<Bus> validation = validator.validate(bus);
      assertTrue(validation.isValid(),
                 "All generated buses must be valid. Errors: " +
                     validation.errors());
    }
  }

  @Test
  void when_produceLargeCount_then_elementsAreDiverse() {
    // Arrange
    int count = 50;

    // Act
    CustomArrayList<Bus> result = source.provide(count);

    // Assert: with 999 possible route numbers, all 50 being identical
    // is practically impossible — this pins the diversity of generation
    long distinctRoutes =
        result.stream().mapToInt(Bus::routeNumber).distinct().count();
    assertTrue(distinctRoutes > 1,
               "Route numbers must be diverse within one call, got " +
                   distinctRoutes + " distinct values");
  }

  @Test
  void when_provideWithZeroCount_then_returnsEmptyCollection() {
    // Act
    CustomArrayList<Bus> result = source.provide(0);

    // Assert
    assertTrue(result.isEmpty(), "count = 0 must return an empty collection");
  }

  @Test
  void when_provideWithNegativeCount_then_throwsException() {
    // Arrange
    int negativeCount = -5;

    // Act & Assert
    assertThrows(IllegalArgumentException.class, () -> {
      source.provide(negativeCount);
    }, "A negative count must throw");
  }

  @Test
  void when_nullValidatorInConstructor_then_throwsNpe() {
    assertThrows(NullPointerException.class, () -> new RandomBusSource(null));
  }

  @Test
  void when_provideMultipleTimes_then_resultsAreDifferent() {
    // Arrange
    int count = 20;

    // Act
    CustomArrayList<Bus> first = source.provide(count);
    CustomArrayList<Bus> second = source.provide(count);

    // Assert: at least one bus must differ (the full-match probability
    // is ~1e-9 — practically zero)
    boolean hasDifference = false;
    for (int i = 0; i < count; i++) {
      if (!first.get(i).equals(second.get(i))) {
        hasDifference = true;
        break;
      }
    }
    assertTrue(hasDifference,
               "Two provide calls must produce different results (randomness)");
  }
}