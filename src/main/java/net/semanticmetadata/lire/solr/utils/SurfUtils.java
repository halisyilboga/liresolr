package net.semanticmetadata.lire.solr.utils;

import net.semanticmetadata.lire.solr.SurfInterestPoint;

import java.util.List;

public class SurfUtils {

    public static float getDistance(List<SurfInterestPoint> points1, List<SurfInterestPoint> points2) {
        int numberOfPoints = 0;

        numberOfPoints = points1.stream().map((a) -> findSmallestDistance(a, points2)).filter((smallestDistance) -> (smallestDistance < 0.15d)).map((_item) -> 1).reduce(numberOfPoints, Integer::sum);
        if (numberOfPoints == 0) {
            return (float) 1.0;
        }
        return (float) (1.0 / numberOfPoints);
    }

    private static double findSmallestDistance(SurfInterestPoint e, List<SurfInterestPoint> array) {
        double ed = e.getDistanceFromOne();
        // We find points with similiar distance from one by binary search.
        int start = 0;
        int end = array.size() - 1;

        while ((end - start) > 1) {
            int pivot = start + ((end - start) / 2);

            if (ed < array.get(pivot).getDistanceFromOne()) {
                end = pivot - 1;
            } else if (ed == array.get(pivot).getDistanceFromOne()) {
                return (double) 0;
            } else {
                start = pivot + 1;
            }
        }

        int k = start;
        double smallestDistance = Double.MAX_VALUE;
        // We search neighborhood of the founded point.
        while (k >= 0 && Math.abs(e.getDistanceFromOne() - array.get(k).getDistanceFromOne()) < 0.05) {
            double distance = e.getDistance(array.get(k));
            if (distance < smallestDistance) {
                smallestDistance = distance;
            }
            --k;
        }
        k = start;
        while (k < array.size() && Math.abs(e.getDistanceFromOne() - array.get(k).getDistanceFromOne()) < 0.05) {
            double distance = e.getDistance(array.get(k));
            if (distance < smallestDistance) {
                smallestDistance = distance;
            }
            ++k;
        }
        return smallestDistance;
    }
}
