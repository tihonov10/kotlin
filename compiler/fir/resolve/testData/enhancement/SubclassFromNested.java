// FILE: SubclassFromNested.java

package test;

public class SubclassFromNested implements B.C {
}

// FILE: B.java

package test;

public class B {
    B(C c) {}

    interface C {
    }
}
