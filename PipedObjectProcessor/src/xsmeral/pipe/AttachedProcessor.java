package xsmeral.pipe;

import java.util.Map;
import xsmeral.pipe.context.ProcessingContext;

/**
 * An attached processor can be used to inject resources, monitor object processors,
 * control the pipe or manipulate the processing context.
 * It runs independently of object processors, in a daemon thread.
 * The associated Pipe shouldn't be referenced in the constructor or the
 * {@link #initialize(java.util.Map) initialize} method.
 * @see Pipe
 * @see ProcessingContext
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public interface AttachedProcessor extends Runnable {

    /**
     * Initializes the processor with given parameter map and returns itself.
     */
    public AttachedProcessor initialize(Map<String, String> params);

    /**
     * Called before context is assigned to object processors, can be used for
     * resource injection.
     */
    public void preContext();

    /**
     * Called after context has been assigned to all processors. 
     */
    public void postContext();

    /**
     * Called after all object processors in chain stopped running.
     */
    public void chainStopped();

    /**
     * Signals the processor to release resources and cease running.
     */
    public void stop();

    /**
     * Associates the processor with a Pipe.
     */
    public void setPipe(Pipe pipe);

    /**
     * Indicates whether this processor has met conditions for starting.
     */
    public boolean canStart();

}
