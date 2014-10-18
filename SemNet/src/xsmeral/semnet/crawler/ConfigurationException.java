package xsmeral.semnet.crawler;

/**
 * Indicates a problem with user configuration.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class ConfigurationException extends Exception {

    public ConfigurationException() {
        super();
    }

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(Throwable cause) {
        super(cause);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
