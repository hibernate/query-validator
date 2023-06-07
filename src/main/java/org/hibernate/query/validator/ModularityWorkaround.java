/*
 * Copyright (C) 2018-2021 The Project Lombok Authors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.hibernate.query.validator;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * COPY/PASTE from Lombok!
 */
public class ModularityWorkaround {

	private static Object getJdkCompilerModule() {
		/* call public api: ModuleLayer.boot().findModule("jdk.compiler").get();
		   but use reflection because we don't want this code to crash on jdk1.7 and below.
		   In that case, none of this stuff was needed in the first place, so we just exit via
		   the catch block and do nothing.
		 */

		try {
			Class<?> cModuleLayer = Class.forName("java.lang.ModuleLayer");
			Method mBoot = cModuleLayer.getDeclaredMethod("boot");
			Object bootLayer = mBoot.invoke(null);
			Class<?> cOptional = Class.forName("java.util.Optional");
			Method mFindModule = cModuleLayer.getDeclaredMethod("findModule", String.class);
			Object oCompilerO = mFindModule.invoke(bootLayer, "jdk.compiler");
			return cOptional.getDeclaredMethod("get").invoke(oCompilerO);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Useful from jdk9 and up; required from jdk16 and up.
	 * This code is supposed to gracefully do nothing on jdk8
	 * and below, as this operation isn't needed there. */
	public static void addOpens() {
		Class<?> cModule;
		try {
			cModule = Class.forName("java.lang.Module");
		} catch (ClassNotFoundException e) {
			return; //jdk8-; this is not needed.
		}

		Unsafe unsafe = getUnsafe();
		Object jdkCompilerModule = getJdkCompilerModule();
		Object ownModule = getOwnModule();
		String[] allPkgs = {
				"com.sun.tools.javac.code",
				"com.sun.tools.javac.model",
				"com.sun.tools.javac.processing",
				"com.sun.tools.javac.tree",
				"com.sun.tools.javac.util",
				"com.sun.tools.javac.resources"
		};

		try {
			Method m = cModule.getDeclaredMethod("implAddOpens", String.class, cModule);
			long firstFieldOffset = getFirstFieldOffset(unsafe);
			unsafe.putBooleanVolatile(m, firstFieldOffset, true);
			for (String p : allPkgs) {
				m.invoke(jdkCompilerModule, p, ownModule);
			}
		} catch (Exception ignore) {}
	}

	private static long getFirstFieldOffset(Unsafe unsafe) {
		try {
			return unsafe.objectFieldOffset(Parent.class.getDeclaredField("first"));
		} catch (NoSuchFieldException e) {
			// can't happen.
			throw new RuntimeException(e);
		} catch (SecurityException e) {
			// can't happen
			throw new RuntimeException(e);
		}
	}

	private static Unsafe getUnsafe() {
		try {
			Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			return (Unsafe) theUnsafe.get(null);
		} catch (Exception e) {
			return null;
		}
	}

	private static Object getOwnModule() {
		try {
			Method m = Permit.getMethod(Class.class, "getModule");
			return m.invoke(JavacProcessor.class);
		} catch (Exception e) {
			return null;
		}
	}
}
