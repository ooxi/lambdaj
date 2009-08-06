// Modified or written by Ex Machina SAGL for inclusion with lambdaj.
// Copyright (c) 2009 Mario Fusco, Luca Marrocco.
// Licensed under the Apache License, Version 2.0 (the "License")

package ch.lambdaj.function.argument;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import ch.lambdaj.proxy.*;

/**
 * An utility class of static factory methods that creates arguments and binds them with their placeholders
 * @author Mario Fusco
 */
public final class ArgumentsFactory {

	private ArgumentsFactory() { }
	
	// ////////////////////////////////////////////////////////////////////////
	// /// Factory
	// ////////////////////////////////////////////////////////////////////////
	
    /**
     * Constructs a proxy object that mocks the given Class registering all the subsequent invocations on the object.
     * @param clazz The class of the object to be mocked
     * @return An object of the given class that register all the invocations made on it
     */
	public static <T> T createArgument(Class<T> clazz) {
		return createArgument(clazz, new InvocationSequence(clazz));
	}
	
	private static final Map<InvocationSequence, Object> placeholderByInvocation = new WeakHashMap<InvocationSequence, Object>();
    
	@SuppressWarnings("unchecked")
	static <T> T createArgument(Class<T> clazz, InvocationSequence invocationSequence) {
		T placeholder = (T)placeholderByInvocation.get(invocationSequence);
		boolean isNewPlaceholder = placeholder == null;
		
		if (isNewPlaceholder) {
			placeholder = (T)createPlaceholder(clazz, invocationSequence);
	    	placeholderByInvocation.put(invocationSequence, placeholder);
		}
		
		if (isNewPlaceholder || isLimitedValues(placeholder))
			bindArgument(placeholder, new Argument<T>(invocationSequence));
		
		return placeholder;
	}
	
	private static Object createPlaceholder(Class<?> clazz, InvocationSequence invocationSequence) {
		return !Modifier.isFinal(clazz.getModifiers()) ? 
				ProxyUtil.createIterableProxy(new ProxyArgument(clazz, invocationSequence), clazz) : 
				createArgumentPlaceholder(clazz);
	}

	// ////////////////////////////////////////////////////////////////////////
	// /// Arguments
	// ////////////////////////////////////////////////////////////////////////
	
	private static final Map<Object, Argument<?>> argumentsByPlaceholder = new WeakHashMap<Object, Argument<?>>();
	
    private static <T> void bindArgument(T placeholder, Argument<T> argument) {
    	if (isLimitedValues(placeholder)) limitedValuesArguments.get().setArgument(placeholder, argument);
    	else argumentsByPlaceholder.put(placeholder, argument);
    }

    /**
     * Converts a placeholder with the actual argument to which is bound
     * @param placeholder The placeholder used to retrieve a registered argument
     * @return The argument bound to the given placeholder
     */
	@SuppressWarnings("unchecked")
    public static <T> Argument<T> actualArgument(T placeholder) {
    	if (placeholder instanceof Argument) return (Argument<T>)placeholder;
    	Argument<T> actualArgument = (Argument<T>)(isLimitedValues(placeholder) ? limitedValuesArguments.get().getArgument(placeholder) : argumentsByPlaceholder.get(placeholder));
    	if (actualArgument == null) throw new RuntimeException("Unable to convert the placeholder " + placeholder + " in a valid argument");
    	return actualArgument;
    }
    
	private static final ThreadLocal<LimitedValuesArgumentHolder> limitedValuesArguments = new ThreadLocal<LimitedValuesArgumentHolder>() {
        protected LimitedValuesArgumentHolder initialValue() {
            return new LimitedValuesArgumentHolder();
        }
    };
    
    private static boolean isLimitedValues(Object placeholder) {
    	return placeholder != null && isLimitedValues(placeholder.getClass());
    }
    
    private static boolean isLimitedValues(Class<?> clazz) {
    	return clazz == Boolean.TYPE || clazz == Boolean.class || clazz.isEnum();
    }
    
    private static final class LimitedValuesArgumentHolder {
    	
    	private boolean booleanPlaceholder = true;
    	private final Argument<?>[] booleanArguments = new Argument[2];

    	private int enumPlaceholder = 0;
    	private final Map<Object, Argument<?>> enumArguments = new HashMap<Object, Argument<?>>();
    	
