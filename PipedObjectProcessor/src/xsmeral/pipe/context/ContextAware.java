package xsmeral.pipe.context;

import xsmeral.pipe.ObjectProcessorException;

/**
 * Indicates processor's capability of accessing a shared {@link ProcessingContext}.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public interface ContextAware {

    /**
     * Returns the associated processing context.
     */
    ProcessingContext getContext();

    /**
     * Initializes (reflectively) the values of all fields annotated with {@link FromContext}
     * with values taken from the context. The context parameter name is either
     * the field name or a name specified as an argument of {@code FromContext}.
     */
    void initContext() throws ObjectProcessorException;
    
    /**
     * Associates the processor with the given context.
     */
    void setContext(ProcessingContext ctx) throws ObjectProcessorException;
    
}
