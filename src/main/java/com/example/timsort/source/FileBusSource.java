package com.example.timsort.source;

import com.example.timsort.codec.BusCodec;
import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;
import com.example.timsort.validation.ValidationResult;
import com.example.timsort.validation.Validator;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Источник данных, читающий коллекцию Bus из CSV-файла.
 * <p>
 * Формат файла: routeNumber;model;mileage (разделитель ;).
 * Первая строка (заголовок) пропускается.
 * Невалидные строки пропускаются с предупреждением в консоль.
 * </p>
 */
public class FileBusSource implements DataSource<Bus> {

    private static final int HEADER_LINES_TO_SKIP = 1;
    private static final int FIRST_DATA_LINE_NUMBER = 2;

    private final Path path;
    private final BusCodec codec;
    private final Validator<Bus> validator;
    private final PrintStream output;

    /**
     * Конструктор с выводом в System.out.
     *
     * @param path      путь к CSV-файлу
     * @param codec     кодек для декодирования строк
     * @param validator валидатор для проверки объектов
     */
    public FileBusSource(Path path, BusCodec codec, Validator<Bus> validator) {
        this(path, codec, validator, System.out);
    }

    /**
     * Конструктор с настраиваемым выводом (для тестов).
     *
     * @param path      путь к CSV-файлу
     * @param codec     кодек для декодирования строк
     * @param validator валидатор для проверки объектов
     * @param output    поток для вывода предупреждений
     */
    public FileBusSource(Path path, BusCodec codec, Validator<Bus> validator, PrintStream output) {
        if (path == null) {
            throw new IllegalArgumentException("Path не должен быть null");
        }
        if (codec == null) {
            throw new IllegalArgumentException("BusCodec не должен быть null");
        }
        if (validator == null) {
            throw new IllegalArgumentException("Validator не должен быть null");
        }
        if (output == null) {
            throw new IllegalArgumentException("PrintStream не должен быть null");
        }
        this.path = path;
        this.codec = codec;
        this.validator = validator;
        this.output = output;
    }

    /**
     * Читает файл и возвращает до count валидных объектов Bus.
     * <p>
     * Если файл не существует или произошла ошибка чтения —
     * выводит сообщение об ошибке и возвращает пустую коллекцию.
     * Если валидных записей меньше, чем запрошено — выводит предупреждение
     * и возвращает все доступные валидные записи.
     * </p>
     *
     * @param count максимальное количество записей (должно быть >= 0)
     * @return коллекция валидных автобусов (может быть меньше count)
     * @throws IllegalArgumentException если count отрицательный
     */
    @Override
    public CustomArrayList<Bus> provide(int count) {
        if (count < 0) {
            throw new IllegalArgumentException(
                    "count не может быть отрицательным: " + count);
        }

        try (Stream<String> lines = Files.lines(path)) {
            AtomicInteger lineNumber = new AtomicInteger(FIRST_DATA_LINE_NUMBER);

            CustomArrayList<Bus> result = lines
                    .skip(HEADER_LINES_TO_SKIP)
                    .filter(line -> !line.trim().isEmpty())
                    .map(line -> {
                        int currentLine = lineNumber.getAndIncrement();
                        return decodeAndValidate(line, currentLine);
                    })
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .limit(count)
                    .collect(Collectors.toCollection(CustomArrayList::new));

            if (result.size() < count) {
                output.println("Предупреждение: найдено только " + result.size()
                        + " валидных записей из запрошенных " + count);
            }

            return result;
        } catch (IOException e) {
            output.println("Ошибка чтения файла " + path + ": " + e.getMessage());
            return new CustomArrayList<>();
        }
    }

    /**
     * Декодирует строку через BusCodec и валидирует результат.
     * При ошибке декодирования или валидации выводит предупреждение.
     *
     * @param line       строка из файла
     * @param lineNumber номер строки (для предупреждений)
     * @return Optional с валидным Bus или пустой Optional
     */
    private Optional<Bus> decodeAndValidate(String line, int lineNumber) {
        Optional<Bus> decoded = codec.decode(line);
        if (decoded.isEmpty()) {
            output.println("Строка " + lineNumber + ": не удалось декодировать — " + line);
            return Optional.empty();
        }

        Bus bus = decoded.get();
        ValidationResult<Bus> validation = validator.validate(bus);
        if (!validation.isValid()) {
            output.println("Строка " + lineNumber + ": " + validation.errors());
            return Optional.empty();
        }

        return Optional.of(bus);
    }
}