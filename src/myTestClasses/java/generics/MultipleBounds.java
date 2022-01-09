package generics;

import foo.EmptyInterface;

public class MultipleBounds<T extends Generics<? extends BaseA> & EmptyInterface> {
}
