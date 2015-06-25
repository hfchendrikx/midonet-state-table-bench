package org.midonet.benchmarks;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A class that contains utility methods used by the benchmarks. Among others,
 * this class can be used to compute the mean, standard deviation, and
 * x-percentile of a list of values.
 */
public class StatUtils {

    private static DecimalFormat oneDecForm = new DecimalFormat("#.#");

    public static double mean(List<Long> values) {
        double mean = 0d;

        for (Number nb: values) {
            double doubleNb = nb.doubleValue();
            mean += doubleNb;
        }
        mean = mean / ((double) values.size());
        return Double.valueOf(oneDecForm.format(mean));
    }

    public static double standardDeviation(List<Long> values) {
        double mean = mean(values);
        double stdDev = 0d;

        for (Number nb: values) {
            double doubleNb = nb.doubleValue();
            stdDev += (doubleNb - mean) * (doubleNb - mean);
        }
        stdDev = Math.sqrt(stdDev / ((double) values.size()));
        return Double.valueOf(oneDecForm.format(stdDev));
    }

    public static long percentile(List<Long> values, double percentile) {
        Collections.sort(values);

        Iterator<Long> it = values.iterator();
        while(it.hasNext()) { System.out.println("latency: " + it.next()); }

        int index = (int) Math.floor(((double) values.size()) * percentile);
        System.out.println(percentile + " percentile index is: " + index +
                           " for " + values.size() + " values");

        if (values.size() > index) {
            return values.get(index);
        } else {
            return 0;
        }
    }
}
