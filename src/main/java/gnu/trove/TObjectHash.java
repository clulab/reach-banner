///////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2001, Eric D. Friedman All Rights Reserved.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
///////////////////////////////////////////////////////////////////////////////

package gnu.trove;

import java.io.Serializable;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An open addressed hashing implementation for Object types.
 *
 * Created: Sun Nov  4 08:56:06 2001
 *
 * @author Eric D. Friedman
 * @version $Id: TObjectHash.java,v 1.1 2011/07/29 09:11:48 bleaman Exp $
 */
abstract public class TObjectHash extends THash implements Serializable, TObjectHashingStrategy {

    /** the set of Objects */
    protected transient Object[] _set;

    /** the strategy used to hash objects in this collection. */
    private TObjectHashingStrategy hashingStrategy;

    protected static final Object REMOVED = new Object();

    /**
     * Creates a new <code>TObjectHash</code> instance with the
     * default capacity and load factor.
     */
    public TObjectHash() {
        super();
        this.hashingStrategy = this;
    }

    /**
     * Creates a new <code>TObjectHash</code> instance with the
     * default capacity and load factor and a custom hashing strategy.
     *
     * @param strategy used to compute hash codes and to compare objects.
     */
    public TObjectHash(TObjectHashingStrategy strategy) {
        super();
        this.hashingStrategy = strategy;
    }

    /**
     * Creates a new <code>TObjectHash</code> instance whose capacity
     * is the next highest prime above <tt>initialCapacity + 1</tt>
     * unless that value is already prime.
     *
     * @param initialCapacity an <code>int</code> value
     */
    public TObjectHash(int initialCapacity) {
        super(initialCapacity);
        this.hashingStrategy = this;
    }

    /**
     * Creates a new <code>TObjectHash</code> instance whose capacity
     * is the next highest prime above <tt>initialCapacity + 1</tt>
     * unless that value is already prime.  Uses the specified custom
     * hashing strategy.
     *
     * @param initialCapacity an <code>int</code> value
     * @param strategy used to compute hash codes and to compare objects.
     */
    public TObjectHash(int initialCapacity, TObjectHashingStrategy strategy) {
        super(initialCapacity);
        this.hashingStrategy = strategy;
    }

    /**
     * Creates a new <code>TObjectHash</code> instance with a prime
     * value at or near the specified capacity and load factor.
     *
     * @param initialCapacity used to find a prime capacity for the table.
     * @param loadFactor used to calculate the threshold over which
     * rehashing takes place.
     */
    public TObjectHash(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        this.hashingStrategy = this;
    }

    /**
     * Creates a new <code>TObjectHash</code> instance with a prime
     * value at or near the specified capacity and load factor.  Uses
     * the specified custom hashing strategy.
     *
     * @param initialCapacity used to find a prime capacity for the table.
     * @param loadFactor used to calculate the threshold over which
     * rehashing takes place.
     * @param strategy used to compute hash codes and to compare objects.
     */
    public TObjectHash(int initialCapacity, float loadFactor, TObjectHashingStrategy strategy) {
        super(initialCapacity, loadFactor);
        this.hashingStrategy = strategy;
    }

    /**
     * @return a shallow clone of this collection
     */
    public Object clone() {
        TObjectHash h = (TObjectHash)super.clone();
        h._set = (Object[])this._set.clone();
        return h;
    }

    protected int capacity() {
        return _set.length;
    }

    protected void removeAt(int index) {
        super.removeAt(index);
        _set[index] = REMOVED;
    }

    /**
     * initializes the Object set of this hash table.
     *
     * @param initialCapacity an <code>int</code> value
     * @return an <code>int</code> value
     */
    protected int setUp(int initialCapacity) {
        int capacity;

        capacity = super.setUp(initialCapacity);
        _set = new Object[capacity];
        return capacity;
    }

