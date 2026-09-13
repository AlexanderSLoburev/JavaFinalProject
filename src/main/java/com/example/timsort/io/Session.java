package com.example.timsort.io;

import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;

import java.util.List;
import java.util.Optional;

/**
 * Shared state holder for the console application.
 *
 * <p>Stores the current bus collection and the result of the last sort operation.
 * Both values are wrapped in {@link Optional} to explicitly represent the
 * "not set yet" state — for example, before the user fills the collection or
 * runs a sort.</p>
 *
 * <p>Used by menu commands to exchange data between invocations.</p>
 */
public class Session {

    private Optional<CustomArrayList<Bus>> current = Optional.empty();
    private Optional<List<Bus>> lastResult = Optional.empty();

    /**
     * Returns the current collection, if it has been set.
     *
     * @return the current collection, or {@link Optional#empty()} if not set
     */
    public Optional<CustomArrayList<Bus>> getCurrent() {
        return current;
    }

    /**
     * Sets the current collection. Passing {@code null} clears it.
     *
     * @param current the collection to store, or {@code null} to clear
     */
    public void setCurrent(CustomArrayList<Bus> current) {
        this.current = Optional.ofNullable(current);
    }

    /**
     * Returns the last sort result, if a sort has been performed.
     *
     * @return the last sort result, or {@link Optional#empty()} if not set
     */
    public Optional<List<Bus>> getLastResult() {
        return lastResult;
    }

    /**
     * Sets the last sort result. Passing {@code null} clears it.
     *
     * @param lastResult the result to store, or {@code null} to clear
     */
    public void setLastResult(List<Bus> lastResult) {
        this.lastResult = Optional.ofNullable(lastResult);
    }
}