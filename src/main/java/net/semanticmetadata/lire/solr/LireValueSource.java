package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.imageanalysis.*;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.DocTermsIndexDocValues;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.util.Base64;

import java.io.IOException;
import java.util.Map;
import net.semanticmetadata.lire.imageanalysis.joint.JointHistogram;

/**
 * A query function for sorting results based on the LIRE CBIR functions.
 * Implementation based partially on the outdated guide given on
 * http://www.supermind.org/blog/756, comments on the mailing list provided from
 * Chris Hostetter, and the 4.4 Solr & Lucene source.
 */
public class LireValueSource extends ValueSource {

    String field = "cl_hi";  //
    byte[] histogramData;
    LireFeature feature, tmpFeature;
    double maxDistance = -1;

    /**
     * @param featureField
     * @param hist
     * @param maxDistance the distance value returned if there is no distance
     * calculation possible.
     */
    public LireValueSource(String featureField, byte[] hist, double maxDistance) {
        if (featureField != null) {
            field = featureField;
        }
        if (!field.endsWith("_hi")) {
            field += "_hi";
        }
        this.histogramData = hist;
        this.maxDistance = maxDistance;

        if (field == null) {
            feature = new EdgeHistogram();
            tmpFeature = new EdgeHistogram();
        } else {
            switch (field) {
                case "cl_hi":
                    feature = new ColorLayout();
                    tmpFeature = new ColorLayout();
                    break;
                case "jc_hi":
                    feature = new JCD();
                    tmpFeature = new JCD();
                    break;
                case "ph_hi":
                    feature = new PHOG();
                    tmpFeature = new PHOG();
                    break;
                case "oh_hi":
                    feature = new OpponentHistogram();
                    tmpFeature = new OpponentHistogram();
                    break;
                case "su_hi":
                    feature = new SurfSolrFeature();
                    tmpFeature = new SurfSolrFeature();
                    break;
                case "fo_hi":
                    feature = new FuzzyOpponentHistogram();
                    tmpFeature = new FuzzyOpponentHistogram();
                    break;
                case "fc_hi":
                    feature = new FCTH();
                    tmpFeature = new FCTH();
                    break;
                case "ce_hi":
                    feature = new CEDD();
                    tmpFeature = new CEDD();
                    break;
                case "sc_hi":
                    feature = new ScalableColor();
                    tmpFeature = new ScalableColor();
                    break;
                case "jh_hi":
                    feature = new JointHistogram();
                    tmpFeature = new JointHistogram();
                    break;
                default:
                    feature = new EdgeHistogram();
                    tmpFeature = new EdgeHistogram();
                    break;
            }
        }
        // debug ...
        System.out.println("Setting " + feature.getClass().getName() + " to " + Base64.byteArrayToBase64(hist, 0, hist.length));
        feature.setByteArrayRepresentation(hist);
    }

    @Override
    public FunctionValues getValues(Map context, AtomicReaderContext readerContext) throws IOException {
        final FieldInfo fieldInfo = readerContext.reader().getFieldInfos().fieldInfo(field);
        if (fieldInfo != null && fieldInfo.getDocValuesType() == FieldInfo.DocValuesType.BINARY) {
            final BinaryDocValues binaryValues = FieldCache.DEFAULT.getTerms(readerContext.reader(), field, true);
            return new FunctionValues() {
                BytesRef tmp = new BytesRef();

                @Override
                public boolean exists(int doc) {
                    return bytesVal(doc, tmp);
                }

                @Override
                public boolean bytesVal(int doc, BytesRef target) {
                    binaryValues.get(doc);
                    return target.length > 0;
                }

                // This is the actual value returned
                @Override
                public float floatVal(int doc) {
                    binaryValues.get(doc);
                    if (tmp.length > 0) {
                        tmpFeature.setByteArrayRepresentation(tmp.bytes, tmp.offset, tmp.length);
                        return tmpFeature.getDistance(feature);
                    } else {
                        return -1f;
                    }
                }

                @Override
                public Object objectVal(int doc) {
                    return floatVal(doc);
                }

                @Override
                public String toString(int doc) {
                    return description() + '=' + strVal(doc);
                }

                @Override
                /**
                 * This method has to be implemented to support sorting!
                 */
                public double doubleVal(int doc) {
                    return (double) floatVal(doc);
                }
            };
        } else {
            // there is no DocVal to sort by. Therefore we need to set the function value to -1 and everything without DocVal gets ranked first?
            return new DocTermsIndexDocValues(this, readerContext, field) {
                @Override
                protected String toTerm(String readableValue) {
                    return Double.toString(maxDistance);
                }

                @Override
                public Object objectVal(int doc) {
                    return maxDistance;
                }

                @Override
                public String toString(int doc) {
                    return description() + '=' + strVal(doc);
                }

                @Override
                public double doubleVal(int doc) {
                    return maxDistance;
                }
            };
        }
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public String description() {
        return "distance to a given feature vector";
    }

}
