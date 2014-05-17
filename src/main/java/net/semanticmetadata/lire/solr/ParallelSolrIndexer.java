package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.imageanalysis.*;
import net.semanticmetadata.lire.indexing.hashing.BitSampling;
import net.semanticmetadata.lire.indexing.parallel.WorkItem;
import net.semanticmetadata.lire.utils.ImageUtils;
import org.apache.commons.codec.binary.Base64;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;


public class ParallelSolrIndexer implements Runnable {
	private static HashMap<Class, String> classToPrefix = new HashMap<Class, String>(5);
	private static boolean force = false;
	private static boolean individualFiles = false;
	private static int numberOfThreads = 4;
	Stack<WorkItem> images = new Stack<WorkItem>();
	boolean ended = false;
	int overallCount = 0;
	OutputStream dos = null;
	LinkedList<LireFeature> listOfFeatures;
	File fileList = null;
	File outFile = null;
	private int monitoringInterval = 10;
	private int maxSideLength = -1;

	static {
		classToPrefix.put(ColorLayout.class, "cl");
		classToPrefix.put(EdgeHistogram.class, "eh");
		classToPrefix.put(PHOG.class, "ph");
		classToPrefix.put(OpponentHistogram.class, "oh");
		classToPrefix.put(JCD.class, "jc");
	}


	public ParallelSolrIndexer() {
		// default constructor.
		listOfFeatures = new LinkedList<LireFeature>();
	}

	/**
	 * Sets the number of consumer threads that are employed for extraction
	 *
	 * @param numberOfThreads
	 */
	public static void setNumberOfThreads(int numberOfThreads) {
		ParallelSolrIndexer.numberOfThreads = numberOfThreads;
	}

