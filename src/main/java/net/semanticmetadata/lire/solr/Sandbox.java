/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.semanticmetadata.lire.solr;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Logger;
import net.semanticmetadata.lire.DocumentBuilder;
import net.semanticmetadata.lire.ImageSearchHits;
import net.semanticmetadata.lire.ImageSearcher;
import net.semanticmetadata.lire.ImageSearcherFactory;
import net.semanticmetadata.lire.filter.LsaFilter;
import net.semanticmetadata.lire.filter.RerankFilter;
import net.semanticmetadata.lire.impl.VisualWordsImageSearcher;
import net.semanticmetadata.lire.utils.FileUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

import net.semanticmetadata.lire.imageanalysis.LireFeature;
import net.semanticmetadata.lire.imageanalysis.ColorLayout;
import net.semanticmetadata.lire.imageanalysis.EdgeHistogram;
import net.semanticmetadata.lire.imageanalysis.JCD;
import net.semanticmetadata.lire.imageanalysis.OpponentHistogram;
import net.semanticmetadata.lire.imageanalysis.PHOG;
import net.semanticmetadata.lire.imageanalysis.AutoColorCorrelogram;
import net.semanticmetadata.lire.imageanalysis.CEDD;
import net.semanticmetadata.lire.imageanalysis.FCTH;
import net.semanticmetadata.lire.imageanalysis.FuzzyOpponentHistogram;
import net.semanticmetadata.lire.imageanalysis.ScalableColor;
import net.semanticmetadata.lire.imageanalysis.Gabor;
import net.semanticmetadata.lire.imageanalysis.Tamura;
import net.semanticmetadata.lire.imageanalysis.LuminanceLayout;
import net.semanticmetadata.lire.imageanalysis.JpegCoefficientHistogram;
import net.semanticmetadata.lire.imageanalysis.SimpleColorHistogram;
import net.semanticmetadata.lire.imageanalysis.LocalBinaryPatterns;
import net.semanticmetadata.lire.imageanalysis.RotationInvariantLocalBinaryPatterns;
import net.semanticmetadata.lire.imageanalysis.BinaryPatternsPyramid;
import net.semanticmetadata.lire.imageanalysis.GenericByteLireFeature;
import net.semanticmetadata.lire.imageanalysis.SurfFeature;
import net.semanticmetadata.lire.imageanalysis.bovw.BOVWBuilder;
import net.semanticmetadata.lire.imageanalysis.bovw.SimpleFeatureBOVWBuilder;
import net.semanticmetadata.lire.imageanalysis.bovw.SurfFeatureHistogramBuilder;

import net.semanticmetadata.lire.imageanalysis.joint.JointHistogram;
import net.semanticmetadata.lire.imageanalysis.spatialpyramid.SPCEDD;
import net.semanticmetadata.lire.imageanalysis.mser.MSERFeature;
import net.semanticmetadata.lire.impl.ChainedDocumentBuilder;
import net.semanticmetadata.lire.impl.GenericDocumentBuilder;
import net.semanticmetadata.lire.impl.SimpleBuilder;
import net.semanticmetadata.lire.impl.SurfDocumentBuilder;
import net.semanticmetadata.lire.indexing.parallel.ParallelIndexer;

/**
 *
 * @author ferdous
 */
public class Sandbox {

    private static final Logger LOG = Logger.getLogger(Sandbox.class.getName());
    private static File indexPath;
    private static final String queryImage = "/Users/ferdous/projects/digitalcandy/liresolr/testdata/ferrari/red/6822615599_18d9915317_b.jpg";
    static String bovw_index_path = "index";
    int sampleToCreateCodebook = -1;
    int numberOfClusters = 10;

    public void createSurfIndex() throws IOException {
        ParallelIndexer pin = new ParallelIndexer(8, bovw_index_path, "./testdata") {
            @Override
            public void addBuilders(ChainedDocumentBuilder builder) {
                builder.addBuilder(new GenericDocumentBuilder(CEDD.class, true));
                builder.addBuilder(new SurfDocumentBuilder());
            }
        };
        pin.run();
        createVisualWords();
    }

    private void createVisualWords() throws IOException {
        try {
            IndexReader ir = DirectoryReader.open(FSDirectory.open(new File("index")));
            BOVWBuilder sfh = new BOVWBuilder(ir, new SurfFeature(), 1000, 50);
            sfh.index();
        } catch (IOException ioe) {
            System.err.println(ioe.getMessage());
        }
    }

