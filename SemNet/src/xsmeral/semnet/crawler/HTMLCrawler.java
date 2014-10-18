package xsmeral.semnet.crawler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XPatherException;
import xsmeral.semnet.crawler.model.CrawlerConfiguration;
import xsmeral.semnet.crawler.model.EntityDescriptor;
import xsmeral.semnet.crawler.model.EntityDocument;
import xsmeral.semnet.crawler.model.HostDescriptor;
import xsmeral.semnet.crawler.model.URLEntry;
import xsmeral.semnet.crawler.util.CharsetDetector;
import xsmeral.semnet.crawler.util.ConnectionManager;
import xsmeral.semnet.crawler.util.RobotsPolicy;
import xsmeral.semnet.util.URLUtil;
import xsmeral.semnet.util.Util;
import xsmeral.semnet.scraper.AbstractScraper;
import xsmeral.semnet.util.XPathUtil;
import xsmeral.pipe.stats.Sum;
import xsmeral.pipe.stats.Stats;
import xsmeral.pipe.stats.StatFunction;
import xsmeral.pipe.ProcessorStoppedException;
import xsmeral.pipe.LocalObjectSource;
import xsmeral.pipe.context.FSContext;
import xsmeral.pipe.context.ToContext;
import xsmeral.pipe.interfaces.ObjectProcessorInterface;
import xsmeral.pipe.interfaces.Param;
import xsmeral.pipe.stats.Average;
import static xsmeral.semnet.crawler.URLManager.Query;

/*
 * TODO: Split the whole thing into modules
 */
/**
 * A web crawler of HTML pages. Crawls configured hosts, looking for links matching specified patterns.
 * <br />
 * Documents at the matched URLs are passed to a scraper ({@link AbstractScraper}),
 * contained in {@link EntityDocument}s.
 * Scrapers work in co-operation with the crawler, using the same configuration.
 * A persistent state is maintained using {@link URLManager} and {@link HostManager},
 * enabling the crawler to be stopped and restarted at any time.<br />
 *
 * <h4>Configuration</h4>
 * The crawler is configured with a {@link CrawlerConfiguration} which contains
 * {@link HostDescriptor}s, that describe the crawling targets. The crawling
 * is focused on entities represented by URLs of the host.
 * Entities are described by {@link EntityDescriptor}s.<br />
 * The database configuration for state persistence, internally represented by a
 * {@link RDBLayer} class is stored in the crawler configuration as well.
 * These configuration files are stored in XML files. An external library
 * (<a href="http://xstream.codehaus.org/">XStream</a>) is used for XML
 * (de)serialization.<br />
 *
 * <h4>Bootstrapping</h4>
 * Certain starting points (URLs) need to be specified to seed the crawler.
 * These are supplied as a list of absolute URLs contained in a single file,
 * one URL per line. This file should be either placed in the same directory
 * as the crawler configuration and named the value of {@link #DEF_BOOTSTRAP_FILE}
 * or some other file should be supplied in the {@code bootstrap} initialization
 * parameter. <br />
 * All URLs in the file need to have their corresponding hosts already defined
 * in the configuration, otherwise they are ignored.
 * <br />
 * After URLs from the file have been succesfully read and added to DB, the file
 * is renamed.
 * 
 * <h4>Crawling</h4>
 * One instance of the crawler crawls multiple hosts at the same time with one
 * or more threads per host. An implementation of the Robots Exclusion standard 
 * is provided in class {@link RobotsPolicy} which allows the crawler to obey the
 * crawling rules defined by the target host ((dis)allowed URL patterns and
 * crawling delay). Adherence to the rules is optional.<br />
 * Retrieved web pages are decoded using the character encoding determined/guessed
 * by the class {@link CharsetDetector} and parsed using a third-party library
 * <a href="http://htmlcleaner.sourceforge.net/">HtmlCleaner</a>. The library is
 * used as a compensatory measure for the multitude of web pages that are non-valid.
 * Another compensatory measure is the use of URL normalization to ensure
 * consistent representation of URLs, provided by 
 * {@link URLUtil#normalize(java.net.URL) URLUtil.normalize(URL)}.<br />
 * Consistent HTTP connection settings are provided by auxiliary class
 * {@link ConnectionManager}.
 *
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 *
 * @init conf Crawler configuration file name
 * @init bootstrap (Optional) Name of file containg list of URLs (one per line) to load to database prior to running
 * @toContext hostManager A HostManager instance initialized with hosts from
 *      crawler configuration.
 */
