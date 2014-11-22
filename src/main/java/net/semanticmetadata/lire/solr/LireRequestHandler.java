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
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SolrIndexSearcher;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.search.ScoreDoc;

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

    private static final HashMap<String, Class> fieldToClass = new HashMap<>(5);
    static long time = 0;
    static int defaultNumberOfResults = 60;
    static int defaultStartValue = 0;
    static final String defaultAlgorithmField = "cl_ha";
    volatile long numErrors;
    volatile long numRequests;
    volatile long totalTime;

    /**
     * number of candidate results retrieved from the index. The higher this
     * number, the slower, the but more accurate the retrieval will be.
     */
    private final int candidateResultNumber = 20000;

    static {
        fieldToClass.put("cl_ha", ColorLayout.class);
        fieldToClass.put("ph_ha", PHOG.class);
        fieldToClass.put("oh_ha", OpponentHistogram.class);
        fieldToClass.put("eh_ha", EdgeHistogram.class);
        fieldToClass.put("jc_ha", JCD.class);

        // one time hash function read ...
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
                doSearch(rsp, searcher, paramField, paramStarts, paramRows, query, queryFeature);
            } else {
                numErrors++;
                rsp.add("Error", "Did not find an image with the given id " + req.getParams().get("id"));
            }
        } catch (IOException | InstantiationException | IllegalAccessException ex) {
            numErrors++;
            rsp.add("Error", "There was an error with your search for the image with the id " + req.getParams().get("id")
                    + ": " + ex.getMessage());
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
     * features takes time and (ii) not every image is readable by Java.
     *
     * @param req
     * @param rsp
     * @throws IOException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    private void handleUrlSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, InstantiationException, IllegalAccessException {
        SolrParams params = req.getParams();
        String paramUrl = params.get("url");

        String paramField = defaultAlgorithmField;
        if (req.getParams().get("field") != null) {
            paramField = req.getParams().get("field");
        }

        int paramRows = defaultNumberOfResults;
        if (params.get("rows") != null) {
            paramRows = params.getInt("rows");
        }

        int paramStarts = defaultStartValue;
        if (params.get("start") != null) {
            paramStarts = params.getInt("start");
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
                default:
                    feat = new EdgeHistogram();
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
            doSearch(rsp, req.getSearcher(), paramField, paramStarts, paramRows, query, feat);
        }
    }

    private void handleExtract(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, InstantiationException, IllegalAccessException {
        SolrParams params = req.getParams();
        String paramUrl = params.get("extract");
        String paramField = defaultAlgorithmField;
        if (req.getParams().get("field") != null) {
            paramField = req.getParams().get("field");
        }
        int paramRows = defaultNumberOfResults;
        if (params.get("rows") != null) {
            paramRows = params.getInt("rows");
        }

        LireFeature feat = null;
        // BooleanQuery query = null;
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
                default:
                    feat = new EdgeHistogram();
                    break;
            }
            feat.extract(img);
            rsp.add("histogram", Base64.encodeBase64String(feat.getByteArrayRepresentation()));
//            int[] hashes = BitSampling.generateHashes(feat.getDoubleHistogram());
//            just use 50% of the hashes for search ...
//            query = createQuery(hashes, paramField, 0.5d);
        } catch (Exception e) {
            numErrors++;
            rsp.add("Error", "Error reading image from URL: " + paramUrl + ": " + e.getMessage());
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
    private void handleHashSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, IllegalAccessException, InstantiationException {
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
        doSearch(rsp, searcher, paramField, paramStarts, paramRows, query, queryFeature);
    }

    /**
     * Actual search implementation based on (i) hash based retrieval and (ii)
     * feature based re-ranking.
     *
     * @param rsp
     * @param searcher
     * @param field
     * @param paramRows
     * @param query
     * @param queryFeature
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    private void doSearch(SolrQueryResponse rsp, SolrIndexSearcher searcher, String field, int paramStarts, int paramRows, BooleanQuery query, LireFeature queryFeature) throws IOException, IllegalAccessException, InstantiationException {
        int maximumHits = paramStarts + paramRows;
        // temp feature instance
        LireFeature tmpFeature = queryFeature.getClass().newInstance();
        // Taking the time of search for statistical purposes.
        time = System.currentTimeMillis();
        TopDocs docs = searcher.search(query, candidateResultNumber);
        time = System.currentTimeMillis() - time;
        rsp.add("RawDocsCount", docs.scoreDocs.length + "");
        rsp.add("RawDocsSearchTime", time + "");
        // re-rank
        time = System.currentTimeMillis();
        TreeSet<SimpleResult> resultScoreDocs = new TreeSet<>();
        float maxDistance = -1f;
        float tmpScore;

        String name = field.replace("_ha", "_hi");
        Document doc;
        // iterating and re-ranking the documents.
        BinaryDocValues binaryValues = MultiDocValues.getBinaryValues(searcher.getIndexReader(), name);         for (ScoreDoc scoreDoc : docs.scoreDocs) {
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
        //LOG.info("** Creating response.");
        time = System.currentTimeMillis() - time;
        rsp.add("ReRankSearchTime", time + "");
        LinkedList list = new LinkedList();
        for (Iterator<SimpleResult> it = resultScoreDocs.iterator(); it.hasNext();) {
            SimpleResult result = it.next();
            HashMap map = new HashMap(2);
            map.put("d", String.format("%.2f", result.getDistance()));
            map.put("id", result.getDocument().get("id"));
            map.put("title", result.getDocument().get("title"));
            list.add(map);
        }
        rsp.add("docs", list.subList(paramStarts, maximumHits));
        //res.add("params", req.getParams().toNamedList());
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
        return "0.9.5-SNAPSHOT";
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
        if (numHashes < 5) {
            numHashes = hashes.length;
        }
        for (int i = 0; i < numHashes; i++) {
            // be aware that the hashFunctionsFileName of the field must match the one you put the hashes in before.
            query.add(new BooleanClause(new TermQuery(new Term(paramField, Integer.toHexString(hashes[i]))), BooleanClause.Occur.SHOULD));
        }
        return query;
    }

}
