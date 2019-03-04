// FILE: NoSamForClassTypeParameter.java

package test;

class NoSamForClassTypeParameter<K extends Runnable> {
    void foo(K runnable1, Runnable runnable2) {}
}

// FILE: NoSamForTypeParameterDerived1.java

package test;

class NoSamForTypeParameterDerived1 extends NoSamForClassTypeParameter<Runnable> {
    @Override
    void foo(Runnable runnable1, Runnable runnable2) {}
}

// FILE: NoSamForTypeParameterDerived2.java

package test;

class NoSamForTypeParameterDerived2<E extends Runnable> extends NoSamForClassTypeParameter<E> {
     void foo(E runnable1, Runnable runnable2) {}
}

// FILE: NoSamForTypeParameterDerived3.java

package test;

class NoSamForTypeParameterDerived3 extends NoSamForTypeParameterDerived1 {
    @Override
    void foo(Runnable runnable1, Runnable runnable2) {}
}

// FILE: NoSamForTypeParameterDerived4.java

package test;

class NoSamForTypeParameterDerived4 extends NoSamForTypeParameterDerived2<Runnable> {
    @Override
    void foo(Runnable runnable1, Runnable runnable2) {}
}
