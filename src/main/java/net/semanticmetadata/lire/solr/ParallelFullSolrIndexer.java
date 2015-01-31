/*
 * This file is part of the LIRE project: http://www.semanticmetadata.net/lire
 * LIRE is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * LIRE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LIRE; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * We kindly ask you to refer the any or one of the following publications in
 * any publication mentioning or employing Lire:
 *
 * Lux Mathias, Savvas A. Chatzichristofis. Lire: Lucene Image Retrieval –
 * An Extensible Java CBIR Library. In proceedings of the 16th ACM International
 * Conference on Multimedia, pp. 1085-1088, Vancouver, Canada, 2008
 * URL: http://doi.acm.org/10.1145/1459359.1459577
 *
 * Lux Mathias. Content Based Image Retrieval with LIRE. In proceedings of the
 * 19th ACM International Conference on Multimedia, pp. 735-738, Scottsdale,
 * Arizona, USA, 2011
 * URL: http://dl.acm.org/citation.cfm?id=2072432
 *
 * Mathias Lux, Oge Marques. Visual Information Retrieval using Java and LIRE
 * Morgan & Claypool, 2013
 * URL: http://www.morganclaypool.com/doi/abs/10.2200/S00468ED1V01Y201301ICR025
 *
 * Copyright statement:
 * ====================
 * (c) 2002-2013 by Mathias Lux (mathias@juggle.at)
 *  http://www.semanticmetadata.net/lire, http://www.lire-project.net
 *
 * Updated: 29.01.15 09:39
 */
package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.DocumentBuilder;
import net.semanticmetadata.lire.DocumentBuilderFactory;
import net.semanticmetadata.lire.impl.ChainedDocumentBuilder;
import net.semanticmetadata.lire.impl.GenericDocumentBuilder;
import net.semanticmetadata.lire.indexing.LireCustomCodec;
import net.semanticmetadata.lire.utils.FileUtils;
import net.semanticmetadata.lire.utils.LuceneUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

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
import net.semanticmetadata.lire.impl.SiftDocumentBuilder;
import net.semanticmetadata.lire.impl.SimpleBuilder;
import net.semanticmetadata.lire.impl.SurfDocumentBuilder;
import net.semanticmetadata.lire.indexing.parallel.ParallelIndexer;
import net.semanticmetadata.lire.indexing.parallel.WorkItem;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;

/**
 * This class allows for creating indexes in a parallel manner. The class at
 * hand reads files from the disk and acts as producer, while several consumer
 * threads extract the features from the given files.
 *
 * To use this override the method {@link ParallelIndexer#addBuilders} to add
 * your own features. Check the source of this class -- the main method -- to
 * get an idea.
 *
 * @author Mathias Lux, mathias@juggle.at, 15.04.13
 */
public class ParallelFullSolrIndexer implements Runnable {

    private Logger log = Logger.getLogger(this.getClass().getName());
    private int numberOfThreads = 10;
    private String indexPath;
    private String imageDirectory;
    IndexWriter writer;
    File imageList = null;
    boolean ended = false;
    boolean threadFinished = false;
    private List<String> files;
    int overallCount = 0, numImages = -1;
    private IndexWriterConfig.OpenMode openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND;
    // all xx seconds a status message will be displayed
    private int monitoringInterval = 30;
    private LinkedBlockingQueue<WorkItem> queue = new LinkedBlockingQueue<WorkItem>(100);

