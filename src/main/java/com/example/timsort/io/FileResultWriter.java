package com.example.timsort.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
 
/**
 * Реализация {@link ResultWriter}, записывающая элементы в файл
 * в режиме добавления (APPEND).
 *
 * <p>Формат вывода:
 * <pre>
 * --- Результат от 2025-03-25T12:00:00 ---
 * routeNumber;model;mileage
 * routeNumber;model;mileage
 * ...
 * </pre>
 *
 * <p>Файл создаётся, если не существует. Повторные вызовы
 * {@link #appendAll(List)} не удаляют предыдущие данные, а дописывают
 * новые блоки в конец. Каждый блок предваряется заголовком с временной
 * меткой (и, при наличии, описанием сортировки).
 * </p>
 *
 * <p>Класс потокобезопасен за счёт синхронизации на самом экземпляре:
 * одновременные вызовы {@code appendAll} не перемешивают блоки.
 * </p>
 *
 * @param <T> тип записываемых элементов
 */
public class FileResultWriter<T> implements ResultWriter<T> {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String HEADER_PREFIX = "--- Результат от ";
    private static final String HEADER_SUFFIX = " ---";
    private static final String DEFAULT_DESCRIPTION = "";

    private final Path path;
    private final Function<T, String> formatter;
    private final String description;

    /**
     * Создаёт писатель с пустым описанием.
     *
     * @param path      путь к файлу результата
     * @param formatter функция преобразования элемента в строку
     * @throws NullPointerException если {@code path} или {@code formatter} равны {@code null}
     */
    public FileResultWriter(Path path, Function<T, String> formatter) {
        this(path, formatter, DEFAULT_DESCRIPTION);
    }

    /**
     * Создаёт писатель с описанием сортировки/обработки.
     *
     * @param path        путь к файлу результата
     * @param formatter   функция преобразования элемента в строку
     * @param description описание (например, "сортировка по пробегу"); может быть пустым
     * @throws NullPointerException если {@code path} или {@code formatter} равны {@code null}
     */
    public FileResultWriter(Path path, Function<T, String> formatter,
                            String description) {
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.formatter = Objects.requireNonNull(formatter,
                "formatter must not be null");
        this.description = (description == null) ? DEFAULT_DESCRIPTION
                                                 : description;
    }

    /**
     * Дописывает блок данных в конец файла.
     * <p>
     * Каждый вызов формирует заголовок с текущей временной меткой и
     * (при наличии) описанием, затем добавляет отформатированные
     * элементы. Файл создаётся, если не существует.
     * </p>
     *
     * @param items список элементов для записи
     * @throws NullPointerException если {@code items} равен {@code null}
     * @throws UncheckedIOException если произошла ошибка ввода-вывода
     */
    @Override
    public synchronized void appendAll(List<T> items) {
        Objects.requireNonNull(items, "items must not be null");

        List<String> lines = new ArrayList<>(items.size() + 1);
        lines.add(buildHeader());
        for (T item : items) {
            lines.add(formatter.apply(item));
        }

        try {
            Files.write(path, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Не удалось записать результаты в " + path, e);
        }
    }

    /**
     * Формирует строку заголовка с временной меткой и описанием.
     *
     * @return строка вида
     *         {@code --- Результат от 2025-03-25T12:00:00 (сортировка по пробегу) ---}
     */
    private String buildHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER_PREFIX)
          .append(LocalDateTime.now().format(TIMESTAMP_FORMAT));
        if (!description.isEmpty()) {
            sb.append(" (").append(description).append(')');
        }
        sb.append(HEADER_SUFFIX);
        return sb.toString();
    }
}