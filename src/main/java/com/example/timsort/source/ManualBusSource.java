package com.example.timsort.source;

import com.example.timsort.app.ConsoleIO;
import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;
import com.example.timsort.validation.ValidationResult;
import com.example.timsort.validation.Validator;

/**
 * Источник данных, запрашивающий ввод автобусов у пользователя через консоль.
 * Реализует паттерн Стратегия (DataSource).
 */
public class ManualBusSource implements DataSource<Bus> {

    private static final int MAX_ATTEMPTS = 3;

    private final ConsoleIO console;
    private final Validator<Bus> validator;

    /**
     * Конструктор.
     *
     * @param console   интерфейс для взаимодействия с консолью
     * @param validator валидатор для проверки введённых данных
     * @throws IllegalArgumentException если любой из параметров null
     */
    public ManualBusSource(ConsoleIO console, Validator<Bus> validator) {
        if (console == null) {
            throw new IllegalArgumentException("ConsoleIO не должен быть null");
        }
        if (validator == null) {
            throw new IllegalArgumentException("Validator не должен быть null");
        }
        this.console = console;
        this.validator = validator;
    }

    /**
     * Запрашивает у пользователя count объектов Bus.
     * Для каждого объекта даётся MAX_ATTEMPTS попыток.
     * При исчерпании попыток элемент пропускается.
     *
     * @param count количество запрошенных объектов (должно быть >= 0)
     * @return коллекция успешно введённых автобусов
     * @throws IllegalArgumentException если count отрицательный
     */
    @Override
    public CustomArrayList<Bus> provide(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count не может быть отрицательным: " + count);
        }

        CustomArrayList<Bus> result = new CustomArrayList<>();

        for (int i = 0; i < count; i++) {
            console.print("\n--- Ввод автобуса " + (i + 1) + " из " + count + " ---");
            Bus bus = readBusWithRetries();

            if (bus != null) {
                result.add(bus);
            } else {
                console.print("Превышено количество попыток. Элемент пропущен.");
            }
        }

        return result;
    }

    /**
     * Пытается считать и валидировать один объект Bus с ограничением попыток.
     *
     * @return валидный объект Bus или null, если попытки исчерпаны
     */
    private Bus readBusWithRetries() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                console.print("Введите номер маршрута (1-999): ");
                String routeStr = console.readLine();
                int routeNumber = Integer.parseInt(routeStr.trim());

                console.print("Введите модель автобуса: ");
                String model = console.readLine();
                if (model == null) {
                    model = "";
                }
                model = model.trim();

                console.print("Введите пробег (0-2000000): ");
                String mileageStr = console.readLine();
                long mileage = Long.parseLong(mileageStr.trim());

                Bus candidate = Bus.builder()
                        .routeNumber(routeNumber)
                        .model(model)
                        .mileage(mileage)
                        .build();

                ValidationResult<Bus> validation = validator.validate(candidate);
                if (validation.isValid()) {
                    return candidate;
                } else {
                    console.print("Ошибка валидации: " + String.join(", ", validation.errors()));
                }
            } catch (NumberFormatException e) {
                console.print("Ошибка: введено некорректное число. Попробуйте снова.");
            } catch (IllegalArgumentException e) {
                console.print("Ошибка создания автобуса: " + e.getMessage());
            } catch (NullPointerException e) {
                console.print("Ошибка: ввод прерван или пуст.");
            }

            if (attempt < MAX_ATTEMPTS) {
                console.print("Осталось попыток: " + (MAX_ATTEMPTS - attempt));
            }
        }
        return null;
    }
}