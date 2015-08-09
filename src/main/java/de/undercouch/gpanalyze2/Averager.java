package de.undercouch.gpanalyze2;

import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

public class Averager {
    private final LinearInterpolator lp = new LinearInterpolator();
    private final int n;
    private final double cx;
    private final double minx;
    private final double maxx;
    private final PolynomialSplineFunction spline;
    
    public Averager(int n, double cx, double[] xvalues, double[] yvalues) {
        this.n = n;
        this.cx = cx;
        this.minx = xvalues[0];
        this.maxx = xvalues[xvalues.length - 1];
        this.spline = lp.interpolate(xvalues, yvalues);
    }
    
    public double apply(double x) {
        double ys = 0;
        for (int j = 0; j < n; ++j) {
            ys += spline.value(Math.min(Math.max(x - (j - n / 2) * cx, minx), maxx));
        }
        return ys / n;
    }
}
