package org.academy.internal.common.structure.sable.reflect;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Lazy optional linkage with cached members. Views preserve backend object identity while
 * alive, without retaining worlds after unload. Collections are live views: removals and
 * additions must reach Sable's ownership indexes, never a detached copy.
 */
public final class SableReflection {
    private static final String API = "org.academy.internal.common.structure.sable.reflect.api.";
    private static final String EXTERNAL = "dev.ryanhcode.sable.";
    private static final Map<Class<?>, Class<?>> TYPES = new ConcurrentHashMap<>();
    private static final Map<Method, AccessibleObject> MEMBERS = new ConcurrentHashMap<>();
    private static final Map<Key, AccessibleObject> STATICS = new ConcurrentHashMap<>();
    private static final ReferenceQueue<Object> QUEUE = new ReferenceQueue<>();
    private static final Map<IdentityRef, WeakReference<Object>> VIEWS = new HashMap<>();
    private static final ClassValue<Set<Class<?>>> VIEW_TYPES = new ClassValue<>() {
        @Override protected Set<Class<?>> computeValue(Class<?> actual) {
            var result = new LinkedHashSet<Class<?>>();
            collectViews(actual, result);
            return Collections.unmodifiableSet(result);
        }
    };
    private static final Map<Key, StaticCall> STATIC_CALLS = new ConcurrentHashMap<>();

    private SableReflection() { }

    public static Class<?> runtimeType(Class<?> view) {
        if (view.isArray()) return Array.newInstance(runtimeType(view.componentType()), 0).getClass();
        var target = view.getAnnotation(SableType.class);
        if (target == null) return view;
        return TYPES.computeIfAbsent(view, ignored -> {
            try { return Class.forName(target.value(), false, SableReflection.class.getClassLoader()); }
            catch (ClassNotFoundException failure) { throw new IllegalStateException("Optional Sable API unavailable: " + target.value(), failure); }
        });
    }

    public static Object unwrap(Object value) {
        if (value != null && Proxy.isProxyClass(value.getClass()) && Proxy.getInvocationHandler(value) instanceof View handler) {
            return handler.target;
        }
        return value;
    }

    public static String runtimeName(Object value) { return unwrap(value).getClass().getName(); }

    public static Object readField(Object target, String name) {
        try {
            var raw = unwrap(target);
            var key = new Key(raw.getClass(), "field:" + name, List.of());
            var field = (Field) STATICS.computeIfAbsent(key, ignored -> {
                try { return findField(raw.getClass(), name); }
                catch (NoSuchFieldException failure) { throw new IllegalStateException("Unsupported Sable field: " + name, failure); }
            });
            return field.get(raw);
        } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
    }

    public static <T> List<T> list(Class<T> view, List<?> source) {
        return new AbstractList<>() {
            @Override public T get(int index) { return wrap(view, source.get(index)); }
            @Override public int size() { return source.size(); }
        };
    }

    public static <T> T optional(Class<T> view, Object value) {
        return value != null && runtimeType(view).isInstance(unwrap(value)) ? wrap(view, value) : null;
    }

    public static <T> T wrap(Class<T> view, Object value) {
        if (value == null) return null;
        if (view.isInstance(value)) return view.cast(value);
        return view.cast(adapt(view, value));
    }

    private static synchronized Object view(Class<?> requested, Object target) {
        for (IdentityRef ref; (ref = (IdentityRef) QUEUE.poll()) != null;) VIEWS.remove(ref);
        var key = new IdentityRef(target, null);
        var saved = VIEWS.get(key);
        var existing = saved == null ? null : saved.get();
        if (existing != null && requested.isInstance(existing)) return existing;
        var interfaces = new LinkedHashSet<Class<?>>();
        interfaces.add(requested);
        interfaces.addAll(VIEW_TYPES.get(target.getClass()));
        var result = Proxy.newProxyInstance(SableReflection.class.getClassLoader(), interfaces.toArray(Class<?>[]::new), new View(target));
        VIEWS.put(new IdentityRef(target, QUEUE), new WeakReference<>(result));
        return result;
    }

    private static void collectViews(Class<?> actual, Set<Class<?>> result) {
        if (actual == null) return;
        if (actual.getName().startsWith(EXTERNAL)) {
            try {
                var candidate = Class.forName(API + actual.getName().substring(EXTERNAL.length()), false, SableReflection.class.getClassLoader());
                if (candidate.isInterface()) result.add(candidate);
            } catch (ClassNotFoundException ignored) { /* Only the consumed API surface has views. */ }
        }
        collectViews(actual.getSuperclass(), result);
        for (var parent : actual.getInterfaces()) collectViews(parent, result);
    }

