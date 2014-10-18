package xsmeral.semnet.manager;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import java.util.Collections;
import java.util.List;

/**
 * Contains a chain of processors with their configurations.
 *
 * @see JobRunner
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
@XStreamAlias("processingJob")
public class ProcessingJob {

    /**
     * Default file name of a processing job file. Used if no name is specified.
     */
    @XStreamOmitField
    public static final String DEFAULT_FILENAME = "job.xml";
    private String name;
    private String description;
    private List<Configuration> processorChain;
    private List<Configuration> attached;

    public ProcessingJob() {
    }

    /**
     * Initializes all fields.
     * @param name Arbitrary user-assigned name
     * @param description Arbitrary description
     * @param processorChain Chain of processors and their configurations
     */
    public ProcessingJob(String name, String description, List<Configuration> processorChain, List<Configuration> attached) {
        this.name = name;
        this.description = description;
        this.processorChain = processorChain;
        this.attached = attached;
    }

    /**
     * Returns description of the job.
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns name of the job.
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns list of object processors.
     */
    public List<Configuration> getProcessorChain() {
        return processorChain != null? processorChain : Collections.<Configuration>emptyList();
    }

    public void setProcessorChain(List<Configuration> processorChain) {
        this.processorChain = processorChain;
    }

    /**
     * Returns collection of attached processors.
     */
    public List<Configuration> getAttached() {
        return attached != null? attached : Collections.<Configuration>emptyList();
    }

    public void setAttached(List<Configuration> attached) {
        this.attached = attached;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ProcessingJob other = (ProcessingJob) obj;
        if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
            return false;
        }
        if ((this.description == null) ? (other.description != null) : !this.description.equals(other.description)) {
            return false;
        }
        if (this.processorChain != other.processorChain && (this.processorChain == null || !this.processorChain.equals(other.processorChain))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 17 * hash + (this.name != null ? this.name.hashCode() : 0);
        hash = 17 * hash + (this.description != null ? this.description.hashCode() : 0);
        hash = 17 * hash + (this.processorChain != null ? this.processorChain.hashCode() : 0);
        return hash;
    }
}
