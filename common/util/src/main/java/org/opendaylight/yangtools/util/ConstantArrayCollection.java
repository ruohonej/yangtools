/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.util;

import com.google.common.base.Preconditions;
import com.google.common.collect.UnmodifiableIterator;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Internal array-backed {@link List}. It assumes the array does not contain nulls and it does not get modified
 * externally. These assumptions are not checked. It does not allow modification of the underlying array -- thus it
 * is very useful for use with {@link ImmutableOffsetMap}.
 *
 * @param <E> the type of elements in this list
 */
final class ConstantArrayCollection<E> implements Collection<E>, Serializable {
    private static final long serialVersionUID = 1L;
    private final E[] array;

    ConstantArrayCollection(final E[] array) {
        this.array = Preconditions.checkNotNull(array);
    }

    @Override
    public int size() {
        return array.length;
    }

    @Override
    public boolean isEmpty() {
        return array.length == 0;
    }

    @Override
    public boolean contains(final Object o) {
        for (Object wlk : array) {
            if (o.equals(wlk)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<E> iterator() {
        return new UnmodifiableIterator<E>() {
            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < array.length;
            }

            @Override
            public E next() {
                if (i >= array.length) {
                    throw new NoSuchElementException();
                }
                return array[i++];
            }
        };
    }

    @Override
    public Object[] toArray() {
        return array.clone();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(final T[] a) {
        if (a.length < array.length) {
            return Arrays.copyOf(array, array.length, (Class<T[]>)a.getClass().getComponentType());
        }

        System.arraycopy(array, 0, a, 0, array.length);
        if (a.length > array.length) {
            a[array.length] = null;
        }
        return a;
    }

    @Override
    public boolean add(final E e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(final Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean addAll(final Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }
}
