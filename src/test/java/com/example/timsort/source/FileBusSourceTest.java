package com.example.timsort.source;

import com.example.timsort.codec.BusCodec;
import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;
import com.example.timsort.validation.BusValidator;
import com.example.timsort.validation.ValidationResult;
import com.example.timsort.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileBusSourceTest {

    @TempDir
    Path tempDir;

    private BusCodec codec;
    private Validator<Bus> validator;

    @BeforeEach
    void setUp() {
        codec = new BusCodec();
        validator = new BusValidator();
    }

    @Test
    void when_fileWithValidLines_then_returnsExactCount() throws IOException {
        // Arrange
        int count = 3;
        Path file = createTempFile(List.of(
                "routeNumber;model;mileage",  // заголовок
                "1;ЛиАЗ-5292;100000",
                "2;МАЗ-203;200000",
                "3;ПАЗ-3204;300000",
                "4;Волжанин-6270;400000"
        ));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FileBusSource source = new FileBusSource(file, codec, validator, new PrintStream(output));

        // Act
        CustomArrayList<Bus> result = source.provide(count);

        // Assert
        assertEquals(count, result.size(), "Должно быть возвращено ровно count записей");

        // Проверяем, что заголовок пропущен (берутся первые 3 валидные строки после заголовка)
        assertEquals(1, result.get(0).routeNumber());
        assertEquals(2, result.get(1).routeNumber());
        assertEquals(3, result.get(2).routeNumber());

        // Предупреждений быть не должно
        assertTrue(output.toString().isEmpty(), "Не должно быть предупреждений для валидных данных");
    }

    @Test
    void when_fileWithInvalidLines_then_skipsThemWithWarnings() throws IOException {
        // Arrange
        Path file = createTempFile(List.of(
                "routeNumber;model;mileage",
                "1;ЛиАЗ-5292;100000",        // валидная
                "invalid;line",               // невалидная (неверный формат)
                "2;МАЗ-203;200000",           // валидная
                "0;ПАЗ-3204;300000"           // невалидная (routeNumber=0)
        ));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FileBusSource source = new FileBusSource(file, codec, validator, new PrintStream(output));

        // Act
        CustomArrayList<Bus> result = source.provide(10);

        // Assert
        assertEquals(2, result.size(), "Должны быть пропущены невалидные строки");
        assertEquals(1, result.get(0).routeNumber());
        assertEquals(2, result.get(1).routeNumber());

        // Проверяем, что были выведены предупреждения
        String warnings = output.toString();
        assertTrue(warnings.contains("Строка 3"), "Должно быть предупреждение о строке 3");
        assertTrue(warnings.contains("Строка 5"), "Должно быть предупреждение о строке 5");
    }

    @Test
    void when_fileWithHeader_then_skipsFirstLine() throws IOException {
        // Arrange
        Path file = createTempFile(List.of(
                "routeNumber;model;mileage",  // заголовок
                "1;ЛиАЗ-5292;100000"
        ));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FileBusSource source = new FileBusSource(file, codec, validator, new PrintStream(output));

        // Act
        CustomArrayList<Bus> result = source.provide(1);

        // Assert
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).routeNumber());
    }

    @Test
    void when_fewerValidLinesThanCount_then_returnsAllAvailable() throws IOException {
        // Arrange
        int requestedCount = 10;
        Path file = createTempFile(List.of(
                "routeNumber;model;mileage",
                "1;ЛиАЗ-5292;100000",
                "2;МАЗ-203;200000"
        ));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // Явно указываем UTF-8 и авто-сброс
        FileBusSource source = new FileBusSource(file, codec, validator,
                new PrintStream(output, true, java.nio.charset.StandardCharsets.UTF_8));

        // Act
        CustomArrayList<Bus> result = source.provide(requestedCount);

        // Assert
        assertEquals(2, result.size(), "Должны быть возвращены все доступные валидные записи");

        // Проверяем, что было выведено сообщение о недостатке данных
        String warnings = output.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(warnings.contains("валидных записей") || warnings.contains("Предупреждение"),
                "Должно быть сообщение о недостатке валидных записей, вывод: " + warnings);
    }

    @Test
    void when_fileDoesNotExist_then_returnsEmptyCollection() {
        // Arrange
        Path nonExistentFile = tempDir.resolve("nonexistent.csv");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FileBusSource source = new FileBusSource(nonExistentFile, codec, validator, new PrintStream(output));

        // Act
        CustomArrayList<Bus> result = source.provide(5);

        // Assert
        assertTrue(result.isEmpty(), "При отсутствии файла должна вернуться пустая коллекция");
        assertTrue(output.toString().contains("Ошибка чтения файла"),
                "Должно быть выведено сообщение об ошибке");
    }

    @Test
    void when_negativeCount_then_throwsException() throws IOException {
        // Arrange
        Path file = createTempFile(List.of("routeNumber;model;mileage"));
        FileBusSource source = new FileBusSource(file, codec, validator);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            source.provide(-1);
        }, "При отрицательном count должно быть выброшено исключение");
    }

    // Вспомогательный метод для создания временного файла
    private Path createTempFile(List<String> lines) throws IOException {
        Path file = tempDir.resolve("test.csv");
        Files.write(file, lines);
        return file;
    }
}