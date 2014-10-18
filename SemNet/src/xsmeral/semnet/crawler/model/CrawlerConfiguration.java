package xsmeral.semnet.crawler.model;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import java.util.Collection;
import xsmeral.semnet.crawler.HTMLCrawler;
import xsmeral.semnet.crawler.RDBLayer;
import xsmeral.semnet.crawler.util.RobotsPolicy;
import xsmeral.semnet.xstream.CrawlerConfigurationConverter;

/**
 * Container for {@link HTMLCrawler} configuration.
 * <br />
 * Contains
 * <ul>
 *  <li>A {@link RDBLayer}</li>
 *  <li>number of threads per host</li>
 *  <li>global crawl delay minimum (in ms)</li>
 *  <li>an indicator, whether the robots policy should be generally ignored</li>
 *  <li>an indicator, whether the crawler should supply fake referrer (the base URL of host)</li>
 *  <li>collection of {@link HostDescriptor}s</li>
 * </ul>
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
@XStreamConverter(CrawlerConfigurationConverter.class)
@XStreamAlias("crawler")
public class CrawlerConfiguration {

    public static final int DEF_GLOBAL_CRAWL_DELAY_MIN = 40;//ms
    public static final boolean DEF_POLICY_IGNORED = false;
    public static final boolean DEF_FAKE_REFERRER = false;

    private RDBLayer dbLayer;
    private int threadsPerHost;
    private int globalCrawlDelayMinimum;
    private boolean policyIgnored;
    private boolean fakeReferrer;
    private Collection<HostDescriptor> hosts;

    public CrawlerConfiguration() {
    }

    /**
     * Initializes all fields
     */
    public CrawlerConfiguration(Collection<HostDescriptor> hosts, RDBLayer dbLayer, int threadsPerHost, int globalCrawlDelayMinimum, boolean policyIgnored, boolean fakeReferrer) {
        this.hosts = hosts;
        this.dbLayer = dbLayer;
        this.threadsPerHost = threadsPerHost;
        this.globalCrawlDelayMinimum = globalCrawlDelayMinimum;
        this.policyIgnored = policyIgnored;
        this.fakeReferrer = fakeReferrer;
    }

    /**
     * The relational DB layer used by the crawler for state persistence (URL storage)
     */
    public RDBLayer getDBLayer() {
        return dbLayer;
    }

    /**
     * The relational DB layer used by the crawler for state persistence (URL storage)
     */
    public void setDBLayer(RDBLayer dbLayer) {
        this.dbLayer = dbLayer;
    }

    /**
     * Indication, whether the HTTP Referer header should be set to the base URL of the host
     */
    public boolean isFakeReferrer() {
        return fakeReferrer;
    }

    /**
     * Indication, whether the HTTP Referer header should be set to the base URL of the host
     */
    public void setFakeReferrer(boolean fakeReferrer) {
        this.fakeReferrer = fakeReferrer;
    }

    /**
     * Minimal crawl delay in milliseconds
     */
    public int getGlobalCrawlDelayMinimum() {
        return globalCrawlDelayMinimum;
    }

    /**
     * Minimal crawl delay in milliseconds
     */
    public void setGlobalCrawlDelayMinimum(int globalCrawlDelayMinimum) {
        this.globalCrawlDelayMinimum = globalCrawlDelayMinimum;
    }

    /**
     * Hosts crawled by the crawler
     */
    public Collection<HostDescriptor> getHosts() {
        return hosts;
    }

    /**
     * Hosts crawled by the crawler
     */
    public void setHosts(Collection<HostDescriptor> hosts) {
        this.hosts = hosts;
    }

    /**
     * Indication of adherence to the Robots Exclusion Protocol
     *
     * @see RobotsPolicy
     */
    public boolean isPolicyIgnored() {
        return policyIgnored;
    }

    /**
     * Indication of adherence to the Robots Exclusion Protocol
     *
     * @see RobotsPolicy
     */
    public void setPolicyIgnored(boolean policyIgnored) {
        this.policyIgnored = policyIgnored;
    }

    /**
     * Number of crawling threads per host
     */
    public int getThreadsPerHost() {
        return threadsPerHost;
    }

    /**
     * Number of crawling threads per host
     */
    public void setThreadsPerHost(int threadsPerHost) {
        this.threadsPerHost = threadsPerHost;
    }
}
