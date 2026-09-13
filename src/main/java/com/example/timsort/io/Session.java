package com.example.timsort.io;

import com.example.timsort.collection.CustomArrayList;
import com.example.timsort.model.Bus;

import java.util.List;
import java.util.Optional;

public class Session {

    private Optional<CustomArrayList<Bus>> current = Optional.empty();
    private Optional<List<Bus>> lastResult = Optional.empty();

    public Optional<CustomArrayList<Bus>> getCurrent() {
        return current;
    }

    public void setCurrent(CustomArrayList<Bus> current) {
        this.current = Optional.ofNullable(current);
    }

    public Optional<List<Bus>> getLastResult() {
        return lastResult;
    }

    public void setLastResult(List<Bus> lastResult) {
        this.lastResult = Optional.ofNullable(lastResult);
    }
}