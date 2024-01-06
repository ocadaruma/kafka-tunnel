package com.mayreh.kafka.http.tunnel.client;

import java.lang.reflect.Method;
import java.util.Arrays;

public class ReflectionUtil {
    private static final Class<?>[] emptyArgTypes = new Class<?>[0];

    public static Object call(Object target, String methodName) {
        return call(target, methodName, emptyArgTypes);
    }

    public static Object call(
            Object target,
            String methodName,
            Class<?>[] argTypes,
            Object... args) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    if (method.getName().equals(methodName) && Arrays.equals(method.getParameterTypes(), argTypes)) {
                        method.setAccessible(true);
                        return method.invoke(target, args);
                    }
                }
                clazz = clazz.getSuperclass();
            }
            throw new NoSuchMethodException(methodName);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
