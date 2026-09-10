package com.example.timsort.collection;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.timsort.model.Bus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Unit tests for {@link CustomArrayList}.
 *
 * Coverage areas: constructors, element access, removal, search, bulk
 * operations, sorting, iterators and list iterators, subList views
 * (including nested views and cross-view modCount propagation), streams
 * and spliterators, toArray, equals/hashCode/toString, cloning,
 * immutableCopyOf, capacity management, serialization (including corrupt
 * streams), null-argument and null-element safety, and concurrency
 * (the regression test for the atomic SubList.add(E) fix).
 */
class CustomArrayListTest {

  // ---------- fixtures ----------

  private static Bus bus(int route) {
    return Bus.builder()
        .routeNumber(route)
        .model("Model-" + route)
        .mileage(route * 1_000L)
        .build();
  }

  private static CustomArrayList<Bus> listOfBuses(int... routes) {
    CustomArrayList<Bus> list = new CustomArrayList<>();
    for (int route : routes) {
      list.add(bus(route));
    }
    return list;
  }

  /** Compares by Bus equality, so distinct-but-equal instances match. */
  private static void assertRoutes(List<Bus> actual, int... expected) {
    assertEquals(expected.length, actual.size(), "size");
    for (int i = 0; i < expected.length; i++) {
      assertEquals(bus(expected[i]), actual.get(i), "element at index " + i);
    }
  }

  /**
   * WHY a record instead of Bus: Bus is not Serializable, so it cannot
   * cross an ObjectOutputStream. Records implement Serializable and are
   * the smallest possible payload for round-trip tests.
   */
  private record Route(int number) {
  }

