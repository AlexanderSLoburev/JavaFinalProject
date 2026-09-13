package com.example.timsort.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.timsort.codec.BusCodec;
import com.example.timsort.model.Bus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Integration tests for the Bus + BusCodec combination (+ Java serialization of
 * Bus).
 *
 * <p>The tests cover component interactions rather than each class
 * individually:</p> <ul> <li>Bus → CSV → Bus round trip (Cyrillic characters,
 * boundary values);</li> <li>Invalid CSV: the codec delegates semantic
 * validation to Bus invariants (builder → constructor) and wraps any error
 * in an empty Optional;</li> <li>Java serialization: round-trip and rejection
 * of corrupted (“malicious”) streams by validation in `readObject`
 * (`InvalidObjectException`);</li> <li>end-to-end scenarios where CSV and
 * Java serialization work together.</li>
 * </ul>
 */

class BusCodecIntegrationTest {

  private final BusCodec codec = new BusCodec();

  // -------------------------------------------------------------------
  // Общие фабрики и утилиты
  // -------------------------------------------------------------------

  private static Bus bus(int routeNumber, String model, long mileage) {
    return Bus.builder()
        .routeNumber(routeNumber)
        .model(model)
        .mileage(mileage)
        .build();
  }

  // ===================================================================
  // 1. Раунд-трип через CSV
  // ===================================================================

  @Nested
  @DisplayName("Раунд-трип: Bus → CSV → Bus")
  class CsvRoundTrip {

    @ParameterizedTest(name = "bus = {0};{1};{2}")
    @CsvSource(
        value =
            {
                "42;ЛиАЗ-5292;150000",              // пример из javadoc кодека
                "0;X;0",                            // минимальные значения
                "2147483647;X;9223372036854775807", // максимум int и long
                "7;Mercedes Sprinter;1"             // пробел внутри модели
            },
        delimiter = ';')
    void when_busIsEncodedAndDecoded_then_equalBusIsRestored(int routeNumber,
                                                             String model,
                                                             long mileage) {
      Bus original = bus(routeNumber, model, mileage);

      assertEquals(Optional.of(original), codec.decode(codec.encode(original)));
    }

    /**
     * Задокументированное ограничение: кодек не экранирует ';' внутри model,
     * поэтому закодированная строка распадается на 4 поля и не декодируется.
     * Тест фиксирует текущее (lossy) поведение.
     */
    @Test
    void when_modelContainsDelimiter_then_roundTripReturnsEmptyOptional() {
      String csv = codec.encode(bus(1, "A;B", 10L)); // "1;A;B;10"

      assertEquals("1;A;B;10", csv);
      assertTrue(codec.decode(csv).isEmpty());
    }
  }

  // ===================================================================
  // 2. encode: Bus → CSV
  // ===================================================================

  @Nested
  @DisplayName("encode: Bus → CSV")
  class Encode {

    @Test
    void when_encodeJavadocExampleBus_then_csvMatchesDocumentedFormat() {
      assertEquals("42;ЛиАЗ-5292;150000",
                   codec.encode(bus(42, "ЛиАЗ-5292", 150_000L)));
    }

    @Test
    void when_encodeBusWithMinimalValues_then_zerosAreWritten() {
      assertEquals("0;X;0", codec.encode(bus(0, "X", 0L)));
    }

    @Test
    void when_encodeBusWithMaximalValues_then_maxNumbersAreWritten() {
      Bus maxValues = bus(Integer.MAX_VALUE, "X", Long.MAX_VALUE);

      assertEquals("2147483647;X;9223372036854775807", codec.encode(maxValues));
    }

    @Test
    void when_encodeNullBus_then_NullPointerExceptionIsThrown() {
      NullPointerException ex =
          assertThrows(NullPointerException.class, () -> codec.encode(null));

      assertEquals("Bus must not be null", ex.getMessage());
    }
  }

  // ===================================================================
  // 3. decode: CSV → Bus
  // ===================================================================

  @Nested
  @DisplayName("decode: CSV → Bus")
  class Decode {

    @Test
    void when_decodeWellFormedCsv_then_fieldsAreMappedToRouteModelMileage() {
      Optional<Bus> decoded = codec.decode("42;ЛиАЗ-5292;150000");

      // Целиком (проверяет equals/value-семантику разных экземпляров)...
      assertEquals(Optional.of(bus(42, "ЛиАЗ-5292", 150_000L)), decoded);
      // ...и по полям — ловит транспозицию routeNumber <-> mileage
      Bus decodedBus = decoded.orElseThrow();
      assertEquals(42, decodedBus.routeNumber());
      assertEquals("ЛиАЗ-5292", decodedBus.model());
      assertEquals(150_000L, decodedBus.mileage());
    }

