package xsmeral.pipe;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import xsmeral.pipe.interfaces.ObjectProcessor;
import xsmeral.pipe.interfaces.ObjectProcessor.Status;
import xsmeral.pipe.interfaces.ObjectProcessorInterface;
import xsmeral.pipe.interfaces.ObjectSink;
import xsmeral.pipe.interfaces.ObjectSource;

/**
 * An object processor in the role of an object sink, usually persists objects.
 * Can only be placed as the last processor in a processor chain.
 * Provides default {@link ObjectProcessorInterface} set to {@code Object}s.
 * <br />
 *
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 * @param <I> Input object type
 */
@ObjectProcessorInterface(in = Object.class)
public class LocalObjectSink<I> extends AbstractObjectProcessor implements ObjectSink<I> {

    /**
     * Reference to the previous processor in chain.
     * Used to access its buffer and status.
     */
    protected ObjectSource<I> prev;
    private static final int BUFFER_POLLING_INTERVAL = 100;//ms

    /**
     * Reads one object from the top of the buffer of the previous processor.
     *
     * @return The read object
     * @throws SourceStoppedException If the previous processor is in
     *  {@link xsmeral.pipe.interfaces.ObjectProcessor.Status#STOPPED STOPPED} state.
     *         Calls {@link #handleStoppedSource() handleStoppedSource()} immediately before throwing.
     */
    protected final I read() throws ProcessorStoppedException {
        I obj = null;
        try {
            while (obj == null) {
                obj = ((BlockingQueue<I>) prev.getOutBuffer()).poll(BUFFER_POLLING_INTERVAL, TimeUnit.MILLISECONDS);
                if (obj == null && ((ObjectProcessor) prev).getStatus() == Status.STOPPED) {
                    handleStoppedSource();
                    throw new ProcessorStoppedException();
                }
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(LocalObjectSink.class.getName()).log(Level.SEVERE, null, ex);
        }
        return obj;
    }

    @Override
    public final void prev(ObjectSource<I> src) {
        this.prev = src;
    }

    @Override
    public final ObjectSource<I> getPrev() {
        return prev;
    }

    @Override
    public final void requestStop() {
    }

    /**
     * Called in case the previous processor is stopped during a {@link #read() read} operation.
     */
    protected void handleStoppedSource() {
    }

    /**
     * {@inheritDoc}
     * This implementation is empty and should be overridden.
     */
    @Override
    protected void process() throws ProcessorStoppedException {
    }
}
