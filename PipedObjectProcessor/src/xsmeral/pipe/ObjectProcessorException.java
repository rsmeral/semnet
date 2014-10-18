package xsmeral.pipe;

/**
 * Indicates a problem while starting a processor chain (e.g. non-matching or
 * uninitialized processors)
 *
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class ObjectProcessorException extends Exception {

    public ObjectProcessorException() {
    }

    public ObjectProcessorException(String msg) {
        super(msg);
    }

    public ObjectProcessorException(String message, Throwable cause) {
        super(message, cause);
    }
}
