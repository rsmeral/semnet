package xsmeral.pipe;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Basic attached processor associated to a Pipe instance.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class PipeAttachedProcessor implements AttachedProcessor {

    private Map<String, String> params;
    private Pipe pipe;
    protected boolean error = false;

    @Override
    public AttachedProcessor initialize(Map<String, String> params) {
        this.params = params;
        try {
            ParamInitializer.initialize(this, params);
        } catch (Exception ex) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "Failed to initialize params: {0}", ex.getMessage());
            error = true;
        }
        return this;
    }

    @Override
    public void setPipe(Pipe pipe) {
        this.pipe = pipe;
    }

    /**
     * Returns the associated Pipe.
     */
    public Pipe getPipe() {
        return pipe;
    }

    /**
     * Returns the parameter map used to initialize this processor.
     */
    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    @Override
    public boolean canStart() {
        return !error;
    }

    @Override
    public void preContext() {
    }

    @Override
    public void postContext() {
    }

    @Override
    public void chainStopped() {
    }

    @Override
    public void stop() {
    }

    /**
     * Starts the processor.
     */
    @Override
    public void run() {
    }

    /**
     * The processor removes itself from the Pipe's list of attached processors.
     */
    protected void detach() {
        if (this.pipe != null) {
            pipe.getAttached().remove(this);
        }
    }
}
