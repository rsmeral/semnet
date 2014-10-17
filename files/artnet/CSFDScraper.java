package xsmeral.artnet.scraper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.htmlcleaner.TagNode;
import org.openrdf.model.URI;
import org.openrdf.model.vocabulary.OWL;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.RDFS;
import xsmeral.semnet.scraper.onto.EntityClass;
import xsmeral.semnet.scraper.onto.Term;
import xsmeral.semnet.crawler.model.EntityDocument;
import xsmeral.semnet.scraper.AbstractScraper;
import static xsmeral.semnet.util.XPathUtil.*;

public class CSFDScraper {

    public static class Film extends AbstractScraper {

        private static final String NODE_ACTORS = "Hrají:";
        private static final String NODE_DIRECTORS = "Režie:";
        private final Pattern PATT_ORIGIN = Pattern.compile("(?:(?!\\d+)([^,]+),?\\s*)?(?:(\\d{4}),?\\s*)?(?:(?:\\d+\\s*x\\s*)?(?:\\s*(\\d+)\\s*min))?");
        public static final String NAMESPACE = "http://www.csfd.cz#";
        @Term
        public static final URI TYPE = RDF.TYPE;
        @Term("Used for IMDb link")
        public static final URI SAME_AS = OWL.SAMEAS;
        @Term("Used for www link")
        public static final URI SEE_ALSO = RDFS.SEEALSO;
        @Term
        @EntityClass
        public static final URI FILM = f.createURI(NAMESPACE, "film");
        @Term
        public static final URI FILM_NAME = RDFS.LABEL;
        @Term
        public static final URI DIRECTED_BY = f.createURI(NAMESPACE, "directed_by");
        @Term
        public static final URI ACTS_IN = f.createURI(NAMESPACE, "acts_in");
        @Term
        public static final URI GENRE = f.createURI(NAMESPACE, "genre");
        @Term
        public static final URI ORIGIN = f.createURI(NAMESPACE, "origin");
        @Term
        public static final URI YEAR = f.createURI(NAMESPACE, "year");
        @Term("Duration in minutes")
        public static final URI DURATION = f.createURI(NAMESPACE, "duration");

        @Override
        protected void scrape(EntityDocument doc) throws Exception {
            TagNode root = doc.getDocument();
            TagNode contentNode = (TagNode) root.evaluateXPath("//*[@id='profile']/div[@class='content']")[0];
            TagNode infoNode = (TagNode) contentNode.evaluateXPath("div[@class='info']")[0];

            // state type of this entity
            fact(TYPE, FILM);

            // name
            String mainName = queryText(infoNode, "h1");
            fact(FILM_NAME, lit(mainName));

            // other names
            for (TagNode node : queryNodes(infoNode, "ul[@class='names']/li/h3")) {
                fact(FILM_NAME, lit(getText(node)));
            }

            // genres
            // Drama / Thriller / Mysteriózní
            String genresStr = queryText(infoNode, "p[@class='genre']");
            if (genresStr != null) {
                String[] genres = genresStr.split("/");
                for (String genreStr : genres) {
                    String genre = genreStr.trim();
                    fact(GENRE, lit(genre));
                }
            }

            // (countries), (year), ((nn x ) duration) - each optional
            // USA / Velká Británie / Německo / Francie, 2011, 30 x 22 min
            String originStr = queryText(infoNode, "p[@class='origin']");
            if (originStr != null) {
                Matcher m = PATT_ORIGIN.matcher(originStr);
                if (m.find()) {
                    String countriesStr = m.group(1);
                    String yearStr = m.group(2);
                    String durationStr = m.group(3);
                    if (countriesStr != null) {
                        String[] countries = countriesStr.split("/");
                        for (String country : countries) {
                            fact(ORIGIN, lit(country));
                        }
                    }
                    if (yearStr != null) {
                        fact(YEAR, f.createLiteral(Integer.parseInt(yearStr.trim())));
                    }
                    if (durationStr != null) {
                        fact(DURATION, f.createLiteral(Integer.parseInt(durationStr.trim())));
                    }
                }
            }

            List<TagNode> creatorNodes = queryNodes(infoNode, "div[h4]");
            for (TagNode creatorsNode : creatorNodes) {
                String nodeType = queryText(creatorsNode, "h4");
                if (nodeType.contains(NODE_ACTORS)) {
                    for (String actor : queryTextNodes(creatorsNode, "span/a/@href")) {
                        fact(uri(actor), ACTS_IN, current());
                    }
                } else if (nodeType.contains(NODE_DIRECTORS)) {
                    for (String director : queryTextNodes(creatorsNode, "span/a/@href")) {
                        fact(DIRECTED_BY, uri(director));
                    }
                }
            }

            TagNode linksNode = (TagNode) contentNode.evaluateXPath("ul[@class='links']")[0];
            if (linksNode != null && linksNode.hasChildren()) {
                // imdb link
                String imdbLink = queryText(linksNode, "li/a[@class='imdb']/@href");
                if (imdbLink != null) {
                    fact(SAME_AS, uri(imdbLink));
                }
                // www link
                String wwwLink = queryText(linksNode, "li/a[@class='www']/@href");
                if (wwwLink != null) {
                    fact(SEE_ALSO, uri(wwwLink));
                }
            }
        }

