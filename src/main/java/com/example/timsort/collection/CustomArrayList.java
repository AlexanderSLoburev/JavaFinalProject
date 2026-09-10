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
  // Why not Integer.MAX_VALUE: some VMs reserve header words inside array
  // allocations, so requesting the theoretical maximum reliably fails with
  // OutOfMemoryError; 8 slots of headroom is what the JDK uses as well.
  private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
  @Serial private static final long serialVersionUID = 1L;

  // Only the live elements go over the wire, never the spare capacity slots or
  // the bookkeeping fields.
  private transient Object[] data;
  private transient int size;
  private transient int modCount = 0;

  private abstract class BaseIterator {
    protected int cursor;           // next index to return
    protected int lastRet = -1;     // index of the last returned element
    protected int expectedModCount; // to detect concurrent modification

    // Why every operation below locks the root list: the iterator object
    // itself is single-threaded, but the state it reads (size, modCount,
    // the backing array) is guarded by the root's monitor.
    //
    // hasNext()/hasPrevious() only promise the state as of their own
    // call; by the time next()/previous() runs the list may have changed —
    // they revalidate under the lock and fail fast.

    /**
     * Creates a new iterator starting at {@code startCursor}.
     *
     * @param startCursor the initial cursor position
     */
    BaseIterator(int startCursor) {
      cursor = startCursor;
      expectedModCount = getModCount();
    }

    /**
     * Returns the number of structural modifications of the
     * list being iterated over.
     *
     * @return the number of structural modifications
     */
    protected final int getModCount() { return CustomArrayList.this.modCount; }

    /**
     * Returns the number of elements in the collection being
     * iterated over.
     *
     * @return the size of the collection
     */
    protected abstract int getSize();

    /**
     * Returns the element at the specified index in the
     * underlying collection.
     *
     * @param index the index of the element to return
     * @return the element at the specified index
     */
    protected abstract T getElement(int index);

    /**
     * Removes the element at the specified index from the
     * underlying collection.
     *
     * @param index the index of the element to remove
     */
    protected abstract void removeAt(int index);

    /**
     * Sets the element at the specified index in the
     * underlying collection.
     *
     * @param index the index of the element to replace
     * @param element the element to store
     */
    protected abstract void setAt(int index, T element);

    /**
     * Inserts an element at the specified index in the
     * underlying collection.
     *
     * @param index the index at which to insert the element
     * @param element the element to insert
     */
    protected abstract void addAt(int index, T element);

    /**
     * Checks whether the collection has been structurally modified
     * since this iterator was created or since the last synchronization
     * point.
     *
     * @throws ConcurrentModificationException if the collection's
     *     modCount differs from the expected value
     */
    protected final void checkForModification() {
      if (getModCount() != expectedModCount) {
        throw new ConcurrentModificationException();
      }
    }

    /**
     * Returns {@code true} if the iteration has more elements.
     *
     * @return {@code true} if there are more elements to iterate
     */
    public boolean hasNext() {
      synchronized (CustomArrayList.this) { return cursor != getSize(); }
    }

    /**
     * Returns the next element in the iteration and advances the
     * cursor.
     *
     * @return the next element
     * @throws NoSuchElementException if the iteration has no more
     *     elements
     */
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

    /**
     * Removes from the underlying collection the last element returned
     * by {@link #next()}.
     *
     * @throws IllegalStateException if {@code next()} has not yet
     *     been called, or {@code remove()} has already been called
     *     after the last call to {@code next()}
     */
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

    /**
     * Creates a new list iterator starting at {@code startCursor}.
     *
     * @param startCursor the initial cursor position
     */
    BaseListIterator(int startCursor) { super(startCursor); }

    /**
     * Inserts the specified element into the list during iteration.
     *
     * @param e the element to insert
     */
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

    /**
     * Performs the given action for each remaining element until all
     * elements have been processed or the action throws an exception.
     *
     * @param action the action to perform on each remaining element
     * @throws NullPointerException if {@code action} is {@code null}
     */
    @Override
    public void forEachRemaining(Consumer<? super T> action) {
      Objects.requireNonNull(action);

      synchronized (CustomArrayList.this) {
        checkForModification();

        while (cursor < getSize() && getModCount() == expectedModCount) {
          lastRet = cursor;
          T element = getElement(cursor);
          ++cursor;
          action.accept(element);
        }

        checkForModification();
      }
    }

    /**
     * Returns {@code true} if the list has a previous element.
     *
     * @return {@code true} if there is a previous element
     */
    @Override
    public boolean hasPrevious() {
      // No lock: 'cursor' is iterator-local state, no shared data involved.
      return cursor > 0;
    }

    /**
     * Returns the index of the element that would be returned by a
     * subsequent call to {@link #next()}.
     *
     * @return the index of the next element
     */
    @Override
    public int nextIndex() {
      return cursor;
    }

    /**
     * Returns the previous element in the list and moves the cursor
     * backward.
     *
     * @return the previous element
     * @throws NoSuchElementException if there is no previous element
     */
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

    /**
     * Returns the index of the element that would be returned by a
     * subsequent call to {@link #previous()}.
     *
     * @return the index of the previous element
     */
    @Override
    public int previousIndex() {
      return cursor - 1;
    }

    /**
     * Replaces the last element returned by {@link #next()} or
     * {@link #previous()} with the specified element.
     *
     * @param e the element to replace the last returned element with
     * @throws IllegalStateException if neither {@code next()} nor
     *     {@code previous()} has been called
     */
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
  }

  private class ArrayListIterator extends BaseListIterator {
    /**
     * Creates an iterator over the root {@code CustomArrayList}
     * starting at {@code index}.
     *
     * @param index the initial cursor position
     */
    ArrayListIterator(int index) { super(index); }

    /**
     * {@inheritDoc}
     * Returns the size of the root {@code CustomArrayList}.
     */
    @Override
    protected int getSize() {
      return CustomArrayList.this.size;
    }

    /**
     * {@inheritDoc}
     * Returns the element at the specified index in the root
     * {@code CustomArrayList}.
     *
     * @param index the index of the element to return
     * @return the element at the specified index
     */
    @Override
    @SuppressWarnings("unchecked")
    protected T getElement(int index) {
      return (T)CustomArrayList.this.data[index];
    }

    /**
     * {@inheritDoc}
     * Removes the element at the specified index from the root
     * {@code CustomArrayList}.
     *
     * @param index the index of the element to remove
     */
    @Override
    protected void removeAt(int index) {
      CustomArrayList.this.remove(index);
    }

    /**
     * {@inheritDoc}
     * Sets the element at the specified index in the root
     * {@code CustomArrayList}.
     *
     * @param index the index of the element to replace
     * @param element the element to store at the index
     */
    @Override
    protected void setAt(int index, T element) {
      CustomArrayList.this.set(index, element);
    }

    /**
     * {@inheritDoc}
     * Inserts an element at the specified index in the root
     * {@code CustomArrayList}.
     *
     * @param index the index at which to insert the element
     * @param element the element to insert
     */
    @Override
    protected void addAt(int index, T element) {
      CustomArrayList.this.add(index, element);
    }
  }

  /**
   * View of a portion of the parent list, defined by a starting index
   * (offset) and a size.
   *
   * Every operation inherited from {@code AbstractList} or {@code
   * AbstractCollection} that would otherwise run as several separate monitor
   * acquisitions is overridden here so the whole operation stays atomic.
   */
  private class SubList extends AbstractList<T> implements RandomAccess {

    // Why every method below locks CustomArrayList.this (the ROOT list)
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

      /**
       * Creates an iterator over this sublist starting at
       * {@code index}.
       *
       * @param index the initial cursor position
       */
      SubListIterator(int index) { super(index); }

      /**
       * {@inheritDoc}
       * Returns the element at the specified index in this
       * sublist's backing array.
       *
       * @param index the index of the element to return
       * @return the element at the specified index
       */
      @Override
      @SuppressWarnings("unchecked")
      protected T getElement(int index) {
        return (T)CustomArrayList.this.data[offset + index];
      }

      /**
       * {@inheritDoc}
       * Returns the number of elements in this sublist.
       *
       * @return the size of this sublist
       */
      @Override
      protected int getSize() {
        return SubList.this.size;
      }

      /**
       * {@inheritDoc}
       * Removes the element at the specified index from this
       * sublist.
       *
       * @param index the index of the element to remove
       */
      @Override
      protected void removeAt(int index) {
        SubList.this.remove(index);
      }

      /**
       * {@inheritDoc}
       * Sets the element at the specified index in this
       * sublist.
       *
       * @param index the index of the element to replace
       * @param element the element to store
       */
      @Override
      protected void setAt(int index, T element) {
        SubList.this.set(index, element);
      }

      /**
       * {@inheritDoc}
       * Inserts an element at the specified index in this
       * sublist.
       *
       * @param index the index at which to insert the element
       * @param element the element to insert
       */
      @Override
      protected void addAt(int index, T element) {
        SubList.this.add(index, element);
      }
    }

    /**
     * Creates a new root SubList view from {@code fromIndex}
     * (inclusive) to {@code toIndex} (exclusive).
     *
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
     * Creates a new SubList view of the parent sublist from {@code fromIndex}
     * (inclusive) to {@code toIndex}.
     *
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

    /**
     * Checks whether the parent list has been structurally modified
     * since this view was created or since the last synchronization
     * point.
     *
     * @throws ConcurrentModificationException if the parent list's
     *     modCount differs from the expected value
     */
    private void checkForModification() {
      if (CustomArrayList.this.modCount != expectedModCount) {
        throw new ConcurrentModificationException();
      }
    }

    /**
     * Validates the specified index for access operations
     * (get, set, remove). The index must be non-negative and
     * strictly less than the list size.
     *
     * @param index the index to validate
     * @throws IndexOutOfBoundsException if the index is negative
     *     or greater than or equal to the list size
     */
    /**
     * Validates the specified index for access operations
     * (get, set, remove). The index must be non-negative and
     * strictly less than {@code size}.
     *
     * @param index the index to validate
     * @throws IndexOutOfBoundsException if the index is negative
     *     or greater than or equal to {@code size}
     */
    private void checkIndexForAccess(int index) {
      if (index < 0 || index >= size) {
        throw new IndexOutOfBoundsException("Index: " + index +
                                            ", Size: " + size);
      }
    }

    /**
     * Validates the specified index for add operations. The index
     * must be non-negative and at most the list size
     * ({@code index == size} is allowed for appending).
     *
     * @param index the index to validate
     * @throws IndexOutOfBoundsException if the index is negative
     *     or greater than the list size
     */
    /**
     * Validates the specified index for add operations. The index
     * must be non-negative and at most {@code size}
     * ({@code index == size} is allowed for appending).
     *
     * @param index the index to validate
     * @throws IndexOutOfBoundsException if the index is negative
     *     or greater than {@code size}
     */
    private void checkIndexForAdd(int index) {
      if (index < 0 || index > size) {
        throw new IndexOutOfBoundsException("Index: " + index +
                                            ", Size: " + size);
      }
    }

    /**
     * In-place compaction of this view's range in the shared backing
     * array; the shared core of {@code removeIf} / {@code removeAll} / {@code
     * retainAll}.
     *
     * Deliberate trade-off: if the user code inside {@code survives} throws
     * (without having re-entered this list), the finally block commits the
     * already-compacted prefix together with the unprocessed tail, so the
     * list is left consistent but partially filtered while the exception
     * still propagates.
     *
     * If the user code re-enters and structurally modifies the list, the
     * violation is detected before any bookkeeping is committed: the tail
     * repair and the commit below are both skipped (the re-entrant
     * operations themselves leave the root in a consistent state), and
     * the trailing modCount check reports the violation as a
     * {@code ConcurrentModificationException}. Two re-entrant cases are
     * distinguished:
     *  - the modification triggered {@code grow()} or {@code trimToSize()) and
     * thereby replaced the root's backing array: caught by the identity check
     *    (data == elements);
     *  - the modification kept the same backing array (remove, clear,
     *    removeRange — none of them reallocates): caught by the modCount
     *    guard (modCount == expectedAtEntry).
     * In both cases syncChain() never runs, so this view chain stays
     * permanently dead instead of being resurrected with stale
     * offset/size that no later check would catch.
     *
     * Residual hazard inherent to in-place compaction: the loop's own write in
     * the very iteration whose test() re-entered can land at a stale position.
     * The loop condition limits that to at most one such write: the
     * sweep stops as soon as modCount diverges from the expected value.
     *
     * @param survives decides, per element, whether it stays in the list
     * @return {@code true} if the list was modified by this operation
     */
    private boolean retainMatching(Predicate<Object> survives) {
      synchronized (CustomArrayList.this) {
        checkForModification();

        final int expectedAtEntry = CustomArrayList.this.modCount;
        // Why capture the array, the bounds and the root's size:
        // `survives` is user code (a predicate or collection.contains)
        // and may re-enter this list; the captured values keep the
        // compaction and the tail shift below mutually consistent even
        // then.
        final Object[] elements = CustomArrayList.this.data;
        final int from = offset;
        final int to = offset + size;
        final int rootSize = CustomArrayList.this.size;

        int readIdx = from;
        int writeIdx = from;
        boolean isModified = false;

        try {
          // Why the modCount condition: stop invoking user code the
          // moment a re-entrant modification is detected (same pattern
          // as `forEach`); every further compaction write would only
          // stomp the shifted live range of the shared array.
          for (;
               readIdx < to && CustomArrayList.this.modCount == expectedAtEntry;
               ++readIdx) {
            if (survives.test(elements[readIdx])) {
              elements[writeIdx++] = elements[readIdx];
            }
          }
        } finally {
          // Why guard the repair (not just the commit): it is meaningful
          // only when the array is still the live one AND untouched by
          // foreign modifications. Into a dead (swapped-out) array the
          // write is invisible anyway; into a live array that re-entrant
          // code has shifted, copying the stale tail would only add
          // corruption on top of what the loop's own writes have
          // already done.
          if (readIdx != to && CustomArrayList.this.data == elements &&
              CustomArrayList.this.modCount == expectedAtEntry) {
            System.arraycopy(elements, readIdx, elements, writeIdx,
                             to - readIdx);
            writeIdx += to - readIdx;
          }

          final int removed = to - writeIdx;
          // Why two guards before the commit:
          //  (1) data == elements — a re-entrant add inside test() may
          //      trigger grow() and REPLACE the root's backing array;
          //      committing into the captured (now dead) array and
          //      updating the root's size from its arithmetic would
          //      leave the LIVE array exposing the wrong elements.
          //  (2) modCount == expectedAtEntry — a re-entrant structural
          //      modification that kept the same array is invisible to
          //      the identity check. Committing over the bounds captured
          //      at entry would write phantom elements into the live
          //      array, set the root's size from stale arithmetic, and
          //      let syncChain 'resurrect' this view chain as valid
          //      despite offset/size no longer matching the root — the
          //      single trailing CME would fire once and never again.
          // Skipping the commit leaves the root in the consistent state
          // the re-entrant operations produced; the trailing check
          // below still reports the violation.
          if (removed > 0 && CustomArrayList.this.data == elements &&
              CustomArrayList.this.modCount == expectedAtEntry) {
            // Why shift the root's tail: this view sits in the middle of
            // the shared array; after compacting its range, the elements
            // that follow it must slide left to close the gap, or the
            // root would keep exposing stale duplicates.
            System.arraycopy(elements, to, elements, writeIdx, rootSize - to);
            // Clear the vacated slots at the root's tail for GC
            Arrays.fill(elements, rootSize - removed, rootSize, null);

            CustomArrayList.this.size = rootSize - removed;
            ++CustomArrayList.this.modCount;
            // syncChain(-removed) shrinks this view and every ancestor
            // and refreshes their modCount expectations.
            syncChain(-removed);
            isModified = true;
          }
        }

        // Fail-fast: was the list structurally modified while we were
        // running (a rogue predicate or contains() re-entering this
        // list)? +1 accounts for our own increment above; in the
        // skipped-commit cases isModified stays false because our +1
        // never happened.
        if (CustomArrayList.this.modCount !=
            expectedAtEntry + (isModified ? 1 : 0)) {
          throw new ConcurrentModificationException();
        }

        return isModified;
      }
    }

    /**
     * Synchronizes the size of this sublist and all its parent sublists with
     * the root list after a structural modification (add or remove).
     * Must be called while holding the root list's monitor.
     *
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

    /**
     * Appends the specified element to the end of this sublist.
     *
     * @param element the element to be added
     * @return {@code true} (as specified by {@link Collection#add})
     */
    @Override
    public boolean add(T element) {
      synchronized (CustomArrayList.this) {
        checkForModification();
        CustomArrayList.this.add(offset + size, element);
        syncChain(1);
        return true;
      }
    }

    /**
     * Inserts the specified element at the specified position in
     * this sublist.
     *
     * @param index index at which the specified element is to be
     *     inserted
     * @param element the element to be inserted
     * @throws IndexOutOfBoundsException if the index is out of
     *     range {@code (index < 0 || index > size)}
     */
    @Override
    public void add(int index, T element) {
      synchronized (CustomArrayList.this) {
        checkIndexForAdd(index);
        checkForModification();

        CustomArrayList.this.add(offset + index, element);
        syncChain(1);
      }
    }

    /**
     * Appends all of the elements in the specified collection to
     * the end of this sublist, in the order that they are returned
     * by the specified collection's iterator.
     *
     * @param collection collection containing elements to be added
     * @return {@code true} if this sublist changed as a result of
     *     the call
     * @throws NullPointerException if the specified collection is
     *     {@code null}
     */
    @Override
    public boolean addAll(Collection<? extends T> collection) {
      // Why lock here instead of plain delegation: reading 'size' outside
      // the monitor could target a stale insertion index.
      synchronized (CustomArrayList.this) { return addAll(size, collection); }
    }

    /**
     * Inserts all of the elements in the specified collection into
     * this sublist at the specified position.
     *
     * @param index index at which to insert the first element from
     *     the specified collection
     * @param collection collection containing elements to be added
     * @return {@code true} if this sublist changed as a result of
     *     the call
     * @throws NullPointerException if the specified collection is
     *     {@code null}
     * @throws IndexOutOfBoundsException if the index is out of
     *     range {@code (index < 0 || index > size)}
     */
    @Override
    public boolean addAll(int index, Collection<? extends T> collection) {
      Objects.requireNonNull(collection);

      synchronized (CustomArrayList.this) {
        checkIndexForAdd(index);
        checkForModification();

        final int expectedBefore = CustomArrayList.this.modCount;
        final int sizeBefore = CustomArrayList.this.size;

        boolean added = CustomArrayList.this.addAll(offset + index, collection);
        if (CustomArrayList.this.modCount != expectedBefore + (added ? 1 : 0)) {
          throw new ConcurrentModificationException();
        }

        if (!added) {
          return false;
        }

        syncChain(CustomArrayList.this.size - sizeBefore);
        return true;
      }
    }

    /**
     * Removes all of the elements from this sublist. The sublist
     * will be empty after this call returns.
     */
    @Override
    public void clear() {
      synchronized (CustomArrayList.this) {
        removeRange(0, size); // reentrantly re-locks; no deadlock
      }
    }

    /**
     * Returns {@code true} if this sublist contains the specified
     * element.
     *
     * @param object element whose presence in this sublist is to
     *     be tested
     * @return {@code true} if this sublist contains the specified
     *     element
     */
    @Override
    public boolean contains(Object object) {
      return indexOf(object) >= 0; // thread-safe: indexOf holds the monitor
    }

    /**
     * Returns {@code true} if this sublist contains all of the
     * elements in the specified collection.
     *
     * @param collection collection to be checked for containment
     * @return {@code true} if this sublist contains all elements
     *     in the specified collection
     * @throws NullPointerException if the specified collection is
     *     {@code null}
     */
    @Override
    public boolean containsAll(Collection<?> collection) {
      Objects.requireNonNull(collection);

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

    /**
     * Compares the specified object with this sublist for equality.
     *
     * @param object the object to be compared for equality
     * @return {@code true} if the specified object is equal to this
     *     sublist
     */
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

      synchronized (CustomArrayList.this) {
        checkForModification();

        final int expectedRootModCount = CustomArrayList.this.modCount;
        final Object[] elements = CustomArrayList.this.data;
        final int end = offset + size;

        for (int i = offset;
             i < end && CustomArrayList.this.modCount == expectedRootModCount;
             ++i) {
          action.accept((T)elements[i]);
        }

        if (CustomArrayList.this.modCount != expectedRootModCount) {
          throw new ConcurrentModificationException();
        }
      }
    }

    /**
     * Returns the element at the specified position in this sublist.
     *
     * @param index index of the element to return
     * @return the element at the specified position
     * @throws IndexOutOfBoundsException if the index is out of
     *     range {@code (index < 0 || index >= size)}
     */
    @Override
    public T get(int index) {
      synchronized (CustomArrayList.this) {
        checkIndexForAccess(index);
        checkForModification();
        return CustomArrayList.this.get(offset + index);
      }
    }

    /**
     * Returns the hash code value for this sublist.
     *
     * @return the hash code value for this sublist
     */
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

    /**
     * Returns the index of the first occurrence of the specified
     * element in this sublist, or {@code -1} if this sublist does
     * not contain the element.
     *
     * @param object element to search for
     * @return the index of the first occurrence, or {@code -1} if
     *     not found
     */
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

    /**
     * Returns an iterator over the elements in this sublist, in
     * proper sequence.
     *
     * @return an iterator over the elements in this sublist
     */
    @Override
    public Iterator<T> iterator() {
      return listIterator(
          0); // thread-safe: listIterator(int) holds the monitor
    }

    /**
     * Returns the index of the last occurrence of the specified
     * element in this sublist, or {@code -1} if this sublist does
     * not contain the element.
     *
     * @param object element to search for
     * @return the index of the last occurrence, or {@code -1} if
     *     not found
     */
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

    /**
     * Returns a list iterator over the elements in this sublist,
     * in proper sequence.
     *
     * @return a list iterator over the elements in this sublist
     */
    @Override
    public ListIterator<T> listIterator() {
      return listIterator(0);
    }

    /**
     * Returns a list iterator over the elements in this sublist,
     * starting at the specified position.
     *
     * @param index index of the first element to be returned from
     *     the list iterator
     * @return a list iterator over the elements in this sublist
     * @throws IndexOutOfBoundsException if the index is out of
     *     range {@code (index < 0 || index > size)}
     */
    @Override
    public ListIterator<T> listIterator(int index) {
      synchronized (CustomArrayList.this) {
        checkIndexForAdd(index);
        checkForModification();
        return new SubListIterator(index);
      }
    }

    /**
     * Removes the first occurrence of the specified element from
     * this sublist, if it is present.
     *
     * @param object the element to be removed
     * @return {@code true} if this sublist contained the specified
     *     element
     * @throws ConcurrentModificationException if the root list was
     *     structurally modified from inside the element's
     *     {@code equals(Object)} during this call
     */
    @Override
    public boolean remove(Object object) {
      synchronized (CustomArrayList.this) {
        checkForModification();

        // Why capture the root's modCount: equals() is user code and may
        // re-enter this list on the same thread. Without re-validation
        // the removal could target an index shifted by that re-entrant
        // modification, and syncChain would then 'resurrect' this view
        // chain as valid with stale offset/size — no
        // ConcurrentModificationException would ever fire again.
        final int expectedAtEntry = CustomArrayList.this.modCount;

        for (int i = offset; i < offset + size; ++i) {
          boolean found = Objects.equals(CustomArrayList.this.data[i], object);

          if (CustomArrayList.this.modCount != expectedAtEntry) {
            throw new ConcurrentModificationException();
          }

          if (found) {
            CustomArrayList.this.remove(i);
            syncChain(-1);
            return true;
          }
        }

        return false;
      }
    }

    /**
     * Removes the element at the specified position in this
     * sublist.
     *
     * @param index the index of the element to be removed
     * @return the element that was removed
     * @throws IndexOutOfBoundsException if the index is out of
     *     range {@code (index < 0 || index >= size)}
     */
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

    /**
     * Removes from this sublist all of its elements that are
     * contained in the specified collection.
     *
     * @param collection collection containing elements to be
     *     removed
     * @return {@code true} if this sublist changed as a result of
     *     the call
     * @throws NullPointerException if the specified collection is
     *     {@code null}
     */
    @Override
    public boolean removeAll(Collection<?> collection) {
      Objects.requireNonNull(collection);

      return retainMatching(element -> !collection.contains(element));
    }

    /**
     * Removes each element of this sublist that satisfies the
     * given predicate.
     *
     * @param filter a predicate returning {@code true} for elements
     *     to be removed
     * @return {@code true} if any elements were removed
     * @throws NullPointerException if {@code filter} is {@code null}
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean removeIf(Predicate<? super T> filter) {
      Objects.requireNonNull(filter);

      return retainMatching(element -> !filter.test((T)element));
    }

    /**
     * Removes from this sublist all elements whose index is between
     * {@code fromIndex}, inclusive, and {@code toIndex}, exclusive.
     *
     * @param fromIndex the index of the first element to be removed
     * @param toIndex the index after the last element to be removed
     */
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

    /**
     * Replaces each element of this sublist with the result of
     * applying the given operator to that element.
     *
     * @param operator the operator to apply to each element
     * @throws NullPointerException if {@code operator} is {@code null}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void replaceAll(UnaryOperator<T> operator) {
      Objects.requireNonNull(operator);

      synchronized (CustomArrayList.this) {
        checkForModification();

        final int expectedRootModCount = CustomArrayList.this.modCount;
        final Object[] elements = CustomArrayList.this.data;
        final int end = offset + size;

        for (int i = offset;
             i < end && CustomArrayList.this.modCount == expectedRootModCount;
             ++i) {
          elements[i] = operator.apply((T)elements[i]);
        }

        if (CustomArrayList.this.modCount != expectedRootModCount) {
          throw new ConcurrentModificationException();
        }
      }
    }

    /**
     * Retains only the elements in this sublist that are contained
     * in the specified collection.
     *
     * @param collection collection containing elements to be
     *     retained
     * @return {@code true} if this sublist changed as a result of
     *     the call
     * @throws NullPointerException if the specified collection is
     *     {@code null}
     */
    @Override
    public boolean retainAll(Collection<?> collection) {
      Objects.requireNonNull(collection);
      return retainMatching(collection::contains);
    }

    /**
     * Replaces the element at the specified position in this
     * sublist with the specified element.
     *
     * @param index index of the element to replace
     * @param element element to be stored at the specified position
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException if the index is out of
     *     range {@code (index < 0 || index >= size)}
     */
    @Override
    public T set(int index, T element) {
      synchronized (CustomArrayList.this) {
        checkIndexForAccess(index);
        checkForModification();
        return CustomArrayList.this.set(offset + index, element);
      }
    }

    /**
     * Returns the number of elements in this sublist.
     *
     * @return the number of elements in this sublist
     */
    @Override
    public int size() {
      synchronized (CustomArrayList.this) {
        checkForModification();
        return size;
      }
    }

    /**
     * Sorts this sublist according to the order induced by the specified
     * comparator.
     *
     * <p>A {@code null} comparator indicates that the elements'
     * <i>natural ordering</i> should be used, as specified by
     * {@link List#sort(Comparator)}: {@code Arrays.sort} delegates to its
     * {@code Comparable}-based overload, so elements that do not
     * implement {@code Comparable} fail with {@link ClassCastException},
     * not with a {@code NullPointerException}.
     *
     * @param comparator the comparator to determine the order, or
     *     {@code null} to use the elements' natural ordering
     * @throws ClassCastException if this sublist contains elements that
     *     are not mutually comparable using the specified comparator
     */
    @Override
    @SuppressWarnings("unchecked")
    public void sort(Comparator<? super T> comparator) {
      synchronized (CustomArrayList.this) {
        checkForModification();

        final int expectedRootModCount = CustomArrayList.this.modCount;
        Arrays.sort((T[])CustomArrayList.this.data, offset, offset + size,
                    comparator);

        // A Comparator is user code and may re-enter the list mid-sort;
        // detect it instead of silently keeping a torn order.
        if (CustomArrayList.this.modCount != expectedRootModCount) {
          throw new ConcurrentModificationException();
        }

        // Reordering is observable by live iterators, so it is reported
        // as a structural change (mirrors ArrayList#sort); syncChain(0)
        // refreshes the modCount expectations of this view chain.
        ++CustomArrayList.this.modCount;
        syncChain(0);
      }
    }

    /**
     * Returns a {@link Spliterator} over the elements in this
     * sublist.
     *
     * @return a {@code Spliterator} over the elements in this
     *     sublist
     */
    @Override
    @SuppressWarnings("unchecked")
    public Spliterator<T> spliterator() {
      // Why a snapshot: same rationale as CustomArrayList#spliterator — the
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

    /**
     * Returns a view of the portion of this sublist between
     * {@code fromIndex}, inclusive, and {@code toIndex}, exclusive.
     *
     * @param fromIndex low endpoint (inclusive) of the subview
     * @param toIndex high endpoint (exclusive) of the subview
     * @return a view of the specified range within this sublist
     * @throws IndexOutOfBoundsException if an endpoint index value
     *     is out of range
     */
    @Override
    public List<T> subList(int fromIndex, int toIndex) {
      synchronized (CustomArrayList.this) {
        checkSubListRange(fromIndex, toIndex, size);
        checkForModification();
        return new SubList(this, fromIndex, toIndex);
      }
    }

    /**
     * Returns an array containing all of the elements in this
     * sublist, in proper sequence.
     *
     * @return an array containing all of the elements in this
     *     sublist
     */
    @Override
    public Object[] toArray() {
      synchronized (CustomArrayList.this) {
        checkForModification();
        return Arrays.copyOfRange(CustomArrayList.this.data, offset,
                                  offset + size);
      }
    }

    /**
     * Returns an array containing all of the elements in this
     * sublist, in proper sequence; the runtime type of the returned
     * array is that of the specified array.
     *
     * @param array the array into which the elements of this
     *     sublist are to be stored
     * @return an array containing the elements of this sublist
     * @throws NullPointerException if the specified array is
     *     {@code null}
     */
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

    /**
     * Returns a string representation of this sublist, in the standard
     * List format {@code [e1, e2, ...]}.
     *
     * @return a string representation of this sublist
     */
    @Override
    public String toString() {
      synchronized (CustomArrayList.this) {
        checkForModification();

        // Why capture the array once: an element's toString() is user
        // code and may re-enter the list; re-reading data[i] each
        // iteration could then mix the old and the new backing array.
        final Object[] elements = CustomArrayList.this.data;

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = offset, end = offset + size; i < end; i++) {
          sb.append(elements[i]);
          if (i < end - 1) {
            sb.append(", ");
          }
        }
        sb.append(']');
        return sb.toString();
      }
    }
  }

  /**
   * Creates an empty {@code CustomArrayList} with the default
   * initial capacity.
   */
  public CustomArrayList() { data = new Object[DEFAULT_CAPACITY]; }

  /**
   * Creates an empty {@code CustomArrayList} with the specified
   * initial capacity.
   *
   * @param initialCapacity the initial capacity of the list
   * @throws IllegalArgumentException if {@code initialCapacity}
   *     is negative
   */
  public CustomArrayList(int initialCapacity) {
    if (initialCapacity < 0) {
      throw new IllegalArgumentException("Illegal Capacity: " +
                                         initialCapacity);
    }
    data = new Object[initialCapacity];
  }

  /**
   * Creates a {@code CustomArrayList} containing the elements of
   * the specified collection, in the order they are returned by
   * the collection's iterator.
   *
   * @param collection the collection whose elements are to be
   *     placed into this list
   * @throws NullPointerException if the specified collection is
   *     {@code null}
   */
  public CustomArrayList(Collection<? extends T> collection) {
    Objects.requireNonNull(collection);

    Object[] elements = collection.toArray();
    size = elements.length;

    if (size == 0) {
      data = new Object[DEFAULT_CAPACITY];
    } else {
      // Why normalize the runtime type: collection.toArray() may (legally)
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
   * Deliberate trade-off: if the user code inside {@code survives} throws
   * (without having re-entered this list), the finally block commits the
   * already-compacted prefix together with the unprocessed tail — the list is
   * left consistent but partially filtered, and the exception still propagates.
   *
   * If {@code survives} re-enters and structurally modifies this list, the
   * violation is detected before any bookkeeping is committed: the tail
   * repair and the commit are both skipped, the list stays in the
   * consistent state the re-entrant operations left it in, and the
   * trailing modCount check reports the violation as a
   * {@code ConcurrentModificationException}. Two re-entrant cases are
   * distinguished:
   *  - the modification kept the same backing array (remove, clear,
   *    removeRange — none of them reallocates): caught by the modCount
   *    guard (modCount == expectedModCount);
   *  - the modification replaced the backing array (grow() via
   *    add/addAll/ensureCapacity, or trimToSize()): caught by the
   *    identity check (data == elements). Every such swap also bumps
   *    modCount, so the modCount guard alone would catch it as well —
   *    the identity check is kept because it documents the intent.
   *
   * @param survives decides, per element, whether it stays in the list
   */
  private boolean retainMatching(Predicate<Object> survives) {
    final int expectedModCount = modCount;
    final Object[] elements = data;
    final int end = size;

    int readIdx = 0;
    int writeIdx = 0;
    boolean modified = false;

    try {
      for (; readIdx < end && modCount == expectedModCount; ++readIdx) {
        if (survives.test(elements[readIdx])) {
          elements[writeIdx++] = elements[readIdx];
        }
      }
    } finally {
      // Why guard the repair (not just the commit): it is meaningful
      // only when the array is still the live one AND untouched by
      // foreign modifications. Into a dead (swapped-out) array the
      // write is invisible anyway; into a live array that re-entrant
      // code has shifted, copying the stale tail would only add
      // corruption on top of what the loop's own writes have already
      // done.
      if (readIdx != end && data == elements && modCount == expectedModCount) {
        System.arraycopy(elements, readIdx, elements, writeIdx, end - readIdx);
        writeIdx += end - readIdx;
      }

      if (writeIdx != end && data == elements && modCount == expectedModCount) {
        Arrays.fill(elements, writeIdx, end, null); // clear for GC
        size = writeIdx;
        ++modCount;
        modified = true;
      }
    }

    // Fail-fast: was the list structurally modified while we were
    // running (a rogue predicate or contains() re-entering this list)?
    // +1 accounts for our own increment above — but only if it happened.
    if (modCount != expectedModCount + (modified ? 1 : 0)) {
      throw new ConcurrentModificationException();
    }

    return modified;
  }

  /**
   * Validates the specified index for access operations
   * (get, set, remove). The index must be non-negative and
   * strictly less than {@code size}.
   *
   * @param index the index to validate
   * @throws IndexOutOfBoundsException if the index is negative
   *     or greater than or equal to {@code size}
   */
  private void checkIndexForAccess(int index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException("Index: " + index +
                                          ", Size: " + size);
    }
  }

  /**
   * Validates the specified index for add operations. The index
   * must be non-negative and at most {@code size}
   * ({@code index == size} is allowed for appending).
   *
   * @param index the index to validate
   * @throws IndexOutOfBoundsException if the index is negative
   *     or greater than {@code size}
   */
  private void checkIndexForAdd(int index) {
    if (index < 0 || index > size) { // index==size is allowed for adding
                                     // elements at the end of list
      throw new IndexOutOfBoundsException("Index: " + index +
                                          ", Size: " + size);
    }
  }

  /**
   * Validates the range bounds for a {@code subList} or
   * {@code removeRange} operation.
   *
   * @param fromIndex the starting index (inclusive)
   * @param toIndex the ending index (exclusive)
   * @param size the size of the list against which to validate
   * @throws IndexOutOfBoundsException if the range is invalid
   */
  private static void checkSubListRange(int fromIndex, int toIndex, int size) {
    if (fromIndex < 0 || toIndex > size || fromIndex > toIndex) {
      throw new IndexOutOfBoundsException("fromIndex: " + fromIndex +
                                          ", toIndex: " + toIndex +
                                          ", Size: " + size);
    }
  }

  /**
   * Builds the bounds-violation exception for {@code copyRange} with all
   * argument values, in the style of {@code checkIndexForAccess}.
   */
  private IndexOutOfBoundsException
  copyRangeBoundsError(int srcIndex, int destIndex, int length) {
    return new IndexOutOfBoundsException(
        "srcIndex: " + srcIndex + ", destIndex: " + destIndex +
        ", length: " + length + ", size: " + size);
  }

  /**
   * Grows the backing array if necessary to accommodate
   * {@code minCapacity} elements. If the current capacity is
   * sufficient, returns {@code false}; otherwise reallocates
   * and returns {@code true}.
   *
   * @param minCapacity the minimum required capacity
   * @return {@code true} if the backing array was reallocated
   */
  private boolean grow(long minCapacity) {
    if (minCapacity <= data.length) {
      return false;
    }

    int newCap = newCapacity(data.length, minCapacity);
    data = Arrays.copyOf(data, newCap);
    return true;
  }

  /**
   * Computes the new capacity for the backing array given the
   * current capacity and the minimum required capacity.
   *
   * @param currentCapacity the current capacity of the backing
   *     array
   * @param minCapacity the minimum capacity required
   * @return the new capacity value
   */
  private static int newCapacity(int currentCapacity, long minCapacity) {
    // Why at least capacity + 1: for tiny capacities (0, 1) the 1.5x growth
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

    return (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
  }

  /**
   * Removes from the list all elements whose index is between
   * {@code fromIndex}, inclusive, and {@code toIndex},
   * exclusive. Shifts any surviving elements left and clears
   * vacated slots for garbage collection.
   *
   * @param fromIndex the index of the first element to be
   *     removed
   * @param toIndex the index after the last element to be
   *     removed
   */
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

  /**
   * Appends the specified element to the end of this list.
   *
   * @param element the element to be appended
   * @return {@code true} (as specified by {@code Collection.add})
   */
  @Override
  public synchronized boolean add(T element) {
    grow(size + 1L);
    data[size++] = element;
    ++modCount;

    return true;
  }

  /**
   * Inserts the specified element at the specified position in
   * this list.
   *
   * @param index index at which the specified element is to be
   *     inserted
   * @param element the element to be inserted
   * @throws IndexOutOfBoundsException if the index is out of
   *     range {@code (index < 0 || index > size)}
   */
  @Override
  public synchronized void add(int index, T element) {
    checkIndexForAdd(index);

    grow(size + 1L);
    System.arraycopy(data, index, data, index + 1, size - index);
    data[index] = element;

    ++size;
    ++modCount;
  }

  /**
   * Appends all of the elements in the specified collection to
   * the end of this list, in the order that they are returned
   * by the specified collection's iterator.
   *
   * @param collection collection containing elements to be added
   * @return {@code true} if this list changed as a result of
   *     the call
   * @throws NullPointerException if the specified collection is
   *     {@code null}
   */
  @Override
  public synchronized boolean addAll(Collection<? extends T> collection) {
    Objects.requireNonNull(collection);
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

  /**
   * Inserts all of the elements in the specified collection into
   * this list at the specified position.
   *
   * @param index index at which to insert the first element from
   *     the specified collection
   * @param collection collection containing elements to be added
   * @return {@code true} if this list changed as a result of
   *     the call
   * @throws NullPointerException if the specified collection is
   *     {@code null}
   * @throws IndexOutOfBoundsException if the index is out of
   *     range {@code (index < 0 || index > size)}
   */
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

  /**
   * Removes all of the elements from this list. The list will
   * be empty after this call returns.
   */
  @Override
  public synchronized void clear() {
    Arrays.fill(data, 0, size, null);
    size = 0;
    ++modCount;
  }

  /**
   * Returns a shallow copy of this {@code CustomArrayList}.
   * The cloned list has its own independent backing array
   * containing a snapshot of the elements at the time of the
   * call, and its modification history starts from zero.
   *
   * @return a clone of this {@code CustomArrayList}
   */
  @Override
  @SuppressWarnings("unchecked")
  public synchronized CustomArrayList<T> clone() {
    try {
      CustomArrayList<T> cloned = (CustomArrayList<T>)super.clone();
      cloned.data = Arrays.copyOf(data, size);
      cloned.modCount = 0;
      return cloned;
    } catch (CloneNotSupportedException e) {
      throw new InternalError(e);
    }
  }

  /**
   * Returns {@code true} if this list contains the specified
   * element.
   *
   * @param object element whose presence in this list is to
   *     be tested
   * @return {@code true} if this list contains the specified
   *     element
   */
  @Override
  public boolean contains(Object object) {
    return indexOf(object) >= 0; // thread-safe: indexOf holds the monitor
  }

  /**
   * Returns {@code true} if this list contains all of the
   * elements in the specified collection.
   *
   * @param collection collection to be checked for containment
   * @return {@code true} if this list contains all elements
   *     in the specified collection
   * @throws NullPointerException if the specified collection is
   *     {@code null}
   */
  @Override
  public synchronized boolean containsAll(Collection<?> collection) {
    Objects.requireNonNull(collection);
    for (Object element : collection) {
      if (indexOf(element) < 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Copies {@code length} elements of {@code source}, starting at
   * {@code srcIndex}, onto this list's range starting at
   * {@code destIndex} — the list-level analogue of
   * {@code System.arraycopy(src, srcPos, dest, destPos, length)} with
   * this list as the destination.
   *
   * The destination range must already exist: the method never changes
   * the list's size and never grows the backing array. Every copy behaves
   * as if the source range were first copied into a temporary array: when
   * {@code source} is this list (or a live view of it), overlapping ranges
   * shift correctly in both directions.
   *
   * Overwriting and reordering elements is observable by live iterators
   * and views, so a copy that may change content bumps the modification
   * count; a zero-length copy and a self-copy onto
   * the identical range are no-ops and leave it untouched.
   *
   * @param source the list to copy from; may be this list itself
   * @param srcIndex the starting index (inclusive) of the source range
   * @param destIndex the starting index (inclusive) of the destination
   *     range in this list
   * @param length the number of elements to copy
   * @throws NullPointerException if {@code source} is {@code null}
   * @throws IndexOutOfBoundsException if an index is negative, or the
   *     source or destination range extends beyond the respective list's
   *     size
   * @throws ConcurrentModificationException if the source snapshot code
   *     re-enters and structurally modifies this list
   */
  public synchronized void copyRange(List<? extends T> source, int srcIndex,
                                     int destIndex, int length) {
    Objects.requireNonNull(source);

    if (srcIndex < 0 || destIndex < 0 || length < 0) {
      throw copyRangeBoundsError(srcIndex, destIndex, length);
    }

    if (destIndex > size - length) {
      throw copyRangeBoundsError(srcIndex, destIndex, length);
    }

    if (source == this) {
      if (srcIndex > size - length) {
        throw copyRangeBoundsError(srcIndex, destIndex, length);
      }

      if (length > 0 && srcIndex != destIndex) {
        System.arraycopy(data, srcIndex, data, destIndex, length);
        ++modCount;
      }
      return;
    }

    // Foreign source: snapshot its range first, then write.
    if (srcIndex > Integer.MAX_VALUE - length) {
      throw copyRangeBoundsError(srcIndex, destIndex, length);
    }

    final int expectedModCount = modCount;
    // Why a snapshot: a live iteration of
    // the source would race with our own writes when the source is a
    // view of this very list — reading back slots we have just
    // overwritten.
    Object[] snapshot = source.subList(srcIndex, srcIndex + length).toArray();

    if (snapshot.length != length) {
      // The source changed between subList() and toArray() (or is not a
      // well-behaved List): the payload no longer matches the promised
      // range — refuse to write it.
      throw new ConcurrentModificationException();
    }

    if (modCount != expectedModCount) {
      throw new ConcurrentModificationException();
    }

    if (length > 0) {
      System.arraycopy(snapshot, 0, data, destIndex, length);
      ++modCount;
    }
  }

  /**
   * Copies {@code length} elements of this list from the range starting
   * at {@code srcIndex} onto the range starting at {@code destIndex}.
   * Convenience overload of
   * {@link #copyRange(List, int, int, int) copyRange(this, ...)}.
   *
   * @param srcIndex the starting index (inclusive) of the source range
   * @param destIndex the starting index (inclusive) of the destination
   *     range
   * @param length the number of elements to copy
   * @throws IndexOutOfBoundsException if an index is negative, or the
   *     source or destination range extends beyond this list's size
   */
  public synchronized void copyRange(int srcIndex, int destIndex, int length) {
    copyRange(this, srcIndex, destIndex, length);
  }

  /**
   * Returns an unmodifiable list containing the elements of the given
   * collection in its iteration order.
   *
   * <p>Why the name is not copyOf: it would shadow and be confused with
   * List.copyOf, whose implementations reject null elements; this variant
   * keeps nulls and is backed by a private CustomArrayList wrapped in an
   * unmodifiable view.
   */
  public static <T> List<T>
  immutableCopyOf(Collection<? extends T> collection) {
    Objects.requireNonNull(collection);

    return Collections.unmodifiableList(new CustomArrayList<>(collection));
  }

  /**
   * Increases the capacity of this {@code CustomArrayList},
   * if necessary, to ensure that it can hold at least
   * {@code minCapacity} elements. If the backing array is
   * reallocated, the modification count is incremented.
   *
   * @param minCapacity the desired minimum capacity
   */
  public synchronized void ensureCapacity(int minCapacity) {
    if (grow(minCapacity)) {
      ++modCount;
    }
  }

  /**
   * Compares the specified object with this list for equality.
   *
   * @param other the object to be compared for equality
   * @return {@code true} if the specified object is equal to
   *     this list
   */
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

  /**
   * Performs the given action for each element of this list,
   * in the order elements are iterated, until all elements have
   * been processed or the action throws an exception.
   *
   * @param action the action to perform on each element
   * @throws NullPointerException if {@code action} is
   *     {@code null}
   */
  @Override
  @SuppressWarnings("unchecked")
  public synchronized void forEach(Consumer<? super T> action) {
    Objects.requireNonNull(action);

    final int expectedModCount = modCount;
    final Object[] elements = data;
    final int end = size;

    for (int i = 0; i < end && modCount == expectedModCount; ++i) {
      action.accept((T)elements[i]);
    }

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

  /**
   * Returns the hash code value for this list.
   *
   * @return the hash code value for this list
   */
  @Override
  public synchronized int hashCode() {
    int result = 1;
    for (int i = 0; i < size; ++i) {
      result = 31 * result + Objects.hashCode(data[i]);
    }

    return result;
  }

  /**
   * Returns the index of the first occurrence of the specified
   * element in this list, or {@code -1} if this list does not
   * contain the element.
   *
   * @param object element to search for
   * @return the index of the first occurrence, or {@code -1}
   *     if not found
   */
  @Override
  public synchronized int indexOf(Object object) {
    for (int i = 0; i < size; ++i) {
      if (Objects.equals(data[i], object)) {
        return i;
      }
    }

    return -1;
  }

  /**
   * Returns {@code true} if this list contains no elements.
   *
   * @return {@code true} if this list contains no elements
   */
  @Override
  public synchronized boolean isEmpty() {
    return size == 0;
  }

  /**
   * Returns an iterator over the elements in this list, in
   * proper sequence.
   *
   * @return an iterator over the elements in this list
   */
  @Override
  public synchronized Iterator<T> iterator() {
    return new ArrayListIterator(0);
  }

  /**
   * Returns the index of the last occurrence of the specified
   * element in this list, or {@code -1} if this list does not
   * contain the element.
   *
   * @param object element to search for
   * @return the index of the last occurrence, or {@code -1}
   *     if not found
   */
  @Override
  public synchronized int lastIndexOf(Object object) {
    for (int i = size - 1; i >= 0; --i) {
      if (Objects.equals(data[i], object)) {
        return i;
      }
    }

    return -1;
  }

  /**
   * Returns a list iterator over the elements in this list,
   * in proper sequence.
   *
   * @return a list iterator over the elements in this list
   */
  @Override
  public synchronized ListIterator<T> listIterator() {
    return new ArrayListIterator(0);
  }

  /**
   * Returns a list iterator over the elements in this list,
   * starting at the specified position.
   *
   * @param index index of the first element to be returned from
   *     the list iterator
   * @return a list iterator over the elements in this list
   * @throws IndexOutOfBoundsException if the index is out of
   *     range {@code (index < 0 || index > size)}
   */
  @Override
  public synchronized ListIterator<T> listIterator(int index) {
    checkIndexForAdd(index);
    return new ArrayListIterator(index);
  }

  /**
   * Returns a parallel {@link Stream} over the elements in
   * this list. The stream traverses an immutable snapshot taken
   * under the list's lock, so the pipeline requires no external
   * synchronization.
   *
   * @return a parallel {@code Stream} over the elements in
   *     this list
   */
  @Override
  public Stream<T> parallelStream() {
    // 'true' allows the pipeline to split the SIZED/SUBSIZED spliterator and
    // process chunks on several threads; that is safe only because the
    // snapshot never touches shared mutable state.
    return StreamSupport.stream(spliterator(), true);
  }

  /**
   * Deserializes a {@code CustomArrayList} from the specified
   * stream. Reads the size first, validates it, then restores
   * the elements and resets the modification count to zero.
   *
   * @param istream the stream from which to deserialize
   * @throws IOException if an I/O error occurs
   * @throws ClassNotFoundException if the class of a serialized
   *     object cannot be found
   */
  @Serial
  private void readObject(ObjectInputStream istream)
      throws IOException, ClassNotFoundException {
    // Why read and validate the size before anything else: deserialization
    // bypasses every constructor invariant, so a hostile or corrupted
    // stream could otherwise force an oversized allocation or declare a
    // size that does not match the elements that follow. Anything above
    // MAX_ARRAY_SIZE cannot be materialized as an array anyway — reject it
    // cleanly instead of dying with OutOfMemoryError.
    int newSize = istream.readInt();
    if (newSize < 0 || newSize > MAX_ARRAY_SIZE) {
      throw new InvalidObjectException("Illegal size: " + newSize);
    }

    // Why build into a local first: if the stream turns out to be corrupt
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

  /**
   * Removes the element at the specified position in this
   * list. Shifts any surviving elements left.
   *
   * @param index the index of the element to be removed
   * @return the element that was removed
   * @throws IndexOutOfBoundsException if the index is out of
   *     range {@code (index < 0 || index >= size)}
   */
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

  /**
   * Removes the first occurrence of the specified element from
   * this list, if it is present.
   *
   * @param object the element to be removed
   * @return {@code true} if this list contained the specified
   *     element
   * @throws ConcurrentModificationException if the list was structurally
   *     modified from inside the element's {@code equals(Object)} during
   *     this call
   */
  @Override
  public synchronized boolean remove(Object object) {
    // Why synchronized here (a synchronized indexOf alone is not
    // enough): the find-then-remove pair must be atomic, otherwise a
    // concurrent remove(index) between the two calls could delete the
    // wrong element.
    //
    // Why re-validate after indexOf(): the equals() calls inside it are
    // user code and may re-enter this list on the same thread and shift
    // elements — the index returned by indexOf would then point at the
    // wrong element.
    final int expectedAtEntry = modCount;

    int index = indexOf(object);

    if (modCount != expectedAtEntry) {
      throw new ConcurrentModificationException();
    }

    if (index == -1) {
      return false;
    }

    remove(index); // modCount is incremented in remove(int index)

    return true;
  }

  /**
   * Removes from this list all of its elements that are
   * contained in the specified collection.
   *
   * @param collection collection containing elements to be
   *     removed
   * @return {@code true} if this list changed as a result of
   *     the call
   * @throws NullPointerException if the specified collection is
   *     {@code null}
   */
  @Override
  public synchronized boolean removeAll(Collection<?> collection) {
    Objects.requireNonNull(collection);

    return retainMatching(element -> !collection.contains(element));
  }

  /**
   * Removes each element of this list that satisfies the
   * given predicate.
   *
   * @param filter a predicate returning {@code true} for elements
   *     to be removed
   * @return {@code true} if any elements were removed
   * @throws NullPointerException if {@code filter} is
   *     {@code null}
   */
  @Override
  @SuppressWarnings("unchecked")
  public synchronized boolean removeIf(Predicate<? super T> filter) {
    Objects.requireNonNull(filter);

    return retainMatching(element -> !filter.test((T)element));
  }

  /**
   * Replaces each element of this list with the result of
   * applying the given operator to that element.
   *
   * @param operator the operator to apply to each element
   * @throws NullPointerException if {@code operator} is
   *     {@code null}
   */
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

    if (modCount != expectedModCount) {
      throw new ConcurrentModificationException();
    }
  }

  /**
   * Retains only the elements in this list that are contained
   * in the specified collection.
   *
   * @param collection collection containing elements to be
   *     retained
   * @return {@code true} if this list changed as a result of
   *     the call
   * @throws NullPointerException if the specified collection is
   *     {@code null}
   */
  @Override
  public synchronized boolean retainAll(Collection<?> collection) {
    Objects.requireNonNull(collection);

    return retainMatching(collection::contains);
  }

  /**
   * Replaces the element at the specified position in this
   * list with the specified element.
   *
   * @param index index of the element to replace
   * @param value element to be stored at the specified position
   * @return the element previously at the specified position
   * @throws IndexOutOfBoundsException if the index is out of
   *     range {@code (index < 0 || index >= size)}
   */
  @Override
  @SuppressWarnings("unchecked")
  public synchronized T set(int index, T value) {
    checkIndexForAccess(index);
    T oldValue = (T)data[index];
    data[index] = value;

    return oldValue;
  }

  /**
   * Returns the number of elements in this list.
   *
   * @return the number of elements in this list
   */
  @Override
  public synchronized int size() {
    return size;
  }

  /**
   * Sorts this list according to the order induced by the specified
   * comparator.
   *
   * <p>A {@code null} comparator indicates that the elements'
   * <i>natural ordering</i> should be used, as specified by
   * {@link List#sort(Comparator)}: {@code Arrays.sort} delegates to its
   * {@code Comparable}-based overload, so a list of elements that do
   * not implement {@code Comparable} fails with
   * {@link ClassCastException}, not with a {@code NullPointerException}.
   *
   * @param comparator the comparator to determine the order, or
   *     {@code null} to use the elements' natural ordering
   * @throws ClassCastException if the list contains elements that are
   *     not mutually comparable using the specified comparator
   */
  @Override
  @SuppressWarnings("unchecked")
  public synchronized void sort(Comparator<? super T> comparator) {
    final int expectedModCount = modCount;

    Arrays.sort((T[])data, 0, size, comparator);

    // A Comparator is user code and can re-enter this list from compare();
    // detect it instead of silently keeping a torn order.
    if (modCount != expectedModCount) {
      throw new ConcurrentModificationException();
    }

    ++modCount;
  }

  /**
   * Returns a {@link Spliterator} over the elements in this
   * list. The spliterator traverses an immutable snapshot taken
   * under the list's lock.
   *
   * @return a {@code Spliterator} over the elements in this
   *     list
   */
  @Override
  @SuppressWarnings("unchecked")
  public synchronized Spliterator<T> spliterator() {
    // Why copy the data instead of streaming the live array: the pipeline
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

  /**
   * Returns a sequential {@link Stream} over the elements in
   * this list. The stream traverses an immutable snapshot taken
   * under the list's lock.
   *
   * @return a sequential {@code Stream} over the elements in
   *     this list
   */
  @Override
  public Stream<T> stream() {
    // Lock-free because spliterator() already captured an immutable
    // snapshot under the monitor.
    return StreamSupport.stream(spliterator(), false);
  }

  /**
   * Returns a view of the portion of this list between
   * {@code fromIndex}, inclusive, and {@code toIndex},
   * exclusive.
   *
   * @param fromIndex low endpoint (inclusive) of the sublist
   * @param toIndex high endpoint (exclusive) of the sublist
   * @return a view of the specified range within this list
   * @throws IndexOutOfBoundsException if an endpoint index value
   *     is out of range
   */
  @Override
  public synchronized List<T> subList(int fromIndex, int toIndex) {
    checkSubListRange(fromIndex, toIndex, size);

    return new SubList(fromIndex, toIndex);
  }

  /**
   * Returns an array containing all of the elements in this
   * list, in proper sequence.
   *
   * @return an array containing all of the elements in this
   *     list
   */
  @Override
  public synchronized Object[] toArray() {
    return Arrays.copyOf(data, size);
  }

  /**
   * Returns an array containing all of the elements in this
   * list, in proper sequence; the runtime type of the returned
   * array is that of the specified array.
   *
   * @param array the array into which the elements of this
   *     list are to be stored
   * @return an array containing the elements of this list
   * @throws NullPointerException if the specified array is
   *     {@code null}
   */
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

  /**
   * Returns a string representation of this list, in the standard
   * List format {@code [e1, e2, ...]} (as produced by
   * {@code AbstractCollection#toString}), so the output is
   * interchangeable with other List implementations.
   *
   * @return a string representation of this list
   */
  @Override
  public synchronized String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < size; i++) {
      sb.append(data[i]);
      if (i < size - 1) {
        sb.append(", ");
      }
    }
    sb.append(']');
    return sb.toString();
  }

  /**
   * Trims the capacity of this {@code CustomArrayList} to
   * the current size. Releases any unused backing array slots.
   * The modification count is incremented.
   */
  public synchronized void trimToSize() {
    if (size < data.length) {
      // Why a fresh copy: arrays cannot be truncated in place; an
      // exact-size copy releases the spare slots.
      data = Arrays.copyOf(data, size);
      // Why bump modCount: the backing array is swapped, and live fail-fast
      // iterators and views must notice (mirrors ArrayList#trimToSize).
      ++modCount;
    }
  }

  /**
   * Serializes this {@code CustomArrayList} to the specified
   * stream. Writes the size followed by each live element,
   * ensuring a consistent snapshot under the list's lock.
   *
   * @param ostream the stream to which to serialize
   * @throws IOException if an I/O error occurs
   */
  @Serial
  private synchronized void writeObject(ObjectOutputStream ostream)
      throws IOException {
    // Why a custom form instead of defaultWriteObject: streaming the raw
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