    public static void main(String[] args) {
        String indexPath = null;
        String imageDirectory = null;
        File imageList = null;
        int numThreads = 10;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-i")) {  // index
                if ((i + 1) < args.length) {
                    indexPath = args[i + 1];
                }
            } else if (arg.startsWith("-n")) { // number of Threads
                if ((i + 1) < args.length) {
                    try {
                        numThreads = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException e) {
                        System.err.println("Could not read number of threads: " + args[i + 1] + "\nUsing default value " + numThreads);
                    }
                }
            } else if (arg.startsWith("-l")) { // list of images in a file ...
                imageDirectory = null;
                if ((i + 1) < args.length) {
                    imageList = new File(args[i + 1]);
                    if (!imageList.exists()) {
                        System.err.println(args[i + 1] + " does not exits!");
                        printHelp();
                        System.exit(-1);
                    }
                }
            } else if (arg.startsWith("-d")) { // image directory
                if ((i + 1) < args.length) {
                    imageDirectory = args[i + 1];
                }
            }
        }

        if (indexPath == null) {
            printHelp();
            System.exit(-1);
        } else if (imageList == null && (imageDirectory == null || !new File(imageDirectory).exists())) {
            printHelp();
            System.exit(-1);
        }
        ParallelIndexer p;

        if (imageList != null) {
            p = new ParallelIndexer(8, indexPath, imageList) {
                @Override
                public void addBuilders(ChainedDocumentBuilder builder) {
                    builder.addBuilder(new GenericDocumentBuilder(CEDD.class, true));
                    builder.addBuilder(new SurfDocumentBuilder());
                    builder.addBuilder(new SiftDocumentBuilder());
                }
            };

        } else {
            p = new ParallelIndexer(8, indexPath, imageDirectory) {
                @Override
                public void addBuilders(ChainedDocumentBuilder builder) {
                    builder.addBuilder(new GenericDocumentBuilder(CEDD.class, true));
                    builder.addBuilder(new SurfDocumentBuilder());
                    builder.addBuilder(new SiftDocumentBuilder());
                }
            };
        }
        p.run();

        try {
            IndexReader ir = DirectoryReader.open(FSDirectory.open(new File(indexPath)));
            BOVWBuilder sfh = new BOVWBuilder(ir, new SurfFeature(), 1000, 50);
            sfh.index();
        } catch (IOException ioe) {
            //System.err.println(ioe.getMessage());
        }

    }

    /**
     * Prints help text in case the thing is not configured correctly.
     */
    private static void printHelp() {
        System.out.println("Usage:\n"
                + "\n"
                + "$> ParallelIndexer -i <index> <-d <image-directory> | -l <image-list>> [-n <number of threads>]\n"
                + "\n"
                + "index             ... The directory of the index. Will be appended or created if not existing.\n"
                + "images-directory  ... The directory the images are found in. It's traversed recursively.\n"
                + "image-list        ... A list of images in a file, one per line. Use instead of images-directory.\n"
                + "number of threads ... The number of threads used for extracting features, e.g. # of CPU cores.");
    }

    /**
     * In this case its appended to the index if there is one already in place.
     *
     * @param numberOfThreads
     * @param indexPath
     * @param imageDirectory a directory containing all the images somewhere in
     * the child hierarchy.
     */
    public ParallelFullSolrIndexer(int numberOfThreads, String indexPath, String imageDirectory) {
        this.numberOfThreads = numberOfThreads;
        this.indexPath = indexPath;
        this.imageDirectory = imageDirectory;
    }

    /**
     *
     * @param numberOfThreads
     * @param indexPath
     * @param imageDirectory
     * @param overWrite overwrite (instead of append) the index. Set it to true
     * if you want to delete the old index before adding new stuff.
     */
    public ParallelFullSolrIndexer(int numberOfThreads, String indexPath, String imageDirectory, boolean overWrite) {
        this.numberOfThreads = numberOfThreads;
        this.indexPath = indexPath;
        this.imageDirectory = imageDirectory;
        if (overWrite) {
            openMode = IndexWriterConfig.OpenMode.CREATE;
        }
    }

    /**
     * @param numberOfThreads
     * @param indexPath
     * @param imageList a file containing a list of images, one per line
     */
    public ParallelFullSolrIndexer(int numberOfThreads, String indexPath, File imageList) {
        this.numberOfThreads = numberOfThreads;
        this.indexPath = indexPath;
        this.imageList = imageList;
    }

    public ParallelFullSolrIndexer(int numberOfThreads, String indexPath, File imageList, boolean overWrite) {
        this.numberOfThreads = numberOfThreads;
        this.indexPath = indexPath;
        this.imageList = imageList;
        if (overWrite) {
            openMode = IndexWriterConfig.OpenMode.CREATE;
        }
    }

    /**
     * Overwrite this method to define the builders to be used within the
     * Indexer.
     *
     * @param builder
     */
    public void addBuilders(ChainedDocumentBuilder builder) {
        builder.addBuilder(DocumentBuilderFactory.getCEDDDocumentBuilder());
        builder.addBuilder(DocumentBuilderFactory.getFCTHDocumentBuilder());
        builder.addBuilder(DocumentBuilderFactory.getJCDDocumentBuilder());
        builder.addBuilder(DocumentBuilderFactory.getPHOGDocumentBuilder());
        builder.addBuilder(DocumentBuilderFactory.getOpponentHistogramDocumentBuilder());
        builder.addBuilder(DocumentBuilderFactory.getJointHistogramDocumentBuilder());
        builder.addBuilder(DocumentBuilderFactory.getAutoColorCorrelogramDocumentBuilder());
        builder.addBuilder(DocumentBuilderFactory.getColorLayoutBuilder());
        builder.addBuilder(DocumentBuilderFactory.getEdgeHistogramBuilder());
        builder.addBuilder(DocumentBuilderFactory.getScalableColorBuilder());
        builder.addBuilder(DocumentBuilderFactory.getLuminanceLayoutDocumentBuilder());
        builder.addBuilder(DocumentBuilderFactory.getColorHistogramDocumentBuilder());

        builder.addBuilder(DocumentBuilderFactory.getSimpleBuilderRandomDocumentBuilder());
//        builder.addBuilder(DocumentBuilderFactory.getSimpleBuilderCVSIFTDocumentBuilder());
//        builder.addBuilder(DocumentBuilderFactory.getSimpleBuilderCVSURFDocumentBuilder());
//        builder.addBuilder(DocumentBuilderFactory.getSimpleBuilderGaussRandomDocumentBuilder());

    }

    public void run() {
        IndexWriterConfig config = new IndexWriterConfig(LuceneUtils.LUCENE_VERSION, new StandardAnalyzer(LuceneUtils.LUCENE_VERSION));
        config.setOpenMode(openMode);
        config.setCodec(new LireCustomCodec());
        try {
            if (imageDirectory != null) {
                System.out.println("Getting all images in " + imageDirectory + ".");
            }
            writer = new IndexWriter(FSDirectory.open(new File(indexPath)), config);
            if (imageList == null) {
                files = FileUtils.getAllImages(new File(imageDirectory), true);
            } else {
                files = new LinkedList<String>();
                BufferedReader br = new BufferedReader(new FileReader(imageList));
                String line = null;
                while ((line = br.readLine()) != null) {
                    if (line.trim().length() > 3) {
                        files.add(line.trim());
                    }
                }
            }
            numImages = files.size();
            System.out.println("Indexing " + files.size() + " images.");
            Thread p = new Thread(new Producer());
            p.start();
            LinkedList<Thread> threads = new LinkedList<Thread>();
            long l = System.currentTimeMillis();
            for (int i = 0; i < numberOfThreads; i++) {
                Thread c = new Thread(new Consumer());
                c.start();
                threads.add(c);
            }
            Thread m = new Thread(new Monitoring());
            m.start();
            for (Iterator<Thread> iterator = threads.iterator(); iterator.hasNext();) {
                iterator.next().join();
            }
            long l1 = System.currentTimeMillis() - l;
            int seconds = (int) (l1 / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            // System.out.println("Analyzed " + overallCount + " images in " + seconds + " seconds, ~" + ((overallCount>0)?(l1 / overallCount):"n.a.") + " ms each.");
            System.out.printf("Analyzed %d images in %03d:%02d ~ %3.2f ms each.\n", overallCount, minutes, seconds, ((overallCount > 0) ? ((float) l1 / (float) overallCount) : -1f));
            writer.commit();
            writer.forceMerge(1);
            writer.close();
            threadFinished = true;
            // add local feature hist here
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Check is this thread is still running.
     *
     * @return
     */
    public boolean hasEnded() {
        return threadFinished;
    }

    /**
     * Returns how many of the images have been processed already.
     *
     * @return
     */
    public double getPercentageDone() {
        return (double) overallCount / (double) numImages;
    }

    class Monitoring implements Runnable {

        public void run() {
            long ms = System.currentTimeMillis();
            try {
                Thread.sleep(1000 * monitoringInterval); // wait xx seconds
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            while (!ended) {
                try {
                    // print the current status:
                    long time = System.currentTimeMillis() - ms;
                    int seconds = (int) (time / 1000);
                    int minutes = seconds / 60;
                    seconds = seconds % 60;
                    // System.out.println("Analyzed " + overallCount + " images in " + seconds + " seconds, ~" + ((overallCount>0)?(l1 / overallCount):"n.a.") + " ms each.");
                    System.out.printf("Analyzed %d images in %03d:%02d ~ %3.2f ms each. (queue size is %d)\n",
                            overallCount, minutes, seconds, ((overallCount > 0) ? ((float) time / (float) overallCount) : -1f), queue.size());
//                    System.out.println("Analyzed " + overallCount + " images in " + time / 1000 + " seconds, " + ((overallCount>0)?(time / overallCount):"n.a.") + " ms each ("+queue.size()+" images currently in queue).");
                    Thread.sleep(1000 * monitoringInterval); // wait xx seconds
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class Producer implements Runnable {

        public void run() {
            boolean leaveOneOut = false;
//            BufferedImage tmpImage;
            int tmpSize = 0;
            for (Iterator<String> iterator = files.iterator(); iterator.hasNext();) {
                String path = iterator.next();
                File next = new File(path);
                try {
                    byte[] buffer = Files.readAllBytes(Paths.get(path)); // JDK 7 only!
                    path = next.getCanonicalPath();
                    queue.put(new WorkItem(path, buffer));
                } catch (IOException | InterruptedException e) {
                    System.err.println("Could not open " + path + ". " + e.getMessage());
                }
            }
            for (int i = 0; i < numberOfThreads * 3; i++) {
                String s = null;
                byte[] b = null;
                try {
                    queue.put(new WorkItem(s, b));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            ended = true;
        }
    }

    /**
     * Consumers take the images prepared from the Producer and extract all the
     * image features.
     */
    class Consumer implements Runnable {

        WorkItem tmp = null;
        ChainedDocumentBuilder builder = new ChainedDocumentBuilder();
        int count = 0;
        boolean locallyEnded = false;

        Consumer() {
            addBuilders(builder);
        }

        public void run() {
            while (!locallyEnded) {
                tmp = null;
                try {
                    tmp = queue.take();
                    if (tmp.getFileName() == null) {
                        locallyEnded = true;
                    } else {
                        count++;
                        overallCount++;
                    }
                } catch (InterruptedException e) {
                    // e.printStackTrace();
                    log.severe(e.getMessage());
                }
                try {
                    if (!locallyEnded && tmp != null) {
                        ByteArrayInputStream b = new ByteArrayInputStream(tmp.getBuffer());
                        BufferedImage img = ImageIO.read(b);
                        Document d = builder.createDocument(img, tmp.getFileName());
                        writer.addDocument(d);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
//                synchronized (images) {
//                    // we wait for the stack to be either filled or empty & not being filled any more.
//                    while (images.empty() && !ended) {
//                        try {
//                            images.wait(10);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                    // make sure the thread locally knows that the end has come (outer loop)
//                    if (images.empty() && ended)
//                        locallyEnded = true;
//                    // well the last thing we want is an exception in the very last round.
//                    if (!images.empty() && !locallyEnded) {
//                        count++;
//                        overallCount++;
//                    }
//                }
//                try {
//                    if (!locallyEnded) {
//                        ByteArrayInputStream b = new ByteArrayInputStream(tmp.getBuffer());
//                        BufferedImage img = ImageIO.read(b);
//                        Document d = builder.createDocument(img, tmp.getFileName());
//                        writer.addDocument(d);
//                    }
//                } catch (Exception e) {
//                    System.err.println("[ParallelIndexer] Could not handle file " + tmp.getFileName() + ": "  + e.getMessage());
//                    e.printStackTrace();
//                }
            }
//            System.out.println("Images analyzed: " + count);
        }
    }
}
