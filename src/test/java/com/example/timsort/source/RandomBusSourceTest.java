package com.example.timsort.source;

import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;
import com.example.timsort.validation.BusValidator;
import com.example.timsort.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
                "Размер коллекции должен быть равен count");
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
                    "Все сгенерированные автобусы должны быть валидны. Ошибки: " + validation.errors());
        }
    }

    @Test
    void when_provideWithZeroCount_then_returnsEmptyCollection() {
        // Arrange
        // Act
        CustomArrayList<Bus> result = source.provide(0);

        // Assert
        assertTrue(result.isEmpty(),
                "При count=0 должна вернуться пустая коллекция");
    }

    @Test
    void when_provideWithNegativeCount_then_throwsException() {
        // Arrange
        int negativeCount = -5;

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            source.provide(negativeCount);
        }, "При отрицательном count должно быть выброшено исключение");
    }

    @Test
    void when_provideMultipleTimes_then_resultsAreDifferent() {
        // Arrange
        int count = 20;

        // Act
        CustomArrayList<Bus> first = source.provide(count);
        CustomArrayList<Bus> second = source.provide(count);

        // Assert
        // Хотя бы один автобус должен отличаться (вероятность совпадения почти нулевая)
        boolean hasDifference = false;
        for (int i = 0; i < count; i++) {
            if (!first.get(i).equals(second.get(i))) {
                hasDifference = true;
                break;
            }
        }
        assertTrue(hasDifference,
                "Два вызова provide должны давать разные результаты (рандомность)");
    }
}