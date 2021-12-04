package jbuild.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

public final class CollectionUtils {

    public static <T> Iterable<T> append(Iterable<T> iter, T last) {
        return () -> new Iterator<T>() {
            final Iterator<T> delegate = iter.iterator();
            boolean done = false;

            @Override
            public boolean hasNext() {
                return !done;
            }

            @Override
            public T next() {
                if (done) {
                    throw new NoSuchElementException();
                }
                if (delegate.hasNext()) {
                    return delegate.next();
                }
                done = true;
                return last;
            }
        };
    }
}
