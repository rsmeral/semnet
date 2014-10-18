package xsmeral.pipe;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import xsmeral.pipe.interfaces.ObjectProcessor;
import xsmeral.pipe.interfaces.ObjectProcessorInterface;
import xsmeral.pipe.interfaces.ObjectSink;
import xsmeral.pipe.interfaces.ObjectSource;

/**
 * An object processor in the role of an object source, produces objects.
 * Can only be placed as the first processor in a processor chain.
 * Provides default {@link ObjectProcessorInterface} set to {@code Object}s.
 * <br />
 * Uses {@link java.util.concurrent.ArrayBlockingQueue} as the output buffer.
 *
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 * @param <O> Output object type
 */
@ObjectProcessorInterface(out = Object.class)
public class LocalObjectSource<O> extends AbstractObjectProcessor implements ObjectSource<O> {

    /**
     * A buffer between the current and the next processor.
     */
    protected BlockingQueue<O> outBuffer;
    /**
     * Reference to the next processor in chain.
     */
    protected ObjectSink<O> next;
    private static final int OFFER_INTERVAL = 100;
    /**
     * Output buffer capacity, defaults to 10000 objects
     */
    protected int outBufferCapacity = 10000;//objects

    /**
     * Instantiates the output buffer, set to {@linkplain #outBufferCapacity the default capacity}
     */
    public LocalObjectSource() {
        outBuffer = new ArrayBlockingQueue<O>(outBufferCapacity);
    }

    /**
     * Sets the output buffer to the one given
     */
    public LocalObjectSource(BlockingQueue<O> outBuffer) {
        this.outBuffer = outBuffer;
    }

    /**
     * Puts one object to the output buffer
     *
     * @param o The object to write
     * @throws SinkStoppedException If the sink is in
     *  {@link xsmeral.pipe.interfaces.ObjectProcessor.Status#STOPPED STOPPED}
     *  state and thus no longer reads the buffer<br />
     *  Calls {@link #handleStoppedSink() handleStoppedSink()} immediately before throwing.
     */
    protected final void write(O o) throws ProcessorStoppedException {
        try {
            boolean success;
            do {
                if (((ObjectProcessor) next).getStatus() == Status.STOPPED) {
                    handleStoppedSink();
                    throw new ProcessorStoppedException();
                }
                success = outBuffer.offer(o, OFFER_INTERVAL, TimeUnit.MILLISECONDS);
            } while (!success);
        } catch (InterruptedException ex) {
            Logger.getLogger(LocalObjectSource.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public final BlockingQueue<O> getOutBuffer() {
        return outBuffer;
    }

    /**
     * Sets the output buffer, used for object flow redirection.
     */
    protected final void setOutBuffer(BlockingQueue<O> buffer) {
        this.outBuffer = buffer;
    }

    /**
     * Sets the next processor, without calling {@link ObjectSink#prev(xsmeral.pipe.interfaces.ObjectSource) prev()} on the sink.
     */
    protected final void setNext(ObjectSink<O> sink) {
        this.next = sink;
    }

    @Override
    public final void next(ObjectSink<O> sink) {
        this.next = sink;
        sink.prev(this);
    }

    @Override
    public final ObjectSink<O> getNext() {
        return next;
    }

    /**
     * Called in case the next processor is stopped during a {@link #write(java.lang.Object) write} operation.
     * Can be used, for example, to persist objects left in the buffer.
     */
    protected void handleStoppedSink() {
    }

    /**
     * {@inheritDoc}
     * This implementation is empty and should be overridden.
     */
    @Override
    protected void process() throws ProcessorStoppedException {
    }
}
