package com.example.timsort.codec;

import com.example.timsort.model.Bus;
import java.util.Optional;

/**
 * Codec for serializing and deserializing Bus objects.
 *
 * <p>This codec converts between {@link Bus} objects and their CSV string
 * representation using semicolon ({@code ;}) as delimiter.
 * The format is: {@code routeNumber;model;mileage}</p>
 *
 * <p>This codec is stateless and thread-safe. It does NOT perform
 * semantic validation (like range checks) - that's the responsibility
 * of BusValidator.</p>
 */
public class BusCodec {

    private static final String DELIMITER = ";";

    /**
     * Encodes a {@link Bus} object into its CSV string representation.
     *
     * <p>The resulting format is: {@code routeNumber;model;mileage}</p>
     *
     * <p>Example: {@code 42;ЛиАЗ-5292;150000}</p>
     *
     * @param bus the Bus object to encode, must not be null
     * @return CSV string representation of the bus
     * @throws NullPointerException if bus is null
     */
    public String encode(Bus bus) {
        if (bus == null) {
            throw new NullPointerException("Bus must not be null");
        }
        return String.format("%d%s%s%s%d",
                bus.routeNumber(),
                DELIMITER,
                bus.model(),
                DELIMITER,
                bus.mileage());
    }

    /**
     * Decodes a CSV string into a {@link Bus} object.
     *
     * <p>The string must contain exactly three parts separated by semicolon:
     * route number (int), model (String), and mileage (long).</p>
     *
     * <p>If the string is malformed, contains non-numeric values, or fails
     * semantic validation in {@link Bus.Builder}, an empty Optional is returned.</p>
     *
     * @param csv the CSV string to decode, may be null or empty
     * @return Optional containing the decoded Bus if successful,
     *         or Optional.empty() if the string is invalid
     */
    public Optional<Bus> decode(String csv) {
        if (csv == null || csv.isEmpty()) {
            return Optional.empty();
        }

        String[] parts = csv.split(DELIMITER, -1);
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
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}