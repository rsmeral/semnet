package xsmeral.pipe;

import java.util.logging.Level;
import java.util.logging.Logger;
import xsmeral.pipe.context.PipeContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import xsmeral.pipe.interfaces.ObjectSource;
import xsmeral.pipe.interfaces.ObjectSink;
import xsmeral.pipe.interfaces.ObjectProcessor;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import xsmeral.pipe.interfaces.ObjectProcessor.Status;

/**
 * A simple implementation of the Chain of Responsibility pattern.
 * Works similar to a unix pipe, working over objects, with processors executed
 * in separate threads.<br />
 *
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class Pipe {

    private class AttachedProcessorUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

        private AttachedProcessor att;

        public AttachedProcessorUncaughtExceptionHandler(AttachedProcessor att) {
            this.att = att;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            Logger.getLogger(Pipe.class.getName()).log(Level.WARNING, "Uncaught exception in attached processor {0}: {1}", new Object[]{att.getClass().getName(), e.getMessage()});
        }
    }

    private final List<ObjectProcessor> processors;
    private final Collection<AttachedProcessor> attached;
    private List<Thread> threads;
    private PipeContext ctx;

    /**
     * In addition to {@link #Pipe(java.util.List)} also sets attached processors.
     * @see AttachedProcessor
     * @param processors List of processors, ordered from the first (source) to last (sink).
     */
    public Pipe(List<ObjectProcessor> processors, Collection<AttachedProcessor> attached) {
        this.processors = processors;
        this.attached = new ArrayBlockingQueue<AttachedProcessor>(Math.max(attached.size(), 1), false, attached);
        this.threads = new ArrayList<Thread>(processors.size());
        ctx = new PipeContext(this);
    }

    /**
     * Initializes the pipe with supplied chain of processors and instantiates
     * a context.
     * @param processors List of processors, ordered from the first (source) to last (sink).
     */
    public Pipe(List<ObjectProcessor> processors) {
        this(processors, Collections.<AttachedProcessor>emptyList());
    }

    /**
     * Does the same as calling {@link #Pipe(java.util.List) Pipe}{@code (Arrays.asList(processors))}
     * @param processors Array of processors
     */
    public Pipe(ObjectProcessor[] processors) {
        this(Arrays.asList(processors));
    }

    /**
     * Returns list of processors in this chain, order from input to output
     */
    public List<ObjectProcessor> getProcessors() {
        return processors;
    }

    /**
     * Returns list of attached processors.
     */
    public Collection<AttachedProcessor> getAttached() {
        return attached;
    }

    /**
     * Returns the context associated with this pipe and all processors.
     */
    public PipeContext getContext() {
        return ctx;
    }

    /**
     * Returns the status of this pipe, determined by the status of the processors.
     */
    public Status getStatus() {
        int running = 0;
        int stopped = 0;

        if (processors.get(0).getStatus() == Status.NOT_STARTED) {
            return Status.NOT_STARTED;
        }
        for (ObjectProcessor p : processors) {
            switch (p.getStatus()) {
                case RUNNING:
                    if (stopped != 0) {
                        return Status.STOPPING;
                    }
                    running++;
                    break;
                case STOPPING:
                    return Status.STOPPING;
                case STOPPED:
                    if (running != 0) {
                        return Status.STOPPING;
                    }
                    stopped++;
                    break;
            }
        }
        int procCount = processors.size();
        if (running == procCount) {
            return Status.RUNNING;
        } else if (stopped == procCount) {
            return Status.STOPPED;
        } else {
            return Status.STOPPING;
        }
    }

    /**
     * Connects processors, checks input-output type match, assigns and initializes context.
     * More information about context initialization in description of
     * {@link AbstractObjectProcessor} class, section "Context initialization".
     *
     * @param blocking If set to true, the method doesn't return until all
     * processor threads stop
     * @throws ObjectProcessorException If a condition for starting was not met
     * (non-matching outputs/inputs, processor fails to start, invalid object
     * is passed as processor, context parameter dependency conflict)
     */
    public void start(boolean blocking) throws ObjectProcessorException {
        // connect processors, check in/out types
        for (int i = 0; i < processors.size() - 1; i++) {
            try {
                ObjectProcessor current = processors.get(i);
                ObjectProcessor next = processors.get(i + 1);
                if (next instanceof ObjectSink
                        && current instanceof ObjectSource
                        && (current.getOutType().equals(next.getInType())
                        || current.getOutType().equals(Object.class)
                        || next.getInType().equals(Object.class))) {
                    ((ObjectSource) current).next((ObjectSink) next);
                } else {
                    throw new ObjectProcessorException("Processors do not match");
                }
            } catch (ClassCastException ex) {
                throw new ObjectProcessorException("Processors do not match", ex);
            }
        }

        for (AttachedProcessor att : attached) {
            att.setPipe(this);
        }

        for (AttachedProcessor att : attached) {
            att.preContext();
        }
        // set context
        for (ObjectProcessor p : processors) {
            p.setContext(ctx);
        }

        // init fields dependent on context, must be run after all processors have set context
        for (ObjectProcessor p : processors) {
            p.initContext();
        }

        // check starting condition, then run, first to last
        boolean canStart = true;
        ObjectProcessor failed = null;
        for (Iterator<ObjectProcessor> it = processors.iterator(); it.hasNext() && canStart; canStart = canStart && (failed = it.next()).canStart());
        if (canStart) {
            for (AttachedProcessor att : attached) {
                att.postContext();
                Thread attThread = new Thread(att);
                attThread.setUncaughtExceptionHandler(new AttachedProcessorUncaughtExceptionHandler(att));
                attThread.setDaemon(true);
                attThread.start();
            }
            for (int i = 0; i < processors.size(); i++) {
                ObjectProcessor p = processors.get(i);
                Thread t = new Thread(p, i + "-" + p.getClass().getSimpleName());
                threads.add(t);
                t.start();
            }
        } else {
            throw new ObjectProcessorException("One of the processors (" + failed.getClass().getSimpleName() + ") failed to start");
        }

        if (blocking) {
            joinThreads();
        }
        for (AttachedProcessor att : attached) {
            att.chainStopped();
        }
    }

    /**
     * Signals the first processor to stop, which should propagate to all following
     * processors.
     * The processing stops as soon as all objects in buffers are processed.
     *
     * @param blocking If set to true, the method doesn't return until all
     * threads stop
     */
    public void stop(boolean blocking) {
        processors.get(0).requestStop();
        if (blocking) {
            joinThreads();
        }
    }

    /**
     * Helper method, doesn't return until all processor threads die.
     */
    private void joinThreads() {
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(Pipe.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Interrupts all threads sequentially, possibly leaving the processors
     * in an indeterminate and erroneous state.<br />
     * Whenever possible, {@link #stop(boolean) stop()} should be used instead.
     */
    public void kill() {
        for (Thread t : threads) {
            t.interrupt();
        }
    }
}
