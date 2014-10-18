package xsmeral.pipe.interfaces;

import java.util.Map;
import xsmeral.pipe.context.ContextAware;

/**
 * An object processor is a runnable command which performs a specific task.
 * Object processors are usually executed in a chain, with objects flowing from
 * the first one to the last, each one performing a transformation on the object
 * or emitting new objects based on information from the one received.
 * <br />
 *
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public interface ObjectProcessor extends Runnable, ContextAware {

    /**
     * Initializes the processor. Should only be called once per instance.
     * After returning from this method, the processor should be ready to start
     * (or indicate inability to start when queried by {@link #canStart() canStart}).
     * <br />
     *
     * @param params Initialization parameters, substitute for constructor arguments
     * @return Should return {@code this}, so that the processor can be instantiated
     * and initialized in one command:<br />
     * <pre>    new SomeProcessor().initialize(params);</pre>
     */
    public ObjectProcessor initialize(Map<String, String> params);

    /**
     * Describes the status of the processor
     */
    public enum Status {

        /**
         * The processor has not yet been started
         */
        NOT_STARTED,
        /**
         * The processor is processing objects
         */
        RUNNING,
        /**
         * In this state, the processor should finish processing as soon as possible
         */
        STOPPING,
        /**
         * The processor is not processing and shouldn't be started again.
         * The processor is in this state if<br />
         * <ul>
         * <li>the previous processor has stopped and the queue is empty</li>
         * <li>it encountered an error</li>
         * <li>it has finished processing</li>
         * </ul>
         */
        STOPPED
    }

    /**
     * Returns the input type of this processor.
     */
    public Class getInType();

    /**
     * Returns the output type of this processor.
     */
    public Class getOutType();

    /**
     * Returns current status of this processor.
     */
    Status getStatus();

    /**
     * Starts the processing, putting the processor into {@link Status#RUNNING running state}.
     */
    @Override
    void run();

    /**
     * Requests the processor to stop and sets its status to {@link Status#STOPPING STOPPING}.
     * There is no limit to the time it takes the processor to stop, it should
     * however stop as soon as possible. <br />
     * Only the first processor in chain should be requested to stop.
     */
    void requestStop();

    /**
     * Indicates whether the processor is ready to start (is correctly initialized).
     * @return True, if the processor is ready to start.
     */
    boolean canStart();
}
