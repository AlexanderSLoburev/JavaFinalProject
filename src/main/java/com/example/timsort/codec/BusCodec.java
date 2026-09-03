package com.example.timsort.codec;

import com.example.timsort.model.Bus;
import java.util.Optional;

public class BusCodec {

    public Optional<Bus> decode(String csv) {
        if (csv == null || csv.isEmpty()){
            return Optional.empty();
        }

        String[] parts = csv.split(";", -1);

        if (parts.length != 3) {
            return Optional.empty();
        }

        try {
            int routeNumber = Integer.parseInt(parts[0].trim());
            String model = parts[1].trim();
            long mileage = Long.parseLong(parts[2].trim());

            Bus bus = Bus.builder()
                    .routeNumber(routeNumber)
                    .model(model)
                    .mileage(mileage)
                    .build();

            return Optional.of(bus);
        } catch (IllegalArgumentException  e){
            return Optional.empty();
        }
    }

    public String encode(Bus bus) {
        if (bus == null) {
            throw new NullPointerException("Bus must not be null");
        }
        return String.format("%d;%s;%d",
                bus.routeNumber(),
                bus.model(),
                bus.mileage());
    }
}

