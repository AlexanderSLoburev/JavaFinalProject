package com.example.timsort.io;

import com.example.timsort.codec.BusCodec;
import com.example.timsort.model.Bus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для {@link FileResultWriter}.
 */
class FileResultWriterTest {
 
    @TempDir
    Path tempDir;

    private Path resultFile;
    private BusCodec codec;
    private FileResultWriter<Bus> writer;

    @BeforeEach
    void setUp() {
        resultFile = tempDir.resolve("result.csv");
        codec = new BusCodec();
        writer = new FileResultWriter<>(resultFile, codec::encode);
    }

    @Test
    @DisplayName("Файл создаётся при отсутствии")
    void appendAll_createsFileIfMissing() {
        assertFalse(Files.exists(resultFile),
                "Файл не должен существовать до вызова");

        writer.appendAll(List.of());

        assertTrue(Files.exists(resultFile),
                "Файл должен быть создан после первого вызова");
    }

    @Test
    @DisplayName("Каждый элемент форматируется переданной функцией")
    void appendAll_formatsEachElement() throws IOException {
        Bus bus1 = Bus.builder()
                .routeNumber(42).model("ЛиАЗ-5292").mileage(150_000).build();
        Bus bus2 = Bus.builder()
                .routeNumber(7).model("МАЗ-203").mileage(80_000).build();

        writer.appendAll(List.of(bus1, bus2));

        String content = Files.readString(resultFile, StandardCharsets.UTF_8);
        assertTrue(content.contains(codec.encode(bus1)),
                "Содержимое должно содержать отформатированный bus1");
        assertTrue(content.contains(codec.encode(bus2)),
                "Содержимое должно содержать отформатированный bus2");
    }

    @Test
    @DisplayName("Перед блоком данных записывается заголовок с временной меткой")
    void appendAll_writesHeaderWithTimestamp() throws IOException {
        writer.appendAll(List.of());

        String content = Files.readString(resultFile, StandardCharsets.UTF_8);
        assertTrue(content.startsWith("--- Результат от "),
                "Файл должен начинаться с заголовка");
        assertTrue(content.contains(" ---"),
                "Заголовок должен заканчиваться ' ---'");
        // Проверка формата метки: yyyy-MM-ddTHH:mm:ss внутри заголовка
        assertTrue(content.matches(
                        "(?s)^--- Результат от \\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"),
                "Заголовок должен содержать метку времени в формате ISO");
    }

    @Test
    @DisplayName("Повторные вызовы добавляют данные, не удаляя предыдущие")
    void appendAll_appendsInsteadOfOverwriting() throws IOException {
        Bus bus1 = Bus.builder()
                .routeNumber(1).model("ПАЗ-3205").mileage(10_000).build();
        Bus bus2 = Bus.builder()
                .routeNumber(2).model("ГАЗель Next").mileage(20_000).build();

        writer.appendAll(List.of(bus1));
        writer.appendAll(List.of(bus2));

        String content = Files.readString(resultFile, StandardCharsets.UTF_8);
        assertTrue(content.contains(codec.encode(bus1)),
                "Первый блок должен сохраниться");
        assertTrue(content.contains(codec.encode(bus2)),
                "Второй блок должен быть добавлен");

        // Оба заголовка присутствуют => два вызова appendAll
        long headerCount = content.lines()
                .filter(line -> line.startsWith("--- Результат от "))
                .count();
        assertEquals(2, headerCount,
                "Должно быть два заголовка — по одному на каждый вызов");
    }

    @Test
    @DisplayName("Порядок элементов сохраняется")
    void appendAll_preservesOrder() throws IOException {
        Bus bus1 = Bus.builder()
                .routeNumber(1).model("A").mileage(1).build();
        Bus bus2 = Bus.builder()
                .routeNumber(2).model("B").mileage(2).build();
        Bus bus3 = Bus.builder()
                .routeNumber(3).model("C").mileage(3).build();

        writer.appendAll(List.of(bus1, bus2, bus3));

        List<String> lines = Files.readAllLines(resultFile, StandardCharsets.UTF_8);
        int i1 = lines.indexOf(codec.encode(bus1));
        int i2 = lines.indexOf(codec.encode(bus2));
        int i3 = lines.indexOf(codec.encode(bus3));

        assertTrue(i1 < i2 && i2 < i3,
                "Порядок элементов должен сохраняться");
    }

    @Test
    @DisplayName("Описание добавляется в заголовок, если задано")
    void appendAll_includesDescriptionWhenProvided() throws IOException {
        FileResultWriter<Bus> describedWriter = new FileResultWriter<>(
                resultFile, codec::encode, "сортировка по пробегу");

        describedWriter.appendAll(List.of());

        String content = Files.readString(resultFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("(сортировка по пробегу)"),
                "Описание должно быть в заголовке");
    }

    @Test
    @DisplayName("Пустой список записывает только заголовок")
    void appendAll_emptyListWritesOnlyHeader() throws IOException {
        writer.appendAll(List.of());

        List<String> lines = Files.readAllLines(resultFile, StandardCharsets.UTF_8);
        assertEquals(1, lines.size(),
                "Должна быть только строка заголовка");
        assertTrue(lines.get(0).startsWith("--- Результат от "));
    }

    @Test
    @DisplayName("appendAll(null) выбрасывает NullPointerException")
    void appendAll_nullList_throwsException() {
        assertThrows(NullPointerException.class,
                () -> writer.appendAll(null));
    }

    @Test
    @DisplayName("Конструктор с null path/formatter выбрасывает исключение")
    void constructor_nullArguments_throwException() {
        assertThrows(NullPointerException.class,
                () -> new FileResultWriter<Bus>(null, codec::encode));
        assertThrows(NullPointerException.class,
                () -> new FileResultWriter<Bus>(resultFile, null));
    }
}