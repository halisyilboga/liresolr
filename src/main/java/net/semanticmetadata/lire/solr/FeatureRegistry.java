package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.imageanalysis.*;
import net.semanticmetadata.lire.imageanalysis.joint.JointHistogram;
import net.semanticmetadata.lire.imageanalysis.spatialpyramid.SPCEDD;

import java.util.HashMap;

/**
 * This file is part of LIRE Solr, a Java library for content based image
 * retrieval.
 *
 * @author Mathias Lux, mathias@juggle.at, 28.11.2014
 */
public class FeatureRegistry {

    /**
     * Naming conventions for code: 2 letters for global features. More for
     * local ones.
     */
    private static final HashMap<String, Class<? extends LireFeature>> codeToClass = new HashMap<String, Class<? extends LireFeature>>(16);
    /**
     * Caching the entries for fast retrieval or Strings without generating new
     * objects.
     */
    private static final HashMap<String, Class<? extends LireFeature>> hashFieldToClass = new HashMap<String, Class<? extends LireFeature>>(16);
    private static final HashMap<String, Class<? extends LireFeature>> featureFieldToClass = new HashMap<String, Class<? extends LireFeature>>(16);
    private static final HashMap<String, String> hashFieldToFeatureField = new HashMap<String, String>(16);
    private static final HashMap<Class<? extends LireFeature>, String> classToCode = new HashMap<Class<? extends LireFeature>, String>(16);

    // Constants.
    private static final String featureFieldPostfix = "_hi";   // contains the histogram
    private static final String hashFieldPostfix = "_ha";      // contains the hash

    static {
        // initial adding of the supported features:
        // classical features from the first implementation
        codeToClass.put("ph", PHOG.class);
        codeToClass.put("cl", ColorLayout.class);
        codeToClass.put("eh", EdgeHistogram.class);
        codeToClass.put("jc", JCD.class);
        codeToClass.put("su", SurfSolrFeature.class);
        // additional global features
        codeToClass.put("ce", CEDD.class);
        codeToClass.put("sc", ScalableColor.class);
        codeToClass.put("oh", OpponentHistogram.class);
        codeToClass.put("fc", FCTH.class);
        codeToClass.put("fo", FuzzyOpponentHistogram.class);
        codeToClass.put("jh", JointHistogram.class);

        codeToClass.put("pc", SPCEDD.class);
        codeToClass.put("ac", AutoColorCorrelogram.class);
        // local feature based histograms.
        codeToClass.put("sim_ce", GenericByteLireFeature.class); // SIMPLE CEDD ... just to give a hint how it might look like.
        // -----< caches to be filled >----------------
        for (String code : codeToClass.keySet()) {
            hashFieldToClass.put(code + hashFieldPostfix, codeToClass.get(code));
            featureFieldToClass.put(code + featureFieldPostfix, codeToClass.get(code));
            hashFieldToFeatureField.put(code + hashFieldPostfix, code + featureFieldPostfix);
            classToCode.put(codeToClass.get(code), code);
        }
    }

    /**
     * Used to retrieve a registered class for a given hash field name.
     *
     * @param hashFieldName the name of the hash field
     * @return the class for the given field or null if not registered.
     */
    public static Class getClassForHashField(String hashFieldName) {
        return hashFieldToClass.get(hashFieldName);
    }

    /**
     * Used to retrieve a registered class for a given field name in SOLR for
     * the feature.
     *
     * @param featureFieldName the name of the field containing the histogram
     * @return the class for the given field or null if not registered.
     */
    public static Class getClassForFeatureField(String featureFieldName) {
        return featureFieldToClass.get(featureFieldName);
    }

    /**
     * Returns the feature's histogram field for a given hash field.
     *
     * @param hashFieldName the name of the hash field
     * @return the name or null if the feature is not registered.
     */
    public static String getFeatureFieldName(String hashFieldName) {
        return hashFieldToFeatureField.get(hashFieldName);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Registered features:\n");
        sb.append("code\thash field\tfeature field\tclass\n");
        for (String code : codeToClass.keySet()) {
            sb.append(code);
            sb.append('\t');
            sb.append(code).append(hashFieldPostfix);
            sb.append('\t');
            sb.append(code).append(featureFieldPostfix);
            sb.append('\t');
            sb.append(codeToClass.get(code).getName());
            sb.append('\n');
        }
        return sb.toString();
    }

    public static String getCodeForClass(Class<? extends LireFeature> featureClass) {
        return classToCode.get(featureClass);
    }

    public static String codeToHashField(String code) {
        return code + hashFieldPostfix;
    }

    public static String codeToFeatureField(String code) {
        return code + featureFieldPostfix;
    }
}
