package xsmeral.semnet.util;

import xsmeral.pipe.LocalObjectFilter;
import xsmeral.pipe.ProcessorStoppedException;

/**
 * Simple processor that outputs anything to the error output.
 * Can be used as a filter or a sink.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class StdErrWriter extends LocalObjectFilter<Object, Object> {

    private boolean isFilter;

    public StdErrWriter() {
    }

    @Override
    protected void process() throws ProcessorStoppedException {
        Object obj = read();
        System.err.println(obj);
        if (isFilter) {
            write(obj);
        }
    }

    @Override
    protected void initPostContext() {
        isFilter = getNext() != null;
    }
}