@ObjectProcessorInterface(out = EntityDocument.class)
public class HTMLCrawler extends LocalObjectSource<EntityDocument> {

    // constants
    public static final String DEF_BOOTSTRAP_FILE = "bootstrap.list";
    public static final String BOOTSTRAP_OLD_SUFFIX = ".old";
    public static final int CONNECTION_RETRIES = 2;
    private static final String CRAWLER_NAME_FORMAT = "%s_%s:%d";
    private static final String CRAWLER_NAME = "crawler";
    private static final int OWNER_ID = 0;// not yet implemented
    // modifiable vars
    private Integer globalCrawlDelayMinimum = CrawlerConfiguration.DEF_GLOBAL_CRAWL_DELAY_MIN;
    private boolean policyIgnored = CrawlerConfiguration.DEF_POLICY_IGNORED;
    private boolean fakeReferrer = CrawlerConfiguration.DEF_FAKE_REFERRER;
    // params
    @Param("conf")
    private String confFileName;
    @Param("bootstrap")
    private String bootstrapFileName = DEF_BOOTSTRAP_FILE;
    // state
    private CrawlerConfiguration conf;
    private RDBLayer db;
    private URLManager urlMgr;
    @ToContext
    private HostManager hostManager;
    private Collection<Integer> hostIds;
    private Map<HostCrawler, Thread> threadMap;
    // stats
    private StatFunction<Long> connError;
    private StatFunction<Long> validLinksFound;
    private StatFunction<Long> newLinksFound;
    private StatFunction<Long> crawled;
    private StatFunction<Double> avgFetch;
    private StatFunction<Double> avgHTMLParse;
    private StatFunction<Double> avgScrape;

    /**
     * Prefetches and buffers URLs.
     * Respects {@code sourceFirst} attribute of {@link HostDescriptor}
     * and weights defined in {@link EntityDescriptor}.
     */
    private class URLBuffer {

        /**
         * Retrieves the URLs from DB.
         */
        private class FetchTask implements Runnable {

            private Collection<Query> queries;

            public FetchTask(Collection<Query> queries) {
                this.queries = queries;
            }

            private void fetchEntries() {
                long fetchStart = System.currentTimeMillis();
                for (Query q : queries) {
                    entries.addAll(urlMgr.fetchEntries(q, ownerId));
                }
                avgFetch.add((double) (System.currentTimeMillis() - fetchStart));
            }

            @Override
            public void run() {
                synchronized (fetchLock) {
                    fetching = true;
                    if (sourceFirst && !gotSource) {
                        Collection<URLEntry> sourceEntries = urlMgr.fetchEntries(sourceQuery, ownerId);
                        gotSource = sourceEntries.isEmpty();
                        if (!gotSource) {
                            entries.addAll(sourceEntries);
                        } else {
                            fetchEntries();
                        }
                    } else {
                        fetchEntries();
                    }
                    fetching = false;
                }
            }
        }

        // const
        private static final int FETCH_SIZE = 50;
        private static final int FETCH_THRESHOLD = 20;
        private int ownerId = OWNER_ID;
        // flags, locks
        private boolean stop = false;
        private boolean fetching = false;
        private final Object fetchLock = new Object();
        private final Object getLock = new Object();
        // state
        private URLManager urlMgr;
        private BlockingQueue<URLEntry> entries;
        private FetchTask fetchTask;
        private Thread fetchThread;
        private boolean sourceFirst;
        private boolean gotSource = false;
        private Query sourceQuery;

        public URLBuffer(URLManager urlMgr, int hostId) throws ConfigurationException {
            this(urlMgr, hostId, FETCH_SIZE);
        }

