package net.semanticmetadata.lire.solr.requesthandler;

import net.semanticmetadata.lire.DocumentBuilder;
import net.semanticmetadata.lire.imageanalysis.ColorLayout;
import net.semanticmetadata.lire.imageanalysis.SurfFeature;
import net.semanticmetadata.lire.impl.SimpleResult;
import net.semanticmetadata.lire.impl.SurfDocumentBuilder;
import net.semanticmetadata.lire.indexing.hashing.BitSampling;
import net.semanticmetadata.lire.solr.SolrSurfFeatureHistogramBuilder;
import net.semanticmetadata.lire.solr.SurfInterestPoint;
import net.semanticmetadata.lire.solr.utils.PropertiesUtils;
import net.semanticmetadata.lire.solr.utils.QueryImageUtils;
import net.semanticmetadata.lire.solr.utils.SurfUtils;
import net.semanticmetadata.lire.utils.ImageUtils;
import net.semanticmetadata.lire.utils.LuceneUtils;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SolrIndexSearcher;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.*;

public class SimilarRequestHandler extends RequestHandlerBase {

	@Override
	public String getDescription() {
		return "LIRE Request Handler finds similar images.";
	}

	@Override
	public String getSource() {
		return "http://lire-project.net";
	}

	@Override
	public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse res)
			throws Exception {
		String url = req.getParams().get("url");
		if (url == null || url.isEmpty()) {
			throw new IllegalArgumentException("You have to specify the url parameter.");
		}

		SolrIndexSearcher searcher = req.getSearcher();
		searcher.setSimilarity(new BM25Similarity());
		IndexReader reader = searcher.getIndexReader();
		QueryParser qp = new QueryParser(LuceneUtils.LUCENE_VERSION, "su_ha", new WhitespaceAnalyzer(LuceneUtils.LUCENE_VERSION));
		BooleanQuery.setMaxClauseCount(10000);

		// Read properties
		Properties properties = PropertiesUtils.getProperties(searcher.getCore());
		int numColorLayoutImages = Integer.parseInt(properties.getProperty("numColorLayoutImages"));
		int numColorLayoutSimImages = Integer.parseInt(properties.getProperty("numColorLayoutSimImages"));
		int numSurfSimImages = Integer.parseInt(properties.getProperty("numSurfSimImages"));
		int numSimImages = Integer.parseInt(properties.getProperty("numSimilarImages"));

		// Load image
		BufferedImage image = ImageIO.read(new URL(url).openStream());
		image = ImageUtils.trimWhiteSpace(image);

		// Extract image information
		
		/* CL */
		ColorLayout clFeat = new ColorLayout();
		clFeat.extract(image);
		// Create hashes
		BitSampling.readHashFunctions();
		int[] clHash = BitSampling.generateHashes(clFeat.getDoubleHistogram());
		
		/* SURF */
		SurfDocumentBuilder sb = new SurfDocumentBuilder();
		Document suFeat = sb.createDocument(QueryImageUtils.resizeQueryImage(ImageIO.read(new URL(url).openStream()), properties), "image");
		SolrSurfFeatureHistogramBuilder sh = new SolrSurfFeatureHistogramBuilder(null);
		sh.setClusterFile(req.getCore().getDataDir() + "/clusters-surf.dat");

		// load interest points from document
		// has to be loaded before getVisualWords (this method delete surf interest points)
		ArrayList<SurfInterestPoint> suPoints = new ArrayList<SurfInterestPoint>();
		IndexableField[] queryFields = suFeat.getFields(DocumentBuilder.FIELD_NAME_SURF);
		for (IndexableField queryField : queryFields) {
			SurfFeature feature = new SurfFeature();
			feature.setByteArrayRepresentation(queryField.binaryValue().bytes, queryField.binaryValue().offset, queryField.binaryValue().length);
			SurfInterestPoint sip = new SurfInterestPoint(feature.descriptor);
			suPoints.add(sip);
		}
		// sort for faster compare
		Collections.sort(suPoints);

		// Get Visual Words
		suFeat = sh.getVisualWords(suFeat);

		// Create queries
		/* CL */
		BooleanQuery clQuery = createQuery(clHash, "cl_ha", 0.5d);
		/* SURF */
		Query suQuery = qp.parse(suFeat.getValues(DocumentBuilder.FIELD_NAME_SURF_VISUAL_WORDS)[0]);

		// Searching..
		// Taking the time of search for statistical purposes.
		long time = System.currentTimeMillis();
		// CL
		TopDocs clDocs = searcher.search(clQuery, numColorLayoutImages);
		// Surf
		TopDocs suDocs = searcher.search(suQuery, numSurfSimImages);

		time = System.currentTimeMillis() - time;
		res.add("RawDocsCount", clDocs.scoreDocs.length + suDocs.scoreDocs.length + "");
		res.add("RawDocsSearchTime", time + "");
		// re-rank
		time = System.currentTimeMillis();

		// Re-rank color layout
		TreeSet<SimpleResult> clScoreDocs = new TreeSet<SimpleResult>();
		ColorLayout clTmpFeature = new ColorLayout();
		float clTmpDistance;
		float maxClDistance = -1;

		BinaryDocValues binaryValues = MultiDocValues.getBinaryValues(searcher.getIndexReader(), "cl_hi"); // ***  #
		BytesRef bytesRef = new BytesRef();
		for (int i = 0; i < clDocs.scoreDocs.length; i++) {
			// using DocValues to retrieve the field values ...
			binaryValues.get(clDocs.scoreDocs[i].doc, bytesRef);
			clTmpFeature.setByteArrayRepresentation(bytesRef.bytes, bytesRef.offset, bytesRef.length);
			// Getting the document from the index.
			// This is the slow step based on the field compression of stored fields.
			clTmpDistance = clFeat.getDistance(clTmpFeature);
			if (clScoreDocs.size() < numColorLayoutSimImages) {
				clScoreDocs.add(new SimpleResult(clTmpDistance, searcher.doc(clDocs.scoreDocs[i].doc), clDocs.scoreDocs[i].doc));
				maxClDistance = clScoreDocs.last().getDistance();
			} else if (clTmpDistance < maxClDistance) {
				// if it is nearer to the sample than at least one of the current set:
				// remove the last one ...
				clScoreDocs.remove(clScoreDocs.last());
				// add the new one ...
				clScoreDocs.add(new SimpleResult(clTmpDistance, searcher.doc(clDocs.scoreDocs[i].doc), clDocs.scoreDocs[i].doc));
				// and set our new distance border ...
				maxClDistance = clScoreDocs.last().getDistance();
			}
		}

		// Re-rank by surf method
		TreeSet<SimpleResult> resultScoreDocs = new TreeSet<SimpleResult>();

		// Re-rank color layout
		for (SimpleResult r : clScoreDocs) {
			rerank(suPoints, r.getDocument(), r.getIndexNumber(), resultScoreDocs, numSimImages);
		}

		// Re-rank surf (visual words)
		for (int i = 0; i < suDocs.scoreDocs.length; i++) {
			Document doc = reader.document(suDocs.scoreDocs[i].doc);
			rerank(suPoints, doc, suDocs.scoreDocs[i].doc, resultScoreDocs, numSimImages);
		}

		time = System.currentTimeMillis() - time;
		res.add("ReRankSearchTime", time + "");

		LinkedList<HashMap<String, String>> result = new LinkedList<HashMap<String, String>>();
		for (SimpleResult r : resultScoreDocs) {
			HashMap<String, String> map = new HashMap<String, String>(2);
			map.put("id", r.getDocument().get("id"));
			map.put("d", "" + r.getDistance());
			result.add(map);
		}
		res.add("docs", result);
	}

	private void rerank(ArrayList<SurfInterestPoint> query, Document doc, int indexNumber, TreeSet<SimpleResult> resultScoreDocs, int numSimImages) {

		float maxDistance = resultScoreDocs.isEmpty() ? -1 : resultScoreDocs.last().getDistance();

		// load interest points from document
		ArrayList<SurfInterestPoint> docPoints = new ArrayList<SurfInterestPoint>();
		IndexableField[] docFields = doc.getFields("su_hi");
		for (IndexableField docField : docFields) {
			SurfFeature feature = new SurfFeature();
			feature.setByteArrayRepresentation(docField.binaryValue().bytes, docField.binaryValue().offset, docField.binaryValue().length);
			SurfInterestPoint sip = new SurfInterestPoint(feature.descriptor);
			docPoints.add(sip);
		}

		float tmpScore = SurfUtils.getDistance(docPoints, query);
		if (resultScoreDocs.size() < numSimImages) {
			resultScoreDocs.add(new SimpleResult(tmpScore, doc, indexNumber));
		} else if (tmpScore < maxDistance) {
			// if it is nearer to the sample than at least one of the current set:
			// remove the last one ...
			resultScoreDocs.remove(resultScoreDocs.last());
			// add the new one ...
			resultScoreDocs.add(new SimpleResult(tmpScore, doc, indexNumber));
			// and set our new distance border ...
			maxDistance = resultScoreDocs.last().getDistance();
		}
	}

	private BooleanQuery createQuery(int[] hashes, String paramField, double size) {
		List<Integer> hList = new ArrayList<Integer>(hashes.length);
		for (int i = 0; i < hashes.length; i++) {
			hList.add(hashes[i]);
		}
		Collections.shuffle(hList);
		BooleanQuery query = new BooleanQuery();
		int numHashes = (int) Math.min(hashes.length, Math.floor(hashes.length * size));
		if (numHashes < 5) numHashes = hashes.length;
		for (int i = 0; i < numHashes; i++) {
			// be aware that the hashFunctionsFileName of the field must match the one you put the hashes in before.
			query.add(new BooleanClause(new TermQuery(new Term(paramField, Integer.toHexString(hashes[i]))), BooleanClause.Occur.SHOULD));
		}
		return query;
	}

}
