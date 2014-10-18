package xsmeral.pipe.interfaces;

/**
 * An object processor receiving objects of one specific type
 * 
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 * @param <I> Input object type
 */
public interface ObjectSink<I> {

    /**
     * Sets this processor as the next in the chain, after the given processor.
     * @param src The previous processor
     */
    public void prev(ObjectSource<I> src);

    /**
     * Returns the input object type.
     */
    public Class getInType();

    /**
     * Returns the previous processor in chain.
     */
    public ObjectSource<I> getPrev();

}
