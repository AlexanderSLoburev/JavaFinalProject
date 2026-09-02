package com.example.timsort.model;

public record Bus(int routeNumber, String model, long mileage) {
  @Override
  public String toString() {
    return "Bus{" + this.routeNumber + ", " + this.model + "," + this.mileage +
        "}";
  }
}
