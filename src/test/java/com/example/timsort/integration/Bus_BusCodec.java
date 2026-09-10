package com.example.timsort.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.timsort.codec.BusCodec;
import com.example.timsort.model.Bus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
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

  private static byte[] javaSerialize(Bus bus) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
      out.writeObject(bus);
    }
    return buffer.toByteArray();
  }

  private static Bus javaDeserialize(byte[] bytes)
      throws IOException, ClassNotFoundException {
    try (ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      return (Bus)in.readObject();
    }
  }

  /** Индекс первого вхождения {@code pattern} в {@code bytes} (или -1). */
  private static int indexOf(byte[] bytes, byte[] pattern) {
  search:
    for (int i = 0; i + pattern.length <= bytes.length; i++) {
      for (int j = 0; j < pattern.length; j++) {
        if (bytes[i + j] != pattern[j]) {
          continue search;
        }
      }
      return i;
    }
    return -1;
  }

  private static byte[] longBytes(long value) {
    return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
  }

  private static byte[] intBytes(int value) {
    return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
  }

  private static void patchLong(byte[] bytes, int offset, long value) {
    ByteBuffer.wrap(bytes, offset, Long.BYTES).putLong(value);
  }

  private static void patchInt(byte[] bytes, int offset, int value) {
    ByteBuffer.wrap(bytes, offset, Integer.BYTES).putInt(value);
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

    // Кодек сознательно не проверяет диапазоны: некорректные значения
    // отбрасываются инвариантами Bus на пути builder -> конструктор.

    @Test
    void when_decodeCsvWithNegativeRouteNumber_then_emptyOptionalIsReturned() {
      assertTrue(codec.decode("-1;ЛиАЗ;150000").isEmpty());
    }

    @Test
    void when_decodeCsvWithNegativeMileage_then_emptyOptionalIsReturned() {
      assertTrue(codec.decode("42;ЛиАЗ;-1").isEmpty());
    }

    @ParameterizedTest(name = "csv = \"{0}\"")
    @ValueSource(strings = {"42;;150000", "42;  ;150000", "42;   ;150000"})
    void when_decodeCsvWithBlankModel_then_emptyOptionalIsReturned(String csv) {
      assertTrue(codec.decode(csv).isEmpty(),
                 () -> "не ожидалось успеха для '" + csv + "'");
    }
  }

  // ===================================================================
  // 4. Java-сериализация
  // ===================================================================

  @Nested
  @DisplayName(
      "Java-сериализация: ObjectOutputStream → ObjectInputStream → Bus")
  class JavaSerialization {

    @Test
    void when_busIsSerializedAndDeserialized_then_equalBusIsRestored()
        throws Exception {
      Bus original = bus(42, "ЛиАЗ-5292", 150_000L);

      Bus restored = javaDeserialize(javaSerialize(original));

      assertEquals(original, restored);  // одинаковые значения
      assertNotSame(original, restored); // но другой экземпляр
      assertEquals(original.hashCode(), restored.hashCode());
    }

    @Test
    void when_busWithBoundaryValuesIsSerialized_then_roundTripPreservesValues()
        throws Exception {
      Bus max = bus(Integer.MAX_VALUE, "X", Long.MAX_VALUE);
      Bus zero = bus(0, "X", 0L);

      assertEquals(max, javaDeserialize(javaSerialize(max)));
      assertEquals(zero, javaDeserialize(javaSerialize(zero)));
    }

    @Test
    void
    when_multipleBusesAreWrittenToSingleStream_then_theyAreReadBackInWriteOrder()
        throws Exception {
      Bus first = bus(1, "ЛиАЗ-5292", 10L);
      Bus second = bus(2, "MAN", 20L);

      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
        out.writeObject(first);
        out.writeObject(second);
      }

      try (ObjectInputStream in = new ObjectInputStream(
               new ByteArrayInputStream(buffer.toByteArray()))) {
        assertEquals(first, in.readObject());
        assertEquals(second, in.readObject());
      }
    }

    // --- readObject() обязан отклонять повреждённые/подделанные потоки ---

    /**
     * Техника: сериализуем валидный Bus и точечно подменяем байты значения
     * поля. Маркерные значения подобраны так, чтобы их байтовое
     * представление встречалось в потоке ровно один раз, поэтому патч не
     * зависит от порядка, в котором Java-сериализация пишет поля (он
     * канонический и не совпадает с порядком объявления).
     */
    @Test
    void
    when_streamContainsBusWithNegativeMileage_then_InvalidObjectExceptionIsThrown()
        throws Exception {
      long markerMileage = 0x05060708090A0B0CL; // уникальный байтовый образец
      byte[] hostile = javaSerialize(bus(5, "Q", markerMileage));

      int offset = indexOf(hostile, longBytes(markerMileage));
      assertTrue(offset >= 0, "маркер mileage не найден в потоке");
      patchLong(hostile, offset, -1L);

      InvalidObjectException ex = assertThrows(InvalidObjectException.class,
                                               () -> javaDeserialize(hostile));

      assertEquals("mileage must be non-negative", ex.getMessage());
    }

    @Test
    void
    when_streamContainsBusWithNegativeRouteNumber_then_InvalidObjectExceptionIsThrown()
        throws Exception {
      int markerRoute = 0x11223344; // = 287454020; уникальный байтовый образец
      byte[] hostile = javaSerialize(bus(markerRoute, "Q", 7L));

      int offset = indexOf(hostile, intBytes(markerRoute));
      assertTrue(offset >= 0, "маркер routeNumber не найден в потоке");
      patchInt(hostile, offset, -3);

      InvalidObjectException ex = assertThrows(InvalidObjectException.class,
                                               () -> javaDeserialize(hostile));

      assertEquals("routeNumber must be non-negative", ex.getMessage());
    }

    @Test
    void
    when_streamContainsBusWithBlankModel_then_InvalidObjectExceptionIsThrown()
        throws Exception {
      // Модель из одного символа записывается в поток как
      // TC_STRING(0x74) + длина(0x0001) + один UTF-8 байт 'Q'.
      byte[] hostile = javaSerialize(bus(3, "Q", 9L));

      byte[] modelMarker = {(byte)0x74, 0x00, 0x01, 'Q'};
      int offset = indexOf(hostile, modelMarker);
      assertTrue(offset >= 0, "маркер model не найден в потоке");
      hostile[offset + 3] = ' '; // 'Q' -> ' ': модель становится blank

      InvalidObjectException ex = assertThrows(InvalidObjectException.class,
                                               () -> javaDeserialize(hostile));

      assertEquals("model must not be null or blank", ex.getMessage());
    }
  }

  // ===================================================================
  // 5. Сквозные сценарии
  // ===================================================================

  @Nested
  @DisplayName("Сквозные сценарии: CSV + Java-сериализация")
  class EndToEnd {

    @Test
    void when_busGoesThroughCsvAndJavaSerialization_then_originalBusIsRestored()
        throws Exception {
      Bus original = bus(42, "ЛиАЗ-5292", 150_000L);

      Bus fromCsv = codec.decode(codec.encode(original)).orElseThrow();
      Bus fromJava = javaDeserialize(javaSerialize(fromCsv));

      assertEquals(original, fromCsv);
      assertEquals(original, fromJava);
    }

    @Test
    void
    when_busIsRestoredFromCsvAndFromJavaStream_then_copiesAreEqualWithSameHashCode()
        throws Exception {
      Bus original = bus(7, "MAN", 123_456L);

      Bus fromCsv = codec.decode(codec.encode(original)).orElseThrow();
      Bus fromJava = javaDeserialize(javaSerialize(original));

      assertEquals(fromCsv, fromJava);
      assertEquals(fromCsv.hashCode(), fromJava.hashCode());
    }

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
