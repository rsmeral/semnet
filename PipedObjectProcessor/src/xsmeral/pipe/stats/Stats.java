package xsmeral.pipe.stats;

import java.util.logging.Level;
import java.util.logging.Logger;
import xsmeral.pipe.context.ProcessingContext;
import xsmeral.pipe.interfaces.ObjectProcessor;

// TODO: maybe create module for conditional actions based on stats
/**
 * Provides means of monitoring statistics of object processors.
 * <br />
 * The monitoring points are created using the {@link #newFunction(java.lang.String, java.lang.Class) newFunction}
 * method and results are stored in a processing context. <br />
 * Values can be accessed directly (by methods of ProcessingContext) or by
 * {@link StatsReader} designed specifically for this purpose.<br />
 * The names of context parameters that hold the values have specific format: <br />
 * <pre>    stats.[group].[name]</pre>
 * where <code>[group]</code> is either a class name of the processor or name
 * of other logical grouping and <code>[name]</code> is name of one monitored
 * value.
 *
 * @see ProcessingContext
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class Stats {

    /*
     * The "namespace" for stats in processing context.
     */
    public static final String PARAM_STATS = "stats";
    private static final String PARAM_FORMAT = "%s.%s.%s";
    public static final String DEF_GROUP = "default";

    /**
     * The main implementation of StatsReader.
     * Provides methods for reading and conversion of stat values stored in
     * processing context.
     * The methods automatically convert values, i.e. an <code>int</code> can
     * be accessed with {@link #getDouble(java.lang.String) getDouble} and vice
     * versa.
     */
    public static class Reader implements StatsReader {

        private final ProcessingContext ctx;

        private Reader(ProcessingContext ctx) {
            this.ctx = ctx;
        }

        /**
         * {@inheritDoc}
         * Automatically casts to output type.
         * @return The value as Double, or null if no such parameter exists or its value is not a Number.
         */
        @Override
        public Double getDouble(String fullStatName) {
            Object value = ctx.getParameterValue(fullStatName);
            if (value != null && value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return null;
        }

        /**
         * {@inheritDoc}
         * Automatically casts to output type.
         * @return The value as Double, or null if no such parameter exists or its value is not a Number.
         */
        @Override
        public Double getDouble(String group, String stat) {
            return getDouble(String.format(PARAM_FORMAT, PARAM_STATS, group, stat));
        }

        /**
         * {@inheritDoc}
         * Automatically casts to output type.
         * @return The value as Long, or null if no such parameter exists or its value is not a Number.
         */
        @Override
        public Long getLong(String fullStatName) {
            Object value = ctx.getParameterValue(fullStatName);
            if (value != null && value instanceof Number) {
                return ((Number) value).longValue();
            }
            return null;
        }

        /**
         * {@inheritDoc}
         * Automatically casts to output type.
         * @return The value as Long, or null if no such parameter exists or its value is not a Number.
         */
        @Override
        public Long getLong(String group, String stat) {
            return getLong(String.format(PARAM_FORMAT, PARAM_STATS, group, stat));
        }
    }

    /**
     * Internal implementation of StatFunction that reflects the value of the function
     * in the context parameter value with each function call.
     * All calls are delegated to a supplied function.
     * This is the return type of {@link #newFunction(java.lang.String, java.lang.Class) newFunction()}.
     * @param <T> Numeric type
     */
    private class FunctionWrapper<T extends Number> implements StatFunction<T> {

        private final StatFunction<T> func;
        private final String name;
        private final String ctxName;

        public FunctionWrapper(String name, StatFunction<T> func) {
            this.func = func;
            this.name = name;
            this.ctxName = String.format(PARAM_FORMAT, PARAM_STATS, group, this.name);
        }

        @Override
        public T getValue() {
            return func.getValue();
        }

        @Override
        public void add() {
            func.add();
            ctx.setParameterValue(ctxName, func.getValue());
        }

        @Override
        public void add(T value) {
            func.add(value);
            ctx.setParameterValue(ctxName, func.getValue());
        }

        @Override
        public void reset() {
            func.reset();
            ctx.setParameterValue(ctxName, func.getValue());
        }

    }

    private String group;
    private ProcessingContext ctx;

    /**
     * Convenience constructor, creates the stat with <tt>group</tt> equal to
     * the processor's simple class name and its associated context.
     */
    public Stats(ObjectProcessor processor) {
        this.ctx = processor.getContext();
        if (ctx == null) {
            throw new IllegalArgumentException("Supplied processor has no context.");
        }
        this.group = processor.getClass().getSimpleName();
    }

    /**
     * Creates a Stats instance for the specified group and context.
     * If the group is null or empty, {@linkplain #DEF_GROUP default} group name 
     * is used.
     */
    public Stats(String group, ProcessingContext ctx) {
        if (group == null || group.isEmpty()) {
            this.group = DEF_GROUP;
            Logger.getLogger(Stats.class.getName()).log(Level.WARNING, "Invalid stats group name supplied, using default");
        } else {
            this.group = group;
        }
        this.ctx = ctx;
    }

    /**
     * Returns group name, which is either processor's simple class name or
     * any other arbitrary name.
     */
    public String getGroup() {
        return group;
    }

    /**
     * Returns the associated context, where values are stored.
     */
    public ProcessingContext getContext() {
        return ctx;
    }

    /**
     * Convenience method.
     * Same as calling
     * <pre>  new Stats.Reader(ctx)</pre>
     */
    public static Reader getReader(ProcessingContext ctx) {
        return new Reader(ctx);
    }

    /**
     * Returns StatFunction instance associated with this instance's context.
     * <br />
     * Runtime type of the instance returned is NOT the same as the second parameter,
     * it is rather a custom implementation that forwards all calls to the supplied
     * Function but at the same time writes them to the context.
     * @param name Name of the stat
     * @param function The StatFunction implementation to use
     */
    public <T extends Number> StatFunction<T> newFunction(String name, Class<? extends StatFunction<T>> function) {
        try {
            StatFunction<T> func = function.newInstance();
            return new FunctionWrapper<T>(name, func);
        } catch (InstantiationException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(Stats.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