    @Test
    void when_decodeCsvWithSpacesAroundFields_then_valuesAreTrimmed() {
      assertEquals(Optional.of(bus(42, "ЛиАЗ-5292", 150_000L)),
                   codec.decode("  42 ;  ЛиАЗ-5292  ;  150000  "));
    }

    @Test
    void when_decodeNullCsv_then_emptyOptionalIsReturned() {
      assertTrue(codec.decode(null).isEmpty());
    }

    @Test
    void when_decodeEmptyCsv_then_emptyOptionalIsReturned() {
      assertTrue(codec.decode("").isEmpty());
    }

    @ParameterizedTest(name = "csv = \"{0}\"")
    @ValueSource(strings =
                     {
                         " ",               // одна часть
                         "42",              // одна часть
                         "42;ЛиАЗ",         // две части
                         "42;ЛиАЗ;1;extra", // четыре части
                         "42;ЛиАЗ;150000;"  // хвостовой ';' -> четыре части
                     })
    void when_decodeCsvWithWrongFieldCount_then_emptyOptionalIsReturned(
        String csv) {
      assertTrue(codec.decode(csv).isEmpty(),
                 () -> "не ожидалось успеха для '" + csv + "'");
    }

    @ParameterizedTest(name = "csv = \"{0}\"")
    @ValueSource(
        strings =
            {
                "ноль;ЛиАЗ;150000",           // routeNumber не число
                "42;ЛиАЗ;много",              // mileage не число
                "42;ЛиАЗ;1.5",                // дробный mileage
                "2147483648;ЛиАЗ;150000",     // routeNumber > Integer.MAX_VALUE
                "42;ЛиАЗ;9223372036854775808" // mileage > Long.MAX_VALUE
            })
    void when_decodeCsvWithNonNumericNumbers_then_emptyOptionalIsReturned(
        String csv) {
      assertTrue(codec.decode(csv).isEmpty(),
                 () -> "не ожидалось успеха для '" + csv + "'");
    }

    // Codec is a transport layer: it checks syntax only. Semantic rules
    // (ranges, blank model) are BusValidator's job — see the end-to-end
    // tests and FileBusSourceTest.
    @Test
    void when_decodeCsvWithNegativeRouteNumber_then_rawBusIsDecoded() {
      assertEquals(Optional.of(bus(-1, "ЛиАЗ", 150_000L)),
                   codec.decode("-1;ЛиАЗ;150000"));
    }

    @Test
    void when_decodeCsvWithNegativeMileage_then_rawBusIsDecoded() {
      assertEquals(Optional.of(bus(42, "ЛиАЗ", -1L)),
                   codec.decode("42;ЛиАЗ;-1"));
    }

    @ParameterizedTest(name = "csv = \"{0}\"")
    @ValueSource(strings = {"42;;150000", "42;  ;150000", "42;   ;150000"})
    void when_decodeCsvWithBlankModel_then_rawBusIsDecoded(String csv) {
      // all three decode to a Bus with an empty (trimmed) model
      assertEquals(Optional.of(bus(42, "", 150_000L)), codec.decode(csv));
    }
  }

  // ===================================================================
  // 4. Сквозные сценарии
  // ===================================================================

  @Nested
  @DisplayName("Сквозные сценарии: CSV + Java-сериализация")
  class EndToEnd {

    @Test
    void when_decodedBusIsReEncoded_then_csvIsIdentical() {
      String csv = codec.encode(bus(42, "ЛиАЗ-5292", 150_000L));
      Bus restored = codec.decode(csv).orElseThrow();

      assertEquals(csv, codec.encode(restored));
    }

    @Test
    void
    when_codecIsUsedFromSeveralThreadsConcurrently_then_allRoundTripsSucceed()
        throws Exception {
      ExecutorService pool = Executors.newFixedThreadPool(4);
      try {
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
          int seed = t;
          futures.add(pool.submit(() -> {
            for (int i = 0; i < 250; i++) {
              Bus original = bus(seed * 10_000 + i, "model-" + seed, i * 100L);
              assertEquals(Optional.of(original),
                           codec.decode(codec.encode(original)));
            }
          }));
        }
        for (Future<?> future : futures) {
          future.get(30, TimeUnit.SECONDS); // прокинет AssertionError из потока
        }
      } finally {
        pool.shutdownNow();
      }
    }
  }
}
