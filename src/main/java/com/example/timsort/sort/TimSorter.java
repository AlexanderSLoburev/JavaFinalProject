package com.example.timsort.sort;

import com.example.timsort.collection.CustomArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A {@link Sorter} that returns a stably sorted copy of a captured list,
 * produced by the TimSort algorithm (the one behind
 * {@code Arrays.sort(Object[])} and Python's {@code sorted()}).
 *
 * <p>How TimSort works: it scans the input for natural ascending (or
 * strictly descending) "runs", extends runs that are too short to
 * {@code minRun} with binary insertion sort, and merges pending runs
 * through a stack kept balanced by the invariant on {@code runLen} (see
 * {@code mergeCollapse}). Merging switches to exponential "galloping"
 * search once one run consistently wins, which makes nearly-sorted inputs
 * and runs of very different value ranges close to linear. Worst case
 * O(n log n) comparisons, best case O(n); stable — elements that compare
 * equal keep their relative order.
 *
 * <p>Contract of {@link #sort()}:
 * <ul>
 *   <li>the source list is never modified; the result is a new list;
 *   <li>each call snapshots the source's current contents, so one sorter
 *       can be reused as the source changes;
 *   <li>a {@code null} comparator (the one-argument constructor) means the
 *       elements' natural ordering — elements must be {@code Comparable}
 *       (else {@code ClassCastException}) and must not be null (else
 *       {@code NullPointerException}); a custom comparator decides for
 *       itself how to treat nulls;
 *   <li>an inconsistent comparator (violating antisymmetry/transitivity)
 *       is detected by the merge invariants in some cases and reported as
 *       {@code IllegalArgumentException("Comparison method violates its
 *       general contract!")}.
 * </ul>
 *
 * <p>Instances are immutable and {@code sort()} keeps all mutable state in
 * method locals, so a single sorter may be shared between threads. The
 * source list's own consistency is the caller's concern: reading a
 * {@code CustomArrayList} through {@code toArray()} takes one atomic
 * snapshot.
 *
 * @param <T> the element type
 */
public class TimSorter<T> implements Sorter<T> {

  /**
   * Natural ordering used when no comparator is given. Typed as
   * {@code Comparator<Object>}, which IS a {@code Comparator<? super T>}
   * for every {@code T} — only the static type system cannot express it.
   */
  private static final Comparator<Object> NATURAL_ORDER =
      new Comparator<Object>() {
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public int compare(Object left, Object right) {
          return ((Comparable)left).compareTo(right);
        }
      };

  private final List<T> source;
  private final Comparator<? super T> comparator; // null -> natural ordering

  /**
   * Creates a sorter over {@code source} using the elements' natural
   * ordering.
   *
   * @param source the list whose contents are sorted by {@link #sort()}
   * @throws NullPointerException if {@code source} is {@code null}
   */
  public TimSorter(List<T> source) { this(source, null); }

  /**
   * Creates a sorter over {@code source} using the given comparator.
   *
   * @param source the list whose contents are sorted by {@link #sort()}
   * @param comparator the order to sort in, or {@code null} for the
   *     elements' natural ordering
   * @throws NullPointerException if {@code source} is {@code null}
   */
  public TimSorter(List<T> source, Comparator<? super T> comparator) {
    this.source = Objects.requireNonNull(source);
    this.comparator = comparator;
  }

  /**
   * Returns a new list containing the source's current elements in
   * ascending order. The source list is not modified.
   *
   * @return a new, stably sorted list
   */
  @Override
  public List<T> sort() {
    // Why a snapshot: the algorithm must never observe a torn state, and
    // the source must stay untouched. toArray() on a CustomArrayList takes
    // one consistent snapshot under its monitor; the returned array is
    // contractually private to us, so sorting it in place is safe.
    @SuppressWarnings("unchecked") // Object[] as T[]: erased to Object[], and
                                   // the algorithm only permutes existing
                                   // elements, never stores foreign ones
    T[] elements = (T[])source.toArray();

    if (elements.length > 1) {
      TimSort.sort(elements, order());
    }

    List<T> result = new CustomArrayList<>(elements.length);
    for (T element : elements) {
      result.add(element);
    }
    return result;
  }

  private Comparator<? super T> order() {
    return (comparator != null) ? comparator
                                : (Comparator<? super T>) NATURAL_ORDER;
  }

  /**
   * One invocation's TimSort state: the runs pending merge, the adaptive
   * gallop threshold and the scratch array. Structure and the merge-stack
   * invariant follow the reference implementation (OpenJDK's
   * {@code java.util.TimSort}).
   */
  private static final class TimSort<T> {

    /** Runs shorter than this get extended via binary insertion sort. */
    private static final int MIN_MERGE = 32;

    /** One run must win this many times in a row before galloping starts. */
    private static final int MIN_GALLOP = 7;

    private static final int INITIAL_TMP_STORAGE = 256;

    private final T[] a;
    private final Comparator<? super T> c;

    /**
     * Scratch for merges; only the SHORTER side of a merge is parked here,
     * so half of {@code a} is the hard upper bound.
     */
    private T[] tmp;

    /** Base index of pending run i in {@code a}. */
    private final int[] runBase;
    /** Length of pending run i. */
    private final int[] runLen;
    private int stackSize = 0;

    /**
     * Adaptive gallop threshold: decays towards 1 while galloping pays
     * off. Always >= 1.
     */
    private int minGallop = MIN_GALLOP;

    private TimSort(T[] a, Comparator<? super T> c) {
      this.a = a;
      this.c = c;
      @SuppressWarnings("unchecked")
      T[] scratch = (T[]) new Object[Math.max(
          2, Math.min(a.length >>> 1, INITIAL_TMP_STORAGE))];
      tmp = scratch;
      // Why these sizes: proven bounds for the (fixed) merge-collapse
      // invariant; a few dozen ints cost nothing next to the data.
      int stackLen = (a.length < 120      ? 5
                      : a.length < 1542   ? 10
                      : a.length < 119151 ? 24
                                          : 40);
      runBase = new int[stackLen];
      runLen = new int[stackLen];
    }

    /**
     * Sorts {@code a[0..a.length)} with the given comparator.
     */
    static <E> void sort(E[] a, Comparator<? super E> c) {
      int nRemaining = a.length;
      if (nRemaining < 2) {
        return; // arrays of size 0 and 1 are always sorted
      }

      if (nRemaining < MIN_MERGE) {
        // Why plain binary insertion sort: below MIN_MERGE merging costs
        // more than it saves.
        int initRunLen = countRunAndMakeAscending(a, 0, a.length, c);
        binarySort(a, 0, a.length, initRunLen, c);
        return;
      }

      TimSort<E> ts = new TimSort<>(a, c);
      int minRun = minRunLength(nRemaining);
      int lo = 0;
      do {
        // Identify the next run; if it is too short, extend it to minRun
        // (or to whatever remains — the tail is taken whole).
        int runLen = countRunAndMakeAscending(a, lo, a.length, c);
        if (runLen < minRun) {
          int force = Math.min(nRemaining, minRun);
          binarySort(a, lo, lo + force, lo + runLen, c);
          runLen = force;
        }
        ts.pushRun(lo, runLen);
        ts.mergeCollapse();
        lo += runLen;
        nRemaining -= runLen;
      } while (nRemaining > 0);

      ts.mergeForceCollapse();
    }

    // ----------------------------------------------------------------
    // Static helpers
    // ----------------------------------------------------------------

    /**
     * Sorts {@code a[lo..hi)} with binary insertion sort, where
     * {@code a[lo..start)} is already known to be sorted.
     *
     * <p>The binary search stops AFTER equal elements
     * ({@code compare(pivot, a[mid]) < 0} moves left) — that is what keeps
     * this sort stable.
     */
    private static <E> void binarySort(E[] a, int lo, int hi, int start,
                                       Comparator<? super E> c) {
      if (start == lo) {
        start++;
      }
      for (; start < hi; start++) {
        E pivot = a[start];
        int left = lo;
        int right = start;
        while (left < right) {
          int mid = (left + right) >>> 1;
          if (c.compare(pivot, a[mid]) < 0) {
            right = mid;
          } else {
            left = mid + 1;
          }
        }
        int n = start - left; // elements to shift right
        System.arraycopy(a, left, a, left + 1, n);
        a[left] = pivot;
      }
    }

    /**
     * Returns the length of the maximal run beginning at {@code lo},
     * reversing the run first if it is descending.
     *
     * <p>A run is either non-strictly ascending
     * ({@code a[lo] <= a[lo+1] <= ...}, equal elements untouched) or
     * strictly descending ({@code a[lo] > a[lo+1] > ...}). Why "strictly"
     * for the descending case: reversing a run that merely ENDS in equal
     * elements would swap those equals and break stability.
     */
    private static <E> int countRunAndMakeAscending(E[] a, int lo, int hi,
                                                    Comparator<? super E> c) {
      int runHi = lo + 1;
      if (runHi == hi) {
        return 1;
      }

      if (c.compare(a[runHi], a[lo]) < 0) {
        while (runHi < hi && c.compare(a[runHi], a[runHi - 1]) < 0) {
          runHi++;
        }
        reverseRange(a, lo, runHi);
      } else {
        while (runHi < hi && c.compare(a[runHi], a[runHi - 1]) >= 0) {
          runHi++;
        }
      }
      return runHi - lo;
    }

    /**
     * Locates the LEFTMOST insertion position for {@code key} in the sorted
     * range {@code a[base..base+len)}: the returned index is the number of
     * elements in the range that are strictly less than the key.
     *
     * <p>Contract: the return value is always within {@code [0, len]}, and
     * only elements of the range itself are ever read.
     */
    private static <E> int gallopLeft(E key, E[] a, int base, int len, int hint,
                                      Comparator<? super E> c) {
      int lastOfs = 0;
      int ofs = 1;
      if (c.compare(key, a[base + hint]) > 0) {
        // Gallop right until a[base+hint+lastOfs] < key <= a[base+hint+ofs]
        int maxOfs = len - hint;
        while (ofs < maxOfs && c.compare(key, a[base + hint + ofs]) > 0) {
          lastOfs = ofs;
          ofs = (ofs << 1) + 1;
          if (ofs <= 0) { // int overflow
            ofs = maxOfs;
          }
        }
        if (ofs > maxOfs) {
          ofs = maxOfs;
        }
        lastOfs += hint; // make offsets relative to base
        ofs += hint;
      } else { // key <= a[base + hint]
        // Gallop left until a[base+hint-ofs] < key <= a[base+hint-lastOfs]
        int maxOfs = hint + 1;
        while (ofs < maxOfs && c.compare(key, a[base + hint - ofs]) <= 0) {
          lastOfs = ofs;
          ofs = (ofs << 1) + 1;
          if (ofs <= 0) { // int overflow
            ofs = maxOfs;
          }
        }
        if (ofs > maxOfs) {
          ofs = maxOfs;
        }
        // Why the clamp: an exhausted left gallop clamps ofs to
        // maxOfs = hint + 1, so 'hint - ofs' becomes -1 — the reference
        // algorithm's "virtual a[-1] = -infinity". That virtual element
        // must never be probed for real: with base == 0 the binary search
        // below would read a[-1] (ArrayIndexOutOfBoundsException), and with
        // base > 0 it would read a foreign element and could return -1,
        // poisoning the merge lengths downstream (negative arraycopy
        // length). The answer counts elements, so it cannot be negative.
        int tmp = lastOfs;
        lastOfs = hint - ofs;
        if (lastOfs < 0) {
          lastOfs = 0;
        }
        ofs = hint - tmp;
      }

      // Binary search: a[base+lastOfs] < key <= a[base+ofs], with the
      // invariant lastOfs <= answer <= ofs; every probe stays inside [0, len).
      while (lastOfs < ofs) {
        int m = lastOfs + ((ofs - lastOfs) >>> 1);
        if (c.compare(key, a[base + m]) > 0) {
          lastOfs = m + 1;
        } else {
          ofs = m;
        }
      }
      return ofs;
    }

    /**
     * Like {@link #gallopLeft}, but locates the RIGHTMOST insertion
     * position: the returned index is the number of elements in the range
     * that are less than or equal to the key.
     *
     * <p>Contract: the return value is always within {@code [0, len]}, and
     * only elements of the range itself are ever read.
     */
    private static <E> int gallopRight(E key, E[] a, int base, int len,
                                       int hint, Comparator<? super E> c) {
      int ofs = 1;
      int lastOfs = 0;
      if (c.compare(key, a[base + hint]) < 0) {
        // Gallop left until a[base+hint-ofs] <= key < a[base+hint-lastOfs]
        int maxOfs = hint + 1;
        while (ofs < maxOfs && c.compare(key, a[base + hint - ofs]) < 0) {
          lastOfs = ofs;
          ofs = (ofs << 1) + 1;
          if (ofs <= 0) { // int overflow
            ofs = maxOfs;
          }
        }
        if (ofs > maxOfs) {
          ofs = maxOfs;
        }
        // Why the clamp: same as in gallopLeft — the exhausted-gallop
        // bracket may conceptually start at -1 ("virtual a[-1]"), but the
        // binary search must never probe it for real.
        int tmp = lastOfs;
        lastOfs = hint - ofs;
        if (lastOfs < 0) {
          lastOfs = 0;
        }
        ofs = hint - tmp;
      } else { // key >= a[base + hint]
        // Gallop right until a[base+hint+lastOfs] <= key < a[base+hint+ofs]
        int maxOfs = len - hint;
        while (ofs < maxOfs && c.compare(key, a[base + hint + ofs]) >= 0) {
          lastOfs = ofs;
          ofs = (ofs << 1) + 1;
          if (ofs <= 0) { // int overflow
            ofs = maxOfs;
          }
        }
        if (ofs > maxOfs) {
          ofs = maxOfs;
        }
        lastOfs += hint; // make offsets relative to base
        ofs += hint;
      }

      // Binary search: a[base+lastOfs] <= key < a[base+ofs], with the
      // invariant lastOfs <= answer <= ofs; every probe stays inside [0, len).
      while (lastOfs < ofs) {
        int m = lastOfs + ((ofs - lastOfs) >>> 1);
        if (c.compare(key, a[base + m]) < 0) {
          ofs = m;
        } else {
          lastOfs = m + 1;
        }
      }
      return ofs;
    }

    /**
     * Returns the run length to aim for: between 16 and 32 such that
     * dividing {@code n} by it yields a power of two, or falls just short
     * of one — which keeps the final merges balanced.
     */
    private static int minRunLength(int n) {
      int r = 0; // becomes 1 if any bit is shifted out
      while (n >= MIN_MERGE) {
        r |= (n & 1);
        n >>= 1;
      }
      return n + r;
    }

    private static <E> void reverseRange(E[] a, int lo, int hi) {
      hi--;
      while (lo < hi) {
        E t = a[lo];
        a[lo++] = a[hi];
        a[hi--] = t;
      }
    }

    // ----------------------------------------------------------------
    // Run-stack management and merging
    // ----------------------------------------------------------------

    /**
     * Grows the scratch array to hold at least {@code capacity} elements,
     * rounded up to a power of two and capped at half of {@code a} (no
     * merge ever needs more).
     */
    private T[] ensureCapacity(int capacity) {
      if (tmp.length < capacity) {
        // Compute the smallest power of 2 > capacity
        int newSize = capacity;
        newSize |= newSize >> 1;
        newSize |= newSize >> 2;
        newSize |= newSize >> 4;
        newSize |= newSize >> 8;
        newSize |= newSize >> 16;
        newSize++;

        if (newSize < 0) { // int overflow — not bloody likely
          newSize = capacity;
        } else {
          newSize = Math.min(newSize, a.length >>> 1);
        }
        @SuppressWarnings("unchecked") T[] newArray = (T[]) new Object[newSize];
        tmp = newArray;
      }
      return tmp;
    }

    /**
     * Merges the runs at stack offsets {@code i} and {@code i + 1}
     * ({@code i} is the second or third from the top), updating the stack.
     */
    private void mergeAt(int i) {
      int base1 = runBase[i];
      int len1 = runLen[i];
      int base2 = runBase[i + 1];
      int len2 = runLen[i + 1];

      // Record the merged run in slot i; if i was third from the top,
      // slide the top run down over it.
      runLen[i] = len1 + len2;
      if (i == stackSize - 3) {
        runBase[i + 1] = runBase[i + 2];
        runLen[i + 1] = runLen[i + 2];
      }
      stackSize--;

      // Where does the FIRST element of run2 belong in run1? Everything
      // before that point in run1 is <= it and already in its final
      // position. gallopRight (not gallopLeft) so equal elements of run1
      // stay ahead of run2's — stability.
      int k = gallopRight(a[base2], a, base1, len1, 0, c);
      base1 += k;
      len1 -= k;
      if (len1 == 0) {
        return; // all of run1 was already in place
      }

      // Where does the LAST element of run1 belong in run2? Everything
      // after that point in run2 is >= it and already in place.
      // gallopLeft (not gallopRight) so equal elements of run2 stay
      // behind run1's — stability.
      len2 = gallopLeft(a[base1 + len1 - 1], a, base2, len2, len2 - 1, c);
      if (len2 == 0) {
        return; // all of run2 was already in place
      }

      // Merge the rest, parking the SHORTER run in the scratch array
      // (that is why tmp never needs more than half of a).
      if (len1 <= len2) {
        mergeLo(base1, len1, base2, len2);
      } else {
        mergeHi(base1, len1, base2, len2);
      }
    }

    /**
     * Merges adjacent runs until the stack invariants are re-established:
     *
     * <pre>
     *   1. runLen[i - 3] &gt; runLen[i - 2] + runLen[i - 1]
     *   2. runLen[i - 2] &gt; runLen[i - 1]
     * </pre>
     *
     * <p>Why BOTH checks: with only the first, runs can be pushed faster
     * than they are merged and the stack outgrows its bound — the bug
     * exposed by formal verification (de Gouw et al.) and fixed in the
     * reference implementation; this second check is that fix.
     */
    private void mergeCollapse() {
      while (stackSize > 1) {
        int n = stackSize - 2;
        if (n > 0 && runLen[n - 1] <= runLen[n] + runLen[n + 1] ||
            n > 1 && runLen[n - 2] <= runLen[n - 1] + runLen[n]) {
          // Merge the smaller neighbour: run n-1 with run n, or run n
          // with run n+1 — whichever leaves the more balanced stack.
          if (runLen[n - 1] < runLen[n + 1]) {
            n--;
          }
        } else if (runLen[n] > runLen[n + 1]) {
          break; // invariant is established
        }
        mergeAt(n);
      }
    }

    /**
     * Merges everything on the stack into one run, ignoring the balance
     * invariant (used once, after the input is consumed).
     */
    private void mergeForceCollapse() {
      while (stackSize > 1) {
        int n = stackSize - 2;
        if (n > 0 && runLen[n - 1] < runLen[n + 1]) {
          n--;
        }
        mergeAt(n);
      }
    }

    /**
     * Merges two adjacent runs right-to-left: run2 (the upper one, at
     * {@code base2}) is copied into the scratch array, run1 stays in
     * {@code a}. Used when {@code len1 > len2}.
     */
    private void mergeHi(int base1, int len1, int base2, int len2) {
      T[] tmp = ensureCapacity(len2);
      System.arraycopy(a, base2, tmp, 0, len2);

      int cursor1 = base1 + len1 - 1; // next unmerged element of run1, in a
      int cursor2 = len2 - 1;         // next unmerged element of run2, in tmp
      int dest = base2 + len2 - 1;    // write position, moving left

      // Move last element of run1 and handle degenerate cases.
      a[dest--] = a[cursor1--];
      if (--len1 == 0) {
        System.arraycopy(tmp, 0, a, dest - (len2 - 1), len2);
        return;
      }
      if (len2 == 1) {
        // The single remaining run2 element is smaller than everything
        // left in run1 (the mergeAt gallop guarantees it), so it goes
        // before all of run1's leftovers.
        dest -= len1;
        cursor1 -= len1;
        System.arraycopy(a, cursor1 + 1, a, dest + 1, len1);
        a[dest] = tmp[cursor2];
        return;
      }

      int minGallop = this.minGallop;
    outer:
      while (true) {
        int count1 = 0; // times in a row that run1 won
        int count2 = 0; // times in a row that run2 won

        // Straight one-pair comparisons, taking the larger element first.
        do {
          if (c.compare(tmp[cursor2], a[cursor1]) < 0) {
            a[dest--] = a[cursor1--];
            count1++;
            count2 = 0;
            if (--len1 == 0) {
              break outer;
            }
          } else {
            a[dest--] = tmp[cursor2--];
            count2++;
            count1 = 0;
            if (--len2 == 1) {
              break outer;
            }
          }
        } while ((count1 | count2) < minGallop);

        // Galloping, mirroring mergeLo from the right.
        do {
          // Elements of run1 strictly greater than tmp[cursor2] move to
          // the right of it; equals stay left — stability.
          count1 =
              len1 - gallopRight(tmp[cursor2], a, base1, len1, len1 - 1, c);
          if (count1 != 0) {
            dest -= count1;
            cursor1 -= count1;
            len1 -= count1;
            System.arraycopy(a, cursor1 + 1, a, dest + 1, count1);
            if (len1 == 0) {
              break outer;
            }
          }
          a[dest--] = tmp[cursor2--];
          if (--len2 == 1) {
            break outer;
          }

          // Elements of run2 greater-or-equal to a[cursor1] move to the
          // right of it; equals of run2 stay right — stability.
          count2 = len2 - gallopLeft(a[cursor1], tmp, 0, len2, len2 - 1, c);
          if (count2 != 0) {
            dest -= count2;
            cursor2 -= count2;
            len2 -= count2;
            System.arraycopy(tmp, cursor2 + 1, a, dest + 1, count2);
            if (len2 <= 1) { // len2 == 1 || len2 == 0
              break outer;
            }
          }
          a[dest--] = a[cursor1--];
          if (--len1 == 0) {
            break outer;
          }
          minGallop--;
        } while (count1 >= MIN_GALLOP | count2 >= MIN_GALLOP);
        if (minGallop < 1) {
          minGallop = 1;
        }
      }
      this.minGallop = Math.max(1, minGallop); // write back

      if (len2 == 1) {
        dest -= len1;
        cursor1 -= len1;
        System.arraycopy(a, cursor1 + 1, a, dest + 1, len1);
        a[dest] = tmp[cursor2]; // run2's minimum before run1's leftovers
      } else if (len2 == 0) {
        throw new IllegalArgumentException(
            "Comparison method violates its general contract!");
      } else {
        System.arraycopy(tmp, 0, a, dest - (len2 - 1), len2); // len1 == 0
      }
    }

    /**
     * Merges two adjacent runs left-to-right: run1 (the lower one, at
     * {@code base1}) is copied into the scratch array, run2 stays in
     * {@code a}. Used when {@code len1 <= len2}.
     */
    private void mergeLo(int base1, int len1, int base2, int len2) {
      // Copy run1 into tmp; merging then writes run2's elements over
      // run1's old range.
      T[] tmp = ensureCapacity(len1);
      System.arraycopy(a, base1, tmp, 0, len1);

      int cursor1 = 0;     // next unmerged element of run1, in tmp
      int cursor2 = base2; // next unmerged element of run2, in a
      int dest = base1;    // write position, moving right

      // Move first element of run2 (the mergeAt gallop guarantees it is
      // the smaller head) and handle degenerate cases.
      a[dest++] = a[cursor2++];
      if (--len2 == 0) {
        System.arraycopy(tmp, cursor1, a, dest, len1);
        return;
      }
      if (len1 == 1) {
        // The single remaining run1 element is run1's maximum, so it goes
        // after everything left in run2.
        System.arraycopy(a, cursor2, a, dest, len2);
        a[dest + len2] = tmp[cursor1];
        return;
      }

      int minGallop = this.minGallop;
    outer:
      while (true) {
        int count1 = 0; // times in a row that run1 won
        int count2 = 0; // times in a row that run2 won

        // Straight one-pair comparisons until one run consistently wins.
        do {
          if (c.compare(a[cursor2], tmp[cursor1]) < 0) {
            a[dest++] = a[cursor2++];
            count2++;
            count1 = 0;
            if (--len2 == 0) {
              break outer;
            }
          } else {
            a[dest++] = tmp[cursor1++];
            count1++;
            count2 = 0;
            if (--len1 == 1) {
              break outer;
            }
          }
        } while ((count1 | count2) < minGallop);

        // One run is winning so consistently that galloping may pay off.
        do {
          count1 = gallopRight(a[cursor2], tmp, cursor1, len1, 0, c);
          if (count1 != 0) {
            System.arraycopy(tmp, cursor1, a, dest, count1);
            dest += count1;
            cursor1 += count1;
            len1 -= count1;
            if (len1 <= 1) { // len1 == 1 || len1 == 0
              break outer;
            }
          }
          a[dest++] = a[cursor2++];
          if (--len2 == 0) {
            break outer;
          }

          count2 = gallopLeft(tmp[cursor1], a, cursor2, len2, 0, c);
          if (count2 != 0) {
            System.arraycopy(a, cursor2, a, dest, count2);
            dest += count2;
            cursor2 += count2;
            len2 -= count2;
            if (len2 == 0) {
              break outer;
            }
          }
          a[dest++] = tmp[cursor1++];
          if (--len1 == 1) {
            break outer;
          }
          minGallop--;
        } while (count1 >= MIN_GALLOP | count2 >= MIN_GALLOP);
        if (minGallop < 1) {
          minGallop = 1;
        }
      }
      this.minGallop = Math.max(1, minGallop); // write back

      if (len1 == 1) {
        System.arraycopy(a, cursor2, a, dest, len2);
        a[dest + len2] = tmp[cursor1]; // run1's maximum to the very end
      } else if (len1 == 0) {
        // Stability guarantees run1 is never exhausted first in mergeLo
        // (its maximum is >= everything remaining in run2); reaching 0
        // means the comparator is inconsistent.
        throw new IllegalArgumentException(
            "Comparison method violates its general contract!");
      } else {
        System.arraycopy(tmp, cursor1, a, dest, len1); // len2 == 0
      }
    }

    private void pushRun(int runBase, int runLen) {
      this.runBase[stackSize] = runBase;
      this.runLen[stackSize] = runLen;
      stackSize++;
    }
  }
}