  private static byte[] serialize(Object object) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
      out.writeObject(object);
    }
    return buffer.toByteArray();
  }

  @SuppressWarnings("unchecked")
  private static <T> T deserialize(byte[] bytes)
      throws IOException, ClassNotFoundException {
    try (ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      return (T)in.readObject();
    }
  }

  /**
   * WHY byte surgery: readObject validation can only be exercised by a
   * genuinely corrupt stream. The custom serialized form of an EMPTY list
   * contains exactly one TC_BLOCKDATA (0x77) block of length 4 holding the
   * size int (zero). We locate that header and rewrite the size field.
   */
  private static byte[] withSizePatchedTo(byte[] stream, int fakeSize) {
    for (int i = 0; i + 6 <= stream.length; i++) {
      boolean blockHeader = stream[i] == (byte)0x77 && stream[i + 1] == 0x04;
      boolean zeroSize = stream[i + 2] == 0 && stream[i + 3] == 0 &&
                         stream[i + 4] == 0 && stream[i + 5] == 0;
      if (blockHeader && zeroSize) {
        byte[] patched = stream.clone();
        patched[i + 2] = (byte)(fakeSize >>> 24);
        patched[i + 3] = (byte)(fakeSize >>> 16);
        patched[i + 4] = (byte)(fakeSize >>> 8);
        patched[i + 5] = (byte)fakeSize;
        return patched;
      }
    }
    throw new AssertionError("size block not found in serialized stream");
  }

  /**
   * WHY byte surgery: Bus.readObject revalidates the constructor's
   * invariants, but deserialization is the only path that bypasses the
   * builder, so that validation can only be exercised with a genuinely
   * corrupt stream.
   *
   * WHY this anchor (not a TC_BLOCKDATA search): a TC_BLOCKDATA (0x77)
   * block appears only when the class HAS a custom writeObject — that is
   * why withSizePatchedTo works for CustomArrayList, whose writeObject
   * emits writeInt(size). Bus has no writeObject, so its primitive fields
   * are written RAW, with no marker bytes. The reliable anchor instead is
   * the end of the class descriptor chain: TC_ENDBLOCKDATA (0x78)
   * immediately followed by TC_NULL (0x70) — Bus's superclass is
   * java.lang.Object, which has no descriptor of its own. After that pair
   * the object's values follow: primitive fields first, in field-name
   * order — mileage (long, 8 bytes), then routeNumber (int, 4 bytes) —
   * and then the model field as a fresh TC_STRING (0x74). The search runs
   * from the END of the stream so a coincidental 0x78 0x70 inside the
   * serialVersionUID can never win, and every candidate is verified
   * structurally before patching.
   */
  private static byte[] withBusRouteNumberPatchedTo(byte[] stream,
                                                    int fakeRoute) {
    for (int i = stream.length - 2; i >= 0; i--) {
      if (stream[i] != (byte)0x78 || stream[i + 1] != (byte)0x70) {
        continue; // not the descriptor-chain end — keep searching
      }
      // Structural sanity check: 'model' must follow the 12 bytes of
      // primitive data as a TC_STRING. If Bus ever gains a writeObject or
      // a new primitive field, the layout after the anchor changes and
      // this guard fails loudly instead of the test silently patching
      // the wrong bytes.
      if (i + 14 >= stream.length || stream[i + 14] != (byte)0x74) {
        continue; // spurious match inside earlier stream data
      }
      byte[] patched = stream.clone();
      // i+2..i+9 hold mileage (long); i+10..i+13 hold routeNumber (int)
      patched[i + 10] = (byte)(fakeRoute >>> 24);
      patched[i + 11] = (byte)(fakeRoute >>> 16);
      patched[i + 12] = (byte)(fakeRoute >>> 8);
      patched[i + 13] = (byte)fakeRoute;
      return patched;
    }
    throw new AssertionError(
        "Bus primitive fields not found after TC_ENDBLOCKDATA/TC_NULL "
        + "anchor — did Bus gain a writeObject or change its fields?");
  }

  // ---------- tests ----------

  @Nested
  class Constructors {

    @Test
    void when_defaultConstructorUsed_then_listIsEmpty() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      assertTrue(list.isEmpty());
      assertEquals(0, list.size());
    }

    @Test
    void when_initialCapacityNegative_then_illegalArgumentException() {
      assertThrows(IllegalArgumentException.class,
                   () -> new CustomArrayList<Bus>(-1));
    }

    @Test
    void when_initialCapacityZero_then_listStillUsable() {
      CustomArrayList<Bus> list = new CustomArrayList<>(0);
      list.add(bus(1));
      list.add(bus(2));
      assertRoutes(list, 1, 2);
    }

    @Test
    void when_collectionIsNull_then_nullPointerException() {
      assertThrows(NullPointerException.class,
                   () -> new CustomArrayList<Bus>(null));
    }

    @Test
    void when_collectionIsEmpty_then_listStartsEmptyAndUsable() {
      CustomArrayList<Bus> list =
          new CustomArrayList<>(Collections.emptyList());
      assertTrue(list.isEmpty());
      list.add(bus(1));
      assertRoutes(list, 1);
    }

    @Test
    void when_collectionCopied_then_laterSourceChangesDoNotAffectList() {
      List<Bus> source = new ArrayList<>(List.of(bus(1), bus(2)));
      CustomArrayList<Bus> list = new CustomArrayList<>(source);
      source.clear();
      assertRoutes(list, 1, 2);
    }

    @Test
    void when_collectionContainsNulls_then_nullsRetained() {
      List<Bus> source = new ArrayList<>();
      source.add(bus(1));
      source.add(null);
      CustomArrayList<Bus> list = new CustomArrayList<>(source);
      assertEquals(2, list.size());
      assertNull(list.get(1));
    }

    @Test
    void
    when_sourceToArrayReturnsSpecificArrayType_then_backingArrayNormalized() {
      // WHY: collection.toArray() may legally return a more specific array
      // type than Object[] (e.g. String[]); without normalization set()
      // with an unrelated element would throw ArrayStoreException.
      Collection<Object> sneaky = new AbstractList<Object>() {
        @Override
        public Object get(int index) {
          return "not a bus";
        }
        @Override
        public int size() {
          return 1;
        }
        @Override
        public Object[] toArray() {
          return new String[] {"not a bus"};
        }
      };
      CustomArrayList<Object> list = new CustomArrayList<>(sneaky);
      assertEquals("not a bus", list.get(0));
      // WHY set() with an unrelated runtime type: a String[] backing array
      // would reject it with ArrayStoreException; the normalized Object[]
      // must accept the value and store it.
      Object replacement = new Object();
      assertDoesNotThrow(() -> list.set(0, replacement));
      assertSame(replacement, list.get(0));
    }
  }

  @Nested
  class ElementAccess {

    @Test
    void when_elementAdded_then_sizeGrowsAndElementReadable() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      assertTrue(list.add(bus(1)));
      assertEquals(1, list.size());
      assertEquals(bus(1), list.get(0));
    }

    @Test
    void when_elementAddedAtMiddleIndex_then_existingElementsShiftRight() {
      CustomArrayList<Bus> list = listOfBuses(1, 3);
      list.add(1, bus(2));
      assertRoutes(list, 1, 2, 3);
    }

    @Test
    void when_elementAddedAtEndIndex_then_appended() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      list.add(2, bus(3)); // index == size is a legal insertion point
      assertRoutes(list, 1, 2, 3);
    }

    @Test
    void when_elementAddedAtInvalidIndex_then_indexOutOfBoundsException() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertThrows(IndexOutOfBoundsException.class, () -> list.add(-1, bus(9)));
      assertThrows(IndexOutOfBoundsException.class, () -> list.add(3, bus(9)));
    }

    @Test
    void when_getCalledWithInvalidIndex_then_indexOutOfBoundsException() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
      assertThrows(IndexOutOfBoundsException.class, () -> list.get(2));
    }

    @Test
    void when_setCalled_then_oldValueReturnedAndElementReplaced() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      Bus replaced = list.set(1, bus(20));
      assertEquals(bus(2), replaced);
      assertRoutes(list, 1, 20);
    }

    @Test
    void when_setCalledWithInvalidIndex_then_indexOutOfBoundsException() {
      assertThrows(IndexOutOfBoundsException.class,
                   () -> listOfBuses(1, 2).set(2, bus(9)));
    }
  }

  @Nested
  class Removal {

    @Test
    void
    when_elementRemovedByIndex_then_removedValueReturnedAndTailShiftsLeft() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3);
      Bus removed = list.remove(0);
      assertEquals(bus(1), removed);
      assertRoutes(list, 2, 3);
    }

    @Test
    void when_lastElementRemovedByIndex_then_sizeDecreases() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertEquals(bus(2), list.remove(1));
      assertRoutes(list, 1);
    }

    @Test
    void when_elementRemovedByInvalidIndex_then_indexOutOfBoundsException() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertThrows(IndexOutOfBoundsException.class, () -> list.remove(-1));
      assertThrows(IndexOutOfBoundsException.class, () -> list.remove(2));
    }

    @Test
    void
    when_elementRemovedByObject_then_firstOccurrenceRemovedAndTrueReturned() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 1);
      assertTrue(list.remove(bus(1)));
      assertRoutes(list, 2, 1);
    }

    @Test
    void when_absentElementRemovedByObject_then_returnsFalse() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertFalse(list.remove(bus(99)));
      assertRoutes(list, 1, 2);
    }

    @Test
    void when_removeFromEmptyListByObject_then_returnsFalse() {
      assertFalse(new CustomArrayList<Bus>().remove(bus(1)));
    }

    @Test
    void when_listCleared_then_emptyAndReusable() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3);
      list.clear();
      assertEquals(0, list.size());
      assertTrue(list.isEmpty());
      list.add(bus(7));
      assertRoutes(list, 7);
    }

    @Test
    void when_iteratorUsedAfterClear_then_nextThrowsConcurrentModification() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      Iterator<Bus> it = list.iterator();
      it.next();
      list.clear();
      assertThrows(ConcurrentModificationException.class, it::next);
    }
  }

  @Nested
  class Search {

    @Test
    void when_elementContained_then_containsReflectsPresence() {
      assertTrue(listOfBuses(1, 2).contains(bus(2)));
      assertFalse(listOfBuses(1, 2).contains(bus(99)));
    }

    @Test
    void when_indexOfCalled_then_returnsFirstMatchingPosition() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 1);
      assertEquals(0, list.indexOf(bus(1)));
      assertEquals(1, list.indexOf(bus(2)));
    }

    @Test
    void when_indexOfAbsentElement_then_returnsMinusOne() {
      assertEquals(-1, listOfBuses(1, 2).indexOf(bus(99)));
    }

    @Test
    void when_lastIndexOfCalled_then_returnsLastMatchingPosition() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 1);
      assertEquals(2, list.lastIndexOf(bus(1)));
      assertEquals(1, list.lastIndexOf(bus(2)));
      assertEquals(-1, list.lastIndexOf(bus(99)));
    }

    @Test
    void when_containsAllSubset_then_returnsTrue() {
      assertTrue(listOfBuses(1, 2, 3).containsAll(List.of(bus(1), bus(3))));
    }

    @Test
    void when_containsAllWithMissingElement_then_returnsFalse() {
      assertFalse(listOfBuses(1, 2, 3).containsAll(List.of(bus(1), bus(99))));
    }

    @Test
    void when_containsAllEmptyCollection_then_returnsTrue() {
      assertTrue(listOfBuses(1, 2).containsAll(Collections.emptyList()));
    }

    @Test
    void when_containsAllNullArgument_then_nullPointerException() {
      assertThrows(NullPointerException.class,
                   () -> listOfBuses(1).containsAll(null));
    }
  }

  @Nested
  class BulkOperations {

    @Test
    void when_addAllNonEmptyCalled_then_appendedInOrderAndTrueReturned() {
      CustomArrayList<Bus> list = listOfBuses(1);
      assertTrue(list.addAll(List.of(bus(2), bus(3))));
      assertRoutes(list, 1, 2, 3);
    }

    @Test
    void when_addAllEmptyCalled_then_returnsFalseAndListUnchanged() {
      CustomArrayList<Bus> list = listOfBuses(1);
      assertFalse(list.addAll(Collections.emptyList()));
      assertRoutes(list, 1);
    }

    @Test
    void when_addAllNullCalled_then_nullPointerException() {
      CustomArrayList<Bus> list = listOfBuses(1);
      assertThrows(NullPointerException.class, () -> list.addAll(null));
      assertThrows(NullPointerException.class, () -> list.addAll(0, null));
    }

    @Test
    void when_addAllAtIndexCalled_then_splicedAtPosition() {
      CustomArrayList<Bus> list = listOfBuses(1, 4);
      assertTrue(list.addAll(1, List.of(bus(2), bus(3))));
      assertRoutes(list, 1, 2, 3, 4);
    }

    @Test
    void when_addAllAtInvalidIndex_then_indexOutOfBoundsException() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertThrows(IndexOutOfBoundsException.class,
                   () -> list.addAll(3, List.of(bus(9))));
      assertThrows(IndexOutOfBoundsException.class,
                   () -> list.addAll(-1, List.of(bus(9))));
    }

    @Test
    void when_addAllCalledWithItself_then_contentsDoubled() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertTrue(list.addAll(list)); // toArray() snapshots before the append
      assertRoutes(list, 1, 2, 1, 2);
    }

    @Test
    void when_removeAllOverlappingCalled_then_onlyOverlappingRemoved() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3, 4, 5);
      assertTrue(list.removeAll(List.of(bus(2), bus(4))));
      assertRoutes(list, 1, 3, 5);
    }

    @Test
    void when_removeAllEmptyCalled_then_returnsFalseAndListUnchanged() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertFalse(list.removeAll(Collections.emptyList()));
      assertRoutes(list, 1, 2);
    }

    @Test
    void
    when_removeAllWithHostileCollection_then_exceptionPropagatesAndPartialCommitKept() {
      // WHY a hostile collection: its contains() throws mid-scan, exercising
      // the documented "commit the kept prefix plus the unprocessed tail"
      // repair path of the shared compaction core.
      RuntimeException boom = new RuntimeException("boom");
      Collection<Object> hostile = new AbstractList<Object>() {
        @Override
        public Object get(int index) {
          return bus(index + 1);
        }
        @Override
        public int size() {
          return 3;
        }
        @Override
        public boolean contains(Object o) {
          if (o instanceof Bus element && element.routeNumber() == 2) {
            throw boom;
          }
          return o instanceof Bus element && element.routeNumber() == 1;
        }
      };
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3);
      RuntimeException thrown =
          assertThrows(RuntimeException.class, () -> list.removeAll(hostile));
      assertSame(boom, thrown);
      assertRoutes(list, 2,
                   3); // bus(1) was already removed when the throw happened
    }

    @Test
    void when_retainAllCalled_then_keepsOnlyRetained() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3, 4, 5);
      assertTrue(list.retainAll(List.of(bus(2), bus(4), bus(99))));
      assertRoutes(list, 2, 4);
    }

    @Test
    void when_retainAllEmptyCalled_then_clearsList() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertTrue(list.retainAll(Collections.emptyList()));
      assertTrue(list.isEmpty());
    }

    @Test
    void when_removeIfSomeMatch_then_removedAndTrueReturned() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3, 4, 5);
      assertTrue(list.removeIf(b -> b.routeNumber() % 2 == 0));
      assertRoutes(list, 1, 3, 5);
    }

    @Test
    void when_removeIfNothingMatches_then_returnsFalseAndIteratorsStayValid() {
      // WHY iterate afterwards: a no-op removeIf must not bump modCount,
      // otherwise "read-only" filters would kill live iterators.
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      Iterator<Bus> it = list.iterator();
      assertFalse(list.removeIf(b -> false));
      it.next();
      assertRoutes(list, 1, 2);
    }

    @Test
    void
    when_removeIfPredicateThrows_then_partialCommitAndExceptionPropagate() {
      // WHY assert the list shape: the finally-block repair commits the kept
      // prefix plus the unprocessed tail, so the list stays consistent
      // (though partially filtered) while the original exception propagates.
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3, 4, 5);
      RuntimeException boom = new RuntimeException("boom");
      RuntimeException thrown =
          assertThrows(RuntimeException.class, () -> list.removeIf(b -> {
            if (b.routeNumber() == 2)
              throw boom;
            return b.routeNumber() == 1;
          }));
      assertSame(boom, thrown);
      assertRoutes(list, 2, 3, 4, 5);
    }

    @Test
    void when_removeIfPredicateReentersList_then_concurrentModification() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      AtomicBoolean reentered = new AtomicBoolean();
      assertThrows(ConcurrentModificationException.class,
                   () -> list.removeIf(b -> {
                     if (reentered.compareAndSet(false, true))
                       list.add(bus(99));
                     return false;
                   }));
    }

    @Test
    void when_forEachCalled_then_visitsAllElementsInOrder() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3);
      List<Bus> seen = new ArrayList<>();
      list.forEach(seen::add);
      assertRoutes(seen, 1, 2, 3);
    }

    @Test
    void when_forEachConsumerThrows_then_exceptionPropagatesAndListUnchanged() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3);
      RuntimeException boom = new RuntimeException("boom");
      RuntimeException thrown =
          assertThrows(RuntimeException.class, () -> list.forEach(b -> {
            if (b.routeNumber() == 2)
              throw boom;
          }));
      assertSame(boom, thrown);
      assertRoutes(list, 1, 2, 3);
    }

    @Test
    void when_forEachConsumerReentersList_then_concurrentModification() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertThrows(ConcurrentModificationException.class,
                   () -> list.forEach(b -> list.add(bus(99))));
    }

    @Test
    void when_replaceAllCalled_then_allElementsTransformed() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      list.replaceAll(b -> bus(b.routeNumber() + 10));
      assertRoutes(list, 11, 12);
    }

    @Test
    void when_replaceAllOperatorThrows_then_elementsBeforeFailureReplaced() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3);
      RuntimeException boom = new RuntimeException("boom");
      RuntimeException thrown =
          assertThrows(RuntimeException.class, () -> list.replaceAll(b -> {
            if (b.routeNumber() == 3)
              throw boom;
            return bus(b.routeNumber() + 10);
          }));
      assertSame(boom, thrown);
      assertRoutes(list, 11, 12, 3);
    }

    @Test
    void when_replaceAllCalled_then_existingIteratorsStayValid() {
      // WHY: replacement is not a structural change (like set()), so
      // modCount must stay untouched and live iterators keep working.
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      Iterator<Bus> it = list.iterator();
      list.replaceAll(b -> bus(b.routeNumber() + 10));
      it.next();
      assertRoutes(list, 11, 12);
    }
  }

  @Nested
  class Sorting {

    @Test
    void when_sortedByRouteNumber_then_ascendingOrder() {
      CustomArrayList<Bus> list = listOfBuses(3, 1, 2);
      list.sort(Comparator.comparing(Bus::routeNumber));
      assertRoutes(list, 1, 2, 3);
    }

    @Test
    void
    when_sortedWithNullComparatorOnNonComparableElements_then_classCastException() {
      // WHY not NPE: a null comparator means "natural ordering", and Bus
      // does not implement Comparable — the cast inside Arrays.sort fails.
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertThrows(ClassCastException.class, () -> list.sort(null));
    }

    @Test
    void when_sortedWithNullComparatorOnEmptyList_then_completesQuietly() {
      assertDoesNotThrow(() -> new CustomArrayList<Bus>().sort(null));
    }

    @Test
    void when_sortedWithNullsAndNullsFirstComparator_then_nullsPrecedeValues() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(bus(2));
      list.add(null);
      list.add(bus(1));
      list.sort(Comparator.nullsFirst(Comparator.comparing(Bus::routeNumber)));
      assertEquals(3, list.size());
      assertNull(list.get(0));
      assertEquals(bus(1), list.get(1));
      assertEquals(bus(2), list.get(2));
    }

    @Test
    void when_sortedWithReentrantComparator_then_concurrentModification() {
      CustomArrayList<Bus> list = listOfBuses(2, 1);
      AtomicBoolean reentered = new AtomicBoolean();
      assertThrows(ConcurrentModificationException.class,
                   () -> list.sort((left, right) -> {
                     if (reentered.compareAndSet(false, true))
                       list.add(bus(9));
                     return Integer.compare(left.routeNumber(),
                                            right.routeNumber());
                   }));
    }

    @Test
    void when_sortedWhileIteratorActive_then_iteratorFailsFast() {
      CustomArrayList<Bus> list = listOfBuses(2, 1);
      Iterator<Bus> it = list.iterator();
      it.next();
      list.sort(Comparator.comparing(Bus::routeNumber));
      assertThrows(ConcurrentModificationException.class, it::next);
    }
  }

  @Nested
  class Iteration {

    @Test
    void when_listIsEmpty_then_iteratorHasNoNextAndNextThrows() {
      Iterator<Bus> it = new CustomArrayList<Bus>().iterator();
      assertFalse(it.hasNext());
      assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void when_iteratorConsumedFully_then_nextThrowsNoSuchElement() {
      CustomArrayList<Bus> list = listOfBuses(1);
      Iterator<Bus> it = list.iterator();
      assertEquals(bus(1), it.next());
      assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void when_removeCalledBeforeNext_then_illegalStateException() {
      Iterator<Bus> it = listOfBuses(1).iterator();
      assertThrows(IllegalStateException.class, it::remove);
    }

    @Test
    void when_removeCalledAfterNext_then_elementRemovedAndIterationContinues() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3);
      Iterator<Bus> it = list.iterator();
      assertEquals(bus(1), it.next());
      it.remove();
      assertEquals(bus(2), it.next());
      assertEquals(bus(3), it.next());
      assertFalse(it.hasNext());
      assertRoutes(list, 2, 3);
    }

    @Test
    void when_removeCalledTwiceWithoutNext_then_illegalStateException() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      Iterator<Bus> it = list.iterator();
      it.next();
      it.remove();
      assertThrows(IllegalStateException.class, it::remove);
    }

    @Test
    void when_listModifiedAfterIteratorCreated_then_nextThrowsCme() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3);
      Iterator<Bus> it = list.iterator();
      assertEquals(bus(1), it.next());
      list.add(bus(4));
      assertThrows(ConcurrentModificationException.class, it::next);
    }

    @Test
    void when_iteratorOverListWithNulls_then_nullElementsReturned() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(bus(1));
      list.add(null);
      Iterator<Bus> it = list.iterator();
      assertEquals(bus(1), it.next());
      assertNull(it.next());
      assertFalse(it.hasNext());
    }
  }

  @Nested
  class ListIteration {

    @Test
    void when_listIteratorCreatedAtIndex_then_firstNextReturnsElementAtIndex() {
      assertEquals(bus(3), listOfBuses(1, 2, 3).listIterator(2).next());
    }

    @Test
    void
    when_listIteratorCreatedAtInvalidIndex_then_indexOutOfBoundsException() {
      assertThrows(IndexOutOfBoundsException.class,
                   () -> listOfBuses(1, 2).listIterator(3));
      assertThrows(IndexOutOfBoundsException.class,
                   () -> listOfBuses(1, 2).listIterator(-1));
    }

    @Test
    void when_listIteratorCreatedAtEnd_then_nextThrowsNoSuchElement() {
      ListIterator<Bus> it = listOfBuses(1).listIterator(1);
      assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void when_previousCalled_then_traversalRunsBackward() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3);
      ListIterator<Bus> it = list.listIterator(3);
      assertEquals(bus(3), it.previous());
      assertEquals(bus(2), it.previous());
      assertEquals(bus(1), it.previous());
      assertThrows(NoSuchElementException.class, it::previous);
    }

    @Test
    void when_previousCalledAtStart_then_noSuchElementException() {
      ListIterator<Bus> it = listOfBuses(1).listIterator();
      assertThrows(NoSuchElementException.class, it::previous);
    }

    @Test
    void when_nextAndPreviousIndexCalled_then_trackCursorPosition() {
      ListIterator<Bus> it = listOfBuses(1, 2, 3).listIterator();
      assertEquals(0, it.nextIndex());
      assertEquals(-1, it.previousIndex());
      it.next();
      assertEquals(1, it.nextIndex());
      assertEquals(0, it.previousIndex());
    }

    @Test
    void when_setCalledAfterNext_then_replacesReturnedElement() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      ListIterator<Bus> it = list.listIterator();
      it.next();
      it.set(bus(10));
      assertRoutes(list, 10, 2);
    }

    @Test
    void when_setCalledAfterPrevious_then_replacesElementBeforeCursor() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3);
      ListIterator<Bus> it = list.listIterator();
      it.next();
      it.next();                           // cursor = 2, last returned bus(2)
      assertEquals(bus(2), it.previous()); // cursor = 1
      it.set(bus(20));
      assertRoutes(list, 1, 20, 3);
    }

    @Test
    void when_setCalledBeforeTraversal_then_illegalStateException() {
      ListIterator<Bus> it = listOfBuses(1).listIterator();
      assertThrows(IllegalStateException.class, () -> it.set(bus(9)));
      assertThrows(IllegalStateException.class, it::remove);
    }

    @Test
    void when_addCalledThenNext_then_insertedElementIsSkipped() {
      CustomArrayList<Bus> list = listOfBuses(1, 3);
      ListIterator<Bus> it = list.listIterator();
      assertEquals(bus(1), it.next());
      it.add(bus(2));
      assertEquals(bus(3), it.next()); // cursor moved past the inserted element
      assertRoutes(list, 1, 2, 3);
    }

    @Test
    void
    when_addCalled_then_lastReturnedIndexResetAndRemoveSetThrowIllegalState() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      ListIterator<Bus> it = list.listIterator();
      it.next();
      it.add(bus(9));
      assertThrows(IllegalStateException.class, it::remove);
      assertThrows(IllegalStateException.class, () -> it.set(bus(9)));
      assertRoutes(list, 1, 9, 2);
    }

    @Test
    void when_removeCalledAfterPrevious_then_removesElementBeforeCursor() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3);
      ListIterator<Bus> it = list.listIterator();
      it.next();
      it.next();                           // cursor = 2, last returned bus(2)
      assertEquals(bus(2), it.previous()); // cursor = 1
      it.remove();
      assertRoutes(list, 1, 3);
      assertEquals(bus(3), it.next());
    }

    @Test
    void when_forEachRemainingCalled_then_consumesElementsFromCursor() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3, 4);
      ListIterator<Bus> it = list.listIterator(1);
      List<Bus> seen = new ArrayList<>();
      it.forEachRemaining(seen::add);
      assertRoutes(seen, 2, 3, 4);
      assertFalse(it.hasNext());
    }

    @Test
    void when_forEachRemainingCalledWhenExhausted_then_actionNotInvoked() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      ListIterator<Bus> it = list.listIterator();
      while (it.hasNext())
        it.next();
      List<Bus> seen = new ArrayList<>();
      it.forEachRemaining(seen::add);
      assertTrue(seen.isEmpty());
    }

    @Test
    void when_forEachRemainingNullAction_then_nullPointerException() {
      ListIterator<Bus> it = listOfBuses(1, 2).listIterator();
      assertThrows(NullPointerException.class, () -> it.forEachRemaining(null));
    }

    @Test
    void when_forEachRemainingActionReentersList_then_concurrentModification() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3);
      ListIterator<Bus> it = list.listIterator();
      it.next();
      assertThrows(ConcurrentModificationException.class,
                   () -> it.forEachRemaining(b -> list.add(bus(9))));
    }
  }

  @Nested
  class SubListView {

    @Test
    void when_subListCreated_then_reflectsRootRange() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      assertEquals(3, sub.size());
      assertEquals(bus(2), sub.get(0));
      assertEquals(bus(3), sub.get(1));
      assertEquals(bus(4), sub.get(2));
    }

    @Test
    void when_subListCoversWholeList_then_mirrorsRootAndMutationsPropagate() {
      CustomArrayList<Bus> root = listOfBuses(1, 2);
      List<Bus> mirror = root.subList(0, 2);
      assertRoutes(mirror, 1, 2);
      mirror.add(bus(3));
      assertRoutes(root, 1, 2, 3);
    }

    @Test
    void when_subListRangeInvalid_then_indexOutOfBoundsException() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3);
      assertThrows(IndexOutOfBoundsException.class, () -> root.subList(-1, 2));
      assertThrows(IndexOutOfBoundsException.class, () -> root.subList(0, 4));
      assertThrows(IndexOutOfBoundsException.class, () -> root.subList(2, 1));
    }

    @Test
    void when_subListRangeEmptyAtTail_then_addAppendsToRootEnd() {
      CustomArrayList<Bus> root = listOfBuses(1, 2);
      List<Bus> tail = root.subList(2, 2);
      assertTrue(tail.isEmpty());
      tail.add(bus(3));
      assertRoutes(root, 1, 2, 3);
      assertRoutes(tail, 3);
    }

    @Test
    void when_subListGetWithInvalidIndex_then_indexOutOfBoundsException() {
      List<Bus> sub = listOfBuses(1, 2, 3).subList(1, 3);
      assertThrows(IndexOutOfBoundsException.class, () -> sub.get(-1));
      assertThrows(IndexOutOfBoundsException.class, () -> sub.get(2));
    }

    @Test
    void when_subListSetCalled_then_rootUpdatedAndOldValueReturned() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3);
      List<Bus> sub = root.subList(1, 3);
      Bus replaced = sub.set(0, bus(20));
      assertEquals(bus(2), replaced);
      assertRoutes(root, 1, 20, 3);
    }

    @Test
    void when_rootStructurallyModified_then_subListAccessThrowsCme() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3);
      List<Bus> sub = root.subList(0, 2);
      root.add(bus(4));
      assertThrows(ConcurrentModificationException.class, sub::size);
      assertThrows(ConcurrentModificationException.class, () -> sub.get(0));
    }

    @Test
    void when_rootReplacedElementBySet_then_subListSeesNewValueAndStaysValid() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3);
      List<Bus> sub = root.subList(1, 3);
      root.set(1, bus(20)); // set() is not structural: the view must survive
      assertRoutes(sub, 20, 3);
    }

    @Test
    void when_subListSizeCalled_then_viewRelative() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3);
      assertEquals(2, root.subList(1, 3).size());
      assertTrue(root.subList(1, 1).isEmpty());
    }
  }

  @Nested
  class SubListMutation {

    @Test
    void when_subListAddCalled_then_appendedAtViewEndInRoot() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4); // [2,3,4]
      sub.add(bus(99));
      assertRoutes(sub, 2, 3, 4, 99);
      assertRoutes(root, 1, 2, 3, 4, 99, 5, 6);
    }

    @Test
    void when_subListAddAtIndexCalled_then_splicedWithinRootRange() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      sub.add(1, bus(25));
      assertRoutes(root, 1, 2, 25, 3, 4, 5, 6);
      assertRoutes(sub, 2, 25, 3, 4);
    }

    @Test
    void when_subListAddNullCalled_then_nullStoredInView() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3);
      List<Bus> sub = root.subList(1, 3);
      sub.add(null);
      assertNull(sub.get(2));
      assertEquals(4, root.size());
    }

    @Test
    void when_subListAddAllCalled_then_splicedAndRootGrown() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      assertTrue(sub.addAll(List.of(bus(21), bus(22))));
      assertRoutes(root, 1, 2, 3, 4, 21, 22, 5, 6);
      assertRoutes(sub, 2, 3, 4, 21, 22);
    }

    @Test
    void when_subListAddAllAtIndexCalled_then_splicedAtViewPosition() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      assertTrue(sub.addAll(1, List.of(bus(21))));
      assertRoutes(root, 1, 2, 21, 3, 4, 5, 6);
    }

    @Test
    void when_subListAddAllItselfCalled_then_viewDoubled() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4); // [2,3,4]
      assertTrue(sub.addAll(sub));
      assertRoutes(root, 1, 2, 3, 4, 2, 3, 4, 5, 6);
      assertRoutes(sub, 2, 3, 4, 2, 3, 4);
    }

    @Test
    void when_subListAddAllEmptyCalled_then_returnsFalse() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3);
      List<Bus> sub = root.subList(0, 2);
      assertFalse(sub.addAll(Collections.emptyList()));
      assertRoutes(root, 1, 2, 3);
    }

    @Test
    void when_subListRemoveByIndexCalled_then_removedFromRootAndView() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      Bus removed = sub.remove(1);
      assertEquals(bus(3), removed);
      assertRoutes(sub, 2, 4);
      assertRoutes(root, 1, 2, 4, 5, 6);
    }

    @Test
    void when_subListRemoveByObjectCalled_then_removedFromRootAndView() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      assertTrue(sub.remove(bus(3)));
      assertFalse(sub.remove(bus(3)));
      assertRoutes(root, 1, 2, 4, 5, 6);
    }

    @Test
    void
    when_subListRemoveAbsentObjectCalled_then_returnsFalseAndViewStaysValid() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3);
      List<Bus> sub = root.subList(0, 2);
      assertFalse(sub.remove(bus(99)));
      assertEquals(bus(1),
                   sub.get(0)); // a no-op removal must not invalidate the view
    }

    @Test
    void when_subListClearCalled_then_rangeRemovedAndRootTailPreserved() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      sub.clear();
      assertTrue(sub.isEmpty());
      assertRoutes(root, 1, 5, 6);
    }

    @Test
    void when_subListRemoveIfCalled_then_rootTailShiftedLeft() {
      // WHY the root assertion matters: the view sits in the middle of the
      // shared array, so the compaction core must also slide the elements
      // that follow the view's range.
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4); // [2,3,4]
      assertTrue(sub.removeIf(b -> b.routeNumber() % 2 == 0));
      assertRoutes(root, 1, 3, 5, 6);
      assertRoutes(sub, 3);
    }

    @Test
    void when_subListRemoveAllCalled_then_rootTailShiftedLeft() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      assertTrue(sub.removeAll(List.of(bus(2), bus(4))));
      assertRoutes(root, 1, 3, 5, 6);
      assertRoutes(sub, 3);
    }

    @Test
    void when_subListRetainAllCalled_then_rootTailShiftedLeft() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      assertTrue(sub.retainAll(List.of(bus(3))));
      assertRoutes(root, 1, 3, 5, 6);
      assertRoutes(sub, 3);
    }

    @Test
    void when_subListRetainAllEmptyCalled_then_rangeCleared() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      assertTrue(sub.retainAll(Collections.emptyList()));
      assertTrue(sub.isEmpty());
      assertRoutes(root, 1, 5, 6);
    }

    @Test
    void
    when_subListRemoveIfNothingMatches_then_returnsFalseAndViewStaysValid() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      assertFalse(sub.removeIf(b -> b.routeNumber() == 99));
      assertRoutes(root, 1, 2, 3, 4, 5, 6);
      assertEquals(bus(2), sub.get(0));
    }

    @Test
    void
    when_subListRemoveIfPredicateThrows_then_partialCommitAndRootTailShift() {
      // WHY this shape: the sublist compaction commits the kept prefix plus
      // the unprocessed tail (documented trade-off), shifts the root's tail
      // left and keeps the whole view chain consistent — the exception
      // still propagates.
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5);
      List<Bus> sub = root.subList(1, 4); // [2,3,4]
      RuntimeException boom = new RuntimeException("boom");
      RuntimeException thrown =
          assertThrows(RuntimeException.class, () -> sub.removeIf(b -> {
            if (b.routeNumber() == 3)
              throw boom;
            return b.routeNumber() == 2; // bus(2) is removed before the throw
          }));
      assertSame(boom, thrown);
      assertRoutes(root, 1, 3, 4, 5);
      assertRoutes(sub, 3, 4);
    }

    @Test
    void when_subListNullBulkArguments_then_nullPointerException() {
      List<Bus> sub = listOfBuses(1, 2, 3).subList(0, 2);
      assertThrows(NullPointerException.class, () -> sub.removeAll(null));
      assertThrows(NullPointerException.class, () -> sub.retainAll(null));
      assertThrows(NullPointerException.class, () -> sub.removeIf(null));
      assertThrows(NullPointerException.class, () -> sub.forEach(null));
      assertThrows(NullPointerException.class, () -> sub.replaceAll(null));
      assertThrows(NullPointerException.class, () -> sub.containsAll(null));
      assertThrows(NullPointerException.class, () -> sub.toArray((Bus[])null));
    }

    @Test
    void when_subListSorted_then_onlyRangeReorderedInRoot() {
      CustomArrayList<Bus> root = listOfBuses(3, 2, 1, 4);
      List<Bus> sub = root.subList(0, 3); // [3,2,1]
      sub.sort(Comparator.comparing(Bus::routeNumber));
      assertRoutes(sub, 1, 2, 3);
      assertRoutes(root, 1, 2, 3, 4);
    }

    @Test
    void when_rootSorted_then_subListAccessThrowsCme() {
      CustomArrayList<Bus> root = listOfBuses(2, 1, 3);
      List<Bus> sub = root.subList(0, 2);
      root.sort(Comparator.comparing(Bus::routeNumber));
      assertThrows(ConcurrentModificationException.class, sub::size);
    }

    @Test
    void when_subListSortedWithNullComparator_then_classCastException() {
      List<Bus> sub = listOfBuses(2, 1).subList(0, 2);
      assertThrows(ClassCastException.class, () -> sub.sort(null));
    }

    @Test
    void when_subListStructuralChange_then_rootIteratorFailsFast() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3);
      Iterator<Bus> it = root.iterator();
      assertEquals(bus(1), it.next());
      root.subList(0, 1).add(bus(9)); // structural modification through a view
      assertThrows(ConcurrentModificationException.class, it::next);
    }
  }

  @Nested
  class SubListQueryAndViews {

    @Test
    void when_subListContainsAndIndexOfCalled_then_viewRelative() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 2);
      List<Bus> sub = root.subList(1, 4); // [2,3,2]
      assertTrue(sub.contains(bus(3)));
      assertEquals(0, sub.indexOf(bus(2)));
      assertFalse(
          sub.contains(bus(1))); // present in the root, outside the view
    }

    @Test
    void when_subListLastIndexOfCalled_then_viewRelative() {
      CustomArrayList<Bus> root = listOfBuses(2, 1, 2, 3);
      List<Bus> sub = root.subList(0, 3); // [2,1,2]
      assertEquals(2, sub.lastIndexOf(bus(2)));
    }

    @Test
    void when_subListContainsNull_then_foundWithinView() {
      CustomArrayList<Bus> root = new CustomArrayList<>();
      root.add(bus(1));
      root.add(null);
      root.add(bus(3));
      List<Bus> sub = root.subList(0, 2); // [1, null]
      assertTrue(sub.contains(null));
      assertEquals(1, sub.indexOf(null));
      assertEquals(1, sub.lastIndexOf(null));
    }

    @Test
    void when_subListContainsAllCalled_then_viewSemantics() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4);
      List<Bus> sub = root.subList(1, 3); // [2,3]
      assertTrue(sub.containsAll(List.of(bus(2), bus(3))));
      assertFalse(sub.containsAll(List.of(bus(2), bus(1))));
    }

    @Test
    void when_subListEqualsEqualList_then_true() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5);
      List<Bus> sub = root.subList(1, 4); // [2,3,4]
      assertTrue(
          sub.equals(List.of(bus(2), bus(3), bus(4)))); // RandomAccess branch
      assertTrue(sub.equals(new LinkedList<>(
          List.of(bus(2), bus(3), bus(4))))); // iterator branch
    }

    @Test
    void when_subListEqualsDifferentList_then_false() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5);
      List<Bus> sub = root.subList(1, 4);
      assertFalse(sub.equals(List.of(bus(2), bus(3))));
      assertFalse(sub.equals(List.of(bus(2), bus(3), bus(9))));
    }

    @Test
    void when_subListEqualsNullOrNonList_then_false() {
      List<Bus> sub = listOfBuses(1, 2).subList(0, 2);
      assertFalse(sub.equals(null));
      assertFalse(sub.equals("1, 2"));
    }

    @Test
    void when_subListHashCodeCalled_then_matchesPlainListHashCode() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5);
      List<Bus> sub = root.subList(1, 4);
      assertEquals(List.of(bus(2), bus(3), bus(4)).hashCode(), sub.hashCode());
    }

    @Test
    void when_subListToArrayCalled_then_rangeCopiedIndependently() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5);
      List<Bus> sub = root.subList(1, 4);
      Object[] copy = sub.toArray();
      assertEquals(3, copy.length);
      assertEquals(bus(2), copy[0]);
      copy[0] = "junk";
      assertEquals(bus(2), sub.get(0)); // the returned array is a private copy
    }

    @Test
    void when_subListToArrayWithSmallArray_then_newArrayCreated() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5);
      List<Bus> sub = root.subList(1, 4);
      Bus[] result = sub.toArray(new Bus[0]);
      assertEquals(3, result.length);
      assertEquals(bus(4), result[2]);
    }

    @Test
    void when_subListToArrayWithOversizedArray_then_filledAndNullTerminated() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5);
      List<Bus> sub = root.subList(1, 4);
      Bus[] target = new Bus[5];
      Bus[] result = sub.toArray(target);
      assertSame(target, result);
      assertEquals(bus(2), result[0]);
      assertNull(result[3]);
    }

    @Test
    void when_subListToStringCalled_then_rendersOnlyViewElements() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3);
      List<Bus> sub = root.subList(1, 3);
      assertEquals("[" + bus(2) + ", " + bus(3) + "]", sub.toString());
    }

    @Test
    void when_subListForEachCalled_then_visitsOnlyRange() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      List<Bus> seen = new ArrayList<>();
      sub.forEach(seen::add);
      assertRoutes(seen, 2, 3, 4);
    }

    @Test
    void when_subListReplaceAllCalled_then_onlyRangeTransformed() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      sub.replaceAll(b -> bus(b.routeNumber() + 10));
      assertRoutes(sub, 12, 13, 14);
      assertRoutes(root, 1, 12, 13, 14, 5, 6);
    }

    @Test
    void when_subListStreamCollected_then_snapshotOfRangeOnly() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      Stream<Bus> stream = sub.stream();
      root.add(bus(7)); // invalidates the view, but the stream already captured
                        // its snapshot
      assertRoutes(stream.collect(Collectors.toList()), 2, 3, 4);
    }

    @Test
    void when_streamRequestedFromInvalidatedView_then_cme() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3);
      List<Bus> sub = root.subList(0, 2);
      root.add(bus(4));
      assertThrows(ConcurrentModificationException.class, sub::stream);
    }

    @Test
    void when_subListSpliteratorCharacteristics_then_sizedToViewRange() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5);
      List<Bus> sub = root.subList(1, 4);
      Spliterator<Bus> spliterator = sub.spliterator();
      assertEquals(3, spliterator.getExactSizeIfKnown());
      int expectedBits = Spliterator.ORDERED | Spliterator.SIZED |
                         Spliterator.SUBSIZED | Spliterator.IMMUTABLE;
      assertEquals(expectedBits, spliterator.characteristics() & expectedBits);
    }

    @Test
    void when_subListParallelStreamAggregated_then_allViewElementsCounted() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      assertEquals(2 + 3 + 4,
                   sub.parallelStream().mapToInt(Bus::routeNumber).sum());
    }
  }

  @Nested
  class SubListIteration {

    @Test
    void when_subListIterated_then_yieldsOnlyViewElements() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5);
      List<Bus> sub = root.subList(1, 4);
      List<Bus> seen = new ArrayList<>();
      for (Bus element : sub) {
        seen.add(element);
      }
      assertRoutes(seen, 2, 3, 4);
    }

    @Test
    void when_subListListIteratorFromIndex_then_viewRelativeTraversal() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5);
      List<Bus> sub = root.subList(1, 4); // [2,3,4]
      ListIterator<Bus> it = sub.listIterator(1);
      assertEquals(1, it.nextIndex());
      assertEquals(0, it.previousIndex());
      assertEquals(bus(3), it.next());
      // WHY the same element again: after next() the cursor stands AFTER
      // element[1]; previous() steps back ONTO it — the standard
      // "alternating next/previous yields the same element twice"
      // ListIterator semantics (identical to ArrayList).
      assertEquals(bus(3), it.previous());
      assertEquals(bus(2), it.previous());
      assertThrows(NoSuchElementException.class, it::previous);
    }

    @Test
    void when_subListListIteratorInvalidIndex_then_indexOutOfBoundsException() {
      List<Bus> sub = listOfBuses(1, 2, 3).subList(1, 3); // size 2
      assertThrows(IndexOutOfBoundsException.class, () -> sub.listIterator(3));
      assertThrows(IndexOutOfBoundsException.class, () -> sub.listIterator(-1));
    }

    @Test
    void when_subListIteratorRemoveCalled_then_sizesSyncedInViewAndRoot() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4); // [2,3,4]
      ListIterator<Bus> it = sub.listIterator();
      assertEquals(bus(2), it.next());
      it.remove();
      assertRoutes(sub, 3, 4);
      assertRoutes(root, 1, 3, 4, 5, 6);
      assertEquals(bus(3), it.next());
    }

    @Test
    void when_subListIteratorAddCalled_then_insertedWithSizesSynced() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4); // [2,3,4]
      ListIterator<Bus> it = sub.listIterator();
      it.add(bus(9)); // inserted at view index 0 → root index 1
      assertRoutes(root, 1, 9, 2, 3, 4, 5, 6);
      assertRoutes(sub, 9, 2, 3, 4);
      assertEquals(bus(2), it.next()); // cursor moved past the inserted element
    }

    @Test
    void when_subListIteratorSetCalled_then_rootUpdated() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub = root.subList(1, 4);
      ListIterator<Bus> it = sub.listIterator();
      assertEquals(bus(2), it.next());
      it.set(bus(20));
      assertEquals(bus(20), root.get(1));
    }

    @Test
    void when_subListIteratorForEachRemainingCalled_then_consumesRestOfView() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5);
      List<Bus> sub = root.subList(1, 4);         // [2,3,4]
      ListIterator<Bus> it = sub.listIterator(1); // positioned before bus(3)
      List<Bus> seen = new ArrayList<>();
      it.forEachRemaining(seen::add);
      assertRoutes(seen, 3, 4);
    }

    @Test
    void when_rootModifiedAfterSubListIteratorCreated_then_nextThrowsCme() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3);
      List<Bus> sub = root.subList(0, 2);
      Iterator<Bus> it = sub.iterator();
      assertEquals(bus(1), it.next());
      root.add(bus(4));
      assertThrows(ConcurrentModificationException.class, it::next);
    }
  }

  @Nested
  class NestedSubLists {

    @Test
    void when_subListOfSubListCreated_then_offsetsCompose() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub1 = root.subList(1, 5); // [2,3,4,5]
      List<Bus> sub2 = sub1.subList(1, 3); // [3,4]
      assertRoutes(sub2, 3, 4);
    }

    @Test
    void when_nestedSubListRemoved_then_allChainSizesSynced() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub1 = root.subList(1, 5); // [2,3,4,5]
      List<Bus> sub2 = sub1.subList(1, 3); // [3,4]
      sub2.remove(0);                      // removes bus(3)
      assertRoutes(root, 1, 2, 4, 5, 6);
      assertRoutes(sub1, 2, 4, 5);
      assertRoutes(sub2, 4);
    }

    @Test
    void when_nestedSubListAdded_then_allChainSizesSynced() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub1 = root.subList(1, 5); // [2,3,4,5]
      List<Bus> sub2 = sub1.subList(1, 3); // [3,4]
      sub2.add(bus(99)); // appended at the view end → root index 4
      assertRoutes(root, 1, 2, 3, 4, 99, 5, 6);
      assertRoutes(sub1, 2, 3, 4, 99, 5);
      assertRoutes(sub2, 3, 4, 99);
    }

    @Test
    void when_nestedSubListRemoveIfCalled_then_rootAndChainSynced() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub1 = root.subList(1, 5); // [2,3,4,5]
      List<Bus> sub2 = sub1.subList(1, 3); // [3,4]
      assertTrue(sub2.removeIf(b -> b.routeNumber() == 3));
      assertRoutes(root, 1, 2, 4, 5, 6);
      assertRoutes(sub1, 2, 4, 5);
      assertRoutes(sub2, 4);
    }

    @Test
    void when_nestedSubListRangeInvalid_then_indexOutOfBoundsException() {
      CustomArrayList<Bus> root = listOfBuses(1, 2, 3, 4, 5, 6);
      List<Bus> sub1 = root.subList(1, 5); // size 4
      assertThrows(IndexOutOfBoundsException.class, () -> sub1.subList(0, 5));
    }
  }

  @Nested
  class StreamsAndSpliterators {

    @Test
    void when_streamCollected_then_matchesListContents() {
      assertRoutes(listOfBuses(1, 2, 3).stream().collect(Collectors.toList()),
                   1, 2, 3);
    }

    @Test
    void when_streamFiltered_then_onlyMatchingElementsRemain() {
      List<Bus> filtered = listOfBuses(1, 2, 3, 4)
                               .stream()
                               .filter(b -> b.routeNumber() % 2 == 0)
                               .collect(Collectors.toList());
      assertRoutes(filtered, 2, 4);
    }

    @Test
    void when_parallelStreamAggregated_then_allElementsAccounted() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      for (int route = 1; route <= 2_000; route++) {
        list.add(bus(route));
      }
      assertEquals(2_000, list.parallelStream().count());
      assertEquals(1_000L * (2_000L * 2_001L / 2L),
                   list.parallelStream().mapToLong(Bus::mileage).sum());
    }

    @Test
    void when_streamCreatedBeforeModification_then_traversesSnapshot() {
      // CopyOnWriteArrayList-style semantics: the pipeline sees the state
      // at the moment of stream creation, never a torn state.
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3);
      Stream<Bus> stream = list.stream();
      list.add(bus(4));
      list.remove(0);
      assertRoutes(stream.collect(Collectors.toList()), 1, 2, 3);
    }

    @Test
    void when_streamOfEmptyList_then_noElements() {
      assertTrue(new CustomArrayList<Bus>().stream().findAny().isEmpty());
    }

    @Test
    void when_spliteratorCharacteristics_then_orderedSizedSubsizedImmutable() {
      Spliterator<Bus> spliterator = listOfBuses(1, 2).spliterator();
      int expectedBits = Spliterator.ORDERED | Spliterator.SIZED |
                         Spliterator.SUBSIZED | Spliterator.IMMUTABLE;
      assertEquals(expectedBits, spliterator.characteristics() & expectedBits);
      assertEquals(2, spliterator.getExactSizeIfKnown());
    }
  }

  @Nested
  class ArrayConversion {

    @Test
    void when_toArrayNoArgsCalled_then_exactSizedIndependentCopy() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      Object[] copy = list.toArray();
      assertEquals(2, copy.length);
      assertEquals(bus(1), copy[0]);
      copy[0] = "junk";
      assertEquals(bus(1), list.get(0));
    }

    @Test
    void when_toArrayWithSmallerArray_then_newExactArrayReturned() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      Bus[] result = list.toArray(new Bus[0]);
      assertEquals(2, result.length);
      assertEquals(bus(2), result[1]);
    }

    @Test
    void
    when_toArrayWithOversizedArray_then_sameArrayFilledAndNullTerminated() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      Bus[] target = new Bus[4];
      Bus[] result = list.toArray(target);
      assertSame(target, result);
      assertEquals(bus(1), result[0]);
      assertEquals(bus(2), result[1]);
      assertNull(result[2]);
    }

    @Test
    void when_toArrayWithIncompatibleRuntimeType_then_arrayStoreException() {
      // WHY mixed content: the contract mirrors ArrayList — the runtime type
      // of the target array must be compatible with every stored element.
      CustomArrayList<Object> mixed = new CustomArrayList<>();
      mixed.add(bus(1));
      mixed.add("not a bus");
      assertThrows(ArrayStoreException.class, () -> mixed.toArray(new Bus[4]));
    }
  }

  @Nested
  class EqualityAndRendering {

    @Test
    void when_comparedToItself_then_true() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertTrue(list.equals(list));
    }

    @Test
    void when_comparedToEqualRandomAccessList_then_true() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertTrue(list.equals(new ArrayList<>(List.of(bus(1), bus(2)))));
    }

    @Test
    void when_comparedToEqualSequentialList_then_true() {
      // Exercises the non-RandomAccess branch of equals (iterator walk).
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertTrue(list.equals(new LinkedList<>(List.of(bus(1), bus(2)))));
    }

    @Test
    void when_comparedToDifferentList_then_false() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertFalse(list.equals(List.of(bus(1), bus(3))));
      assertFalse(list.equals(List.of(bus(1))));
      assertFalse(list.equals(List.of(bus(1), bus(2), bus(3))));
    }

    @Test
    void when_comparedToNull_then_false() {
      assertFalse(listOfBuses(1, 2).equals(null));
    }

    @Test
    void when_comparedToNonListObject_then_false() {
      assertFalse(listOfBuses(1, 2).equals("1, 2"));
    }

    @Test
    void when_hashCodeOfEqualLists_then_equalValues() {
      assertEquals(List.of(bus(1), bus(2)).hashCode(),
                   listOfBuses(1, 2).hashCode());
    }

    @Test
    void when_hashCodeOfEmptyList_then_one() {
      assertEquals(1, new CustomArrayList<Bus>().hashCode());
    }

    @Test
    void when_toStringOfEmptyList_then_canonicalFormat() {
      assertEquals("[]", new CustomArrayList<Bus>().toString());
    }

    @Test
    void when_toStringWithElements_then_rendersElementsInCanonicalFormat() {
      CustomArrayList<Bus> list = listOfBuses(1, 2);
      assertEquals("[" + bus(1) + ", " + bus(2) + "]", list.toString());
    }

    @Test
    void when_toStringWithNullElement_then_containsNullLiteral() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(bus(1));
      list.add(null);
      assertTrue(list.toString().contains("null"));
    }
  }

  @Nested
  class Cloning {

    @Test
    void when_cloned_then_contentEqualsButIndependent() {
      CustomArrayList<Bus> original = listOfBuses(1, 2, 3);
      CustomArrayList<Bus> clone = original.clone();
      assertEquals(original, clone);
      assertNotSame(original, clone);
      original.set(0, bus(10));
      original.add(bus(4));
      assertRoutes(clone, 1, 2, 3);
    }

    @Test
    void when_originalClearedAfterClone_then_cloneKeepsSnapshot() {
      CustomArrayList<Bus> original = listOfBuses(1, 2);
      CustomArrayList<Bus> clone = original.clone();
      original.clear();
      assertRoutes(clone, 1, 2);
    }

    @Test
    void when_cloneIteratedWhileOriginalMutated_then_noCme() {
      // WHY: the clone has its own monitor, so mutations of the original
      // must not fail the clone's fail-fast iterator.
      CustomArrayList<Bus> original = listOfBuses(1, 2, 3);
      CustomArrayList<Bus> clone = original.clone();
      Iterator<Bus> it = clone.iterator();
      assertEquals(bus(1), it.next());
      original.add(bus(4));
      assertEquals(bus(2), it.next());
      assertEquals(bus(3), it.next());
    }

    @Test
    void when_emptyListCloned_then_emptyAndIndependent() {
      CustomArrayList<Bus> original = new CustomArrayList<>();
      CustomArrayList<Bus> clone = original.clone();
      assertTrue(clone.isEmpty());
      original.add(bus(1));
      assertTrue(clone.isEmpty());
    }
  }

  @Nested
  class ImmutableCopy {

    @Test
    void when_immutableCopyOfCalled_then_contentAndOrderPreserved() {
      List<Bus> copy =
          CustomArrayList.immutableCopyOf(List.of(bus(3), bus(1), bus(2)));
      assertRoutes(copy, 3, 1, 2);
    }

    @Test
    void when_immutableCopyOfNullSource_then_nullPointerException() {
      assertThrows(NullPointerException.class,
                   () -> CustomArrayList.immutableCopyOf(null));
    }

    @Test
    void when_immutableCopyOfMutated_then_unsupportedOperationException() {
      List<Bus> copy = CustomArrayList.immutableCopyOf(List.of(bus(1)));
      assertThrows(UnsupportedOperationException.class, () -> copy.add(bus(2)));
      assertThrows(UnsupportedOperationException.class, () -> copy.remove(0));
      assertThrows(UnsupportedOperationException.class, copy::clear);
    }

    @Test
    void when_immutableCopyOfNullElements_then_nullsKeptUnlikeListCopyOf() {
      // WHY: List.copyOf rejects nulls; this helper deliberately allows
      // them — the documented difference from the JDK method.
      List<Bus> source = new ArrayList<>();
      source.add(bus(1));
      source.add(null);
      List<Bus> copy = CustomArrayList.immutableCopyOf(source);
      assertEquals(2, copy.size());
      assertNull(copy.get(1));
    }

    @Test
    void when_sourceChangedAfterCopy_then_copyUnaffected() {
      List<Bus> source = new ArrayList<>(List.of(bus(1)));
      List<Bus> copy = CustomArrayList.immutableCopyOf(source);
      source.add(bus(2));
      assertEquals(1, copy.size());
    }
  }

  @Nested
  class Capacity {

    @Test
    void when_growthBeyondInitialCapacity_then_noElementsLost() {
      CustomArrayList<Bus> list = new CustomArrayList<>(2);
      for (int route = 1; route <= 1_000; route++) {
        list.add(bus(route));
      }
      assertEquals(1_000, list.size());
      assertEquals(bus(1), list.get(0));
      assertEquals(bus(500), list.get(499));
      assertEquals(bus(1_000), list.get(999));
      assertTrue(list.contains(bus(317)));
    }

    @Test
    void when_ensureCapacityReserved_then_subsequentAddsSucceed() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.ensureCapacity(50);
      for (int route = 1; route <= 60; route++) {
        list.add(bus(route));
      }
      assertEquals(60, list.size());
    }

    @Test
    void when_trimToSizeCalled_then_contentKeptAndLiveIteratorFailsFast() {
      CustomArrayList<Bus> list = listOfBuses(1, 2, 3);
      Iterator<Bus> it = list.iterator();
      it.next();
      list.trimToSize(); // swaps the backing array and bumps modCount
      assertThrows(ConcurrentModificationException.class, it::next);
      assertRoutes(list, 1, 2, 3);
    }

    @Test
    void when_trimToSizeOnEmptyList_then_remainsEmpty() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.trimToSize();
      assertTrue(list.isEmpty());
    }
  }

  @Nested
  class Serialization {

    @Test
    void when_roundTripped_then_contentsSizeAndUsabilityRestored()
        throws Exception {
      CustomArrayList<Bus> original = new CustomArrayList<>();
      original.add(bus(1));
      original.add(bus(2));
      original.add(bus(3));

      CustomArrayList<Bus> restored = deserialize(serialize(original));

      assertEquals(original, restored);
      assertEquals(3, restored.size());
      restored.add(bus(4)); // the restored list must be fully usable
      assertEquals(4, restored.size());
      assertEquals(bus(4), restored.get(3));
    }

    @Test
    void when_emptyListRoundTripped_then_stillEmpty() throws Exception {
      CustomArrayList<Bus> restored =
          deserialize(serialize(new CustomArrayList<Bus>()));
      assertTrue(restored.isEmpty());
    }

    @Test
    void when_sourceMutatedAfterSerialization_then_restoredStateUnaffected()
        throws Exception {
      CustomArrayList<Bus> original = new CustomArrayList<>();
      original.add(bus(1));
      original.add(bus(2));
      byte[] bytes = serialize(original);
      original.clear();
      CustomArrayList<Bus> restored = deserialize(bytes);
      assertEquals(2, restored.size());
      assertEquals(bus(1), restored.get(0));
    }

    @Test
    void when_serialized_then_capacityIsNotPartOfTheStream() throws Exception {
      // WHY compare raw bytes: the custom form must contain only the size
      // and the live elements — spare capacity slots must not leak into
      // the stream.
      CustomArrayList<Bus> tight = new CustomArrayList<>(2);
      CustomArrayList<Bus> spacious = new CustomArrayList<>(100);
      tight.add(bus(1));
      tight.add(bus(2));
      spacious.add(bus(1));
      spacious.add(bus(2));
      assertArrayEquals(serialize(tight), serialize(spacious));
    }

    @Test
    void
    when_listContainsNonSerializableElement_then_notSerializableException() {
      // WHY a plain Object as the element: Bus itself is serializable now,
      // so a non-serializable element is the only way to keep covering the
      // failure path of the per-element write loop.
      CustomArrayList<Object> list = new CustomArrayList<>();
      list.add(new Object());
      assertThrows(NotSerializableException.class, () -> serialize(list));
    }

    @Test
    void when_subListSerialized_then_notSerializableException() {
      // Unchanged: the view itself is not serializable (AbstractList does
      // not implement Serializable), regardless of the element type.
      CustomArrayList<Bus> root = listOfBuses(1, 2);
      assertThrows(NotSerializableException.class,
                   () -> serialize(root.subList(0, 1)));
    }

    @Test
    void when_deserializedSizeIsNegative_then_invalidObjectException()
        throws Exception {
      byte[] stream = serialize(new CustomArrayList<Bus>());
      byte[] corrupted = withSizePatchedTo(stream, -1);
      assertThrows(InvalidObjectException.class, () -> deserialize(corrupted));
    }

    @Test
    void when_deserializedSizeExceedsMaxArraySize_then_invalidObjectException()
        throws Exception {
      // Integer.MAX_VALUE is above MAX_ARRAY_SIZE: rejected cleanly instead
      // of attempting a doomed huge allocation.
      byte[] stream = serialize(new CustomArrayList<Bus>());
      byte[] corrupted = withSizePatchedTo(stream, Integer.MAX_VALUE);
      assertThrows(InvalidObjectException.class, () -> deserialize(corrupted));
    }

    @Test
    void when_busRoundTripped_then_allFieldsPreservedAndEqual()
        throws Exception {
      Bus original = bus(42);
      Bus restored = deserialize(serialize(original));
      assertEquals(original, restored);  // value equality survives
      assertNotSame(original, restored); // a distinct instance comes back
      assertEquals(42, restored.routeNumber());
      assertEquals("Model-42", restored.model());
      assertEquals(42_000L, restored.mileage());
    }

    @Test
    void
    when_busDeserializedWithCorruptedRouteNumber_then_invalidObjectException()
        throws Exception {
      // Bus.readObject revalidates the constructor's invariants; the only
      // way to reach it with broken state is a genuinely corrupt stream.
      byte[] stream = serialize(bus(1));
      byte[] corrupted = withBusRouteNumberPatchedTo(stream, -1);
      assertThrows(InvalidObjectException.class, () -> deserialize(corrupted));
    }
  }

  @Nested
  class NullArguments {

    @Test
    void when_nullPassedToConstructor_then_nullPointerException() {
      assertThrows(NullPointerException.class,
                   () -> new CustomArrayList<Bus>(null));
    }

    @Test
    void when_nullPassedToAddAll_then_nullPointerException() {
      CustomArrayList<Bus> list = listOfBuses(1);
      assertThrows(NullPointerException.class, () -> list.addAll(null));
      assertThrows(NullPointerException.class, () -> list.addAll(0, null));
    }

    @Test
    void when_nullPassedToBulkCollectionMethods_then_nullPointerException() {
      CustomArrayList<Bus> list = listOfBuses(1);
      assertThrows(NullPointerException.class, () -> list.removeAll(null));
      assertThrows(NullPointerException.class, () -> list.retainAll(null));
      assertThrows(NullPointerException.class, () -> list.containsAll(null));
    }

    @Test
    void when_nullPassedToFunctionalArguments_then_nullPointerException() {
      CustomArrayList<Bus> list = listOfBuses(1);
      assertThrows(NullPointerException.class, () -> list.forEach(null));
      assertThrows(NullPointerException.class, () -> list.removeIf(null));
      assertThrows(NullPointerException.class, () -> list.replaceAll(null));
    }

    @Test
    void when_nullArrayPassedToToArray_then_nullPointerException() {
      CustomArrayList<Bus> list = listOfBuses(1);
      assertThrows(NullPointerException.class, () -> list.toArray((Bus[])null));
    }

    @Test
    void when_nullPassedToImmutableCopyOf_then_nullPointerException() {
      assertThrows(NullPointerException.class,
                   () -> CustomArrayList.immutableCopyOf(null));
    }

    @Test
    void when_nullPassedToSubListBulkMethods_then_nullPointerException() {
      List<Bus> sub = listOfBuses(1, 2).subList(0, 2);
      assertThrows(NullPointerException.class, () -> sub.addAll(null));
      assertThrows(NullPointerException.class, () -> sub.addAll(0, null));
      assertThrows(NullPointerException.class, () -> sub.removeAll(null));
      assertThrows(NullPointerException.class, () -> sub.retainAll(null));
      assertThrows(NullPointerException.class, () -> sub.containsAll(null));
      assertThrows(NullPointerException.class, () -> sub.removeIf(null));
      assertThrows(NullPointerException.class, () -> sub.forEach(null));
      assertThrows(NullPointerException.class, () -> sub.replaceAll(null));
      assertThrows(NullPointerException.class, () -> sub.toArray((Bus[])null));
    }

    @Test
    void when_nullActionPassedToIteratorForEachRemaining_then_npe() {
      ListIterator<Bus> it = listOfBuses(1, 2).listIterator();
      assertThrows(NullPointerException.class, () -> it.forEachRemaining(null));
    }

    @Test
    void when_nullPassedToEquals_then_returnsFalseInsteadOfException() {
      assertFalse(listOfBuses(1).equals(null));
      List<Bus> sub = listOfBuses(1).subList(0, 1);
      assertFalse(sub.equals(null));
    }
  }

  @Nested
  class NullElements {

    @Test
    void when_nullElementsAdded_then_sizeCountsThem() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(null);
      list.add(bus(1));
      list.add(null);
      assertEquals(3, list.size());
    }

    @Test
    void when_nullElementReadByGet_then_returnsNull() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(null);
      assertNull(list.get(0));
    }

    @Test
    void when_nullElementRemovedByIndex_then_returnsNullAndShrinks() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(bus(1));
      list.add(null);
      assertNull(list.remove(1));
      assertRoutes(list, 1);
    }

    @Test
    void when_nullElementRemovedByObject_then_onlyFirstNullRemoved() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(null);
      list.add(bus(1));
      list.add(null);
      assertTrue(list.remove((Object)null));
      assertEquals(2, list.size());
      assertEquals(bus(1), list.get(0));
      assertNull(list.get(1));
    }

    @Test
    void when_nullElementsSearched_then_indexOfAndLastIndexOfLocateThem() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(bus(1));
      list.add(null);
      list.add(bus(2));
      list.add(null);
      assertTrue(list.contains(null));
      assertEquals(1, list.indexOf(null));
      assertEquals(3, list.lastIndexOf(null));
    }

    @Test
    void when_nullElementsAbsent_then_searchReturnsMinusOneAndFalse() {
      CustomArrayList<Bus> list = listOfBuses(1);
      assertFalse(list.contains(null));
      assertEquals(-1, list.indexOf(null));
      assertEquals(-1, list.lastIndexOf(null));
    }

    @Test
    void when_listsWithNullsCompared_then_equalityFollowsPosition() {
      CustomArrayList<Bus> first = new CustomArrayList<>();
      first.add(bus(1));
      first.add(null);
      CustomArrayList<Bus> sameShape = new CustomArrayList<>();
      sameShape.add(bus(1));
      sameShape.add(null);
      CustomArrayList<Bus> flipped = new CustomArrayList<>();
      flipped.add(null);
      flipped.add(bus(1));
      assertTrue(first.equals(sameShape));
      assertFalse(first.equals(flipped));
      assertEquals(first.hashCode(), sameShape.hashCode());
    }

    @Test
    void when_nullsFilteredByRemoveIf_then_removed() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(null);
      list.add(bus(1));
      list.add(null);
      assertTrue(list.removeIf(Objects::isNull));
      assertRoutes(list, 1);
    }

    @Test
    void when_nullsInSubList_then_viewOperationsHandleThem() {
      CustomArrayList<Bus> root = new CustomArrayList<>();
      root.add(bus(1));
      root.add(null);
      root.add(bus(3));
      List<Bus> sub = root.subList(1, 3); // [null, bus(3)]
      assertNull(sub.get(0));
      assertEquals(0, sub.indexOf(null));
      assertTrue(sub.remove(null));
      assertRoutes(sub, 3);
      assertRoutes(root, 1, 3);
    }

    @Test
    void when_nullsReturnedByIterator_then_traversalSeesThem() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(bus(1));
      list.add(null);
      Iterator<Bus> it = list.iterator();
      assertEquals(bus(1), it.next());
      assertNull(it.next());
      assertFalse(it.hasNext());
    }

    @Test
    void when_nullsInStream_then_counted() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(null);
      list.add(bus(1));
      list.add(null);
      assertEquals(3, list.stream().count());
    }

    @Test
    void when_nullsCopiedByToArray_then_presentInResult() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(null);
      list.add(bus(1));
      Object[] array = list.toArray();
      assertNull(array[0]);
      assertEquals(bus(1), array[1]);
    }

    @Test
    void when_nullsInClone_then_preserved() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(bus(1));
      list.add(null);
      CustomArrayList<Bus> clone = list.clone();
      assertNull(clone.get(1));
      assertEquals(2, clone.size());
    }

    @Test
    void when_nullsSerialized_then_roundTripPreservesThem() throws Exception {
      // WHY mix nulls with a real bus: proves both that null elements
      // serialize (writeObject(null) is legal) and that their positions
      // survive the round trip.
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(null);
      list.add(bus(1));
      list.add(null);
      CustomArrayList<Bus> restored = deserialize(serialize(list));
      assertEquals(list, restored);
      assertNull(restored.get(0));
      assertEquals(bus(1), restored.get(1));
      assertNull(restored.get(2));
    }

    @Test
    void when_nullsVisitedByForEach_then_consumerReceivesThem() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(null);
      list.add(bus(1));
      List<Bus> seen = new ArrayList<>();
      list.forEach(seen::add);
      assertEquals(2, seen.size());
      assertNull(seen.get(0));
    }

    @Test
    void when_replaceAllFillsNulls_then_nullsReplaced() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(null);
      list.add(bus(2));
      list.replaceAll(b -> b == null ? bus(1) : b);
      assertRoutes(list, 1, 2);
    }

    @Test
    void when_containsAllWithNullQuery_then_findsStoredNull() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(bus(1));
      list.add(null);
      assertTrue(list.containsAll(Collections.singletonList((Object)null)));
    }

    @Test
    void when_removeAllWithNullQuery_then_removesStoredNulls() {
      CustomArrayList<Bus> list = new CustomArrayList<>();
      list.add(null);
      list.add(bus(1));
      list.add(null);
      assertTrue(list.removeAll(Collections.singletonList((Object)null)));
      assertRoutes(list, 1);
    }
  }

  @Nested
  class Concurrency {

    @Test
    @Timeout(10)
    void
    when_subListAppendedFromMultipleThreads_then_noFailFastAndSizesConsistent()
        throws Exception {
      // WHY this test exists: SubList.add(E) must be one atomic critical
      // section. The AbstractList default (add(size(), e)) takes and
      // releases the monitor twice, which used to surface as spurious
      // ConcurrentModificationExceptions under exactly this workload.
      CustomArrayList<Bus> root = listOfBuses(1, 2);
      List<Bus> view = root.subList(0, 2);

      int threads = 4;
      int additionsPerThread = 250;
      ExecutorService pool = Executors.newFixedThreadPool(threads);
      try {
        CountDownLatch start = new CountDownLatch(1);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        List<Future<?>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
          final int threadId = t;
          tasks.add(pool.submit(() -> {
            try {
              start.await();
              for (int i = 0; i < additionsPerThread; i++) {
                view.add(bus(threadId * 1_000 + i));
              }
            } catch (Throwable failure) {
              failures.add(failure);
            }
            return null;
          }));
        }
        start.countDown();
        for (Future<?> task : tasks) {
          task.get(9, TimeUnit.SECONDS);
        }
        assertTrue(failures.isEmpty(),
                   () -> "concurrent adds failed: " + failures);
        assertEquals(2 + threads * additionsPerThread, view.size());
        assertEquals(2 + threads * additionsPerThread, root.size());
        assertEquals(bus(1),
                     root.get(0)); // the untouched prefix stays in place
        assertEquals(bus(2), root.get(1));
      } finally {
        pool.shutdownNow();
      }
    }

    @Test
    @Timeout(10)
    void when_readersAndWritersRunConcurrently_then_noErrorsAndFinalSizeExact()
        throws Exception {
      CustomArrayList<Bus> root = new CustomArrayList<>();
      for (int route = 1; route <= 100; route++) {
        root.add(bus(route));
      }
      ExecutorService pool = Executors.newFixedThreadPool(5);
      try {
        CountDownLatch start = new CountDownLatch(1);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        List<Future<?>> tasks = new ArrayList<>();
        for (int w = 0; w < 3; w++) {
          final int writer = w;
          tasks.add(pool.submit(() -> {
            try {
              start.await();
              for (int i = 0; i < 400; i++) {
                root.add(bus(1_000 + writer * 10_000 + i));
              }
            } catch (Throwable failure) {
              failures.add(failure);
            }
            return null;
          }));
        }
        for (int r = 0; r < 2; r++) {
          tasks.add(pool.submit(() -> {
            try {
              start.await();
              for (int i = 0; i < 20_000; i++) {
                int size = root.size(); // snapshot read under the monitor
                if (size > 0) {
                  root.get(size - 1); // index only grows: writers only add
                }
                root.contains(bus(1));
              }
            } catch (Throwable failure) {
              failures.add(failure);
            }
            return null;
          }));
        }
        start.countDown();
        for (Future<?> task : tasks) {
          task.get(9, TimeUnit.SECONDS);
        }
        assertTrue(failures.isEmpty(),
                   () -> "unexpected failures: " + failures);
        assertEquals(100 + 3 * 400, root.size());
      } finally {
        pool.shutdownNow();
      }
    }
  }
}