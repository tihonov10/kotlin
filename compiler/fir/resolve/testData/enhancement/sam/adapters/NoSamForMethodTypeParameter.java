// FILE: NoSamForMethodTypeParameter.java

package test;

class NoSamForMethodTypeParameter {
    <K extends Runnable> void foo(K runnable1, Runnable runnable2) {}
}

// FILE: NoSamForTypeParameterDerived1.java

package test;

class NoSamForTypeParameterDerived1 extends NoSamForMethodTypeParameter {
    @Override
    void foo(Runnable runnable1, Runnable runnable2) {}
}

// FILE: NoSamForTypeParameterDerived2.java

package test;

class NoSamForTypeParameterDerived2 extends NoSamForMethodTypeParameter {
    @Override
    <K extends Runnable> void foo(K runnable1, Runnable runnable2) {}
}

// FILE: NoSamForTypeParameterDerived3.java

package test;

class NoSamForTypeParameterDerived3 extends NoSamForTypeParameterDerived1 {
    @Override
    void foo(Runnable runnable1, Runnable runnable2) {}
}
