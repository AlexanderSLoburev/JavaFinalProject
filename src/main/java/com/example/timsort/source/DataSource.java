package com.example.timsort.source;

import com.example.timsort.collection.CustomArrayList;

/**
 * Стратегия заполнения коллекции данными.
 * Реализации предоставляют count элементов заданного типа.
 *
 * @param <T> тип генерируемых или предоставляемых объектов
 */
public interface DataSource<T> {

    /**
     * Генерирует или предоставляет коллекцию из count элементов.
     *
     * @param count количество элементов (должно быть >= 0)
     * @return коллекция предоставленных объектов
     * @throws IllegalArgumentException если count отрицательный
     */
    CustomArrayList<T> provide(int count);
}