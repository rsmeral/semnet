package xsmeral.pipe.interfaces;

import java.util.Queue;

/**
 * An object processor producing objects of one specific type.
 *
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 * @param <O> Output object type
 */
public interface ObjectSource<O> {

    /**
     * Returns the output object type
     */
    public Class getOutType();

    /**
     * Returns the output buffer of this processor.
     */
    public Queue<O> getOutBuffer();

    /**
     * Sets the next processor in the chain and sets this as its previous.
     * @param sink The next processor
     */
    public void next(ObjectSink<O> sink);

    /**
     * Returns the next processor in chain.
     */
    public ObjectSink<O> getNext();
}
