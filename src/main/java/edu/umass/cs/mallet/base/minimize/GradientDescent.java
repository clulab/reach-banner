/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.minimize;

import edu.umass.cs.mallet.base.minimize.LineMinimizer;
import edu.umass.cs.mallet.base.minimize.Minimizable;
import edu.umass.cs.mallet.base.types.Matrix;
import edu.umass.cs.mallet.base.util.MalletLogger;
import java.util.logging.*;

// Gradient Descent

public class GradientDescent implements Minimizer.ByGradient
{
	private static Logger logger = MalletLogger.getLogger(GradientDescent.class.getName());

	double initialStepSize = 0.2;
	double tolerance = 0.001;
	int maxIterations = 200;
	LineMinimizer lineMinimizer = new GradientBracketLineMinimizer ();

	// "eps" is a small number to recitify the special case of converging
	// to exactly zero function value
	final double eps = 1.0e-10;
	
	public GradientDescent ()
	{
	}

	public boolean minimize (Minimizable.ByGradient minable)
	{
		return minimize (minable, maxIterations);
	}
	
	public boolean minimize (Minimizable.ByGradient minable, int numIterations)
	{
		int iterations;
		double step = initialStepSize;
		double fret;
		double fp = minable.getCost ();
		Matrix xi = minable.getNewMatrix();
		minable.getCostGradient(xi);

		xi.timesEquals (-1.0);
		for (iterations = 0; iterations < numIterations; iterations++) {
			logger.info ("At iteration "+iterations+", cost = "+fp);
			step = lineMinimizer.minimize (minable, xi, step);
			fret = minable.getCost();
			if (2.0*Math.abs(fret-fp) <= tolerance*(Math.abs(fret)+Math.abs(fp)+eps))
				return true;
			fp = fret;
			minable.getCostGradient(xi);
			xi.timesEquals (-1);
		}
		return false;
	}
	
}
