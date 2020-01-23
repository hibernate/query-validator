package org.hibernate.query.validator;

import net.bytebuddy.ByteBuddy;

import java.util.HashMap;
import java.util.Map;

import static net.bytebuddy.implementation.FixedValue.value;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.returns;

public class Mocker {

	private static final Map<Class<?>, Class<?>> mocks = new HashMap<>();

	public static <T> T make(Class<T> clazz) {
		try {
			return load(clazz).newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> T make(Class<T> clazz, Object... args) {
		try {
			return (T) load(clazz).getDeclaredConstructors()[0].newInstance(args);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> Class<? extends T> load(Class<T> clazz) {
		if (mocks.containsKey(clazz)) {
			return (Class<? extends T>) mocks.get(clazz);
		}
		Class<? extends T> mock = new ByteBuddy()
				.subclass(clazz)
				.method(returns(String.class).and(isAbstract()))
				.intercept(value(""))
				.method(returns(boolean.class).and(isAbstract()))
				.intercept(value(false))
				.method(returns(int.class).and(isAbstract()))
				.intercept(value(0))
				.method(returns(long.class).and(isAbstract()))
				.intercept(value(0L))
				.method(returns(int[].class).and(isAbstract()))
				.intercept(value(new int[0]))
				.make()
				.load(clazz.getClassLoader())
				.getLoaded();
		mocks.put(clazz,mock);
		return mock;
	}
}