    /**
     * Executes <tt>procedure</tt> for each element in the set.
     *
     * @param procedure a <code>TObjectProcedure</code> value
     * @return false if the loop over the set terminated because
     * the procedure returned false for some value.
     */
    public boolean forEach(TObjectProcedure procedure) {
        Object[] set = _set;
        for (int i = set.length; i-- > 0;) {
            if (set[i] != null
                && set[i] != REMOVED
                && ! procedure.execute(set[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Searches the set for <tt>obj</tt>
     *
     * @param obj an <code>Object</code> value
     * @return a <code>boolean</code> value
     */
    public boolean contains(Object obj) {
        return index(obj) >= 0;
    }

    /**
     * Locates the index of <tt>obj</tt>.
     *
     * @param obj an <code>Object</code> value
     * @return the index of <tt>obj</tt> or -1 if it isn't in the set.
     */
    protected int index(Object obj) {
        int hash, probe, index, length;
        Object[] set;
        Object cur;

        set = _set;
        length = set.length;
        hash = hashingStrategy.computeHashCode(obj) & 0x7fffffff;
        index = hash % length;
        cur = set[index];

        if (cur != null
            && (cur == REMOVED || ! hashingStrategy.equals(cur, obj))) {
            // see Knuth, p. 529
            probe = 1 + (hash % (length - 2));

            do {
                index -= probe;
                if (index < 0) {
                    index += length;
                }
                cur = set[index];
            } while (cur != null
                     && (cur == REMOVED || ! hashingStrategy.equals(cur, obj)));
        }

        return cur == null ? -1 : index;
    }

    /**
     * Locates the index at which <tt>obj</tt> can be inserted.  if
     * there is already a value equal()ing <tt>obj</tt> in the set,
     * returns that value's index as <tt>-index - 1</tt>.
     *
     * @param obj an <code>Object</code> value
     * @return the index of a FREE slot at which obj can be inserted
     * or, if obj is already stored in the hash, the negative value of
     * that index, minus 1: -index -1.
     */
    protected int insertionIndex(Object obj) {
        int hash, probe, index, length;
        Object[] set;
        Object cur;

        set = _set;
        length = set.length;
        hash = hashingStrategy.computeHashCode(obj) & 0x7fffffff;
        index = hash % length;
        cur = set[index];

        if (cur == null) {
            return index;       // empty, all done
        } else if (hashingStrategy.equals(cur, obj)) {
            return -index -1;   // already stored
        } else {                // already FULL or REMOVED, must probe
            // compute the double hash
            probe = 1 + (hash % (length - 2));
            // starting at the natural offset, probe until we find an
            // offset that isn't full.
            do {
                index -= probe;
                if (index < 0) {
                    index += length;
                }
                cur = set[index];
            } while (cur != null
                     && cur != REMOVED
                     && ! hashingStrategy.equals(cur, obj));

            // if the index we found was removed: continue probing until we
            // locate a free location or an element which equal()s the
            // one we have.
            if (cur == REMOVED) {
                int firstRemoved = index;
                while (cur != null
                       && (cur == REMOVED || ! hashingStrategy.equals(cur, obj))) {
                    index -= probe;
                    if (index < 0) {
                        index += length;
                    }
                    cur = set[index];
                }
                return (cur != null
                        && cur != REMOVED) ? -index -1 : firstRemoved;
            }
            // if it's full, the key is already stored
                return (cur != null
                        && cur != REMOVED) ? -index -1 : index;
        }
    }

    /**
     * This is the default implementation of TObjectHashingStrategy:
     * it delegates hashing to the Object's hashCode method.
     *
     * @param object for which the hashcode is to be computed
     * @return the hashCode
     * @see Object#hashCode()
     */
    public final int computeHashCode(Object o) {
        return o.hashCode();
    }

    /**
     * This is the default implementation of TObjectHashingStrategy:
     * it delegates equality comparisons to the first parameter's
     * equals() method.
     *
     * @param o1 an <code>Object</code> value
     * @param o2 an <code>Object</code> value
     * @return true if the objects are equal
     * @see Object#equals(Object)
     */
    public final boolean equals(Object o1, Object o2) {
        return o1.equals(o2);
    }
} // TObjectHash
