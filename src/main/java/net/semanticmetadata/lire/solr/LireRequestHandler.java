package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.imageanalysis.*;
import net.semanticmetadata.lire.impl.SimpleResult;
import net.semanticmetadata.lire.indexing.hashing.BitSampling;
import net.semanticmetadata.lire.utils.ImageUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.MultiDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.parser.ParseException;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.QParser;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.search.SyntaxError;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.semanticmetadata.lire.imageanalysis.joint.JointHistogram;

/**
 * This is the main LIRE RequestHandler for the Solr Plugin. It supports query
 * by example using the indexed id, an url or a feature vector. Furthermore,
 * feature extraction and random selection of images are supported.
 *
 * @author Mathias Lux, mathias@juggle.at, 07.07.13
 * @author Nurul Ferdous <nurul@ferdo.us>
 * {@link net.semanticmetadata.lire.solr.LireRequestHandler} objects.
 */
public class LireRequestHandler extends RequestHandlerBase {

    private static final HashMap<String, Class> fieldToClass = new HashMap<>(11);
    volatile long totalTime = 0;
    volatile int defaultNumberOfResults = 60;
    volatile int defaultStartValue = 0;
    volatile String defaultAlgorithmField = "cl_ha";
    volatile long numErrors;
    volatile long numRequests;

    /**
     * number of candidate results retrieved from the index. The higher this
     * number, the slower, the but more accurate the retrieval will be. 10k is a
     * good value for starters.
     */
    private int numberOfCandidateResults = 40000;
    private static final int DEFAULT_NUMBER_OF_CANDIDATES = 40000;

    /**
     * The number of query terms that go along with the TermsFilter search. We
     * need some to get a score, the less the faster. I put down a minimum of
     * three in the method, this value gives the percentage of the overall
     * number used (selected randomly).
     */
    private double numberOfQueryTerms = 0.33;
    private static final double DEFAULT_NUMBER_OF_QUERY_TERMS = 0.33;

    static {
        fieldToClass.put("cl_ha", ColorLayout.class);
        fieldToClass.put("ph_ha", PHOG.class);
        fieldToClass.put("oh_ha", OpponentHistogram.class);
        fieldToClass.put("eh_ha", EdgeHistogram.class);
        fieldToClass.put("jc_ha", JCD.class);
        fieldToClass.put("su_ha", SurfSolrFeature.class);
        fieldToClass.put("ce_ha", CEDD.class);
        fieldToClass.put("sc_ha", ScalableColor.class);
        fieldToClass.put("fc_ha", FCTH.class);
        fieldToClass.put("fo_ha", FuzzyOpponentHistogram.class);
        fieldToClass.put("jh_ha", JointHistogram.class);

        // one totalTime hash function read ...
        try {
            BitSampling.readHashFunctions();
        } catch (IOException ioe) {
            //LOG.error(ioe.getMessage());
        }
    }

    @Override
    public void init(NamedList args) {
        super.init(args);
        // Caching off by default
        httpCaching = false;
        if (args != null) {
            Object caching = args.get("httpCaching");
            if (caching != null) {
                httpCaching = Boolean.parseBoolean(caching.toString());
            }
        }
    }

    /**
     * Handles three types of requests.
     * <ol>
     * <li>search by already extracted images.</li>
     * <li>search by an image URL.</li>
     * <li>Random results.</li>
     * </ol>
     *
     * @param req
     * @param rsp
     * @throws Exception
     */
    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        SolrParams params = req.getParams();
        numberOfQueryTerms = req.getParams().getDouble("accuracy", DEFAULT_NUMBER_OF_QUERY_TERMS);
        numberOfCandidateResults = req.getParams().getInt("candidates", DEFAULT_NUMBER_OF_CANDIDATES);

