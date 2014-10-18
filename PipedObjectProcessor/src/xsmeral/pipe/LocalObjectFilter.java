package xsmeral.pipe;

import java.util.concurrent.BlockingQueue;
import xsmeral.pipe.interfaces.ObjectProcessorInterface;
import xsmeral.pipe.interfaces.ObjectSink;
import xsmeral.pipe.interfaces.ObjectSource;

/**
 * An object processor that serves the role of a filter.
 * Receives objects, processes them and writes to the output buffer.
 * Provides default {@link ObjectProcessorInterface} set to {@code Object}s.
 * <br />
 * This implementation delegates all calls to an instance of {@link LocalObjectSink}
 * and {@link LocalObjectSource}.
 *
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 * @param <I> Input object type
 * @param <O> Output object type
 */
@ObjectProcessorInterface(in = Object.class, out = Object.class)
public class LocalObjectFilter<I, O> extends AbstractObjectProcessor implements ObjectSink<I>, ObjectSource<O> {

    /**
     * Helper class for "reverse delegation" - delegates overridable methods back to the enclosing class
     */
    private class FilterObjectSink<I> extends LocalObjectSink<I> {

        @Override
        protected void stop() {
            super.stop();
            LocalObjectFilter.this.stop();
        }

        @Override
        protected void handleStoppedSource() {
            super.handleStoppedSource();
            LocalObjectFilter.this.handleStoppedSource();
        }
    }

    /**
     * Helper class for "reverse delegation" - delegates overridable methods back to the enclosing class
     */
    private class FilterObjectSource<O> extends LocalObjectSource<O> {

        public FilterObjectSource() {
        }

        public FilterObjectSource(BlockingQueue<O> outBuffer) {
            super(outBuffer);
        }

        @Override
        protected void stop() {
            super.stop();
            LocalObjectFilter.this.stop();
        }

        @Override
        protected void handleStoppedSink() {
            super.handleStoppedSink();
            LocalObjectFilter.this.handleStoppedSink();
        }
    }

    private FilterObjectSink<I> sink;
    private FilterObjectSource<O> source;

    /**
     * Instantiates the sink and the source
     */
    public LocalObjectFilter() {
        sink = new FilterObjectSink<I>();
        source = new FilterObjectSource<O>();
    }

    /**
     * Instantiates a new sink and a source, setting the source to the supplied output buffer.
     */
    public LocalObjectFilter(BlockingQueue<O> outBuffer) {
        sink = new FilterObjectSink<I>();
        source = new FilterObjectSource<O>(outBuffer);
    }

    @Override
    public final void prev(ObjectSource<I> src) {
        sink.prev(src);
    }

    @Override
    public final void next(ObjectSink<O> sink) {
        source.next(sink);
        sink.prev(this);
    }

    @Override
    public final ObjectSource<I> getPrev() {
        return sink.getPrev();
    }

    @Override
    public final ObjectSink<O> getNext() {
        return source.getNext();
    }

    @Override
    public final BlockingQueue<O> getOutBuffer() {
        return source.getOutBuffer();
    }

    /**
     * Sets the output buffer, used for object flow redirection.
     * Shouldn't be called when running.
     */
    public void setOutBuffer(BlockingQueue<O> buffer) {
        source.setOutBuffer(buffer);
    }

    /**
     * Sets the next processor, without calling {@link ObjectSink#prev(xsmeral.pipe.interfaces.ObjectSource) prev()} on the sink.
     */
    public final void setNext(ObjectSink<O> sink) {
        source.setNext(sink);
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
        source.write(o);
    }

    /**
     * Reads one object from the top of the buffer of the previous processor.
     *
     * @return The read object
     * @throws SourceStoppedException If the previous processor is in
     *  {@link xsmeral.pipe.interfaces.ObjectProcessor.Status#STOPPED STOPPED} state.
     *         Calls {@link #handleStoppedSource() handleStoppedSource()} immediately before throwing.
     */
    protected final I read() throws ProcessorStoppedException {
        return sink.read();
    }

    /**
     * Called in case the next processor is stopped during a {@link #write(java.lang.Object) write} operation.
     * Can be used, for example, to persist objects left in the buffer.
     */
    protected void handleStoppedSink() {
    }

    /**
     * Called in case the previous processor is stopped during a {@link #read() read} operation.
     */
    protected void handleStoppedSource() {
    }

    @Override
    public final void requestStop() {
    }

    /**
     * {@inheritDoc}
     * This implementation is empty and should be overridden.
     */
    @Override
    protected void process() throws ProcessorStoppedException {
    }
}
