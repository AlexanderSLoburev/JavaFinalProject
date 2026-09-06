package com.example.timsort.collection;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Thread-safe, array-backed implementation of the List interface.
 *
 * Every state-touching operation synchronizes on the list instance itself.
 * SubList views and iterators lock the same root monitor,
 * so the root and all of its views always exclude each other. Iterators
 * remain fail-fast. stream()/parallelStream() traverse an immutable
 * snapshot taken under the lock (CopyOnWriteArrayList-style semantics), so
 * pipelines require no external synchronization.
 *
 * As with the JDK's synchronized wrappers: several methods invoke foreign
 * code while holding this list's monitor — addAll/containsAll/removeAll/
 * retainAll (the foreign collection's iterator and contains()), equals()
 * (the other list's size()/get()/iterator()), contains/indexOf (the
 * elements' equals/hashCode), hashCode/toString (the elements'
 * hashCode/toString), forEach/removeIf (the callback), replaceAll (the
 * operator) and sort (the comparator). Do not call back into this list
 * from such code on another thread: two lists whose equals() or
 * retainAll() touch each other in opposite order will deadlock.
 *
 * SubList views are not serializable (serializing one throws
 * NotSerializableException): a view is only meaningful together with its
 * root's state at one instant. Serialize the root, or copy the view out
 * first via {@code new CustomArrayList<>(view)}.
 */
public class CustomArrayList<T>
    implements List<T>, RandomAccess, Cloneable, Serializable {

  private static final int DEFAULT_CAPACITY = 8;
  // WHY not Integer.MAX_VALUE: some VMs reserve header words inside array
  // allocations, so requesting the theoretical maximum reliably fails with
  // OutOfMemoryError; 8 slots of headroom is what the JDK uses as well.
  private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
  @Serial private static final long serialVersionUID = 1L;

  // WHY transient: the serialized form is custom (see writeObject) — only
  // the live elements go over the wire, never the spare capacity slots or
  // the bookkeeping fields.
  private transient Object[] data;
  private transient int size;
  private transient int modCount = 0;

  private abstract class BaseIterator {
    protected int cursor;           // next index to return
    protected int lastRet = -1;     // index of the last returned element
    protected int expectedModCount; // to detect concurrent modification

    // WHY every operation below locks the root list: the iterator object
    // itself is still single-threaded (as everywhere in the JDK), but the
    // state it reads (size, modCount, the backing array) is guarded by the
    // root's monitor. The lock is reentrantly re-acquired by the delegated
    // list methods, so no deadlock with them is possible.
    //
    // NOTE: hasNext()/hasPrevious() only promise the state as of their own
    // call; by the time next()/previous() runs the list may have changed —
    // they revalidate under the lock and fail fast, exactly like the JDK's
    // fail-fast iterators.

    BaseIterator(int startCursor) {
      cursor = startCursor;
      expectedModCount = getModCount();
    }

    /**
     * returns the modCount (i.e. the number of structural
     * modifications) of the list being iterated over
     */
    protected final int getModCount() { return CustomArrayList.this.modCount; }

    protected abstract int getSize();
    protected abstract T getElement(int index);
    protected abstract void removeAt(int index);
    protected abstract void setAt(int index, T element);
    protected abstract void addAt(int index, T element);

    protected final void checkForModification() {
      if (getModCount() != expectedModCount) {
        throw new ConcurrentModificationException();
      }
    }

    public boolean hasNext() {
      synchronized (CustomArrayList.this) { return cursor != getSize(); }
    }

    public T next() {
      synchronized (CustomArrayList.this) {
        checkForModification();

        if (cursor >= getSize()) {
          throw new NoSuchElementException();
        }

        ++cursor;
        lastRet = cursor - 1;

        return getElement(lastRet);
      }
    }

    public void remove() {
      synchronized (CustomArrayList.this) {
        if (lastRet < 0) {
          throw new IllegalStateException();
        }

        checkForModification();
        removeAt(lastRet);
        cursor =
            lastRet; // move cursor back to the index of the removed element
        lastRet = -1;
        expectedModCount = getModCount(); // update expectedModCount after
                                          // a successful removal
      }
    }
  }

  private abstract class BaseListIterator
      extends BaseIterator implements ListIterator<T> {

    BaseListIterator(int startCursor) { super(startCursor); }

    @Override
    public boolean hasPrevious() {
      // No lock: 'cursor' is iterator-local state, no shared data involved.
      return cursor > 0;
    }

    @Override
    public int nextIndex() {
      return cursor;
    }

    @Override
    public int previousIndex() {
      return cursor - 1;
    }

    @Override
    public T previous() {
      synchronized (CustomArrayList.this) {
        checkForModification();

        if (cursor <= 0) {
          throw new NoSuchElementException();
        }

        --cursor;
        lastRet = cursor;

        return getElement(lastRet);
      }
    }

    @Override
    public void set(T e) {
      synchronized (CustomArrayList.this) {
        if (lastRet < 0) {
          throw new IllegalStateException();
        }

        checkForModification();
        setAt(lastRet, e);
      }
    }

    @Override
    public void add(T e) {
      synchronized (CustomArrayList.this) {
        checkForModification();

        addAt(cursor, e);
        ++cursor; // move cursor forward to point after the newly added element
        lastRet = -1;
        expectedModCount = getModCount();
      }
    }

    @Override
    public void forEachRemaining(Consumer<? super T> action) {
      Objects.requireNonNull(action);

      // WHY an explicit override: the Iterator default drives hasNext()/
      // next(), taking and releasing the monitor once per element. One
      // critical section plus a captured bound makes the whole sweep
      // atomic.
      synchronized (CustomArrayList.this) {
        checkForModification();

        final int expectedModCount = getModCount();
        final int end = getSize();

        // WHY a guarded loop: the consumer is user code and may re-enter
        // the list mid-sweep; the guard stops at the first structural
        // change so no element is served from a torn state, and the check
        // below converts the violation into a fail-fast CME.
        while (cursor < end && getModCount() == expectedModCount) {
          action.accept(getElement(cursor));
          ++cursor;
          lastRet = cursor - 1;
        }

        if (getModCount() != expectedModCount) {
          throw new ConcurrentModificationException();
        }
      }
    }
  }

  private class ArrayListIterator extends BaseListIterator {
    ArrayListIterator(int index) { super(index); }

    @Override
    protected int getSize() {
      return CustomArrayList.this.size;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected T getElement(int index) {
      return (T)CustomArrayList.this.data[index];
    }

    @Override
    protected void removeAt(int index) {
      CustomArrayList.this.remove(index);
    }

    @Override
    protected void setAt(int index, T element) {
      CustomArrayList.this.set(index, element);
    }

    @Override
    protected void addAt(int index, T element) {
      CustomArrayList.this.add(index, element);
    }
  }

  /**
   * View of a portion of the parent list, defined by a starting index
   * (offset) and a size.
   *
   * <p>Every operation inherited from AbstractList/AbstractCollection that
   * would otherwise run as several separate monitor acquisitions (the
   * iterator-driven defaults) is overridden here so the whole operation
   * stays atomic — see the individual WHY comments. Instances are not
   * serializable: serializing one throws NotSerializableException, because
   * a view is only meaningful together with its root's state at one
   * instant; copy the view out ({@code new CustomArrayList<>(view)}) if
   * its contents must go over the wire.
   */
  private class SubList extends AbstractList<T> implements RandomAccess {

    // WHY every method below locks CustomArrayList.this (the ROOT list)
    // instead of using its own monitor: the root's synchronized methods use
    // the root's monitor, and a view touching the same shared backing array
    // under a different lock would race with the root. One lock shared by
    // the root and all of its views keeps them mutually exclusive.

    private final SubList parent; // null if this is the root sublist, otherwise
                                  // points to the parent sublist
    private final int offset;     // starting index in the parent list
    private int size;             // number of elements in this sublist
    private int expectedModCount; // to detect concurrent modifications in the
                                  // parent list

    private class SubListIterator extends BaseListIterator {

      SubListIterator(int index) { super(index); }

      @Override
      protected int getSize() {
        return SubList.this.size;
      }

      @Override
      @SuppressWarnings("unchecked")
      protected T getElement(int index) {
        return (T)CustomArrayList.this.data[offset + index];
      }

      @Override
      protected void removeAt(int index) {
        SubList.this.remove(index);
      }

      @Override
      protected void setAt(int index, T element) {
        SubList.this.set(index, element);
      }

      @Override
      protected void addAt(int index, T element) {
        SubList.this.add(index, element); // refreshes size and modCount
                                          // of the subList.parent
      }
    }

    /**
     * Creates a new SubList view of the parent list from fromIndex (inclusive)
     * to toIndex (exclusive).
     * @param fromIndex inclusive starting index of the sublist in the parent
     *     list
     * @param toIndex exclusive ending index of the sublist in the parent list
     */
    SubList(int fromIndex, int toIndex) {
      this.parent = null;
      this.offset = fromIndex;
      this.size = toIndex - fromIndex;
      this.expectedModCount = CustomArrayList.this.modCount;
    }

    /**
     * Creates a new SubList view of the parent sublist from fromIndex
     * (inclusive) to toIndex (exclusive).
     * @param parent the parent sublist
     * @param fromIndex inclusive starting index of the sublist in the parent
     * @param toIndex exclusive ending index of the sublist in the parent list
     */
    private SubList(SubList parent, int fromIndex, int toIndex) {
      this.parent = parent;
      this.offset = parent.offset + fromIndex;
      this.size = toIndex - fromIndex;
      this.expectedModCount = CustomArrayList.this.modCount;
    }

    private void checkForModification() {
      if (CustomArrayList.this.modCount != expectedModCount) {
        throw new ConcurrentModificationException();
      }
    }

    private void checkIndexForAccess(int index) {
      if (index < 0 || index >= size) {
        throw new IndexOutOfBoundsException("Index: " + index +
                                            ", Size: " + size);
      }
    }

    private void checkIndexForAdd(int index) {
      if (index < 0 || index > size) {
        throw new IndexOutOfBoundsException("Index: " + index +
                                            ", Size: " + size);
      }
    }

    /**
     * Synchronizes the size of this sublist and all its parent sublists with
     * the root list after a structural modification (add or remove).
     * Must be called while holding the root list's monitor.
     * @param sizeDelta the change in size (positive for add, negative for
     *     remove)
     */
    private void syncChain(int sizeDelta) {
      if (parent != null) {
        parent.syncChain(sizeDelta);
      }
      size += sizeDelta;
      expectedModCount = CustomArrayList.this.modCount;
    }

    @Override
    public boolean add(T element) {
      // WHY an explicit override: the AbstractList default calls
      // add(size(), e), taking and releasing the monitor twice — a
      // concurrent modification between the two calls could either target
      // a stale insertion index or surface as a bogus
      // ConcurrentModificationException.
      synchronized (CustomArrayList.this) {
        checkForModification();
        CustomArrayList.this.add(offset + size, element);
        syncChain(1);
        return true;
      }
    }

    @Override
    public void add(int index, T element) {
      synchronized (CustomArrayList.this) {
        checkIndexForAdd(index);
        checkForModification();

        CustomArrayList.this.add(offset + index, element);
        syncChain(1);
      }
    }

    @Override
    public boolean addAll(Collection<? extends T> collection) {
      // WHY lock here instead of plain delegation: reading 'size' outside
      // the monitor could target a stale insertion index.
      synchronized (CustomArrayList.this) { return addAll(size, collection); }
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> collection) {
      Objects.requireNonNull(collection);

      synchronized (CustomArrayList.this) {
        checkIndexForAdd(index);
        checkForModification();

        // WHY measure the delta via the root's size instead of calling
        // collection.toArray() twice: a second toArray() is wasteful, and
        // if the source collection changes in between the recorded count
        // no longer matches what was actually inserted, so syncChain would
        // corrupt the size of every view in the chain.
        int sizeBefore = CustomArrayList.this.size;
        CustomArrayList.this.addAll(offset + index, collection);
        int numNew = CustomArrayList.this.size - sizeBefore;

        if (numNew == 0) {
          return false;
        }

        syncChain(numNew);
        return true;
      }
    }

    @Override
    public void clear() {
      // WHY an explicit override: AbstractList.clear() reads size() in one
      // critical section and then calls removeRange in another; doing both
      // under a single lock acquisition keeps the operation atomic.
      synchronized (CustomArrayList.this) {
        removeRange(0, size); // reentrantly re-locks; no deadlock
      }
    }

    @Override
    public boolean contains(Object object) {
      return indexOf(object) >= 0; // thread-safe: indexOf holds the monitor
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
      Objects.requireNonNull(collection);

      // WHY an outer lock: the AbstractCollection default calls contains()
      // once per element, each in its own critical section, so the combined
      // answer could describe several different moments in time. One
      // critical section makes the whole check atomic (indexOf
      // reentrantly re-acquires the monitor).
      synchronized (CustomArrayList.this) {
        checkForModification();

        for (Object element : collection) {
          if (indexOf(element) < 0) {
            return false;
          }
        }
        return true;
      }
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }

      if (!(object instanceof List<?>)) {
        return false;
      }

      synchronized (CustomArrayList.this) {
        checkForModification();

        List<?> otherList = (List<?>)object;
        if (size != otherList.size()) {
          return false;
        }

        Object[] elements = CustomArrayList.this.data;

        // WHY branch on RandomAccess: get(i) on a linked implementation is
        // O(n), which would turn equals into O(n^2) for such lists.
        if (otherList instanceof RandomAccess) {
          for (int i = 0; i < size; ++i) {
            if (!Objects.equals(elements[offset + i], otherList.get(i))) {
              return false;
            }
          }
        } else {
          Iterator<?> otherIterator = otherList.iterator();
          for (int i = 0; i < size; ++i) {
            if (!otherIterator.hasNext() ||
                !Objects.equals(elements[offset + i], otherIterator.next())) {
              return false;
            }
          }
        }

        return true;
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void forEach(Consumer<? super T> action) {
      Objects.requireNonNull(action);

      // WHY an explicit override: the Iterable default drives the iterator,
      // taking and releasing the monitor once per element; one critical
      // section plus a captured snapshot keeps the sweep atomic and
      // fail-fast.
      synchronized (CustomArrayList.this) {
        checkForModification();

        final int expectedModCount = CustomArrayList.this.modCount;
        final Object[] elements = CustomArrayList.this.data;
        final int end = offset + size;

        for (int i = offset;
             i < end && CustomArrayList.this.modCount == expectedModCount;
             ++i) {
          action.accept((T)elements[i]);
        }

        // Fail-fast: was this list structurally modified while we were
        // running?
        if (CustomArrayList.this.modCount != expectedModCount) {
          throw new ConcurrentModificationException();
        }
      }
    }

    @Override
    public T get(int index) {
      synchronized (CustomArrayList.this) {
        checkIndexForAccess(index);
        checkForModification();
        return CustomArrayList.this.get(offset + index);
      }
    }

    @Override
    public int hashCode() {
      synchronized (CustomArrayList.this) {
        checkForModification();
        int hash = 1;
        Object[] elements = CustomArrayList.this.data;
        for (int i = offset, end = offset + size; i < end; ++i) {
          hash = 31 * hash + Objects.hashCode(elements[i]);
        }
        return hash;
      }
    }

    @Override
    public int indexOf(Object object) {
      synchronized (CustomArrayList.this) {
        checkForModification();
        Object[] elements = CustomArrayList.this.data;

        for (int i = offset, end = offset + size; i < end; ++i) {
          if (Objects.equals(elements[i], object)) {
            return i - offset;
          }
        }

        return -1;
      }
    }

    @Override
    public Iterator<T> iterator() {
      return listIterator(
          0); // thread-safe: listIterator(int) holds the monitor
    }

    @Override
    public int lastIndexOf(Object object) {
      synchronized (CustomArrayList.this) {
        checkForModification();
        Object[] elements = CustomArrayList.this.data;
        for (int i = offset + size - 1; i >= offset; --i) {
          if (Objects.equals(elements[i], object)) {
            return i - offset;
          }
        }
        return -1;
      }
    }

    @Override
    public ListIterator<T> listIterator() {
      return listIterator(0);
    }

    @Override
    public ListIterator<T> listIterator(int index) {
      synchronized (CustomArrayList.this) {
        checkIndexForAdd(index);
        checkForModification();
        return new SubListIterator(index);
      }
    }

    @Override
    public boolean remove(Object object) {
      // WHY an explicit override: the AbstractCollection default walks this
      // view's iterator (one critical section per element) and removes
      // through the iterator's own critical section — find-then-remove is
      // not atomic, and a concurrent modification in between could delete
      // the wrong element. One lock acquisition keeps the pair atomic
      // (mirrors CustomArrayList#remove(Object)).
      synchronized (CustomArrayList.this) {
        checkForModification();

        for (int i = offset; i < offset + size; ++i) {
          if (Objects.equals(CustomArrayList.this.data[i], object)) {
            CustomArrayList.this.remove(i);
            syncChain(-1);
            return true;
          }
        }

        return false;
      }
    }

    @Override
    public T remove(int index) {
      synchronized (CustomArrayList.this) {
        checkIndexForAccess(index);
        checkForModification();
        T removed = CustomArrayList.this.remove(offset + index);
        syncChain(-1);
        return removed;
      }
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
      Objects.requireNonNull(collection);
      // WHY wrap in a predicate: removeIf, removeAll and retainAll share
      // one compaction core; only the survival rule differs.
      return retainMatching(element -> !collection.contains(element));
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean removeIf(Predicate<? super T> filter) {
      Objects.requireNonNull(filter);
      return retainMatching(element -> !filter.test((T)element));
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
      synchronized (CustomArrayList.this) {
        checkSubListRange(fromIndex, toIndex, size);
        checkForModification();

        if (fromIndex == toIndex) {
          return;
        }

        CustomArrayList.this.removeRange(offset + fromIndex, offset + toIndex);
        syncChain(fromIndex - toIndex);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void replaceAll(UnaryOperator<T> operator) {
      Objects.requireNonNull(operator);

      // WHY an explicit override: the List default drives the listIterator,
      // taking and releasing the monitor once per element. One critical
      // section keeps the replacement atomic; the captured snapshot keeps
      // it self-consistent even if the operator re-enters the list.
      synchronized (CustomArrayList.this) {
        checkForModification();

        final int expectedModCount = CustomArrayList.this.modCount;
        final Object[] elements = CustomArrayList.this.data;
        final int end = offset + size;

        for (int i = offset;
             i < end && CustomArrayList.this.modCount == expectedModCount;
             ++i) {
          elements[i] = operator.apply((T)elements[i]);
        }

        // Fail-fast: was this list structurally modified while we were
        // running?
        if (CustomArrayList.this.modCount != expectedModCount) {
          throw new ConcurrentModificationException();
        }
        // Element replacement (like set()) is not a structural change, so
        // modCount is intentionally left untouched.
      }
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
      Objects.requireNonNull(collection);
      return retainMatching(collection::contains);
    }

    /**
     * In-place compaction of this view's range in the shared backing
     * array; the shared core of removeIf / removeAll / retainAll.
     *
     * Deliberate trade-off (the same one the JDK's ArrayList.batchRemove
     * makes): if the user code inside 'survives' throws, the finally block
     * commits the already-compacted prefix together with the unprocessed
     * tail, so the list is left consistent but partially filtered while
     * the exception still propagates. Likewise, if that user code
     * re-enters and structurally modifies the list, the commit below uses
     * the bounds captured at entry; the modCount check afterwards reports
     * the violation as a ConcurrentModificationException (detect, not
     * prevent).
     *
     * @param survives decides, per element, whether it stays in the list
     */
    private boolean retainMatching(Predicate<Object> survives) {
      synchronized (CustomArrayList.this) {
        checkForModification();

        final int expectedModCount = CustomArrayList.this.modCount;
        // WHY capture the array, the bounds and the root's size: 'survives'
        // is user code (a predicate or collection.contains) and may
        // re-enter this list; the captured values keep the compaction and
        // the tail shift below mutually consistent even then.
        final Object[] elements = CustomArrayList.this.data;
        final int from = offset;
        final int to = offset + size;
        final int rootSize = CustomArrayList.this.size;

        int readIdx = from;
        int writeIdx = from;
        boolean isModified = false;

        try {
          for (; readIdx < to; ++readIdx) {
            if (survives.test(elements[readIdx])) {
              elements[writeIdx++] = elements[readIdx];
            }
          }
        } finally {
          if (readIdx != to) {
            // WHY repair on early exit: the user code inside test() may
            // throw; appending the unprocessed tail after the kept prefix
            // leaves the array holding a consistent (partially filtered)
            // sequence.
            System.arraycopy(elements, readIdx, elements, writeIdx,
                             to - readIdx);
            writeIdx += to - readIdx;
          }

          final int removed = to - writeIdx;
          if (removed > 0) {
            // WHY shift the root's tail: this view sits in the middle of
            // the shared array; after compacting its range, the elements
            // that follow it must slide left to close the gap, or the root
            // would keep exposing stale duplicates.
            System.arraycopy(elements, to, elements, writeIdx, rootSize - to);
            // Clear the vacated slots at the root's tail for GC
            Arrays.fill(elements, rootSize - removed, rootSize, null);

            CustomArrayList.this.size = rootSize - removed;
            ++CustomArrayList.this.modCount;
            // syncChain(-removed) shrinks this view and every ancestor and
            // refreshes their modCount expectations.
            syncChain(-removed);
            isModified = true;
          }
        }

        // Fail-fast: was the list structurally modified while we were
        // running (a rogue predicate or contains() re-entering this list)?
        // +1 accounts for our own increment above.
        if (CustomArrayList.this.modCount !=
            expectedModCount + (isModified ? 1 : 0)) {
          throw new ConcurrentModificationException();
        }

        return isModified;
      }
    }

    @Override
    public T set(int index, T element) {
      synchronized (CustomArrayList.this) {
        checkIndexForAccess(index);
        checkForModification();
        return CustomArrayList.this.set(offset + index, element);
      }
    }

    @Override
    public int size() {
      synchronized (CustomArrayList.this) {
        checkForModification();
        return size;
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void sort(Comparator<? super T> comparator) {
      synchronized (CustomArrayList.this) {
        checkForModification();

        final int expectedModCount = CustomArrayList.this.modCount;
        // Sort only this view's range of the shared backing array.
        Arrays.sort((T[])CustomArrayList.this.data, offset, offset + size,
                    comparator);

        // A Comparator is user code and may re-enter the list mid-sort;
        // detect it instead of silently keeping a torn order.
        if (CustomArrayList.this.modCount != expectedModCount) {
          throw new ConcurrentModificationException();
        }

        // Reordering is observable by live iterators, so it is reported as a
        // structural change (mirrors ArrayList#sort); syncChain(0) refreshes
        // the modCount expectations of this view chain.
        CustomArrayList.this.modCount++;
        syncChain(0);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Spliterator<T> spliterator() {
      // WHY a snapshot: same rationale as CustomArrayList#spliterator — the
      // stream pipeline runs lock-free over stable data. Copying only this
      // view's range also decouples it from later root modifications.
      final Object[] snapshot;
      synchronized (CustomArrayList.this) {
        checkForModification();
        snapshot = Arrays.copyOfRange(CustomArrayList.this.data, offset,
                                      offset + size);
      }
      int characteristics = Spliterator.ORDERED | Spliterator.SIZED |
                            Spliterator.SUBSIZED | Spliterator.IMMUTABLE;
      return Spliterators.spliterator((T[])snapshot, characteristics);
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
      synchronized (CustomArrayList.this) {
        checkSubListRange(fromIndex, toIndex, size);
        checkForModification();
        return new SubList(this, fromIndex, toIndex);
      }
    }

    @Override
    public Object[] toArray() {
      // WHY an explicit override: the AbstractCollection default walks the
      // iterator, taking and releasing the monitor once per element;
      // copying the range under a single lock yields an atomic snapshot.
      synchronized (CustomArrayList.this) {
        checkForModification();
        return Arrays.copyOfRange(CustomArrayList.this.data, offset,
                                  offset + size);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T1> T1[] toArray(T1[] array) {
      Objects.requireNonNull(array);

      synchronized (CustomArrayList.this) {
        checkForModification();

        if (array.length < size) {
          // If the provided array is too small, create a new array of the
          // same runtime type and return it.
          return (T1[])Arrays.copyOfRange(CustomArrayList.this.data, offset,
                                          offset + size, array.getClass());
        }

        System.arraycopy(CustomArrayList.this.data, offset, array, 0, size);

        if (array.length > size) {
          array[size] = null;
        }

        return array;
      }
    }

    @Override
    public String toString() {
      // WHY an explicit override: the AbstractCollection default walks the
      // iterator, taking and releasing the monitor once per element — the
      // printed snapshot could then mix different points in time.
      synchronized (CustomArrayList.this) {
        checkForModification();

        StringBuilder sb = new StringBuilder();
        sb.append("CustomArrayList.SubList{");
        sb.append("offset=").append(offset).append(", ");
        sb.append("size=").append(size).append(", ");
        sb.append("data=[");
        Object[] elements = CustomArrayList.this.data;
        for (int i = offset, end = offset + size; i < end; i++) {
          sb.append(elements[i]);
          if (i < end - 1) {
            sb.append(", ");
          }
        }
        sb.append("]}");
        return sb.toString();
      }
    }
  }

  public CustomArrayList() { data = new Object[DEFAULT_CAPACITY]; }

  public CustomArrayList(int initialCapacity) {
    if (initialCapacity < 0) {
      throw new IllegalArgumentException("Illegal Capacity: " +
                                         initialCapacity);
    }
    data = new Object[initialCapacity];
  }

  public CustomArrayList(Collection<? extends T> collection) {
    Objects.requireNonNull(collection);

    Object[] elements = collection.toArray();
    size = elements.length;

    if (size == 0) {
      // Behave like the default constructor:
      // avoids growing 0 -> 1 -> 2 -> 3 -> 4 ... on first adds
      data = new Object[DEFAULT_CAPACITY];
    } else {
      // WHY normalize the runtime type: collection.toArray() may (legally)
      // return a more specific array type than Object[] (e.g. String[]),
      // which would later cause ArrayStoreException on set/add of an
      // unrelated element.
      data =
          elements.getClass() == Object[].class ? elements
                                                : Arrays.copyOf(elements, size,
                                                                Object[].class);
    }
  }

  /**
   * Shared in-place compaction core of removeIf / removeAll / retainAll.
   * Must be called with the monitor held.
   *
   * Deliberate trade-off (mirroring the JDK's ArrayList.batchRemove): if
   * the user code inside 'survives' throws, the finally block commits the
   * already-compacted prefix together with the unprocessed tail — the list
   * is left consistent but partially filtered, and the exception still
   * propagates.
   *
   * @param survives decides, per element, whether it stays in the list
   */
  private boolean retainMatching(Predicate<Object> survives) {
    final int expectedModCount = modCount;
    // WHY capture array and bound: 'survives' is user code (a predicate or
    // collection.contains) and may re-enter this list; scanning the
    // snapshot we started with keeps the compaction loop self-consistent
    // even then (the modCount check below reports the violation).
    final Object[] elements = data;
    final int end = size;

    int readIdx = 0;
    int writeIdx = 0;

    try {
      for (; readIdx < end; ++readIdx) {
        if (survives.test(elements[readIdx])) {
          elements[writeIdx++] = elements[readIdx];
        }
      }
    } finally {
      if (readIdx != end) {
        // WHY repair on early exit: the user code inside test() may throw;
        // appending the unprocessed tail after the kept prefix leaves the
        // array holding a consistent (partially filtered) sequence.
        System.arraycopy(elements, readIdx, elements, writeIdx, end - readIdx);
        writeIdx += end - readIdx;
      }

      if (writeIdx != end) {
        Arrays.fill(elements, writeIdx, end, null); // clear for GC
        size = writeIdx;
        ++modCount;
      }
    }

    // Fail-fast: was this list structurally modified while we were running
    // (a rogue predicate or contains() re-entering this list)?
    // +1 accounts for our own increment above.
    if (modCount != expectedModCount + (writeIdx != end ? 1 : 0)) {
      throw new ConcurrentModificationException();
    }

    return writeIdx != end;
  }

  private void checkIndexForAccess(int index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException("Index: " + index +
                                          ", Size: " + size);
    }
  }

  private void checkIndexForAdd(int index) {
    if (index < 0 || index > size) { // index==size is allowed for adding
                                     // elements at the end of list
      throw new IndexOutOfBoundsException("Index: " + index +
                                          ", Size: " + size);
    }
  }

  // WHY a shared static helper (root and SubList): keeps the validation
  // rules and the error message format identical for both entry points.
  private static void checkSubListRange(int fromIndex, int toIndex, int size) {
    if (fromIndex < 0 || toIndex > size || fromIndex > toIndex) {
      throw new IndexOutOfBoundsException("fromIndex: " + fromIndex +
                                          ", toIndex: " + toIndex +
                                          ", Size: " + size);
    }
  }

  // WHY a 'long' parameter: callers compute "size + n", which can overflow
  // int arithmetic long before the real limit is reached; the long parameter
  // keeps the comparison overflow-safe.
  private void grow(long minCapacity) {
    // WHY compare against data.length: the array's length is the single
    // source of truth for capacity — no redundant 'capacity' field to keep
    // in sync (one less invariant to violate).
    if (minCapacity <= data.length) {
      return;
    }

    int newCap = newCapacity(data.length, minCapacity);
    data = Arrays.copyOf(data, newCap);
  }

  private static int newCapacity(int currentCapacity, long minCapacity) {
    // WHY at least capacity + 1: for tiny capacities (0, 1) the 1.5x growth
    // step rounds down to zero/one and would not make any progress.
    long desired = Math.max((long)currentCapacity + 1,
                            (long)currentCapacity + (currentCapacity >> 1));
    long newCap = Math.max(desired, minCapacity);

    if (newCap <= MAX_ARRAY_SIZE) {
      return (int)newCap;
    }

    if (minCapacity > Integer.MAX_VALUE) {
      throw new OutOfMemoryError("Required capacity " + minCapacity +
                                 " exceeds Integer.MAX_VALUE");
    }

    // Saturate at the largest array a VM can realistically allocate instead
    // of failing requests that are only slightly above MAX_ARRAY_SIZE.
    return (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
  }

  protected synchronized void removeRange(int fromIndex, int toIndex) {
    checkSubListRange(fromIndex, toIndex, size);

    int numMoved = size - toIndex;
    System.arraycopy(data, toIndex, data, fromIndex, numMoved);

    int numElementsToClear = toIndex - fromIndex;

    // Clear the vacated slots for GC
    Arrays.fill(data, size - numElementsToClear, size, null);

    size -= numElementsToClear;
    ++modCount;
  }

  @Override
  public synchronized boolean add(T element) {
    grow(size + 1L);
    data[size++] = element;
    ++modCount;

    return true;
  }

  @Override
  public synchronized void add(int index, T element) {
    checkIndexForAdd(index);

    grow(size + 1L);
    System.arraycopy(data, index, data, index + 1, size - index);
    data[index] = element;

    ++size;
    ++modCount;
  }

  @Override
  public synchronized boolean addAll(Collection<? extends T> collection) {
    Object[] arrayToAdd = collection.toArray();
    int numElementsToAdd = arrayToAdd.length;

    if (numElementsToAdd == 0) {
      return false;
    }

    grow((long)size + numElementsToAdd);

    System.arraycopy(arrayToAdd, 0, data, size, numElementsToAdd);
    size += numElementsToAdd;
    ++modCount;
    return true;
  }

  @Override
  public synchronized boolean addAll(int index,
                                     Collection<? extends T> collection) {
    Objects.requireNonNull(collection);
    checkIndexForAdd(index);

    Object[] arrayToAdd = collection.toArray();
    int numElementsToAdd = arrayToAdd.length;

    if (numElementsToAdd == 0) {
      return false;
    }

    grow((long)size + numElementsToAdd);

    System.arraycopy(data, index, data, index + numElementsToAdd, size - index);
    System.arraycopy(arrayToAdd, 0, data, index, numElementsToAdd);

    size += numElementsToAdd;
    ++modCount;
    return true;
  }

  @Override
  public synchronized void clear() {
    Arrays.fill(data, 0, size, null);
    size = 0;
    ++modCount;
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized CustomArrayList<T> clone() {
    try {
      CustomArrayList<T> cloned = (CustomArrayList<T>)super.clone();
      // WHY copy under the monitor: super.clone() would share the backing
      // array; replacing it with a snapshot taken while holding the
      // monitor guarantees the clone observes one consistent moment.
      cloned.data = Arrays.copyOf(data, size);
      // WHY reset: the clone is a fresh list — no iterator or view observes
      // it, so its modification history starts from zero.
      cloned.modCount = 0;
      return cloned;
    } catch (CloneNotSupportedException e) {
      throw new InternalError(e);
    }
  }

  @Override
  public boolean contains(Object object) {
    return indexOf(object) >= 0; // thread-safe: indexOf holds the monitor
  }

  @Override
  public synchronized boolean containsAll(Collection<?> collection) {
    Objects.requireNonNull(collection);
    // WHY a plain loop instead of collection.stream().allMatch(...): the
    // whole check must run atomically under our monitor; the stream version
    // would re-acquire the lock for every single element.
    for (Object element : collection) {
      if (indexOf(element) < 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns an unmodifiable list containing the elements of the given
   * collection in its iteration order.
   *
   * <p>WHY the name is not copyOf: it would shadow and be confused with
   * List.copyOf, whose implementations reject null elements; this variant
   * keeps nulls and is backed by a private CustomArrayList wrapped in an
   * unmodifiable view.
   */
  public static <T> List<T>
  immutableCopyOf(Collection<? extends T> collection) {
    Objects.requireNonNull(collection);
    // The constructor copies the source in one shot; building via addAll()
    // would copy it a second time and grow the backing array piecemeal.
    return Collections.unmodifiableList(new CustomArrayList<>(collection));
  }

  public synchronized void ensureCapacity(int minCapacity) {
    grow(minCapacity);
  }

  @Override
  public synchronized boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof List<?>)) {
      return false;
    }

    List<?> otherList = (List<?>)other;

    if (size != otherList.size()) {
      return false;
    }

    // WHY branch on RandomAccess: get(i) on a linked implementation is O(n),
    // which would turn equals into O(n^2) for such lists.
    if (otherList instanceof RandomAccess) {
      for (int i = 0; i < size; ++i) {
        if (!Objects.equals(data[i], otherList.get(i))) {
          return false;
        }
      }
    } else {
      Iterator<?> otherIterator = otherList.iterator();
      for (int i = 0; i < size; ++i) {
        if (!otherIterator.hasNext() ||
            !Objects.equals(data[i], otherIterator.next())) {
          return false;
        }
      }
    }

    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized void forEach(Consumer<? super T> action) {
    Objects.requireNonNull(action);

    final int expectedModCount = modCount;
    // WHY capture array and bound: the consumer is user code and may
    // re-enter this list; the captured snapshot keeps traversal consistent
    // even then (the modCount check below reports the violation).
    final Object[] elements = data;
    final int end = size;

    for (int i = 0; i < end && modCount == expectedModCount; ++i) {
      action.accept((T)elements[i]);
    }

    // Fail-fast: was this list structurally modified while we were running?
    if (modCount != expectedModCount) {
      throw new ConcurrentModificationException();
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized T get(int index) {
    checkIndexForAccess(index);
    return (T)data[index];
  }

  @Override
  public synchronized int hashCode() {
    int result = 1;
    for (int i = 0; i < size; ++i) {
      result = 31 * result + Objects.hashCode(data[i]);
    }

    return result;
  }

  @Override
  public synchronized int indexOf(Object object) {
    for (int i = 0; i < size; ++i) {
      if (Objects.equals(data[i], object)) {
        return i;
      }
    }

    return -1;
  }

  @Override
  public synchronized boolean isEmpty() {
    return size == 0;
  }

  @Override
  public synchronized Iterator<T> iterator() {
    return new ArrayListIterator(0);
  }

  @Override
  public synchronized int lastIndexOf(Object object) {
    for (int i = size - 1; i >= 0; --i) {
      if (Objects.equals(data[i], object)) {
        return i;
      }
    }

    return -1;
  }

  @Override
  public synchronized ListIterator<T> listIterator() {
    return new ArrayListIterator(0);
  }

  @Override
  public synchronized ListIterator<T> listIterator(int index) {
    checkIndexForAdd(index);
    return new ArrayListIterator(index);
  }

  @Override
  public Stream<T> parallelStream() {
    // 'true' allows the pipeline to split the SIZED/SUBSIZED spliterator and
    // process chunks on several threads; that is safe only because the
    // snapshot never touches shared mutable state.
    return StreamSupport.stream(spliterator(), true);
  }

  @Serial
  private void readObject(ObjectInputStream istream)
      throws IOException, ClassNotFoundException {
    // WHY read and validate the size before anything else: deserialization
    // bypasses every constructor invariant, so a hostile or corrupted
    // stream could otherwise force an oversized allocation or declare a
    // size that does not match the elements that follow. Anything above
    // MAX_ARRAY_SIZE cannot be materialized as an array anyway — reject it
    // cleanly instead of dying with OutOfMemoryError.
    int newSize = istream.readInt();
    if (newSize < 0 || newSize > MAX_ARRAY_SIZE) {
      throw new InvalidObjectException("Illegal size: " + newSize);
    }

    // WHY build into a local first: if the stream turns out to be corrupt
    // mid-way, the exception propagates before any field is committed, so
    // the half-built object is never observable.
    Object[] elements = new Object[Math.max(newSize, DEFAULT_CAPACITY)];
    for (int i = 0; i < newSize; ++i) {
      elements[i] = istream.readObject();
    }

    data = elements;
    size = newSize;
    modCount = 0; // fresh object: no iterator or view can observe it yet
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized T remove(int index) {
    checkIndexForAccess(index);
    T removedElement = (T)data[index];
    int numMoved = size - index - 1; // number of elements to move after
                                     // the removed element

    if (numMoved > 0) {
      System.arraycopy(data, index + 1, data, index, numMoved);
    }

    data[--size] = null; // clear to let GC do its work
    ++modCount;

    return removedElement;
  }

  @Override
  public synchronized boolean remove(Object object) {
    // WHY synchronized here (a synchronized indexOf alone is not enough):
    // the find-then-remove pair must be atomic, otherwise a concurrent
    // remove(index) between the two calls could delete the wrong element.
    int index = indexOf(object);

    if (index == -1) {
      return false;
    }

    remove(index); // modCount is incremented in remove(int index)

    return true;
  }

  @Override
  public synchronized boolean removeAll(Collection<?> collection) {
    Objects.requireNonNull(collection);
    // WHY wrap in a predicate: removeIf, removeAll and retainAll share one
    // compaction core; only the survival rule differs.
    return retainMatching(element -> !collection.contains(element));
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized boolean removeIf(Predicate<? super T> filter) {
    Objects.requireNonNull(filter);
    return retainMatching(element -> !filter.test((T)element));
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized void replaceAll(UnaryOperator<T> operator) {
    Objects.requireNonNull(operator);

    final int expectedModCount = modCount;
    // Same capture rationale as in forEach().
    final Object[] elements = data;
    final int end = size;

    for (int i = 0; i < end && modCount == expectedModCount; ++i) {
      elements[i] = operator.apply((T)elements[i]);
    }

    // Fail-fast: was this list structurally modified while we were running?
    if (modCount != expectedModCount) {
      throw new ConcurrentModificationException();
    }
    // Element replacement (like set()) is not a structural change, so
    // modCount is intentionally left untouched.
  }

  @Override
  public synchronized boolean retainAll(Collection<?> collection) {
    Objects.requireNonNull(collection);
    return retainMatching(collection::contains);
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized T set(int index, T value) {
    checkIndexForAccess(index);
    T oldValue = (T)data[index];
    data[index] = value;
    return oldValue;
  }

  @Override
  public synchronized int size() {
    return size;
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized void sort(Comparator<? super T> comparator) {
    final int expectedModCount = modCount;
    // Sorts the live range [0, size) of the backing array in place.
    Arrays.sort((T[])data, 0, size, comparator);

    // A Comparator is user code and can re-enter this list from compare();
    // detect it instead of silently keeping a torn order.
    if (modCount != expectedModCount) {
      throw new ConcurrentModificationException();
    }

    // Reordering is observable by live iterators, so it is reported as a
    // structural modification (mirrors ArrayList#sort).
    ++modCount;
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized Spliterator<T> spliterator() {
    // WHY copy the data instead of streaming the live array: the pipeline
    // runs without holding this list's monitor (holding it would serialize
    // parallelStream() workers and buy nothing), yet it must never observe
    // a torn state. An immutable snapshot gives both: no locking during
    // traversal and no ConcurrentModificationException mid-pipeline.
    // Trade-off, as with CopyOnWriteArrayList: modifications made after
    // stream creation are not visible to the stream.
    Object[] snapshot = Arrays.copyOf(data, size);
    int characteristics = Spliterator.ORDERED | Spliterator.SIZED |
                          Spliterator.SUBSIZED | Spliterator.IMMUTABLE;
    return Spliterators.spliterator((T[])snapshot, characteristics);
  }

  @Override
  public Stream<T> stream() {
    // StreamSupport is the documented bridge from a Spliterator into the
    // stream pipeline machinery; 'false' requests sequential execution.
    // Lock-free because spliterator() already captured an immutable
    // snapshot under the monitor.
    return StreamSupport.stream(spliterator(), false);
  }

  @Override
  public synchronized List<T> subList(int fromIndex, int toIndex) {
    checkSubListRange(fromIndex, toIndex, size);
    return new SubList(fromIndex, toIndex);
  }

  @Override
  public synchronized Object[] toArray() {
    return Arrays.copyOf(data, size);
  }

  @Override
  @SuppressWarnings("unchecked")
  public synchronized<T1> T1[] toArray(T1[] array) {
    Objects.requireNonNull(array);
    if (array.length < size) {
      // If the provided array is too small, create a new array of the same
      // runtime type and return it.
      return (T1[])Arrays.copyOf(data, size, array.getClass());
    }

    System.arraycopy(data, 0, array, 0, size);

    if (array.length > size) {
      array[size] = null;
    }

    return array;
  }

  @Override
  public synchronized String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("CustomArrayList{");
    sb.append("size=").append(size).append(", ");
    sb.append("data=[");
    for (int i = 0; i < size; i++) {
      sb.append(data[i]);
      if (i < size - 1) {
        sb.append(", ");
      }
    }
    sb.append("]}");
    return sb.toString();
  }

  public synchronized void trimToSize() {
    if (size < data.length) {
      // WHY a fresh copy: arrays cannot be truncated in place; an
      // exact-size copy releases the spare slots.
      data = Arrays.copyOf(data, size);
      // WHY bump modCount: the backing array is swapped, and live fail-fast
      // iterators and views must notice (mirrors ArrayList#trimToSize).
      ++modCount;
    }
  }

  @Serial
  private synchronized void writeObject(ObjectOutputStream ostream)
      throws IOException {
    // WHY a custom form instead of defaultWriteObject: streaming the raw
    // fields would put the whole backing array (O(capacity) bytes, mostly
    // nulls) and the bookkeeping fields on the wire. Writing the size and
    // just the live elements keeps the stream minimal, while the
    // synchronized keyword guarantees they describe one consistent moment
    // in time.
    ostream.writeInt(size);
    for (int i = 0; i < size; ++i) {
      ostream.writeObject(data[i]);
    }
  }
}