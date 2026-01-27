package io.helidon.examples.jaxrs.apt.runtime;

import jakarta.ws.rs.container.ResourceInfo;

import java.lang.reflect.Method;

/**
 * Implementation of JAX-RS ResourceInfo that provides access to the matched
 * resource class and method.
 *
 * <p>This allows post-matching filters to inspect the target method's annotations
 * and make decisions based on them.
 */
public class HelidonResourceInfo implements ResourceInfo {

    private final Class<?> resourceClass;
    private final Method resourceMethod;

    /**
     * Create a new ResourceInfo.
     *
     * @param resourceClass the resource class
     * @param resourceMethod the resource method
     */
    public HelidonResourceInfo(Class<?> resourceClass, Method resourceMethod) {
        this.resourceClass = resourceClass;
        this.resourceMethod = resourceMethod;
    }

    /**
     * Create a ResourceInfo by looking up the method by name and parameter types.
     *
     * @param resourceClass the resource class
     * @param methodName the method name
     * @param parameterTypes the method parameter types
     * @return the ResourceInfo
     * @throws RuntimeException if the method cannot be found
     */
    public static HelidonResourceInfo create(Class<?> resourceClass, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = resourceClass.getMethod(methodName, parameterTypes);
            return new HelidonResourceInfo(resourceClass, method);
        } catch (NoSuchMethodException e) {
            // Try declared methods (including private/protected)
            try {
                Method method = resourceClass.getDeclaredMethod(methodName, parameterTypes);
                return new HelidonResourceInfo(resourceClass, method);
            } catch (NoSuchMethodException e2) {
                throw new RuntimeException("Cannot find method " + methodName + " in " + resourceClass.getName(), e2);
            }
        }
    }

    @Override
    public Method getResourceMethod() {
        return resourceMethod;
    }

    @Override
    public Class<?> getResourceClass() {
        return resourceClass;
    }

    @Override
    public String toString() {
        return "HelidonResourceInfo[" + resourceClass.getSimpleName() + "." + resourceMethod.getName() + "]";
    }
}