        numRequests++;
        long startTime = System.currentTimeMillis();
        try {
            if (params.get("hashes") != null) {
                // we are searching for hashes ...
                handleHashSearch(req, rsp);
            } else if (params.get("url") != null) {
                // we are searching for an image based on an URL
                handleUrlSearch(req, rsp);
            } else if (params.get("id") != null) {
                // we are searching for an image based on an ID [what? -ed]
                handleIdSearch(req, rsp);
            } else if (params.get("extract") != null) {
                // we are trying to extract features from an image based on an URL
                handleExtract(req, rsp);
            } else { // lets return random results.
                handleRandomSearch(req, rsp);
            }
        } catch (IOException | IllegalAccessException | InstantiationException e) {
            numErrors++;
            //LOG.error(e.getMessage());
        } finally {
            totalTime += System.currentTimeMillis() - startTime;
        }
    }

    /**
     * Handles the get parameters id, field and rows.
     *
     * @param req
     * @param rsp
     * @throws IOException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    private void handleIdSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, InstantiationException, IllegalAccessException {
        SolrIndexSearcher searcher = req.getSearcher();
        try {
            TopDocs hits = searcher.search(new TermQuery(new Term("id", req.getParams().get("id"))), 1);
            String paramField = defaultAlgorithmField;
            if (req.getParams().get("field") != null) {
                paramField = req.getParams().get("field");
            }
            LireFeature queryFeature = (LireFeature) fieldToClass.get(paramField).newInstance();
            rsp.add("QueryField", paramField);
            rsp.add("QueryFeature", queryFeature.getClass().getName());
            rsp.add("params", req.getParams().toNamedList());

            if (hits.scoreDocs.length > 0) {
                // Using DocValues to get the actual data from the index.
                BinaryDocValues binaryValues = MultiDocValues.getBinaryValues(searcher.getIndexReader(), paramField.replace("_ha", "_hi")); // ***  #
                if (binaryValues == null) {
                    //LOG.info("Could not find the DocValues of the query document. Are they in the index?");
                    return;
                }
                BytesRef bytesRef = new BytesRef();
                binaryValues.get(hits.scoreDocs[0].doc);
//                Document d = searcher.getIndexReader().document(hits.scoreDocs[0].doc);
                String histogramFieldName = paramField.replace("_ha", "_hi");
                //LOG.info(histogramFieldName);

                queryFeature.setByteArrayRepresentation(bytesRef.bytes, bytesRef.offset, bytesRef.length);

                int paramRows = defaultNumberOfResults;
                if (req.getParams().getInt("rows") != null) {
                    paramRows = req.getParams().getInt("rows");
                }

                int paramStarts = defaultStartValue;
                if (req.getParams().getInt("start") != null) {
                    paramStarts = req.getParams().getInt("start");
                }

                // Re-generating the hashes to save space (instead of storing them in the index)
                int[] hashes = BitSampling.generateHashes(queryFeature.getDoubleHistogram());
                // just use 50% of the hashes for search ...
                BooleanQuery query = createQuery(hashes, paramField, 0.5d);
                doSearch(rsp, req, paramField, paramStarts, paramRows, query, queryFeature);
            } else {
                numErrors++;
                rsp.add("Error", "Did not find an image with the given id " + req.getParams().get("id"));
            }
        } catch (IOException | InstantiationException | IllegalAccessException ex) {
            numErrors++;
            rsp.add("Error", "There was an error with your search for the image with the id " + req.getParams().get("id")
                    + ": " + ex.getMessage());
        } catch (ParseException | SyntaxError sex) {            
            numErrors++;
            rsp.add("Error", "There was an error with your search for the image with the id " + req.getParams().get("id")
                    + ": " + sex.getMessage());
        }
    }

    /**
     * Returns a random set of documents from the index. Mainly for testing
     * purposes.
     *
     * @param req
     * @param rsp
     * @throws IOException
     */
    private void handleRandomSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException {
        SolrIndexSearcher searcher = req.getSearcher();
        DirectoryReader indexReader = searcher.getIndexReader();
        double maxDoc = indexReader.maxDoc();

        int paramRows = defaultNumberOfResults;
        if (req.getParams().getInt("rows") != null) {
            paramRows = req.getParams().getInt("rows");
        }

        int paramStarts = defaultStartValue;
        if (req.getParams().getInt("start") != null) {
            paramStarts = req.getParams().getInt("start");
        }

        LinkedList list = new LinkedList();
        while (list.size() < paramRows && list.size() > paramStarts) {
            HashMap m = new HashMap(2);
            Document doc = indexReader.document((int) Math.floor(Math.random() * maxDoc));
            m.put("id", doc.getValues("id")[0]);
            m.put("title", doc.getValues("title")[0]);
            list.add(m);
        }
        rsp.add("docs", list);
        rsp.add("params", req.getParams().toNamedList());
    }

    /**
     * Searches for an image given by an URL. Note that (i) extracting image
     * features takes totalTime and (ii) not every image is readable by Java.
     *
     * @param req
     * @param rsp
     * @throws IOException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    private void handleUrlSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, InstantiationException, IllegalAccessException, ParseException, SyntaxError {

        SolrParams params = req.getParams();
        String q = params.get(CommonParams.Q);
        String[] fqs = params.getParams(CommonParams.FQ);
        
        int paramStarts = defaultStartValue;
        try {
            paramStarts = Integer.parseInt(params.get(CommonParams.START));
        } catch (Exception e) { /* default */ }
        
        int paramRows = defaultNumberOfResults;
        try {
            paramRows = Integer.parseInt(params.get(CommonParams.ROWS));
        } catch (Exception e) { /* default */ }

        String paramUrl = null;
        if (params.get("url") != null) {
            paramUrl = params.get("url");
        }
        
        String paramField = defaultAlgorithmField;
        if (req.getParams().get("field") != null) {
            paramField = req.getParams().get("field");
        }

        LireFeature feat = null;
        BooleanQuery query = null;
        // wrapping the whole part in the try
        try {
            BufferedImage img = ImageIO.read(new URL(paramUrl).openStream());
            img = ImageUtils.trimWhiteSpace(img);
            // getting the right feature per field:
            switch (paramField) {
                case "cl_ha":
                    feat = new ColorLayout();
                    break;
                case "jc_ha":
                    feat = new JCD();
                    break;
                case "ph_ha":
                    feat = new PHOG();
                    break;
                case "oh_ha":
                    feat = new OpponentHistogram();
                    break;
                case "eh_ha":
                    feat = new EdgeHistogram();
                    break;
                case "su_ha":
                    feat = new SurfSolrFeature();
                    break;
                case "ce_ha":
                    feat = new CEDD();
                    break;
                case "sc_ha":
                    feat = new ScalableColor();
                    break;
                case "fc_ha":
                    feat = new FCTH();
                    break;
                case "fo_ha":
                    feat = new FuzzyOpponentHistogram();
                    break;
                case "jh_ha":
                    feat = new JointHistogram();
                    break;
                default:
                    feat = new PHOG();
                    break;
            }
            feat.extract(img);
            int[] hashes = BitSampling.generateHashes(feat.getDoubleHistogram());
            // just use 50% of the hashes for search ...
            query = createQuery(hashes, paramField, 0.5d);
        } catch (Exception e) {
            numErrors++;
            rsp.add("Error", "Error reading image from URL: " + paramUrl + ": " + e.getMessage());
        }
        // search if the feature has been extracted.
        if (feat != null) {
            doSearch(rsp, req, paramField, paramStarts, paramRows, query, feat);
        }
    }

    private void handleExtract(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, InstantiationException, IllegalAccessException {
        SolrParams params = req.getParams();
        String paramUrl = params.get("extract");
        String paramField = "cl_ha";
        if (req.getParams().get("field") != null) {
            paramField = req.getParams().get("field");
        }
//        int paramRows = defaultNumberOfResults;
//        if (params.get("rows") != null)
//            paramRows = params.getInt("rows");
        LireFeature feat = null;
//        BooleanQuery query = null;
        // wrapping the whole part in the try
        try {
            BufferedImage img = ImageIO.read(new URL(paramUrl).openStream());
            img = ImageUtils.trimWhiteSpace(img);
            // getting the right feature per field:
            if (paramField == null || FeatureRegistry.getClassForHashField(paramField) == null) // if the feature is not registered.
            {
                feat = new EdgeHistogram();
            } else {
                feat = (LireFeature) FeatureRegistry.getClassForHashField(paramField).newInstance();
            }
            feat.extract(img);
            SolrDocument doc = new SolrDocument();
            doc.setField("histogram", Base64.encodeBase64String(feat.getByteArrayRepresentation()));
            
            int[] hashes = BitSampling.generateHashes(feat.getDoubleHistogram());
            ArrayList<String> hashStrings = new ArrayList<String>(hashes.length);
            for (int i = 0; i < hashes.length; i++) {
                hashStrings.add(Integer.toHexString(hashes[i]));
            }
            Collections.shuffle(hashStrings);
            doc.setField("hashes", hashStrings);
            rsp.add("response", doc);
//            just use 50% of the hashes for search ...
//            query = createTermFilter(hashes, paramField, 0.5d);
        } catch (IOException | InstantiationException | IllegalAccessException ioex) {
            numErrors++;
            rsp.add("Error", "Error reading image from URL: " + paramUrl + ": " + ioex.getMessage());
        }
        // search if the feature has been extracted.
//        if (feat != null) doSearch(rsp, req.getSearcher(), paramField, paramRows, query, feat);
    }

    /**
     * Search based on the given image hashes.
     *
     * @param req
     * @param rsp
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    private void handleHashSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, IllegalAccessException, InstantiationException, ParseException, SyntaxError {
        SolrParams params = req.getParams();
        SolrIndexSearcher searcher = req.getSearcher();
        // get the params needed:
        // hashes=x y z ...
        // feature=<base64>
        // field=<cl_ha|ph_ha|...>

        String[] hashes = params.get("hashes").trim().split(" ");
        byte[] featureVector = Base64.decodeBase64(params.get("feature"));

        String paramField = defaultAlgorithmField;
        if (req.getParams().get("field") != null) {
            paramField = req.getParams().get("field");
        }

        int paramRows = defaultNumberOfResults;
        if (params.getInt("rows") != null) {
            paramRows = params.getInt("rows");
        }

        int paramStarts = defaultStartValue;
        if (params.getInt("start") != null) {
            paramStarts = params.getInt("start");
        }

        // create boolean query:
        //LOG.info("** Creating query.");
        BooleanQuery query = new BooleanQuery();
        for (int i = 0; i < hashes.length; i++) {
            // be aware that the hashFunctionsFileName of the field must match the one you put the hashes in before.
            hashes[i] = hashes[i].trim();
            if (hashes[i].length() > 0) {
                query.add(new BooleanClause(new TermQuery(new Term(paramField, hashes[i].trim())), BooleanClause.Occur.SHOULD));
                //LOG.info("** " + paramField + ": " + hashes[i].trim());
            }
        }
        //LOG.info("** Doing search.");

        // query feature
        LireFeature queryFeature = (LireFeature) fieldToClass.get(paramField).newInstance();
        queryFeature.setByteArrayRepresentation(featureVector);

        // get results:
        doSearch(rsp, req, paramField, paramStarts, paramRows, query, queryFeature);
    }

    /**
     * Actual search implementation based on (i) hash based retrieval and (ii)
     * feature based re-ranking.
     *
     * @param rsp
     * @param req
     * @param field
     * @param paramRows
     * @param query
     * @param queryFeature
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    private void doSearch(SolrQueryResponse rsp, SolrQueryRequest req, String field, int paramStarts, int paramRows, BooleanQuery query, LireFeature queryFeature) throws IOException, IllegalAccessException, InstantiationException, ParseException, SyntaxError {

        // extract params from request
        SolrParams params = req.getParams();
        String q = params.get(CommonParams.Q);
        String[] fqs = params.getParams(CommonParams.FQ);
        SolrDocumentList results = new SolrDocumentList();
        Map<String, SchemaField> fields = req.getSchema().getFields();
        int ndocs = paramStarts + paramRows;
        Set<Integer> alreadyFound = new HashSet<Integer>();
        Filter filter = buildFilter(fqs, req);

        int maximumHits = paramStarts + paramRows;
        // temp feature instance
        LireFeature tmpFeature = queryFeature.getClass().newInstance();
        // Taking the totalTime of search for statistical purposes.
        totalTime = System.currentTimeMillis();
        SolrIndexSearcher searcher = req.getSearcher();
        TopDocs docs = searcher.search(query, numberOfCandidateResults);
        totalTime = System.currentTimeMillis() - totalTime;
        rsp.add("RawDocsCount", docs.scoreDocs.length + "");
        rsp.add("RawDocsSearchTime", totalTime + "");
        // re-rank
        totalTime = System.currentTimeMillis();
        TreeSet<SimpleResult> resultScoreDocs = new TreeSet<>();
        float maxDistance = -1f;
        float tmpScore;

        String name = field.replace("_ha", "_hi");
        Document doc;
        // iterating and re-ranking the documents.
        BinaryDocValues binaryValues = MultiDocValues.getBinaryValues(searcher.getIndexReader(), name);
        for (ScoreDoc scoreDoc : docs.scoreDocs) {
            // using DocValues to retrieve the field values ...
            tmpFeature.setByteArrayRepresentation(binaryValues.get(scoreDoc.doc).bytes, binaryValues.get(scoreDoc.doc).offset, binaryValues.get(scoreDoc.doc).length);
            // Getting the document from the index.
            // This is the slow step based on the field compression of stored fields.
//            tmpFeature.setByteArrayRepresentation(d.getBinaryValue(name).bytes, d.getBinaryValue(name).offset, d.getBinaryValue(name).length);
            tmpScore = queryFeature.getDistance(tmpFeature);
            if (resultScoreDocs.size() < maximumHits) {
                resultScoreDocs.add(new SimpleResult(tmpScore, searcher.doc(scoreDoc.doc), scoreDoc.doc));
                maxDistance = resultScoreDocs.last().getDistance();
            } else if (tmpScore < maxDistance) {
                //                if it is nearer to the sample than at least one of the current set:
//                remove the last one ...
                resultScoreDocs.remove(resultScoreDocs.last());
//                add the new one ...
                resultScoreDocs.add(new SimpleResult(tmpScore, searcher.doc(scoreDoc.doc), scoreDoc.doc));
                //                and set our new distance border ...
                maxDistance = resultScoreDocs.last().getDistance();
            }
        }
//        //LOG.info("** Creating response.");
        totalTime = System.currentTimeMillis() - totalTime;
        rsp.add("ReRankSearchTime", totalTime + "");
//        LinkedList list = new LinkedList();
//        for (Iterator<SimpleResult> it = resultScoreDocs.iterator(); it.hasNext();) {
//            SimpleResult result = it.next();
//            HashMap map = new HashMap(2);
//            map.put("d", String.format("%.2f", result.getDistance()));
//            map.put("id", result.getDocument().get("id"));
//            map.put("title", result.getDocument().get("title"));
//            list.add(map);
//        }
//        rsp.add("docs", list.subList(paramStarts, maximumHits));
        //res.add("params", req.getParams().toNamedList());

        float maxScore = 0.0F;
        int numFound = 0;
        //LinkedList list = new LinkedList();
        List<SolrDocument> slice = new ArrayList<SolrDocument>();

        for (SimpleResult sdoc : resultScoreDocs) {
            Float distance = (Float) sdoc.getDistance();
            if (maxScore < distance) {
                maxScore = distance;
            }
            if (numFound >= paramStarts && numFound < paramStarts + paramRows) {
                SolrDocument solrDocument = new SolrDocument();
                solrDocument.setField("id", sdoc.getDocument().get("id"));
                solrDocument.setField("title", sdoc.getDocument().get("title"));
                solrDocument.setField("width", sdoc.getDocument().get("width"));
                solrDocument.setField("height", sdoc.getDocument().get("height"));
                solrDocument.setField("distance", distance);
                slice.add(solrDocument);
            }
            numFound++;
        }

        results.clear();
        results.addAll(slice);
        results.setNumFound(docs.scoreDocs.length);
        results.setMaxScore(maxScore);
        results.setStart(paramStarts);
        rsp.add("response", results);

    }

    private Filter buildFilter(String[] fqs, SolrQueryRequest req)
            throws IOException, ParseException, SyntaxError {
        if (fqs != null && fqs.length > 0) {
            BooleanQuery fquery = new BooleanQuery();
            for (String fq : fqs) {
                QParser parser = QParser.getParser(fq, null, req);
                fquery.add(parser.getQuery(), BooleanClause.Occur.MUST);
            }
            return new CachingWrapperFilter(new QueryWrapperFilter(fquery));
        }
        return null;
    }

    @Override
    public String getDescription() {
        return "LIRE Request Handler to add images to an index and search them. Search images by id, by url and by extracted features.";
    }

    @Override
    public String getSource() {
        return "https://github.com/dynamicguy/liresolr";
    }

    @Override
    public String getVersion() {
        return "0.9.6-SNAPSHOT";
    }

    @Override
    public NamedList<Object> getStatistics() {
        NamedList<Object> statistics = super.getStatistics();
        statistics.add("requests", numRequests);
        statistics.add("errors", numErrors);
        statistics.add("totalTime", "" + totalTime);
        return statistics;
    }

    private BooleanQuery createQuery(int[] hashes, String paramField, double size) {
        List<Integer> hList = new ArrayList<>(hashes.length);
        for (int i = 0; i < hashes.length; i++) {
            hList.add(hashes[i]);
        }
        Collections.shuffle(hList);
        BooleanQuery query = new BooleanQuery();
        int numHashes = (int) Math.min(hashes.length, Math.floor(hashes.length * size));
        if (numHashes < 11) {
            numHashes = hashes.length;
        }
        for (int i = 0; i < numHashes; i++) {
            // be aware that the hashFunctionsFileName of the field must match the one you put the hashes in before.
            query.add(new BooleanClause(new TermQuery(new Term(paramField, Integer.toHexString(hashes[i]))), BooleanClause.Occur.SHOULD));
        }
        return query;
    }

}
