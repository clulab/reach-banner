/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.maximize;

import edu.umass.cs.mallet.base.util.MalletLogger;
import edu.umass.cs.mallet.base.types.MatrixOps;

import java.util.logging.*;

// Gradient Ascent

public class GradientAscent implements Maximizer.ByGradient
{

  private double maxStep = 1.0;

  public LineMaximizer.ByGradient getLineMaximizer ()
  {
    return lineMaximizer;
  }

  public void setLineMaximizer (LineMaximizer.ByGradient lineMaximizer)
  {
    this.lineMaximizer = lineMaximizer;
  }

	private static Logger logger = MalletLogger.getLogger(GradientAscent.class.getName());

  public double getInitialStepSize ()
  {
    return initialStepSize;
  }

  public void setInitialStepSize (double initialStepSize)
  {
    step = initialStepSize;
  }

  static final double initialStepSize = 0.2;
	double tolerance = 0.001;
	int maxIterations = 200;
	LineMaximizer.ByGradient lineMaximizer = new BackTrackLineSearch();

  double stpmax = 100;

	// "eps" is a small number to recitify the special case of converging
	// to exactly zero function value
	final double eps = 1.0e-10;

	public GradientAscent ()
	{
	}

  double step = initialStepSize;

  public double getStpmax ()
  {
    return stpmax;
  }

  public void setStpmax (double stpmax)
  {
    this.stpmax = stpmax;
  }

	public boolean maximize (Maximizable.ByGradient maxable)
	{
		return maximize (maxable, maxIterations);
	}

	public boolean maximize (Maximizable.ByGradient maxable, int numIterations)
	{
		int iterations;
		double fret;
		double fp = maxable.getValue ();
    double[] xi = new double [maxable.getNumParameters()];
		maxable.getValueGradient(xi);

		for (iterations = 0; iterations < numIterations; iterations++) {
			logger.info ("At iteration "+iterations+", cost = "+fp+", scaled = "+maxStep+" step = "+step+", gradient infty-norm = "+MatrixOps.infinityNorm (xi));

      // Ensure step not too large
      double sum = MatrixOps.twoNorm (xi);
      if (sum > stpmax) {
        logger.info ("*** Step 2-norm "+sum+" greater than max "+stpmax+"  Scaling...");
        MatrixOps.timesEquals (xi,stpmax/sum);
      }

      step = lineMaximizer.maximize (maxable, xi, step);
			fret = maxable.getValue ();
			if (2.0*Math.abs(fret-fp) <= tolerance*(Math.abs(fret)+Math.abs(fp)+eps)) {
        logger.info ("Gradient Ascent: Value difference "+Math.abs(fret-fp)+" below " +
                "tolerance; saying converged.");
       	return true;
      }
			fp = fret;
			maxable.getValueGradient(xi);
		}
		return false;
	}

  public void setMaxStepSize (double v)
  {
    maxStep = v;
  }
}