    	private int booleanToInt(Object placeholder) {
        	return (Boolean)placeholder ? 1 : 0;
        }
    	
    	public void setArgument(Object placeholder, Argument<?> argument) {
    		if (placeholder.getClass().isEnum()) enumArguments.put(placeholder, argument);
    		else booleanArguments[booleanToInt(placeholder)] = argument;
    	}

    	public Argument<?> getArgument(Object placeholder) {
    		return placeholder.getClass().isEnum() ? enumArguments.get(placeholder) : booleanArguments[booleanToInt(placeholder)];
    	}
    	
    	@SuppressWarnings("unchecked")
    	public Object getNextPlaceholder(Class<?> clazz) {
    		return clazz.isEnum() ? getNextEnumPlaceholder((Class<? extends Enum>)clazz) : getNextBooleanPlaceholder(); 
    	}
    	
    	private boolean getNextBooleanPlaceholder() {
    		booleanPlaceholder = !booleanPlaceholder;
    		return booleanPlaceholder;
    	}
    	
    	private <E extends Enum<E>> Enum<E> getNextEnumPlaceholder(Class<E> clazz) {
    		List<E> enums = new ArrayList<E>(EnumSet.allOf(clazz));
    		return enums.get(enumPlaceholder++ % enums.size());
    	}
    }
    
	// ////////////////////////////////////////////////////////////////////////
	// /// Placeholders
	// ////////////////////////////////////////////////////////////////////////
	
    @SuppressWarnings("unchecked")
    public static <T> T createFinalArgumentPlaceholder(Class<T> clazz) {
    	if (clazz == Boolean.TYPE || clazz == Boolean.class) return (T)Boolean.FALSE; 
    	if (clazz.isEnum()) return (T)EnumSet.allOf((Class<? extends Enum>)clazz).iterator().next();
    	return (T)createArgumentPlaceholder(clazz, Integer.MIN_VALUE+1);
	}
    
    private static final AtomicInteger placeholderCounter = new AtomicInteger(Integer.MIN_VALUE);
    
    static int getNextPlaceholderId() {
    	return placeholderCounter.addAndGet(1);
    }
    
    public static Object createArgumentPlaceholder(Class<?> clazz) {
    	return isLimitedValues(clazz) ? limitedValuesArguments.get().getNextPlaceholder(clazz) : createArgumentPlaceholder(clazz, placeholderCounter.addAndGet(1));
	}
	
    private static Object createArgumentPlaceholder(Class<?> clazz, Integer placeholderId) {
		if (clazz.isPrimitive() || Number.class.isAssignableFrom(clazz)) 
			return getPrimitivePlaceHolder(clazz, placeholderId);
		
		if (clazz == String.class) return String.valueOf(placeholderId);
		if (Date.class.isAssignableFrom(clazz)) return new Date(placeholderId);
		if (clazz.isArray()) return Array.newInstance(clazz.getComponentType(), 1);

		try {
			return clazz.newInstance();
		} catch (Exception e) {
			throw new RuntimeException("It is not possible to create a placeholder for class: " + clazz.getName());
		}
    }
    
    private static Object getPrimitivePlaceHolder(Class<?> clazz, Integer placeholderId) {
		try {
			return ArgumentsFactory.class.getMethod(clazz.getSimpleName().substring(0, 3).toLowerCase() + "Placeholder", Integer.class).invoke(null, placeholderId);
		} catch (Exception e) {
			throw new RuntimeException("Unable to create placeholder", e);
		}
	}
	
	public static Integer intPlaceholder(Integer i) {
		return i;
	}

	public static Character chaPlaceholder(Integer i) {
		return Character.forDigit(i % Character.MAX_RADIX, Character.MAX_RADIX);
	}
	
	public static Byte bytPlaceholder(Integer i) {
		return i.byteValue();
	}
	
	public static Short shoPlaceholder(Integer i) {
		return i.shortValue();
	}
	
	public static Long lonPlaceholder(Integer i) {
		return i.longValue();
	}

	public static Float floPlaceholder(Integer i) {
		return i.floatValue();
	}
	
	public static Double douPlaceholder(Integer i) {
		return i.doubleValue();
	}
}