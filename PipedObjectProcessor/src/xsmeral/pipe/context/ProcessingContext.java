package xsmeral.pipe.context;

import java.util.Set;

/**
 * A processing context is a shared space and possibly a communication channel between processors.<br />
 * Can be used to share resources.
 * <br />
 * It stores arbitrary objects, mapped by String values.
 *
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public interface ProcessingContext {

    /**
     * Returns the parameter value for supplied name.
     */
    Object getParameterValue(String name);

    /**
     * Creates the parameter for the supplied name and assigns value.
     * Should warn if the parameter already existed.
     * @param name Parameter name
     * @param value Parameter value
     */
    Object setParameter(String name, Object value);

    /**
     * Sets parameter value without warning
     * @param name Parameter name
     * @param value Parameter value
     */
    void setParameterValue(String name, Object value);

    /**
     * Returns set of all parameter names currently present in the map
     * (also those mapped to {@code null}).
     */
    Set<String> getParameterNames();

    /**
     * Removes the parameter for the supplied name.
     * Does nothing if parameter for the name does not exist.
     */
    void removeParameter(String name);
}
