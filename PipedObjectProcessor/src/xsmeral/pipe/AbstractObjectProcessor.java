package xsmeral.pipe;

import java.util.logging.Level;
import java.util.logging.Logger;
import xsmeral.pipe.context.ProcessingContext;
import java.lang.reflect.Field;
import java.util.Map;
import xsmeral.pipe.context.FromContext;
import xsmeral.pipe.context.ToContext;
import xsmeral.pipe.interfaces.ObjectProcessor;
import xsmeral.pipe.interfaces.ObjectProcessor.Status;
import xsmeral.pipe.interfaces.ObjectProcessorInterface;

/**
 * Implements basic functionality common for all object processors.
 * <br />
 * Provides default (however, overloadable) implementation of the {@link #run() run}
 * method, which defines a specific life-cycle of the processor, with multiple 
 * initialization phases and life-cycle stages.
 * <br />
 * The complete cycle is as follows:
 * <br />
 * 
 * <a name="initialization" />
 * <h3>Initialization</h3>
 * The {@link #initialize(java.util.Map) initialize} method
 * <ol>
 *  <li>initializes parameters using {@link ParamInitializer}</li>
 *  <li>calls {@link #initializeInternal() initializeInternal()}</li>
 *  <li>marks the processor as initialized, so that any following invocation of
 *      this method returns immediately</li>
 * </ol>
 * Therefore, in {@code initializeInternal} the processor can access the initialization
 * parameters but not yet the context parameters.
 * <br />
 *
 * <a name="context" />
 * <h3>Context initialization</h3>
 * The context-related methods accessible from outside should be called in the
 * following order:
 * <ol>
 *  <li>{@link #setContext(xsmeral.pipe.context.ProcessingContext) setContext()}, which assigns
 *      the processor a context and in turn calls:
 *   <ol>
 *    <li>{@link #initWithContext() initWithContext()} which can be used to access
 *        context parameters already set by an external entity (not those set by
 *        other processors)
 *    </li>
 *    <li>{@link #initContextSet() initContextSet()} which sets context parameter
 *        values from {@link ToContext} fields
 *    </li>
 *   </ol>
 *  </li>
 *  <li>{@link #initContext() initContext()}, which initializes fields annotated
 *      with {@link FromContext} with values from context and in turn calls:
 *   <ol>
 *    <li>{@link #initPostContext() initPostContext()} at which time all context
 *        parameters and all context-dependent fields can be accessed</li>
 *   </ol>
 *  </li>
 * </ol>
 * <br />
 *
 * <a name="running" />
 * <h3>Running</h3>
 * After initialization (which is, however, not required), the processor can be
 * started, using the {@link #run() run()} method. The default run cycle is as
 * follows:
 * <ol>
 *  <li>the processor is in {@link Status#NOT_STARTED NOT_STARTED} state
 *  <li>{@link #preRun() preRun()} is called, where the processor
 *      can perform any preparatory operations</li>
 *  <li>status is set to {@link Status#RUNNING RUNNING}</li>
 *  <li>{@link #process() process()} is called in loop, while the status is 
 *      {@code RUNNING}. If a neighboring processor stops, this processor
 *      {@link #stop() stop}s.</li>
 *  <li>{@link #postRun() postRun()} is called, which can be used for cleanup
 *      and releasing resources</li>
 *
 * </ol>
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public abstract class AbstractObjectProcessor implements ObjectProcessor {

    private Map<String, String> params;
    /**
     * Current status
     */
    protected Status status;
    /**
     * Associated context (might be null)
     */
    protected ProcessingContext context;
    /**
     * Starting condition indicator ({@code true} by default)
     */
    protected boolean canStart = true;
    private boolean initialized = false;

    /**
     * Sets the status to {@link Status#NOT_STARTED NOT_STARTED}.
     */
    public AbstractObjectProcessor() {
        status = Status.NOT_STARTED;
    }

    @Override
    public final Status getStatus() {
        return this.status;
    }

    /**
     * Returns the parameter map that was used to initialize this processor.
     */
    public Map<String, String> getParams() {
        return params;
    }

    @Override
    public boolean canStart() {
        return canStart;
    }

    /**
     * Called by the processor itself, indicates negative starting conditions.
     */
    protected final void failStart() {
        canStart = false;
    }

    /**
     * In addition to {@link #failStart() failStart()}, logs a message
     * indicating the reason for the inability to start
     */
    public final void failStart(String message) {
        canStart = false;
        Logger.getLogger(getClass().getName()).log(Level.SEVERE, message);
    }

    /**
     * In addition to {@link #failStart(java.lang.String) failStart(String)},
     * logs a Throwable
     */
    public final void failStart(String message, Throwable thrown) {
        canStart = false;
        Logger.getLogger(getClass().getName()).log(Level.SEVERE, message, thrown);
    }

    @Override
    public void requestStop() {
        status = Status.STOPPING;
    }

    /**
     * {@inheritDoc}
     * More information in description of {@link AbstractObjectProcessor} class, section "Running".
     */
    @Override
    public void run() {
        try {
            preRun();
            status = Status.RUNNING;
            while (status == Status.RUNNING) {
                process();
            }
        } catch (ProcessorStoppedException ex) {
            stop();
        }
        postRun();
    }

    @Override
    public final Class getOutType() {
        return getClass().getAnnotation(ObjectProcessorInterface.class).out();
    }

    @Override
    public final Class getInType() {
        return getClass().getAnnotation(ObjectProcessorInterface.class).in();
    }

    /**
     * Called by the processor itself, sets the status to {@link Status#STOPPED STOPPED}
     */
    protected void stop() {
        status = Status.STOPPED;
    }

    /**
     * {@inheritDoc}
     * Might return null.
     */
    @Override
    public final ProcessingContext getContext() {
        return context;
    }

    /**
     * {@inheritDoc}
     * Also, initializes context parameters from fields annotated with {@link ToContext}.
     */
    @Override
    public final void setContext(ProcessingContext context) throws ObjectProcessorException {
        this.context = context;
        initWithContext();
        initContextSet();
    }

    /**
     * Initializes (reflectively) context parameters from fields annotated with {@link ToContext}.
     * @throws ObjectProcessorException If a parameter already exists in the context.
     */
    protected final void initContextSet() throws ObjectProcessorException {
        Field[] fields = getClass().getDeclaredFields();
        for (Field f : fields) {
            ToContext annot = f.getAnnotation(ToContext.class);
            if (annot != null) {
                try {
                    String paramName = !annot.value().isEmpty() ? annot.value() : f.getName();
                    f.setAccessible(true);
                    Object newValue = f.get(this);
                    Object prevValue = getContext().setParameter(paramName, newValue);
                    if (prevValue != null) {
                        throw new ObjectProcessorException("Can't overwrite context parameter at initialization: " + getClass().getName() + " tried to overwrite param '" + paramName + "'");
                    }
                } catch (IllegalArgumentException ex) {
                    failStart("Can't set context parameter", ex);
                } catch (IllegalAccessException ex) {
                    failStart("Can't set context parameter", ex);
                }
            }
        }
    }

    @Override
    public final void initContext() throws ObjectProcessorException {
        Field[] fields = getClass().getDeclaredFields();
        for (Field f : fields) {
            FromContext annot = f.getAnnotation(FromContext.class);
            if (annot != null) {
                try {
                    String paramName = !annot.value().isEmpty() ? annot.value() : f.getName();
                    Object val = getContext().getParameterValue(paramName);
                    if (val == null) {
                        throw new ObjectProcessorException("Unsatisfied context parameter dependency in " + getClass().getName() + " for parameter '" + paramName + "'");
                    } else if (!f.getType().isAssignableFrom(val.getClass())) {
                        throw new ObjectProcessorException("Context parameter type mismatch in " + getClass().getName() + ". Required: " + f.getType().getName() + ", supplied: " + val.getClass().getName());
                    }
                    f.setAccessible(true);
                    f.set(this, val);
                } catch (IllegalArgumentException ex) {
                    failStart("Can't get context parameter", ex);
                } catch (IllegalAccessException ex) {
                    failStart("Can't get context parameter", ex);
                }
            }
        }
        initPostContext();
    }

    /**
     * {@inheritDoc}
     * <br />
     * More information is provided in description of {@link AbstractObjectProcessor} class, section "Initialization".
     */
    @Override
    public final ObjectProcessor initialize(Map<String, String> params) {
        if (!initialized) {
            this.params = params;
            try {
                ParamInitializer.initialize(this, params);
            } catch (Exception ex) {
                failStart(ex.getMessage());
            }
            if (canStart) {
                initializeInternal();
                initialized = true;
            }
        }
        return this;
    }

    /**
     * Returns simple class name of this processor.
     * @return {@code getClass().getSimpleName()}
     */
    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    /**
     * Should initialize the processor for running, analogically to constructor.
     * More information in description of {@link AbstractObjectProcessor} class, section "Initialization".
     */
    protected void initializeInternal() {
    }

    /**
     * Called after a context is assigned.
     * More information in description of {@link AbstractObjectProcessor} class, section "Context initialization".
     */
    protected void initWithContext() {
    }

    /**
     * Called after context-dependencies ({@link FromContext} fields) are resolved.
     * More information in description of {@link AbstractObjectProcessor} class, section "Context initialization".
     */
    protected void initPostContext() {
    }

    /**
     * Called after initialization, as the first statement in {@link #run() run()}.
     * More information in description of {@link AbstractObjectProcessor} class, section "Running".
     * @throws ProcessorStoppedException
     */
    protected void preRun() throws ProcessorStoppedException {
    }

    /**
     * Called as the last statement in {@link #run() run()}.
     * More information in description of {@link AbstractObjectProcessor} class, section "Running".
     * @throws ProcessorStoppedException
     */
    protected void postRun() {
    }

    /**
     * Called by the processor itself from {@link #run() run()}.
     * Should process one object and return.
     * More information in description of {@link AbstractObjectProcessor} class, section "Running".
     * @throws ProcessorStoppedException If a neighboring processor has stopped.
     */
    protected abstract void process() throws ProcessorStoppedException;
}
