package xsmeral.semnet.manager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import xsmeral.pipe.AttachedProcessor;
import xsmeral.pipe.Pipe;
import xsmeral.pipe.ObjectProcessorException;
import xsmeral.pipe.interfaces.ObjectProcessor;
import xsmeral.pipe.interfaces.ObjectProcessor.Status;
import xsmeral.semnet.crawler.ConfigurationException;
import xsmeral.semnet.util.Util;

/**
 * Executor of processing jobs.
 *
 * @see ProcessingJob
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class JobRunner {

    /**
     * Stops the pipe (waits for it to stop).
     */
    private class ShutdownHook implements Runnable {

        @Override
        public void run() {
            if (pipe.getStatus() != Status.STOPPED) {
                Logger.getLogger(JobRunner.class.getName()).log(Level.INFO, "Shutdown signal received");
                Logger.getLogger(JobRunner.class.getName()).log(Level.INFO, "Stopping pipe...");
                pipe.stop(true);
                Logger.getLogger(JobRunner.class.getName()).log(Level.INFO, "Pipe stopped");
            }
        }
    }

    private String workingDir;
    private ProcessingJob job;
    private Pipe pipe;

    /**
     * Creates an instance for the specified job and working directory.
     * Instantiates and initializes processors in the supplied processing
     * job's chain, creates a pipe, assigns supplied working directory.
     * <br />
     * Registers a shutdown hook that stops the pipe.
     * @param job The processing job to execute
     * @param workingDir The working directory for the processors. All files are
     *  resolved against it.
     * @see Runtime#addShutdownHook(java.lang.Thread) 
     */
    public JobRunner(ProcessingJob job, String workingDir) throws ConfigurationException {
        this.workingDir = workingDir;
        this.job = job;
        List<ObjectProcessor> processors = new ArrayList<ObjectProcessor>();
        Collection<AttachedProcessor> attached = new ArrayList<AttachedProcessor>();
        try {
            for (Configuration conf : job.getProcessorChain()) {
                ObjectProcessor processor = (ObjectProcessor) conf.getClazz().newInstance();
                Map<String, String> params = conf.getParams();
                if (params != null) {
                    processor.initialize(params);
                }
                processors.add(processor);
            }
            for (Configuration conf : job.getAttached()) {
                AttachedProcessor processor = (AttachedProcessor) conf.getClazz().newInstance();
                Map<String, String> params = conf.getParams();
                if (params != null) {
                    if (processor.initialize(params).canStart()) {
                        attached.add(processor);
                    }
                }
            }
        } catch (InstantiationException ex) {
            throw new ConfigurationException("Failed to instantiate configured processor: " + ex.getMessage());
        } catch (IllegalAccessException ex) {
            throw new ConfigurationException("Failed to instantiate configured processor: " + ex.getMessage());
        }
        this.pipe = new Pipe(processors, attached);
        pipe.getContext().setWorkingDir(workingDir);
        Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHook()));
    }

    /**
     * Starts the Pipe and waits for it to stop.
     * @throws ObjectProcessorException If the pipe can't start.
     */
    public void run() throws ObjectProcessorException {
        pipe.start(true);
    }

    /**
     * Returns the supplied processing job.
     */
    public ProcessingJob getJob() {
        return job;
    }

    /**
     * Returns the Pipe created from processors in the supplied job.
     */
    public Pipe getPipe() {
        return pipe;
    }

    private static void printUsage() {
        System.err.println(JobRunner.class.getSimpleName() + ": runs processing jobs");
        System.err.println("Usage: java " + JobRunner.class.getSimpleName() + " <file name>");
    }

    /**
     * Executes the supplied job.
     * The first command line argument should be a path to a job file or a name
     * of a directory that contains the job file with
     * {@linkplain ProcessingJob#DEFAULT_FILENAME default name}.
     */
    public static void main(String[] args) {
        if (args.length > 0) {
            String argFileName = args[0];
            File argFile = new File(argFileName);
            if (argFileName != null && argFile.exists()) {
                String workDir = argFile.isDirectory() ? argFileName : argFile.getAbsoluteFile().getParent();
                String jobFileName = argFile.isDirectory() ? workDir + File.separator + ProcessingJob.DEFAULT_FILENAME : argFileName;
                if (new File(jobFileName).exists()) {
                    try {
                        ProcessingJob job = Util.objectFromXml(jobFileName, ProcessingJob.class);
                        JobRunner runner = new JobRunner(job, workDir);
                        runner.run();
                    } catch (ObjectProcessorException ex) {
                        Logger.getLogger(JobRunner.class.getName()).log(Level.SEVERE, "Can''t start pipe: {0}", ex.getMessage());
                    } catch (ConfigurationException ex) {
                        Logger.getLogger(JobRunner.class.getName()).log(Level.SEVERE, "Can''t start processing job: {0}", ex.getMessage());
                    }
                } else {
                    Logger.getLogger(JobRunner.class.getName()).log(Level.SEVERE, "File ''{0}'' does not exist", jobFileName);
                    System.err.println();
                }
            } else {
                Logger.getLogger(JobRunner.class.getName()).log(Level.SEVERE, "File ''{0}'' does not exist", argFileName);
            }
        } else {
            printUsage();
        }
    }
}
