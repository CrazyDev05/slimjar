//
// MIT License
//
// Copyright (c) 2021 Vaishnav Anil
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//

package io.github.slimjar.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.Arrays;

public final class Reflections {
    private Reflections() { }

    public static @NotNull Constructor<?> findConstructor(@NotNull final Class<?> clazz, @Nullable final Object @NotNull ... args) throws NoSuchMethodException {
        try {
            return clazz.getConstructor(typesFrom(args));
        } catch (NoSuchMethodException e) {
            return Arrays.stream(clazz.getConstructors())
                    .filter(c -> isConstructorApplicable(c, args))
                    .findFirst()
                    .orElseThrow(() -> e);
        }
    }

    public static @Nullable Class<?> @NotNull [] typesFrom(@Nullable final Object @NotNull ... args) {
        final var result = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            Object current = args[i];
            result[i] = current != null ? current.getClass() : Object.class;
        }

        return result;
    }

    private static boolean isConstructorApplicable(@NotNull Constructor<?> constructor, @Nullable Object @NotNull [] args) {
        Class<?>[] params = constructor.getParameterTypes();
        if (params.length != args.length && !(constructor.isVarArgs() && args.length >= params.length - 1)) {
            return false;
        }

        for (int i = 0; i < params.length; i++) {
            if (constructor.isVarArgs() && i == params.length - 1) {
                Class<?> varArgType = params[i].getComponentType();
                for (int j = i; j < args.length; j++) {
                    var arg = args[j];
                    if (arg != null && !varArgType.isInstance(arg)) {
                        return false;
                    }
                }
                return true;
            }

            if (i < args.length) {
                var arg = args[i];
                if (arg != null && !params[i].isInstance(arg)) {
                    return false;
                }
            }
        }

        return true;
    }

}