    private record View(Object target) implements InvocationHandler {
        @Override public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            Object[] args = arguments == null ? new Object[0] : arguments;
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "equals" -> target.equals(unwrap(args[0]));
                case "hashCode" -> target.hashCode();
                case "toString" -> target.toString();
                default -> throw new IllegalStateException(method.toString());
            };
            var member = MEMBERS.computeIfAbsent(method, SableReflection::resolve);
            Object result;
            if (member instanceof Field field) result = field.get(target);
            else {
                var call = (Method) member;
                result = invokeMethod(call, target, arguments(call.getParameterTypes(), args));
            }
            return adapt(method.getGenericReturnType(), result);
        }
    }

    private static AccessibleObject resolve(Method view) {
        var owner = runtimeType(view.getDeclaringClass());
        var field = view.getAnnotation(SableField.class);
        try {
            if (field != null) return findField(owner, field.value().isEmpty() ? view.getName() : field.value());
            var args = Arrays.stream(view.getParameterTypes()).map(SableReflection::runtimeType).toArray(Class<?>[]::new);
            return findMethod(owner, view.getName(), args);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unsupported Sable API member: " + view, failure);
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>[] args) throws NoSuchMethodException {
        try { return owner.getMethod(name, args); }
        catch (NoSuchMethodException missing) {
            for (var type = owner; type != null; type = type.getSuperclass()) {
                try { var method = type.getDeclaredMethod(name, args); method.setAccessible(true); return method; }
                catch (NoSuchMethodException ignored) { }
            }
            throw missing;
        }
    }

    private static Field findField(Class<?> owner, String name) throws NoSuchFieldException {
        for (var type = owner; type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static Object invokeMethod(Method method, Object target, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    private record Key(Class<?> view, String name, List<Class<?>> args) { }
    private record StaticCall(Method method, Type result) { }

    @SuppressWarnings("unchecked")
    public static <T> T callStatic(Class<?> view, String name, Class<?>[] signature, Object... args) {
        var key = new Key(view, name, List.of(signature));
        try {
            var call = STATIC_CALLS.computeIfAbsent(key, ignored -> {
                try { return new StaticCall(findMethod(runtimeType(view), name,
                        Arrays.stream(signature).map(SableReflection::runtimeType).toArray(Class<?>[]::new)),
                        view.getMethod(name, signature).getGenericReturnType()); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unsupported Sable API: " + key, failure); }
            });
            return (T) adapt(call.result(), invokeMethod(call.method(), null, arguments(call.method().getParameterTypes(), args)));
        } catch (Throwable failure) { return rethrow(failure); }
    }

    public static <T> T construct(Class<T> view, Class<?>[] signature, Object... args) {
        var key = new Key(view, "<init>", List.of(signature));
        try {
            var constructor = (Constructor<?>) STATICS.computeIfAbsent(key, ignored -> {
                try { return runtimeType(view).getConstructor(Arrays.stream(signature).map(SableReflection::runtimeType).toArray(Class<?>[]::new)); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unsupported Sable constructor: " + key, failure); }
            });
            return wrap(view, constructor.newInstance(arguments(constructor.getParameterTypes(), args)));
        } catch (InvocationTargetException failure) { return rethrow(failure.getCause()); }
        catch (Throwable failure) { return rethrow(failure); }
    }

    @SuppressWarnings("unchecked")
    public static <T> T field(Class<?> view, String name) {
        try { return (T) adapt(view.getField(name).getGenericType(), findField(runtimeType(view), name).get(null)); }
        catch (Throwable failure) { return rethrow(failure); }
    }

    private static Object[] arguments(Class<?>[] types, Object[] args) {
        var result = args.clone();
        for (int i = 0; i < result.length; i++) result[i] = argument(types[i], result[i]);
        return result;
    }

    private static Object argument(Class<?> type, Object value) {
        var raw = unwrap(value);
        if (raw != value || value == null || type.isInstance(value) || type.isPrimitive()) return raw;
        if (value instanceof SableView && type.isInterface()) {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                    case "equals" -> proxy == args[0]; case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "Academy Sable observer"; default -> null;
                };
                for (var callback : value.getClass().getMethods()) {
                    if (!callback.getName().equals(method.getName()) || callback.getParameterCount() != method.getParameterCount()) continue;
                    var converted = args == null ? new Object[0] : args.clone();
                    for (int i = 0; i < converted.length; i++) converted[i] = adapt(callback.getGenericParameterTypes()[i], converted[i]);
                    callback.setAccessible(true);
                    return unwrap(invokeMethod(callback, value, converted));
                }
                throw new IllegalStateException("Unsupported Sable callback: " + method);
            });
        }
        return raw;
    }

    private static Object adapt(Type type, Object value) {
        if (value == null) return null;
        if (type instanceof TypeVariable<?> variable) return adapt(variable.getBounds()[0], value);
        if (type instanceof WildcardType wildcard) return adapt(wildcard.getUpperBounds()[0], value);
        if (type instanceof ParameterizedType generic) {
            var raw = (Class<?>) generic.getRawType();
            var params = generic.getActualTypeArguments();
            if (value instanceof List<?> list && raw == List.class) return new AbstractList<Object>() {
                @Override public Object get(int index) { return adapt(params[0], list.get(index)); }
                @Override public int size() { return list.size(); }
                @Override public Object remove(int index) { return adapt(params[0], list.remove(index)); }
                @Override public boolean add(Object item) { return addRaw(list, item); }
                @Override public boolean remove(Object item) { return list.remove(unwrap(item)); }
                @Override public boolean contains(Object item) { return list.contains(unwrap(item)); }
            };
            if (value instanceof Map<?, ?> map && raw == Map.class) return new AbstractMap<Object, Object>() {
                @Override public Object get(Object key) { return adapt(params[1], map.get(unwrap(key))); }
                @Override public Object remove(Object key) { return adapt(params[1], map.remove(unwrap(key))); }
                @Override public Set<Entry<Object, Object>> entrySet() {
                    return new AbstractSet<>() {
                        @Override public int size() { return map.size(); }
                        @Override public Iterator<Entry<Object, Object>> iterator() {
                            var iterator = map.entrySet().iterator();
                            return new Iterator<>() {
                                @Override public boolean hasNext() { return iterator.hasNext(); }
                                @Override public Entry<Object, Object> next() {
                                    var entry = iterator.next();
                                    return new SimpleImmutableEntry<>(adapt(params[0], entry.getKey()), adapt(params[1], entry.getValue()));
                                }
                                @Override public void remove() { iterator.remove(); }
                            };
                        }
                    };
                }
            };
            if (value instanceof Iterable<?> iterable && raw == Iterable.class) {
                return (Iterable<Object>) () -> adaptingIterator(iterable.iterator(), params[0]);
            }
            if (value instanceof Collection<?> collection && (raw == Collection.class || raw == Set.class)) {
                if (raw == Set.class) return new AbstractSet<Object>() {
                    @Override public Iterator<Object> iterator() { return adaptingIterator(collection.iterator(), params[0]); }
                    @Override public int size() { return collection.size(); }
                    @Override public boolean remove(Object item) { return collection.remove(unwrap(item)); }
                    @Override public boolean contains(Object item) { return collection.contains(unwrap(item)); }
                    @Override public boolean add(Object item) { return addRaw(collection, item); }
                };
                return new AbstractCollection<Object>() {
                    @Override public Iterator<Object> iterator() { return adaptingIterator(collection.iterator(), params[0]); }
                    @Override public int size() { return collection.size(); }
                    @Override public boolean remove(Object item) { return collection.remove(unwrap(item)); }
                    @Override public boolean contains(Object item) { return collection.contains(unwrap(item)); }
                    @Override public boolean add(Object item) { return addRaw(collection, item); }
                };
            }
            return adapt(raw, value);
        }
        if (type instanceof Class<?> cls && cls.isAnnotationPresent(SableType.class)) {
            if (cls.isInstance(value)) return value;
            if (!runtimeType(cls).isInstance(value)) throw new IllegalArgumentException("Wrong Sable object for " + cls.getName());
            return view(cls, value);
        }
        return value;
    }

    private static Iterator<Object> adaptingIterator(Iterator<?> iterator, Type itemType) {
        return new Iterator<>() {
            @Override public boolean hasNext() { return iterator.hasNext(); }
            @Override public Object next() { return adapt(itemType, iterator.next()); }
            @Override public void remove() { iterator.remove(); }
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean addRaw(Collection collection, Object value) { return collection.add(unwrap(value)); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> void listen(Class<T> eventView, Consumer<T> listener) {
        listen(eventView, net.neoforged.bus.api.EventPriority.NORMAL, listener);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> void listen(Class<T> eventView, net.neoforged.bus.api.EventPriority priority, Consumer<T> listener) {
        var eventClass = runtimeType(eventView).asSubclass(net.neoforged.bus.api.Event.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                priority, false, (Class) eventClass,
                event -> listener.accept(wrap(eventView, event)));
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T rethrow(Throwable failure) throws E { throw (E) failure; }

    private static final class IdentityRef extends WeakReference<Object> {
        private final int hash;
        IdentityRef(Object target, ReferenceQueue<Object> queue) { super(target, queue); hash = System.identityHashCode(target); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof IdentityRef ref && get() != null && get() == ref.get();
        }
    }
}
