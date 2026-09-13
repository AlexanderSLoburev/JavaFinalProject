package com.example.timsort.source;

import com.example.timsort.app.ConsoleIO;
import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;
import com.example.timsort.validation.BusValidator;
import com.example.timsort.validation.ValidationResult;
import com.example.timsort.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class ManualBusSourceTest {

    private Validator<Bus> validator;

    @BeforeEach
    void setUp() {
        validator = new BusValidator();
    }

    @Test
    void when_allInputsValid_then_returnsExactCount() {
        // Arrange: эмулируем ввод 2 корректных автобусов
        FakeConsoleIO console = new FakeConsoleIO(
                "1", "ЛиАЗ-5292", "100000",  // Автобус 1
                "2", "МАЗ-203", "200000"     // Автобус 2
        );
        ManualBusSource source = new ManualBusSource(console, validator);

        // Act
        CustomArrayList<Bus> result = source.provide(2);

        // Assert
        assertEquals(2, result.size());
        assertEquals(1, result.get(0).routeNumber());
        assertEquals(2, result.get(1).routeNumber());
        assertFalse(console.getOutputs().stream().anyMatch(out -> out.contains("Ошибка")));
    }

    @Test
    void when_invalidNumber_then_retriesAndSucceeds() {
        // Arrange: первая попытка с нечисловым значением, вторая успешная
        FakeConsoleIO console = new FakeConsoleIO(
                "abc",          // Ошибка парсинга routeNumber
                "42", "ЛиАЗ", "50000" // Успешный ввод
        );
        ManualBusSource source = new ManualBusSource(console, validator);

        // Act
        CustomArrayList<Bus> result = source.provide(1);

        // Assert
        assertEquals(1, result.size());
        assertEquals(42, result.get(0).routeNumber());
        assertTrue(console.getOutputs().stream().anyMatch(out -> out.contains("Ошибка") || out.contains("некорректное")),
                "Должно быть сообщение об ошибке парсинга");
    }

    @Test
    void when_validationFails_then_retriesAndSucceeds() {
        // Arrange: первая попытка с невалидным маршрутом (0), вторая успешная
        FakeConsoleIO console = new FakeConsoleIO(
                "0", "ЛиАЗ", "100000",   // Ошибка валидации (маршрут < 1)
                "5", "МАЗ", "200000"     // Успешный ввод
        );
        ManualBusSource source = new ManualBusSource(console, validator);

        // Act
        CustomArrayList<Bus> result = source.provide(1);

        // Assert
        assertEquals(1, result.size());
        assertEquals(5, result.get(0).routeNumber());
        assertTrue(console.getOutputs().stream().anyMatch(out -> out.contains("Ошибка валидации")),
                "Должно быть сообщение об ошибке валидации");
    }

    @Test
    void when_maxAttemptsExceeded_then_skipsElement() {
        // Arrange: 3 неудачные попытки для одного элемента, затем 1 успешный для второго
        FakeConsoleIO console = new FakeConsoleIO(
                "bad", "bad", "bad", // 3 ошибки для первого элемента
                "10", "ПАЗ", "300000" // Успешный ввод для второго элемента
        );
        ManualBusSource source = new ManualBusSource(console, validator);

        // Act
        CustomArrayList<Bus> result = source.provide(2);

        // Assert
        assertEquals(1, result.size(), "Первый элемент должен быть пропущен");
        assertEquals(10, result.get(0).routeNumber());
        assertTrue(console.getOutputs().stream()
                        .anyMatch(out -> out.toLowerCase().contains("превышено") ||
                                out.toLowerCase().contains("пропуск")),
                "Должно быть сообщение о превышении попыток");
    }

    @Test
    void when_negativeCount_then_throwsException() {
        // Arrange
        FakeConsoleIO console = new FakeConsoleIO();
        ManualBusSource source = new ManualBusSource(console, validator);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            source.provide(-1);
        });
    }

    /**
     * Простая фейковая реализация ConsoleIO для тестов.
     * Отдаёт строки из очереди и запоминает все выводы.
     */
    private static class FakeConsoleIO implements ConsoleIO {
        private final Queue<String> inputs;
        private final List<String> outputs = new ArrayList<>();

        FakeConsoleIO(String... inputs) {
            this.inputs = new LinkedList<>(Arrays.asList(inputs));
        }

        @Override
        public String readLine() {
            return inputs.poll();
        }

        @Override
        public void print(String message) {
            outputs.add(message);
        }

        @Override
        public void printf(String format, Object... args) {
            outputs.add(String.format(format, args));
        }

        public List<String> getOutputs() {
            return outputs;
        }
    }
}