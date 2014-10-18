package xsmeral.pipe.stats;

/**
 * Statistical function that can return a result from any number of input values.
 */
public interface StatFunction<T extends Number> {

    /**
     * Adds one unit (may not make sense for all functions).
     */
    public void add();

    /**
     * Adds the specified value.
     */
    public void add(T value);

    /**
     * Returns result of this function over all values supplied since instantiation or last reset.
     */
    public T getValue();

    /**
     * Resets value of this function to the initial state.
     */
    public void reset();
}