        /**
         * Creates queries, calculates limits, instantiates the queue.
         */
        public URLBuffer(URLManager urlMgr, int hostId, int fetchSize) throws ConfigurationException {
            Collection<Query> queries = new ArrayList<Query>();
            int total = 0;
            HostDescriptor hostDesc = hostManager.getHostDescriptor(hostId);
            sourceFirst = hostDesc.isSourceFirst();
            if (sourceFirst) {
                sourceQuery = urlMgr.getQueryForHost(hostId).entity(false).working(true).current().notLocked().limit(fetchSize).getQuery();
            }
            Collection<EntityDescriptor> entities = hostDesc.getEntityDescriptors();
            for (EntityDescriptor entity : entities) {
                total += entity.getWeight();
            }
            for (EntityDescriptor entity : entities) {
                int limit = (int) ((entity.getWeight() / (float) total) * fetchSize);
                Query q = urlMgr.getQueryForHost(hostId).working(true).forPattern(entity.getUrlPattern().toString()).notLocked().current().limit(limit).getQuery();
                queries.add(q);
            }
            this.fetchTask = new FetchTask(queries);
            this.entries = new ArrayBlockingQueue<URLEntry>(fetchSize + FETCH_THRESHOLD);
            this.urlMgr = urlMgr;
        }