        @Override
        public String getNamespace() {
            return NAMESPACE;
        }
    }

    public static class Creator extends AbstractScraper {

        private static final String DIR_FILMOGRAPHY = "Režijní";
        private static final String ACT_FILMOGRAPHY = "Herecká";
        private static final Pattern PATT_BIRTH = Pattern.compile("nar\\.\\s*(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");
        public static final String NAMESPACE = "http://www.csfd.cz#";
        @Term
        public static final URI TYPE = RDF.TYPE;
        @Term("Used for IMDb link")
        public static final URI SAME_AS = OWL.SAMEAS;
        @Term
        @EntityClass
        public static final URI DIRECTOR = f.createURI(NAMESPACE, "director");
        @Term
        @EntityClass
        public static final URI ACTOR = f.createURI(NAMESPACE, "actor");
        @Term
        public static final URI PERSON_NAME = RDFS.LABEL;
        @Term
        public static final URI BIRTH_DATE = f.createURI(NAMESPACE, "birth_date");

        @Override
        protected void scrape(EntityDocument doc) throws Exception {

            TagNode root = doc.getDocument();
            TagNode contentNode = (TagNode) root.evaluateXPath("//*[@id='profile']/div[@class='content']")[0];
            TagNode infoNode = (TagNode) contentNode.evaluateXPath("div[@class='info']")[0];


            // name
            String name = queryText(infoNode, "h1");
            if (name != null) {
                fact(PERSON_NAME, lit(name));
            }

            // type
            for (String filmography : queryTextNodes(root, "data(//*[@id='filmography']//div[@class='header']/h2)")) {
                if (filmography.contains(DIR_FILMOGRAPHY)) {
                    fact(TYPE, DIRECTOR);
                } else if (filmography.contains(ACT_FILMOGRAPHY)) {
                    fact(TYPE, ACTOR);
                }
            }

            // birth date
            String birthStr = queryText(infoNode, "ul[1]/li");
            if (birthStr != null) {
                Matcher m = PATT_BIRTH.matcher(birthStr);
                if (m.find()) {
                    try {
                        int day = Integer.parseInt(m.group(1));
                        int month = Integer.parseInt(m.group(2));
                        int year = Integer.parseInt(m.group(3));
                        XMLGregorianCalendar birthDate = DatatypeFactory.newInstance().newXMLGregorianCalendarDate(year, month, day, DatatypeConstants.FIELD_UNDEFINED);
                        fact(BIRTH_DATE, f.createLiteral(birthDate));
                    } catch (NumberFormatException ex) {
                    }
                }
            }
            // imdb link
            String imdbLink = queryText(contentNode, "ul[@class='links']/li/a[@class='imdb']/@href");
            if (imdbLink != null) {
                fact(SAME_AS, uri(imdbLink));
            }
        }

        @Override
        public String getNamespace() {
            return NAMESPACE;
        }
    }

}
