package xsmeral.semnet.manager;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import java.util.Map;
import xsmeral.semnet.xstream.ConfigurationConverter;

/**
 * Container for an object processor class and its configuration parameters.
 */
@XStreamAlias("processor")
@XStreamConverter(ConfigurationConverter.class)
public class Configuration {

    private final int hashCode;
    private final Class clazz;
    private final Map<String, String> params;

    /**
     * Initializes fields and computes hash code in advance.
     */
    public Configuration(Class processor, Map<String, String> params) {
        this.clazz = processor;
        this.params = params;
        this.hashCode = hashCodeInternal(clazz, params);
    }

    /**
     * Returns parameter map, that is used to initialize the processor.
     */
    public Map<String, String> getParams() {
        return params;
    }

    /**
     * Returns class of the object processor.
     */
    public Class getClazz() {
        return clazz;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Configuration other = (Configuration) obj;
        if (this.clazz != other.clazz && (this.clazz == null || !this.clazz.equals(other.clazz))) {
            return false;
        }
        if (this.params != other.params && (this.params == null || !this.params.equals(other.params))) {
            return false;
        }
        return true;
    }

    /**
     * Calculates hashCode the usual way.
     */
    private int hashCodeInternal(Class clazz, Map<String, String> params) {
        int hash = 7;
        hash = 23 * hash + (clazz != null ? clazz.hashCode() : 0);
        hash = 23 * hash + (params != null ? params.hashCode() : 0);
        return hash;
    }

    /**
     * Returns hashCode that is computed the usual way during construction.
     * HashCode is cached, because fields can only be set once.
     */
    @Override
    public final int hashCode() {
        return hashCode;
    }
}