        /**
         * Retrieves one entry from the buffer.
         * Starts fetching thread if buffer length is below threshold.
         * @return A URL entry or null if no more entries are available.
         */
        public URLEntry getEntry() {
            synchronized (getLock) {
                if (entries.size() < FETCH_THRESHOLD) {
                    if (!fetching && !stop) {
                        fetching = true;
                        fetchThread = new Thread(fetchTask);
                        fetchThread.start();
                    }
                }
            }

            synchronized (getLock) {
                if (entries.size() > 0) {
                    return entries.poll();
                } else {
                    try {
                        // either fetching is in progress or no more entries are available
                        fetchThread.join();
                    } catch (InterruptedException ex) {
                        Logger.getLogger(HTMLCrawler.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    return entries.poll();
                }
            }
        }

        /**
         * Causes the buffer not to fetch any more URLs.
         */
        public void stop() {
            if (!stop) {
                stop = true;
                synchronized (getLock) {
                    try {
                        fetchThread.join();
                    } catch (InterruptedException ex) {
                        Logger.getLogger(HTMLCrawler.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    Collection<URLEntry> entriesToUnlock = new ArrayList<URLEntry>(entries.size());
                    entries.drainTo(entriesToUnlock);
                    urlMgr.unlockUrls(entriesToUnlock);
                    urlMgr.close();
                }
            }
        }

        /**
         * Indicates queue status.
         */
        public boolean hasMore() {
            return !entries.isEmpty();
        }
    }

    /**
     * Crawling thread for one host.
     */
    private class HostCrawler implements Runnable {

        //<editor-fold desc="Fields">
        // const
        private static final int CONN_TEST_WAIT = 5000;
        private static final int WORK_WAIT_INTERVAL = 500;
        private static final String CHARSET_FALLBACK = "UTF-8";
        private static final String XPATH_LINKS = "//a/@href";
        private static final String HTML_BASE = "/head/base/@href";
        // runtime
        //  parent
        private Map<HostCrawler, Thread> children = null;
        private long nextGet = System.currentTimeMillis();
        private long crawlDelay;
        //  own
        private URLManager urlMgr;
        private boolean working = true;
        private boolean stopCondition = false;
        //  common
        private URLBuffer urlBuffer;
        private int hostId;
        private HostDescriptor desc;
        private URL baseURL;
        private RobotsPolicy policy;
        private HostCrawler authority;
        private final Integer workLock = 0;
        private String charset;
        private long ownerId = OWNER_ID;
        private HtmlCleaner cleaner;

        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Simple methods">
        /**
         * Serves as a timer between requests to the same host, for crawl delay obedience
         * @return The time (unix timestamp in ms) of next request to the host
         */
        private synchronized long nextGetAt() {
            long result = nextGet;
            nextGet = Math.max(System.currentTimeMillis(), nextGet + crawlDelay);
            return result;
        }

        /**
         * Returns ID assigned (by the HostManager) to the host crawled by this thread 
         * @return Host ID
         * @see HostManager
         */
        public int getHostId() {
            return hostId;
        }

        /**
         * Returns associated host descriptor
         * @return Host descriptor
         */
        public HostDescriptor getDesc() {
            return desc;
        }

        /**
         * Work status indicator of this thread
         * @return True, if this crawling thread is working (running and not waiting for work)
         */
        public boolean isWorking() {
            return working;
        }

        /**
         * Indicates whether this thread is the parent or the child
         * @return True, if this thread has been forked by another HostCrawler
         */
        private boolean isChild() {
            return authority != this;
        }

        /**
         * Work status indicator of all threads
         * @return True, if any thread (including this) is working
         * @see #isWorking()
         */
        private synchronized boolean isAnyWorking() {
            boolean anyWorking = false;
            if (this.working) {
                return true;
            }
            Iterator<HostCrawler> it = children.keySet().iterator();
            while (it.hasNext() && !anyWorking) {
                anyWorking = it.next().isWorking();
            }
            return anyWorking;
        }
        //</editor-fold>

        /**
         * Common constructor.
         */
        private HostCrawler() {
            CleanerProperties cp = new CleanerProperties();
            cp.setTransResCharsToNCR(true);
            cp.setTransSpecialEntitiesToNCR(true);
            cp.setPruneTags("script,style,embed,object,iframe");
            cleaner = new HtmlCleaner(cp);
        }

        /**
         * Parent constructor. Forks child threads, if <tt> threads &gt; 1</tt>
         * @param hostId ID of the crawled host
         * @param threads Number of threads
         * @see HostManager
         */
        @SuppressWarnings("LeakingThisInConstructor")
        public HostCrawler(int hostId, int threads) throws MalformedURLException, SQLException, ConfigurationException {
            this();
            this.urlMgr = new URLManager(db);
            this.urlBuffer = new URLBuffer(new URLManager(db), hostId);
            this.children = new HashMap<HostCrawler, Thread>();
            this.authority = this;
            this.hostId = hostId;
            this.desc = hostManager.getHostDescriptor(hostId);
            this.baseURL = new URL(desc.getBaseURL());
            this.policy = new RobotsPolicy(baseURL, ConnectionManager.getUserAgent());
            this.charset = desc.getCharset() != null ? desc.getCharset() : Util.nonNull(CharsetDetector.detectCharset(baseURL), CHARSET_FALLBACK);
            // determine the crawl delay; host-defined crawl delay overrides the robots policy
            Integer descDelay = desc.getCrawlDelay();
            this.crawlDelay = Math.max(globalCrawlDelayMinimum, Util.nonNull(descDelay, policy.getCrawlDelayMillis()));
            if (threads > 1) {
                for (int i = 1; i < threads; i++) {
                    HostCrawler child = new HostCrawler(this);
                    Thread t = new Thread(child, String.format(CRAWLER_NAME_FORMAT, CRAWLER_NAME, desc.getName(), i));
                    children.put(child, t);
                }
            }
        }

        /**
         * Child constructor.
         * @param parent Parent crawler thread
         */
        private HostCrawler(HostCrawler parent) throws SQLException {
            this();
            this.authority = parent;
            this.urlMgr = new URLManager(db);
        }

        /**
         * Starts children threads, if this thread is parent.
         * @see #isChild()
         */
        private void startChildrenIfParent() {
            if (!isChild()) {
                Iterator<Thread> it = children.values().iterator();
                while (it.hasNext()) {
                    it.next().start();
                }
            }
        }

        /**
         * Creates connection to the URL represented by the supplied URLEntry and parses the HTML content.
         * @param fetchedEntry The URL to connect to
         * @return Root node of the HTML document
         * @throws IOException In case of connection or parsing error
         */
        private TagNode getRootNode(URLEntry fetchedEntry) throws IOException {
            URL fetchedURL = new URL(fetchedEntry.getUrl());
            // obey the crawl delay
            long delay = authority.nextGetAt() - System.currentTimeMillis();
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ex) {
                    Logger.getLogger(HTMLCrawler.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            InputStream is = ConnectionManager.getInputStream(fetchedURL, CONNECTION_RETRIES, fakeReferrer ? authority.desc.getBaseURL() : null);
            long parseStart = System.currentTimeMillis();
            TagNode result = cleaner.clean(is, authority.charset);
            avgHTMLParse.add((double) (System.currentTimeMillis() - parseStart));
            is.close();
            return result;
        }

        /**
         * Looks for base URI in /html/head/base/@href.
         */
        private URI getBaseURI(TagNode rootNode) throws XPatherException {
            String baseHref = XPathUtil.queryText(rootNode, HTML_BASE);
            if (baseHref != null) {
                try {
                    return URLUtil.normalize(baseHref).toURI();
                } catch (MalformedURLException ex) {
                    return null;
                } catch (URISyntaxException ex) {
                    return null;
                }
            } else {
                return null;
            }
        }

        /**
         * Scrapes links, according to defined patterns
         * @param rootNode The node to scrape
         * @param fetchedURI The URI of the document, to resolve links against
         * @throws XPatherException
         * @throws MalformedURLException
         * @see EntityDescriptor
         */
        private void scrapeLinks(TagNode rootNode, URI base, URI fetchedURI) throws XPatherException {
            long scrapeStart = System.currentTimeMillis();
            Object[] links = rootNode.evaluateXPath(XPATH_LINKS);
            URI baseURI = base != null ? base : fetchedURI;
            Collection<URLEntry> entriesToAdd = new ArrayList<URLEntry>();
            for (Object obj : links) {
                String link = String.valueOf(obj);
                try {
                    URI linkURI = new URI(link);
                    URL resolvedURL;
                    // if the found link is absolute...
                    if (linkURI.isAbsolute()) {
                        URL linkURL = linkURI.toURL();
                        // ..and comes from the same host, normalize it
                        if (URLUtil.equalHosts(new URL(URLUtil.fullHost(linkURL)), authority.baseURL, true)) {
                            resolvedURL = URLUtil.normalize(linkURL);
                        } else {// otherwise skip the link
                            continue;
                        }
                    } else {// link not absolute, resolve, normalize
                        resolvedURL = URLUtil.normalize(baseURI.resolve(linkURI).toURL());
                    }
                    String resolvedPath = resolvedURL.getFile();
                    Pattern patt = hostManager.getPattern(authority.hostId, resolvedPath);
                    if (patt != null) {// if the URL is matched by one of defined patterns
                        EntityDescriptor entDesc = hostManager.getEntityDescriptorMap(authority.hostId).get(patt);
                        boolean entity = entDesc != null;
                        int updateFreq;
                        if (entity) {// if the URL is an entity
                            updateFreq = entDesc.getUpdateFreq();
                        } else {// URL is source
                            updateFreq = hostManager.getSourceURLMap(authority.hostId).get(patt);
                        }
                        // add found link to DB
                        URLEntry newEntry = new URLEntry(authority.baseURL.toString(), resolvedPath, new Date(0), 0, updateFreq, entity, patt.toString(), true, (short) 0);
                        entriesToAdd.add(newEntry);
                    }
                } catch (URISyntaxException ex) {
                    Logger.getLogger(HTMLCrawler.class.getName()).log(Level.FINER, "Bad URI syntax: {0}", link);
                } catch (MalformedURLException ex) {
                    Logger.getLogger(HTMLCrawler.class.getName()).log(Level.FINER, "Malformed URL: {0}", link);
                }
            }
            avgScrape.add((double) (System.currentTimeMillis() - scrapeStart));
            validLinksFound.add((long) entriesToAdd.size());
            long newLinks = urlMgr.addEntries(entriesToAdd);
            newLinksFound.add(newLinks);
            // notify threads wating for work
            synchronized (authority.workLock) {
                authority.workLock.notifyAll();
            }
        }

        /**
         * Called when no URLs have been fetched from the DB, to check whether any other thread is working.
         * Used to synchronize threads.
         */
        private void waitForWork() {
            working = false;
            if (authority.isAnyWorking()) {// some thread is still working, wait for signal
                synchronized (authority.workLock) {
                    try {
                        while (!authority.urlBuffer.hasMore() && authority.isAnyWorking()) {
                            authority.workLock.wait(WORK_WAIT_INTERVAL);
                        }
                    } catch (InterruptedException ex) {
                        stopCondition = true;
                        Logger.getLogger(HTMLCrawler.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            } else {// otherwise stop
                stopCondition = true;
            }
        }

        /**
         * Called when the parent thread finishes sooner than some of its children
         */
        private void waitForChildren() {
            // if parent, wait for children / forked crawling threads
            working = false;
            if (!isChild()) {
                Iterator<Thread> it = children.values().iterator();
                while (it.hasNext()) {
                    try {
                        it.next().join();
                    } catch (InterruptedException ex) {
                        Logger.getLogger(HTMLCrawler.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }

        /**
         * Tries to connect to baseURL of current host and blocks until success or the processor is stopped.
         * @return True, if there was a connection error, false otherwise.
         */
        private boolean detectConnectionError() {
            boolean error = false, wasError = false;
            do {
                try {
                    ConnectionManager.getConnection(authority.baseURL).getInputStream().close();
                    error = false;
                } catch (IOException ex) {
                    if (!error) {
                        Logger.getLogger(HostCrawler.class.getName()).log(Level.WARNING, "Internet connection error...");
                        wasError = error = true;
                    }
                    try {
                        Thread.sleep(CONN_TEST_WAIT);
                    } catch (InterruptedException ex1) {
                        Logger.getLogger(HTMLCrawler.class.getName()).log(Level.SEVERE, null, ex1);
                    }
                }
            } while (error && HTMLCrawler.this.status == Status.RUNNING);
            return wasError;
        }

        /**
         * Runs the crawling. Retrieves URL from DB, visits it, scrapes links and updates the URL entry.
         */
        @Override
        public void run() {
            startChildrenIfParent();
            boolean canRun = true;
            // each iteration processes one URL from DB (retrieves, visits, scrapes links, returns)
            while ((canRun = HTMLCrawler.this.status == Status.RUNNING && !stopCondition) || authority.urlBuffer.hasMore()) {
                if (!canRun) {
                    authority.urlBuffer.stop();
                }
                working = true;
                boolean urlWorking = true;
                URLEntry fetchedEntry = authority.urlBuffer.getEntry();
                // if there is an unvisited url, process it
                if (fetchedEntry != null) {
                    try {
                        if (policyIgnored || authority.policy.allows(fetchedEntry.getPath())) {
                            URI fetchedURI = new URI(fetchedEntry.getUrl());
                            TagNode rootNode = getRootNode(fetchedEntry);
                            URI baseURI = getBaseURI(rootNode);
                            // if entity, write to output
                            if (fetchedEntry.isEntity()) {
                                EntityDocument entDoc = new EntityDocument(baseURI != null ? baseURI.toString() : fetchedEntry.getUrl(), fetchedEntry.getUrl(), hostManager.getEntityDescriptor(authority.hostId, fetchedEntry.getPattern()), rootNode);
                                write(entDoc);
                            }
                            scrapeLinks(rootNode, baseURI, fetchedURI);
                            fetchedEntry.setLastVisited(new Date());
                            fetchedEntry.setVisitCount(fetchedEntry.getVisitCount() + 1);
                            crawled.add();
                        }
                    } catch (ProcessorStoppedException ex) {
                        // shouldn't happen - sink stopped
                        status = Status.STOPPING;
                    } catch (URISyntaxException ex) {
                        urlWorking = false;
                        Logger.getLogger(HTMLCrawler.class.getName()).log(Level.SEVERE, "Bad URI returned from DB", ex);// shouldn't happen
                    } catch (XPatherException ex) {
                        urlWorking = false;
                        Logger.getLogger(HTMLCrawler.class.getName()).log(Level.WARNING, "No links found");
                    } catch (IOException ex) {
                        if (ex instanceof SocketTimeoutException) {
                            Logger.getLogger(HTMLCrawler.class.getName()).log(Level.SEVERE, "HTTP request timed out: {0}", fetchedEntry.getUrl());
                        } else {
                            Logger.getLogger(HTMLCrawler.class.getName()).log(Level.SEVERE, "I/O error while accessing {0}", fetchedEntry.getUrl());
                        }
                        urlWorking = detectConnectionError();
                        connError.add();
                    }
                    short currScore = fetchedEntry.getScore();
                    short newScore = (short) (urlWorking ? Math.min(0, currScore + 1) : Math.max(currScore - 1, URLEntry.SCORE_MIN));
                    fetchedEntry.setScore(newScore);
                    fetchedEntry.setWorking(newScore > URLEntry.SCORE_MIN);
                    urlMgr.returnEntry(fetchedEntry);// updates and unlocks
                } else {// if no URLs have been returned by fetch...
                    waitForWork();
                }
            }// while
            waitForChildren();
        }
    }

    public HTMLCrawler() {
        super();
    }

    /**
     * Initializes the state using the supplied configuration
     * @param crawlerConf Crawler configuration
     */
    public HTMLCrawler(CrawlerConfiguration crawlerConf) {
        this.conf = crawlerConf;
        initState(conf);
    }

    //<editor-fold defaultstate="collapsed" desc="Accessors">
    /**
     * @see CrawlerConfiguration#isPolicyIgnored()
     */
    public boolean ignoresPolicy() {
        return policyIgnored;
    }

    /**
     * @see CrawlerConfiguration#setPolicyIgnored(boolean)
     */
    public void setIgnoresPolicy(boolean ignoresPolicy) {
        this.policyIgnored = ignoresPolicy;
    }

    /**
     * @see CrawlerConfiguration#setGlobalCrawlDelayMinimum(int)
     */
    public void setGlobalCrawlDelayMinimum(int globalCrawlDelayMinimum) {
        this.globalCrawlDelayMinimum = Math.max(0, globalCrawlDelayMinimum);
    }

    /**
     * @see CrawlerConfiguration#getGlobalCrawlDelayMinimum() 
     */
    public int getGlobalCrawlDelayMinimum() {
        return globalCrawlDelayMinimum;
    }

    /**
     * @see CrawlerConfiguration#isFakeReferrer()
     */
    public boolean isFakeReferrer() {
        return fakeReferrer;
    }

    /**
     * @see CrawlerConfiguration#setFakeReferrer(boolean)
     */
    public void setFakeReferrer(boolean fakeReferrer) {
        this.fakeReferrer = fakeReferrer;
    }

    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="Bootstrap">
    /**
     * Adds valid (matched by a defined pattern) URLs from the supplied collection to the DB.
     * @param urls The list of URLs to add
     * @return Number of valid URLs added to DB
     * @see EntityDescriptor
     * @see HostDescriptor
     */
    public int bootstrapURLs(Collection<String> urls) {
        int validURLs = 0;
        for (String url : urls) {
            validURLs += addURL(url) ? 1 : 0;
        }
        return validURLs;
    }

    /**
     * Adds the supplied URL to the DB, if it matches a defined pattern
     * @param absoluteURL The URL to add
     * @return Indication of success
     */
    private boolean addURL(String absoluteURL) {
        try {
            // normalize and split to host and path
            URL normalized = URLUtil.normalize(absoluteURL);
            String host = URLUtil.fullHost(normalized);
            String file = normalized.getFile();
            // is the host configured?
            if (hostManager.getMapper().containsHost(host)) {
                int hostId = hostManager.getMapper().getHostId(host);
                Pattern pattern = hostManager.getPattern(hostId, file);
                // does the URL match any pattern?
                if (pattern != null) {
                    EntityDescriptor entDesc = hostManager.getEntityDescriptorMap(hostId).get(pattern);
                    boolean entity = entDesc != null;
                    int updateFreq;
                    if (entity) {// if the URL is an entity
                        updateFreq = entDesc.getUpdateFreq();
                    } else {
                        updateFreq = hostManager.getSourceURLMap(hostId).get(pattern);
                    }
                    URLEntry entry = new URLEntry(host, file, new Date(0), 0, updateFreq, entity, pattern.toString(), true, (short) 0);
                    return urlMgr.addEntry(entry);
                }
            } else {
                Logger.getLogger(HTMLCrawler.class.getName()).log(Level.WARNING, "Host {0} is not configured", host);
            }
        } catch (MalformedURLException ex) {
            Logger.getLogger(HTMLCrawler.class.getName()).log(Level.WARNING, "Invalid URL: {0}", ex.getMessage());
        }
        return false;
    }
    //</editor-fold>

    /**
     * Reads a file containing list of URL and calls {@link #bootstrapURLs(java.util.Collection) bootStrapURLs}.
     */
    public void bootstrapFromFile(File bootFile) {
        if (bootFile.exists()) {
            BufferedReader br = null;
            Collection<String> lines = new ArrayList<String>();
            try {
                br = new BufferedReader(new FileReader(bootFile));
                String line = null;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        lines.add(line);
                    }
                }
                bootstrapURLs(lines);
                // rename if read
            } catch (FileNotFoundException ex) {
                Logger.getLogger(HTMLCrawler.class.getName()).log(Level.WARNING, "Failed to read bootstrap file: {0}", ex.getMessage());
            } catch (IOException ex) {
                Logger.getLogger(HTMLCrawler.class.getName()).log(Level.WARNING, "I/O error while reading bootstrap file: {0}", ex.getMessage());
            } finally {
                if (br != null) {
                    try {
                        br.close();
                        bootFile.renameTo(new File(bootFile.getPath() + BOOTSTRAP_OLD_SUFFIX));
                    } catch (IOException ex) {
                        Logger.getLogger(HTMLCrawler.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        } else {
            Logger.getLogger(HTMLCrawler.class.getName()).log(Level.FINE, "Bootstrap file not found");
        }
    }

    /**
     * Initializes the state of this crawler using the supplied configuration.
     * Instantiates parent crawler for each host and creates threads
     * @param crawlerConf The configuration
     */
    private void initState(CrawlerConfiguration crawlerConf) {
        try {
            this.db = crawlerConf.getDBLayer();
            hostManager = new HostManager(db);
            hostManager.loadHosts(crawlerConf.getHosts());
            urlMgr = new URLManager(db);
            hostIds = hostManager.getHostIds();
            threadMap = new HashMap<HostCrawler, Thread>(hostIds.size());
            globalCrawlDelayMinimum = crawlerConf.getGlobalCrawlDelayMinimum();
            policyIgnored = crawlerConf.isPolicyIgnored();
            fakeReferrer = crawlerConf.isFakeReferrer();
            for (int hostId : hostIds) {
                HostCrawler crawler = new HostCrawler(hostId, crawlerConf.getThreadsPerHost());
                Thread thread = new Thread(crawler, String.format(CRAWLER_NAME_FORMAT, CRAWLER_NAME, hostManager.getHostDescriptor(hostId).getName(), 0));
                threadMap.put(crawler, thread);
            }
        } catch (MalformedURLException ex) {
            failStart("Bad URL in HostDescriptor", ex);
        } catch (SQLException ex) {
            failStart("Failed to initialize HostManager or URLManager: " + ex.getMessage());
        } catch (ConfigurationException ex) {
            failStart("Can't start HostCrawler thread: " + ex.getMessage());
        }
    }

    /**
     * Deserializes crawler configuration from XML and initializes crawler state
     */
    @Override
    protected void initWithContext() {
        try {
            File confFile = ((FSContext) getContext()).getFile(confFileName);
            CrawlerConfiguration crawlerConf = Util.objectFromXml(confFile.getAbsolutePath(), CrawlerConfiguration.class);
            initState(crawlerConf);
        } catch (ConfigurationException ex) {
            failStart("Can't read configuration: " + ex.getMessage());
        }
    }

    @Override
    protected void initPostContext() {
        Stats stats = new Stats(this);
        connError = stats.newFunction("err.connError", Sum.class);
        validLinksFound = stats.newFunction("stat.validLinks", Sum.class);
        newLinksFound = stats.newFunction("stat.newLinks", Sum.class);
        crawled = stats.newFunction("stat.crawled", Sum.class);
        avgFetch = stats.newFunction("perf.avgFetch", Average.class);
        avgScrape = stats.newFunction("perf.avgScrape", Average.class);
        avgHTMLParse = stats.newFunction("perf.avgHTMLParse", Average.class);
    }

    /**
     * Starts the crawling threads, waits for all to die, then stops.
     * Looks for a bootstrap file which should contain a list of URLs to bootstrap
     * the crawler. Once the list is read, it is renamed, so that the same URLs
     * won't be read again.
     * Also, before starting, checks for locked URLs (from previous run)
     * and unlocks them.
     */
    @Override
    public void run() {
        this.status = Status.RUNNING;
        // bootstrap
        File bootFile = ((FSContext) getContext()).getFile(bootstrapFileName);
        bootstrapFromFile(bootFile);
        // unlock locked urls
        Collection<URLEntry> locked = urlMgr.listLocked();
        if (!locked.isEmpty()) {
            Logger.getLogger(HTMLCrawler.class.getName()).log(Level.WARNING, "Found {0} locked URLs, unlocking", locked.size());
            boolean unlocked = urlMgr.unlockAll();
            Logger.getLogger(HTMLCrawler.class.getName()).log(Level.INFO, unlocked ? "URLs unlocked successfully" : "Failed to unlock URLs");
        }
        urlMgr.close();
        // start host masters
        for (Thread thread : threadMap.values()) {
            thread.start();
        }
        // wait for all threads to die
        for (Thread thread : threadMap.values()) {
            try {
                thread.join();
            } catch (InterruptedException ex) {
                stop();
                Logger.getLogger(HTMLCrawler.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        hostManager.close();
        stop();
    }
}
