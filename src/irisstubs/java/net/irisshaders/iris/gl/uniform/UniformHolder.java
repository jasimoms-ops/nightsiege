package net.irisshaders.iris.gl.uniform;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/** Compile-time stand-in for the Iris class of the same name. Not packaged. */
public interface UniformHolder {
    UniformHolder uniform1f(UniformUpdateFrequency frequency, String name, DoubleSupplier value);

    UniformHolder uniform1i(UniformUpdateFrequency frequency, String name, IntSupplier value);
}
