package com.example.timsort.source;

import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;
import com.example.timsort.validation.ValidationResult;
import com.example.timsort.validation.Validator;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Источник данных, генерирующий случайные валидные объекты Bus.
 * Использует Stream API и ThreadLocalRandom для генерации.
 */
public class RandomBusSource implements DataSource<Bus> {

    private static final List<String> MODELS_POOL = List.of(
            "ЛиАЗ-5292",
            "МАЗ-203",
            "ПАЗ-3204",
            "Волжанин-6270",
            "НефАЗ-5299",
            "KAvZ-4270",
            "ГолАЗ-6228",
            "MAN Lion City"
    );

    private static final int MIN_ROUTE_NUMBER = 1;
    private static final int MAX_ROUTE_NUMBER = 999;

    private static final long MIN_MILEAGE = 0;
    private static final long MAX_MILEAGE = 2_000_000;

    private final Validator<Bus> validator;

    /**
     * Конструктор.
     *
     * @param validator валидатор для проверки сгенерированных объектов
     * @throws IllegalArgumentException если validator равен null
     */
    public RandomBusSource(Validator<Bus> validator) {
        if (validator == null) {
            throw new IllegalArgumentException("Validator не должен быть null");
        }
        this.validator = validator;
    }

    /**
     * Генерирует count случайных валидных объектов Bus.
     *
     * @param count количество объектов (должно быть >= 0)
     * @return коллекция сгенерированных автобусов
     * @throws IllegalArgumentException если count отрицательный
     */
    @Override
    public CustomArrayList<Bus> provide(int count) {
        if (count < 0) {
            throw new IllegalArgumentException(
                    "count не может быть отрицательным: " + count);
        }

        return IntStream.range(0, count)
                .mapToObj(i -> generateValidBus())
                .collect(Collectors.toCollection(CustomArrayList::new));
    }

    /**
     * Генерирует один валидный объект Bus.
     * Если объект не проходит валидацию, бросает IllegalStateException.
     *
     * @return валидный объект Bus
     */
    private Bus generateValidBus() {
        Bus bus = Bus.builder()
                .routeNumber(randomInt(MIN_ROUTE_NUMBER, MAX_ROUTE_NUMBER))
                .model(randomModel())
                .mileage(randomLong(MIN_MILEAGE, MAX_MILEAGE))
                .build();

        ValidationResult<Bus> result = validator.validate(bus);
        if (!result.isValid()) {
            throw new IllegalStateException(
                    "Сгенерированный Bus не прошёл валидацию: " + result.errors());
        }

        return bus;
    }

    /**
     * Возвращает случайную модель из пула.
     *
     * @return случайное название модели
     */
    private String randomModel() {
        int index = ThreadLocalRandom.current().nextInt(MODELS_POOL.size());
        return MODELS_POOL.get(index);
    }

    /**
     * Возвращает случайное int в диапазоне [min, max] включительно.
     */
    private int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Возвращает случайное long в диапазоне [min, max] включительно.
     */
    private long randomLong(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }
}