	public static void main(String[] args) throws IOException {
		BitSampling.readHashFunctions();
		ParallelSolrIndexer e = new ParallelSolrIndexer();

		// parse programs args ...
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.startsWith("-i")) {
				// infile ...
				if ((i + 1) < args.length)
					e.setFileList(new File(args[i + 1]));
				else printHelp();
			} else if (arg.startsWith("-o")) {
				// out file, if it's not set a single file for each input image is created.
				if ((i + 1) < args.length)
					e.setOutFile(new File(args[i + 1]));
				else printHelp();
			} else if (arg.startsWith("-m")) {
				// out file
				if ((i + 1) < args.length) {
					try {
						int s = Integer.parseInt(args[i + 1]);
						if (s > 10)
							e.setMaxSideLength(s);
					} catch (NumberFormatException e1) {
						e1.printStackTrace();
						printHelp();
					}
				} else printHelp();
			} else if (arg.startsWith("-f")) {
				force = true;
			} else if (arg.startsWith("-h")) {
				// help
				printHelp();
			} else if (arg.startsWith("-n")) {
				if ((i + 1) < args.length)
					try {
						ParallelSolrIndexer.numberOfThreads = Integer.parseInt(args[i + 1]);
					} catch (Exception e1) {
						System.err.println("Could not set number of threads to \"" + args[i + 1] + "\".");
						e1.printStackTrace();
					}
				else printHelp();
			}
		}
		// check if there is an infile, an outfile and some features to extract.
		if (!e.isConfigured()) {
			printHelp();
		} else {
			e.run();
		}
	}

	private static void printHelp() {
		System.out.println("Help for the ParallelSolrIndexer class.\n" +
				"=============================\n" +
				"This help text is shown if you start the ParallelSolrIndexer with the '-h' option.\n" +
				"\n" +
				"1. Usage\n" +
				"========\n" +
				"$> ParallelSolrIndexer -i <infile> [-o <outfile>] [-n <threads>] [-f] [-m <max_side_length>]\n" +
				"\n" +
				"Note: if you don't specify an outfile just \".xml\" is appended to the infile for output.\n" +
				"\n");
	}

	public static String arrayToString(int[] array) {
		StringBuilder sb = new StringBuilder(array.length * 8);
		for (int i = 0; i < array.length; i++) {
			if (i > 0) sb.append(' ');
			sb.append(Integer.toHexString(array[i]));
		}
		return sb.toString();
	}

	/**
	 * Adds a feature to the extractor chain. All those features are extracted from images.
	 *
	 * @param feature
	 */
	public void addFeature(LireFeature feature) {
		listOfFeatures.add(feature);
	}

	/**
	 * Sets the file list for processing. One image file per line is fine.
	 *
	 * @param fileList
	 */
	public void setFileList(File fileList) {
		this.fileList = fileList;
	}

	/**
	 * Sets the outfile. The outfile has to be in a folder parent to all input images.
	 *
	 * @param outFile
	 */
	public void setOutFile(File outFile) {
		this.outFile = outFile;
	}

	public int getMaxSideLength() {
		return maxSideLength;
	}

	public void setMaxSideLength(int maxSideLength) {
		this.maxSideLength = maxSideLength;
	}

	private boolean isConfigured() {
		boolean configured = true;
		if (fileList == null || !fileList.exists()) configured = false;
		else if (outFile == null) {
			individualFiles = true;
			// create an outfile ...
//            try {
//                outFile = new File(fileList.getCanonicalPath() + ".xml");
//                System.out.println("Setting out file to " + outFile.getCanonicalFile());
//            } catch (IOException e) {
//                configured = false;
//            }
		} else if (outFile.exists() && !force) {
			System.err.println(outFile.getName() + " already exists. Please delete or choose another outfile.");
			configured = false;
		}
		return configured;
	}

	@Override
	public void run() {
		// check:
		if (fileList == null || !fileList.exists()) {
			System.err.println("No text file with a list of images given.");
			return;
		}
		try {
			if (!individualFiles) {
				dos = new BufferedOutputStream(new FileOutputStream(outFile));
				dos.write("<add>\n".getBytes());
			}
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
			for (Iterator<Thread> iterator = threads.iterator(); iterator.hasNext(); ) {
				iterator.next().join();
			}
			long l1 = System.currentTimeMillis() - l;
			System.out.println("Analyzed " + overallCount + " images in " + l1 / 1000 + " seconds, ~" + (overallCount > 0 ? (l1 / overallCount) : "inf.") + " ms each.");
			if (!individualFiles) {
				dos.write("</add>\n".getBytes());
				dos.close();
			}
//            writer.commit();
//            writer.close();
//            threadFinished = true;

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void addFeatures(List features) {
		features.add(new PHOG());
		features.add(new ColorLayout());
		features.add(new EdgeHistogram());
		features.add(new JCD());
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
					System.out.println("Analyzed " + overallCount + " images in " + time / 1000 + " seconds, " + ((overallCount > 0) ? (time / overallCount) : "n.a.") + " ms each (" + images.size() + " images currently in queue).");
					Thread.sleep(1000 * monitoringInterval); // wait xx seconds
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	class Producer implements Runnable {
		public void run() {
			int tmpSize = 0;
			try {
				BufferedReader br = new BufferedReader(new FileReader(fileList));
				String file = null;
				File next = null;
				while ((file = br.readLine()) != null) {
					next = new File(file);
					BufferedImage img = null;
					try {
						int fileSize = (int) next.length();
						byte[] buffer = new byte[fileSize];
						FileInputStream fis = new FileInputStream(next);
						fis.read(buffer);
						// Change to relative path to allow map images to url path
						// For example localhost/lire/data/[relative_path_to_image]
						String path = next.getPath();
						synchronized (images) {
							images.add(new WorkItem(path, buffer));
							tmpSize = images.size();
							// if the cache is too crowded, then wait.
							if (tmpSize > 500) images.wait(500);
							// if the cache is too small, dont' notify.
							images.notify();
						}
					} catch (Exception e) {
						System.err.println("Could not read image " + file + ": " + e.getMessage());
					}
					try {
						if (tmpSize > 500) Thread.sleep(1000);
//                        else Thread.sleep(2);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
			synchronized (images) {
				ended = true;
				images.notifyAll();
			}
		}
	}

	class Consumer implements Runnable {
		WorkItem tmp = null;
		LinkedList<LireFeature> features = new LinkedList<LireFeature>();
		int count = 0;
		boolean locallyEnded = false;
		StringBuilder sb = new StringBuilder(1024);

		Consumer() {
			addFeatures(features);
		}

		public void run() {
			byte[] myBuffer = new byte[1024 * 1024 * 10];
			int bufferCount = 0;

			while (!locallyEnded) {
				synchronized (images) {
					// we wait for the stack to be either filled or empty & not being filled any more.
					while (images.empty() && !ended) {
						try {
							images.wait(200);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					// make sure the thread locally knows that the end has come (outer loop)
					if (images.empty() && ended)
						locallyEnded = true;
					// well the last thing we want is an exception in the very last round.
					if (!images.empty() && !locallyEnded) {
						tmp = images.pop();
						count++;
						overallCount++;
					}
				}
				try {
					if (!locallyEnded) {
						sb.delete(0, sb.length());
						ByteArrayInputStream b = new ByteArrayInputStream(tmp.getBuffer());
						BufferedImage img = ImageUtils.trimWhiteSpace(ImageIO.read(b));
						if (maxSideLength > 50) img = ImageUtils.scaleImage(img, maxSideLength);
						byte[] tmpBytes = tmp.getFileName().getBytes();
						sb.append("<add>");
						sb.append("<doc>");
						sb.append("<field name=\"id\">");
						sb.append(tmp.getFileName());
						sb.append("</field>");
						sb.append("<field name=\"title\">");
						sb.append(new File(tmp.getFileName()).getName());
						sb.append("</field>");

						for (LireFeature feature : features) {
							if (classToPrefix.get(feature.getClass()) != null) {
								feature.extract(img);
								String histogramField = classToPrefix.get(feature.getClass()) + "_hi";
								String hashesField = classToPrefix.get(feature.getClass()) + "_ha";

								sb.append("<field name=\"" + histogramField + "\">");
								sb.append(Base64.encodeBase64String(feature.getByteArrayRepresentation()));
								sb.append("</field>");
								sb.append("<field name=\"" + hashesField + "\">");
								sb.append(arrayToString(BitSampling.generateHashes(feature.getDoubleHistogram())));
								sb.append("</field>");
							}
						}
						sb.append("</doc>\n");
						sb.append("</add>\n");
						// finally write everything to the stream - in case no exception was thrown..
						if (!individualFiles) {
							synchronized (dos) {
								dos.write(sb.toString().getBytes());
								dos.flush();
							}
						} else {
							OutputStream mos = new BufferedOutputStream(new FileOutputStream(tmp.getFileName() + "_solr.xml"));
							mos.write(sb.toString().getBytes());
							mos.flush();
							mos.close();
						}
					}
				} catch (Exception e) {
					System.err.println("Error processing file " + tmp.getFileName());
					e.printStackTrace();
				}
			}
		}
	}


}
