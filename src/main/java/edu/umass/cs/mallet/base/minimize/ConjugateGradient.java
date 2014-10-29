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

// Conjugate Gradient, Polak and Ribiere version
// from "Numeric Recipes in C", Section 10.6.

public class ConjugateGradient implements Minimizer.ByGradient
{
	private static Logger logger = MalletLogger.getLogger(ConjugateGradient.class.getName());

	// xxx If this is too big, we can get inconsistent value and gradient in MaxEntTrainer
	// Investigate!!!
	double initialStepSize = 0.01;
	double tolerance = 0.001;
	int maxIterations = 200;
//	LineMinimizer lineMinimizer = new GradientBracketLineMinimizer ();
	LineMinimizer lineMinimizer = new GoldenLineMinimizer ();

	// "eps" is a small number to recitify the special case of converging
	// to exactly zero function value
	final double eps = 1.0e-10;
	
	public ConjugateGradient (double initialStepSize)
	{
		this.initialStepSize = initialStepSize;
	}

	public ConjugateGradient ()
	{
	}

	public void setInitialStepSize (double initialStepSize) { this.initialStepSize = initialStepSize; }
	public double getInitialStepSize () { return this.initialStepSize; }

	// The state of a conjugate gradient search
	double fp, gg, gam, dgg, step, fret;
	Matrix xi, g, h;
	int j, iterations;

//	public int getIterations() {return iterations;}
	
	public boolean minimize (Minimizable.ByGradient minable)
	{
		return minimize (minable, Integer.MAX_VALUE);
	}
	
	public boolean minimize (Minimizable.ByGradient minable, int numIterations)
	{
		if (xi == null) {
			fp = minable.getCost ();
			xi = minable.getNewMatrix();
			minable.getCostGradient(xi);
			xi.timesEquals (-1.0);
			g = (Matrix) xi.cloneMatrix ();
			h = (Matrix) xi.cloneMatrix ();
			step = initialStepSize;
			iterations = 0;
		}

		for (int iterationCount = 0; iterationCount < numIterations; iterationCount++) {
			logger.info ("At iteration "+iterations+", cost = "+fp);
			step = lineMinimizer.minimize (minable, xi, step);
			fret = minable.getCost();
			// This termination provided by "Numeric Recipes in C".
			if (2.0*Math.abs(fret-fp) <= tolerance*(Math.abs(fret)+Math.abs(fp)+eps))
				return true;
			fp = fret;
			minable.getCostGradient(xi);
			//System.out.println ("Conjugate Gradient gradient xi:"); xi.print ();
			logger.info ("Gradient infinityNorm = "+xi.infinityNorm());
			// This termination provided by McCallum
			if (xi.infinityNorm() < tolerance)
				return true;

			dgg = gg = 0.0;
			double gj, xj;
			for (j = 0; j < xi.singleSize(); j++) {
				gj = g.singleValue (j);
				gg += gj * gj;
				xj = xi.singleValue (j);
				dgg += (xj + gj) * xj;
			}
			if (gg == 0.0)
				return true; // In unlikely case that gradient is exactly zero, then we are done
			gam = dgg/gg;
			// System.out.println ("Conjugate Gradient gam = "+gam);
			// System.out.println ("Conjugate Gradient h:"); h.print ();
			double hj;
			for (j = 0; j < xi.singleSize(); j++) {
				xj = xi.singleValue (j);
				g.setSingleValue (j, -xj);
				hj = h.singleValue (j);
				hj = (-xj) + gam * hj;
				h.setSingleValue (j, hj);
			}
			assert (!h.isNaN());
			xi.set (h);
			//System.out.println ("Conjugate Gradient h after setting:"); h.print ();
			//System.out.println ("Conjugate Gradient xi (=line):"); xi.print ();
			iterations++;
			if (iterations > maxIterations)
				throw new IllegalStateException ("Too many iterations.");
		}
		return false;
	}
	
}