    private static ImageSearcher getSearcher(int selectedIndex, int limit) {
        int numResults = 50;
        try {
            numResults = limit;
        } catch (Exception e) {
            // nothing to do ...
        }
        ImageSearcher searcher = ImageSearcherFactory.createColorLayoutImageSearcher(numResults);

        if (selectedIndex == 1) { // ScalableColor
            searcher = ImageSearcherFactory.createScalableColorImageSearcher(numResults);
        } else if (selectedIndex == 2) { // EdgeHistogram
            searcher = ImageSearcherFactory.createEdgeHistogramImageSearcher(numResults);
        } else if (selectedIndex == 3) { // AutoColorCorrelogram
            searcher = ImageSearcherFactory.createAutoColorCorrelogramImageSearcher(numResults);
        } else if (selectedIndex == 4) { // CEDD
            searcher = ImageSearcherFactory.createCEDDImageSearcher(numResults);
        } else if (selectedIndex == 5) { // FCTH
            searcher = ImageSearcherFactory.createFCTHImageSearcher(numResults);
        } else if (selectedIndex == 6) { // JCD
            searcher = ImageSearcherFactory.createJCDImageSearcher(numResults);
        } else if (selectedIndex == 7) { // SimpleColorHistogram
            searcher = ImageSearcherFactory.createColorHistogramImageSearcher(numResults);
        } else if (selectedIndex == 8) { // Tamura
            searcher = ImageSearcherFactory.createTamuraImageSearcher(numResults);
        } else if (selectedIndex == 9) { // Gabor
            searcher = ImageSearcherFactory.createGaborImageSearcher(numResults);
        } else if (selectedIndex == 10) { // JpegCoefficientHistogram
            searcher = ImageSearcherFactory.createJpegCoefficientHistogramImageSearcher(numResults);
        } else if (selectedIndex == 11) { // SURF
            searcher = new VisualWordsImageSearcher(numResults, DocumentBuilder.FIELD_NAME_SURF + DocumentBuilder.FIELD_NAME_BOVW);
        } else if (selectedIndex == 12) { // JointHistogram
            searcher = ImageSearcherFactory.createJointHistogramImageSearcher(numResults);
        } else if (selectedIndex == 13) { // OpponentHistogram
            searcher = ImageSearcherFactory.createOpponentHistogramSearcher(numResults);
        } else if (selectedIndex == 14) { // LuminanceLayout
            searcher = ImageSearcherFactory.createLuminanceLayoutImageSearcher(numResults);
        } else if (selectedIndex >= 15) { // PHOG
            searcher = ImageSearcherFactory.createPHOGImageSearcher(numResults);
        }
        return searcher;
    }

    private static void searchForDocument(Document d) {
        final Document myDoc = d;
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    ImageSearchHits hits;
                    try (IndexReader reader = DirectoryReader.open(FSDirectory.open(indexPath))) {
                        ImageSearcher searcher = getSearcher(11, 12);
                        System.out.println(searcher.getClass().getName() + " " + searcher.toString());
                        hits = searcher.search(myDoc, reader);
                        hits = lsa(hits, myDoc);
                        FileUtils.saveImageResultsToHtml("filtertest", hits, myDoc.getValues(DocumentBuilder.FIELD_NAME_IDENTIFIER)[0]);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        t.start();
    }

    private static void searchForImage(String imagePath) throws FileNotFoundException, IOException {
        System.out.println("---< searching >-------------------------");
        Document document = null;
        try (IndexReader reader = IndexReader.open(FSDirectory.open(indexPath))) {
            for (int i = 0; i < reader.numDocs(); i++) {
                Document idoc = reader.document(i);
                String fileName = idoc.getValues(DocumentBuilder.FIELD_NAME_IDENTIFIER)[0];
                if(imagePath == null ? fileName == null : imagePath.equals(fileName)){
                    document = idoc;
                }
                System.out.println(i + ": \t" + fileName);
            }              
            String path = document.getValues(DocumentBuilder.FIELD_NAME_IDENTIFIER)[0];
            System.out.println("searching for " + path);
            searchForDocument(document);
        }
        
    }

    private static ImageSearchHits lsa(ImageSearchHits hits, Document document) {
        System.out.println("---< LSA filtering >-------------------------");
        LsaFilter filter = new LsaFilter(CEDD.class, DocumentBuilder.FIELD_NAME_CEDD);
        hits = filter.filter(hits, document);
        return hits;
    }

    private static ImageSearchHits rerank(ImageSearchHits hits, Document document) {
        // rerank
        System.out.println("---< filtering >-------------------------");
        RerankFilter filter = new RerankFilter(ColorLayout.class, DocumentBuilder.FIELD_NAME_COLORLAYOUT);
        hits = filter.filter(hits, document);
        return hits;
    }

    public void findByUrl() throws IOException {
        searchForImage("/Users/ferdous/projects/digitalcandy/liresolr/testdata/cars/trucks/pickup-red.jpg");
    }

    public Sandbox() {
        indexPath = new File(bovw_index_path);
    }

    public static void main(String[] args) {
        try {
            Sandbox sandbox = new Sandbox();
            //sandbox.createSurfIndex();
//            sandbox.createVisualWords();
            sandbox.findByUrl();
        } catch (IOException ex) {
            LOG.info(ex.getMessage());
        }
    }

}
