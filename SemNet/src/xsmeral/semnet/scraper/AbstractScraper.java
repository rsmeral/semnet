package xsmeral.semnet.scraper;

import java.net.URISyntaxException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringEscapeUtils;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.RDFS;
import xsmeral.pipe.LocalObjectFilter;
import xsmeral.pipe.ProcessorStoppedException;
import xsmeral.pipe.interfaces.ObjectProcessorInterface;
import xsmeral.pipe.stats.StatFunction;
import xsmeral.pipe.stats.Stats;
import xsmeral.pipe.stats.Sum;
import xsmeral.semnet.crawler.model.EntityDocument;
import xsmeral.semnet.scraper.onto.EntityClass;
import xsmeral.semnet.scraper.onto.Term;
import xsmeral.semnet.util.URLUtil;
import xsmeral.semnet.util.Util;
import xsmeral.semnet.util.XPathUtil;

/**
 * A scraper works in co-operation with crawler, extracting data from web pages.
 * This implementation uses Sesame {@link Statement}s to represent facts and
 * XPath to extract data.<br />
 * The only method that needs to be implemented is the {@link #scrape(xsmeral.semnet.crawler.model.EntityDocument) scrape(EntityDocument)}
 * method, which should scrape one EntityDocument. If more entity types are
 * processed in one processing chain, the {@link ScraperWrapper} can be used
 * to route EntityDocuments to correct scrapers, based on their class (and configuration).
 * Convenience methods like {@link #fact(org.openrdf.model.Resource, org.openrdf.model.URI, org.openrdf.model.Value) fact},
 * {@link #uri(java.lang.String) uri} or {@link #lit(java.lang.String) lit} are
 * provided. Also, the {@link XPathUtil} can be used, providing simple methods for
 * querying the DOM tree.
 *
 * @see Stats
 * @see ScraperWrapper
 * @init stats (optional) Name of stats group for this scraper. Default is simple class name.
 * @author Ron Šmeral (xsmeral@fi.muni.cz)
 */
@ObjectProcessorInterface(in = EntityDocument.class, out = Statement.class)
public abstract class AbstractScraper extends LocalObjectFilter<EntityDocument, Statement> {

    private static final char UNKNOWN_CHAR = '?';
    private static final String SCRAPE_ERRORS = "err.scrapeErrors";
    //
    private Stats stats;
    private StatFunction<Long> scrapeErrors;
    protected static final ValueFactory f = ValueFactoryImpl.getInstance();
    private URI thisURI;
    protected EntityDocument doc;

    public AbstractScraper() {
    }

    /**
     * Returns a ValueFactory (instantiated at initialization).
     */
    protected static ValueFactory getValueFactory() {
        return f;
    }

    /**
     * Writes a statement composed of given subject, predicate and object to the output.
     * @param sub The subject
     * @param pred The predicate
     * @param obj The object
     */
    protected void fact(Resource sub, URI pred, Value obj) throws ProcessorStoppedException {
        write(f.createStatement(sub, pred, obj));
    }

    /**
     * Writes a statement composed of {@link #current() current()} as subject
     * and given predicate and object to the output.
     * @see #fact(org.openrdf.model.Resource, org.openrdf.model.URI, org.openrdf.model.Value) 
     */
    protected void fact(URI pred, Value obj) throws ProcessorStoppedException {
        write(f.createStatement(current(), pred, obj));
    }

    /**
     * Returns Sesame Literal for the specified string.
     * Also performs decoding of HTML special entities.
     * @param literal The literal string
     * @return Literal value
     */
    protected Value lit(String literal) {
        String decoded = StringEscapeUtils.unescapeHtml(literal.trim());
        return f.createLiteral(decoded);
    }

    /**
     * Returns given URI normalized and resolved against current base URL if relative.
     * @param uri The URI to normalize
     * @return Normalized and resolved URI
     * @throws MalformedURLException If the given string does not contain a valid URL
     */
    protected URI uri(String uri) throws URISyntaxException, MalformedURLException {
        java.net.URI netURI = new java.net.URI(uri);
        if (netURI.isAbsolute()) {
            return f.createURI(URLUtil.normalize(uri).toString().trim());
        } else {// relative uri supplied
            return f.createURI(URLUtil.normalize(new java.net.URI(doc.getBaseURL()).resolve(netURI).toURL()).toString().trim());
        }
    }

    /**
     * Returns currently processed URL as an instance of URI.
     */
    protected URI current() {
        if (thisURI == null) {
            thisURI = f.createURI(doc.getUrl());
        }
        return thisURI;
    }

    /**
     * Returns map of all terms and their definitions in this scraper's vocabulary (fields of type URI annotated with {@link Term}).
     */
    public Map<URI, String> getVocabulary() {
        Map<URI, String> vocab = new HashMap<URI, String>();
        Field[] fields = getClass().getDeclaredFields();
        for (Field field : fields) {
            Term t = field.getAnnotation(Term.class);
            if (t != null) {
                try {
                    URI term = (URI) field.get(this);
                    String def = t.value();
                    vocab.put(term, def);
                } catch (IllegalArgumentException ex) {
                    Logger.getLogger(AbstractScraper.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IllegalAccessException ex) {
                    Logger.getLogger(AbstractScraper.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return vocab;
    }

    /**
     * Outputs a statement ({@code uri}, {@link RDF#TYPE}, {@link RDFS#CLASS})
     * for each field of type {@link URI} annotated with {@link EntityClass}.
     * <br />
     * {@inheritDoc}
     */
    @Override
    protected void preRun() throws ProcessorStoppedException {
        entityClassStatement();
    }

    /**
     * Initializes the stats.
     * <br />
     * {@inheritDoc}
     * @see Stats
     */
    @Override
    protected void initPostContext() {
        final String group = Util.nonNull(getParams().get(Stats.PARAM_STATS), AbstractScraper.class.getSimpleName());
        stats = new Stats(group, getContext());
        scrapeErrors = stats.newFunction(SCRAPE_ERRORS, Sum.class);
    }

    private void entityClassStatement() throws ProcessorStoppedException {
        for (Field field : getClass().getDeclaredFields()) {
            EntityClass ec = field.getAnnotation(EntityClass.class);
            if (ec != null) {
                try {
                    URI classURI = (URI) field.get(this);
                    fact(classURI, RDF.TYPE, RDFS.CLASS);
                } catch (IllegalArgumentException ex) {
                    Logger.getLogger(AbstractScraper.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IllegalAccessException ex) {
                    Logger.getLogger(AbstractScraper.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    /**
     * Calls {@link #scrape(xsmeral.semnet.crawler.model.EntityDocument) scrape(EntityDocument)}
     * and catches any Exception, logging it as a parsing error.
     */
    @Override
    protected void process() throws ProcessorStoppedException {
        doc = read();
        thisURI = null;
        try {
            scrape(doc);
        } catch (Exception ex) {
            // propagate the stopping, catch everything else
            if (ex instanceof ProcessorStoppedException) {
                throw (ProcessorStoppedException) ex;
            }
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "Parsing failure in {0} in document {1}: {2}: {3}", new Object[]{getClass().getName(), doc.getUrl(), ex.getClass().getSimpleName(), ex.getMessage()});
            scrapeErrors.add();
        }
    }

    /**
     * Returns namespace used by this scraper.
     */
    public abstract String getNamespace();

    /**
     * Scrapes one document and outputs any number of facts.
     * @param doc The document to scrape
     * @throws Exception Can throw any exception
     */
    protected abstract void scrape(EntityDocument doc) throws Exception;
}
