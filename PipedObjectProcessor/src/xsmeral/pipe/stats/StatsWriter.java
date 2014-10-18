package xsmeral.pipe.stats;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import xsmeral.pipe.PipeAttachedProcessor;
import xsmeral.pipe.context.PipeContext;
import xsmeral.pipe.interfaces.Param;

// TODO: in non-periodical case, file is not written, daemon thread killed before writing
// TODO: limit double precision on output
/**
 * Writes pipe context parameters to a file (or standard/error output) periodically.
 *
 * @init file Name of file for writing stats, or &quot;stderr&quot;/&quot;stdout&quot;
 *  to write to standard/error output
 * @init interval (Optional) Interval, in seconds, of writing. Value of 0 means
 *  the stats are written only once, when the pipe stops
 * @init filter (Optional) Comma-separated list of prefixes of context parameter
 *  names, e.g. &quot;stats.p1,stats.p2&quot;, that will be written to output. Empty
 *  string means no filtering.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class StatsWriter extends PipeAttachedProcessor {

    private static final String STDOUT = "stdout";
    private static final String STDERR = "stderr";
    @Param("file")
    private String statsFileName;
    @Param
    private int interval = 0;//s, 0 = don't write periodically
    @Param
    private String filter = "";
    private File file;
    private PipeContext ctx;
    private PrintWriter pw = null;
    private ScheduledFuture exec;
    private ScheduledExecutorService execSvc;
    private String[] patterns;

    @Override
    public void postContext() {
        ctx = getPipe().getContext();
        if (STDOUT.equals(statsFileName)) {
            pw = new PrintWriter(System.out);
        } else if (STDERR.equals(statsFileName)) {
            pw = new PrintWriter(System.err);
        } else {
            file = ctx.getFile(statsFileName);
        }
        patterns = filter.split(",");
    }

    private void writeStats() {
        if (file != null) {
            try {
                pw = new PrintWriter(file);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(StatsWriter.class.getName()).log(Level.WARNING, "Stats file not found");
            }
        }
        pw.println("=======================================");
        pw.println(" Stats " + new Date().toString());
        pw.println("=======================================");

        // filter
        for (String key : ctx.getParameterNames()) {
            boolean found = filter.isEmpty();
            for (int i = 0; !found && i < patterns.length; i++) {
                found = key.startsWith(patterns[i]);
            }
            if (found) {
                pw.println(key + "=" + ctx.getParameterValue(key));
            }
        }
        pw.println("---------------------------------------");
        pw.println();
        pw.flush();
        if (file != null) {
            pw.close();
        }
    }

    @Override
    public void chainStopped() {
        if (interval == 0) {
            writeStats();
        } else {
            exec.cancel(true);
            execSvc.shutdownNow();
        }
        if (pw != null) {
            pw.close();
        }
    }

    @Override
    public void run() {
        // if interval > 0, schedule task
        if (interval > 0) {
            execSvc = Executors.newScheduledThreadPool(1);
            exec = execSvc.scheduleAtFixedRate(new Runnable() {

                @Override
                public void run() {
                    writeStats();
                }
            }, interval, interval, TimeUnit.SECONDS);
        }
    }
}
