package com.example.timsort.validation;

import com.example.timsort.model.Bus;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BusValidatorTest {

    @Test
    void shouldAcceptValidBus() {
        BusValidator validator = new BusValidator();
        Bus validBus = Bus.builder()  // ← используем API loburev
                .routeNumber(42)
                .model("ЛиАЗ-5292")
                .mileage(150000)
                .build();

        ValidationResult<Bus> result = validator.validate(validBus);

        assertTrue(result.isValid(), "Валидный автобус должен пройти валидацию");
        assertEquals(validBus, result.value().orElse(null), "Результат должен содержать исходный объект");
        assertTrue(result.errors().isEmpty(), "Список ошибок должен быть пуст");
    }

    @Test
    void shouldRejectInvalidRouteNumber() {
        BusValidator validator = new BusValidator();
        Bus invalidBus = Bus.builder()  // ← используем API loburev
                .routeNumber(0)
                .model("ЛиАЗ-5292")
                .mileage(150000)
                .build();

        ValidationResult<Bus> result = validator.validate(invalidBus);

        assertFalse(result.isValid(), "Невалидный номер маршрута должен быть отклонён");
        assertTrue(result.errors().stream()
                        .anyMatch(error -> error.toLowerCase().contains("маршрут")),
                "Ошибка должна упоминать маршрут");
    }

    @Test
    void shouldRejectEmptyModel() {
        // Arrange
        BusValidator validator = new BusValidator();
        // Пустая строка не пройдёт Builder, поэтому используем недопустимые символы
        Bus invalidBus = Bus.builder()
                .routeNumber(42)
                .model("!!!")  // Спецсимволы не проходят шаблон
                .mileage(150000)
                .build();

        // Act
        ValidationResult<Bus> result = validator.validate(invalidBus);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.errors().stream()
                .anyMatch(error -> error.toLowerCase().contains("модель")));
    }

    @Test
    void shouldRejectNegativeMileage() {
        // Arrange
        BusValidator validator = new BusValidator();
        // Отрицательное не пройдёт Builder, поэтому используем слишком большое
        Bus invalidBus = Bus.builder()
                .routeNumber(42)
                .model("ЛиАЗ-5292")
                .mileage(2_000_001)  // Больше максимума
                .build();

        // Act
        ValidationResult<Bus> result = validator.validate(invalidBus);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.errors().stream()
                .anyMatch(error -> error.toLowerCase().contains("пробег")));
    }

    @Test
    void shouldCollectMultipleErrors() {
        // Arrange
        BusValidator validator = new BusValidator();
        // Используем значения, которые проходят Builder, но не проходят Validator:
        // routeNumber=0 (должно быть 1-999)
        // model="!" (допустимая строка, но не проходит шаблон)
        // mileage=2_000_001 (должно быть 0-2_000_000)
        Bus invalidBus = Bus.builder()
                .routeNumber(0)
                .model("!")
                .mileage(2_000_001)
                .build();

        // Act
        ValidationResult<Bus> result = validator.validate(invalidBus);

        // Assert
        assertFalse(result.isValid());
        assertEquals(3, result.errors().size(), "Должно быть ровно 3 ошибки");
    }

    @Test
    void when_validatorsComposedWithAnd_then_errorsAccumulate() {
        // Arrange
        Validator<Bus> validator1 = new BusValidator();
        Validator<Bus> validator2 = bus -> {
            if (bus.routeNumber() == 42) {
                return ValidationResult.failure(List.of("Custom error: route 42 not allowed"));
            }
            return ValidationResult.of(bus);
        };

        Validator<Bus> composed = validator1.and(validator2);
        Bus bus = Bus.builder()
                .routeNumber(42)
                .model("ЛиАЗ-5292")
                .mileage(150000)
                .build();

        // Act
        ValidationResult<Bus> result = composed.validate(bus);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.errors().size() >= 1);
        assertTrue(result.errors().stream()
                .anyMatch(e -> e.contains("Custom error")));
    }

    @Test
    void when_boundaryValidValues_then_validationPasses() {
        // Arrange
        BusValidator validator = new BusValidator();

        // Минимальные допустимые значения
        Bus minValid = Bus.builder()
                .routeNumber(1)
                .model("AB")
                .mileage(0)
                .build();

        // Максимальные допустимые значения
        Bus maxValid = Bus.builder()
                .routeNumber(999)
                .model("A".repeat(30))
                .mileage(2_000_000)
                .build();

        // Act
        ValidationResult<Bus> resultMin = validator.validate(minValid);
        ValidationResult<Bus> resultMax = validator.validate(maxValid);

        // Assert
        assertTrue(resultMin.isValid(), "Минимальные допустимые значения должны проходить");
        assertTrue(resultMax.isValid(), "Максимальные допустимые значения должны проходить");
    }

    @Test
    void when_validateNull_then_returnsFailure() {
        // Arrange
        BusValidator validator = new BusValidator();

        // Act
        ValidationResult<Bus> result = validator.validate(null);

        // Assert
        assertFalse(result.isValid(), "Null объект должен быть отклонён");
        assertTrue(result.errors().stream()
                        .anyMatch(error -> error.contains("null")),
                "Ошибка должна упоминать null");
    }
}