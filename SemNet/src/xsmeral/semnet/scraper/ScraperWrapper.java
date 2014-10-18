package xsmeral.semnet.scraper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openrdf.model.Statement;
import xsmeral.semnet.crawler.HostManager;
import xsmeral.semnet.crawler.model.EntityDescriptor;
import xsmeral.semnet.crawler.model.EntityDocument;
import xsmeral.pipe.LocalObjectFilter;
import xsmeral.pipe.LocalObjectSource;
import xsmeral.pipe.ObjectProcessorException;
import xsmeral.pipe.ProcessorStoppedException;
import xsmeral.pipe.context.FromContext;
import xsmeral.pipe.interfaces.ObjectProcessorInterface;
import xsmeral.semnet.manager.Configuration;

/**
 * This processor works as a router, dispatching entity documents to scrapers.
 * The scrapers are configured from a HostManager instance and pre-instantiated
 * at post-context initialization.
 *
 * @fromContext hostManager A HostManager instance, used to instantiate scrapers
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 * @see HostManager
 */
@ObjectProcessorInterface(in = EntityDocument.class, out = Statement.class)
public class ScraperWrapper extends LocalObjectFilter<EntityDocument, Statement> {

    @FromContext
    private HostManager hostManager;
    /**
     * Map of scraper threads (by their Runnables), for starting and stopping.
     */
    private Map<AbstractScraper, Thread> scraperMap;
    /**
     * Map of DocumentSources associated with scraper configuration for a given entity.
     * For correct routing in:entityDoc(w/ entDesc) -> docSource(s) -> scraper(s) -> out:statement
     */
    private Map<EntityDescriptor, Collection<DocumentSource>> sourceMap;
    /**
     * Cache of document sources, mapped by corresponding scraper configurations.
     */
    private Map<Configuration, DocumentSource> confCache;

    public ScraperWrapper() {
        sourceMap = new HashMap<EntityDescriptor, Collection<DocumentSource>>();
        scraperMap = new HashMap<AbstractScraper, Thread>();
        confCache = new HashMap<Configuration, DocumentSource>();
    }

    /**
     * Instantiates scrapers.
     */
    @Override
    protected void initPostContext() {
        try {
            for (int hostId : hostManager.getHostIds()) {
                for (EntityDescriptor entDesc : hostManager.getHostDescriptor(hostId).getEntityDescriptors()) {
                    Collection<Configuration> scrapers = entDesc.getScrapers();
                    for (Configuration scraperConf : scrapers) {
                        DocumentSource src;
                        if (!confCache.containsKey(scraperConf)) {
                            Class cls = scraperConf.getClazz();
                            src = new DocumentSource();
                            AbstractScraper scraper = (AbstractScraper) cls.newInstance();
                            src.next(scraper);
                            scraper.setOutBuffer(this.getOutBuffer());
                            scraper.setNext(this.getNext());
                            scraper.initialize(scraperConf.getParams());
                            Thread t = new Thread(scraper, cls.getSimpleName());
                            Collection<DocumentSource> sources = sourceMap.get(entDesc);
                            if (sources == null) {
                                sourceMap.put(entDesc, new ArrayList<DocumentSource>());
                            }
                            scraperMap.put(scraper, t);
                            confCache.put(scraperConf, src);
                        } else {
                            src = confCache.get(scraperConf);
                        }
                        sourceMap.get(entDesc).add(src);
                    }
                }
            }
            for (AbstractScraper s : scraperMap.keySet()) {
                s.setContext(getContext());
            }
            for (AbstractScraper s : scraperMap.keySet()) {
                s.initContext();
            }
        } catch (InstantiationException ex) {
            failStart("Can't instantiate a scraper", ex);
        } catch (IllegalAccessException ex) {
            failStart("Can't instantiate a scraper", ex);
        } catch (ObjectProcessorException ex) {
            failStart("Scraper failed to start", ex);
        }
    }

    private class DocumentSource extends LocalObjectSource<EntityDocument> {

        public DocumentSource() {
            status = Status.RUNNING;
        }

        public void addDoc(EntityDocument doc) throws ProcessorStoppedException {
            write(doc);
        }

        public void doStop() {
            stop();
        }
    }

    /**
     * Starts scrapers.
     */
    @Override
    protected void preRun() throws ProcessorStoppedException {
        for (Thread t : scraperMap.values()) {
            t.start();
        }
    }

    @Override
    protected void process() throws ProcessorStoppedException {
        EntityDocument doc = read();
        for (DocumentSource src : sourceMap.get(doc.getEntityDescriptor())) {
            src.addDoc(doc);
        }
    }

    @Override
    public boolean canStart() {
        boolean allCanStart = true;
        for (Iterator<AbstractScraper> it = scraperMap.keySet().iterator();
                it.hasNext() && allCanStart;
                allCanStart = allCanStart && it.next().canStart());
        return allCanStart;
    }

    private void stopAll() {
        for (DocumentSource src : confCache.values()) {
            src.doStop();
        }
        for (Thread t : scraperMap.values()) {
            try {
                t.join();
            } catch (InterruptedException ex1) {
                Logger.getLogger(ScraperWrapper.class.getName()).log(Level.SEVERE, null, ex1);
            }
        }
        stop();
    }

    @Override
    protected void handleStoppedSink() {
        stopAll();
    }

    @Override
    protected void handleStoppedSource() {
        stopAll();
    }
}
