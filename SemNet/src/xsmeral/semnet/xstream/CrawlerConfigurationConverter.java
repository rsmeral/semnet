package xsmeral.semnet.xstream;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.Collection;
import xsmeral.semnet.crawler.RDBLayer;
import xsmeral.semnet.crawler.model.CrawlerConfiguration;
import xsmeral.semnet.crawler.model.HostDescriptor;

/**
 * Converter for {@link CrawlerConfiguration}.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
public class CrawlerConfigurationConverter implements Converter {

    private static final String RDBLAYER_NODE = "dbLayer";
    private static final String THREADS_NODE = "threadsPerHost";
    private static final String CRAWLDELAY_NODE = "globalCrawlDelayMinimum";
    private static final String POLICY_NODE = "policyIgnored";
    private static final String FAKEREFERER_NODE = "fakeReferrer";
    private static final String HOSTS_NODE = "hosts";
    private static final String HOST_NODE = "host";

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        CrawlerConfiguration conf = (CrawlerConfiguration) source;

        // db layer
        RDBLayer rdb = conf.getDBLayer();
        if (rdb != null) {
            writer.startNode(RDBLAYER_NODE);
            context.convertAnother(rdb);
            writer.endNode();
        }

        // threads
        int threads = conf.getThreadsPerHost();
        if (threads <= 0) {
            threads = 1;
        }
        writer.startNode(THREADS_NODE);
        writer.setValue(Integer.toString(threads));
        writer.endNode();

        // delay
        int delay = conf.getGlobalCrawlDelayMinimum();
        if (delay <= 0) {
            delay = CrawlerConfiguration.DEF_GLOBAL_CRAWL_DELAY_MIN;
        }
        writer.startNode(CRAWLDELAY_NODE);
        writer.setValue(Integer.toString(delay));
        writer.endNode();

        // policy
        writer.startNode(POLICY_NODE);
        writer.setValue(Boolean.toString(conf.isPolicyIgnored()));
        writer.endNode();

        // fake referer
        writer.startNode(FAKEREFERER_NODE);
        writer.setValue(Boolean.toString(conf.isFakeReferrer()));
        writer.endNode();

        // host descriptors
        Collection<HostDescriptor> hosts = conf.getHosts();
        if (!hosts.isEmpty()) {
            writer.startNode(HOSTS_NODE);
            context.convertAnother(hosts);
            writer.endNode();
        }
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        CrawlerConfiguration conf = new CrawlerConfiguration();
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            String curNode = reader.getNodeName();
            if (RDBLAYER_NODE.equals(curNode)) {
                RDBLayer rdb = (RDBLayer) context.convertAnother(conf, RDBLayer.class);
                conf.setDBLayer(rdb);
            } else if (THREADS_NODE.equals(curNode)) {
                conf.setThreadsPerHost(Integer.parseInt(reader.getValue()));
            } else if (CRAWLDELAY_NODE.equals(curNode)) {
                conf.setGlobalCrawlDelayMinimum(Integer.parseInt(reader.getValue()));
            } else if (POLICY_NODE.equals(curNode)) {
                conf.setPolicyIgnored(Boolean.parseBoolean(reader.getValue()));
            } else if (FAKEREFERER_NODE.equals(curNode)) {
                conf.setFakeReferrer(Boolean.parseBoolean(reader.getValue()));
            } else if (HOSTS_NODE.equals(curNode)) {
                Collection<HostDescriptor> hosts = (Collection<HostDescriptor>) context.convertAnother(conf, Collection.class);
                conf.setHosts(hosts);
            }
            reader.moveUp();
        }
        return conf;
    }

    @Override
    public boolean canConvert(Class type) {
        return CrawlerConfiguration.class.equals(type);
    }
}
