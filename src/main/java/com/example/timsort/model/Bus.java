package com.example.timsort.model;

public record Bus(int routeNumber, String model, long mileage) {

  public Bus {
    if (routeNumber < 0) {
      throw new IllegalArgumentException("routeNumber must be non-negative");
    }
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("model must not be null or blank");
    }
    if (mileage < 0) {
      throw new IllegalArgumentException("mileage must be non-negative");
    }
  }

  @Override
  public String toString() {
    return "Bus{" + routeNumber + ", " + model + ", " + mileage + "}";
  }
}