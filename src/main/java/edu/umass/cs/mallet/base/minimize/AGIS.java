/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
 * Implementation of Salakhutdinav and Roweis Adaptive Overrelaxed GIS (2003)
   @author Ryan McDonald <a href="mailto:ryantm@cis.upenn.edu">ryantm@cis.upenn.edu</a>
 */

package edu.umass.cs.mallet.base.minimize;

import edu.umass.cs.mallet.base.minimize.Minimizable;
import edu.umass.cs.mallet.base.types.Matrix;
import java.util.logging.*;

public class AGIS implements Minimizer.ByGISUpdate
{
	private static Logger logger =
	Logger.getLogger("edu.umass.cs.mallet.base.minimize.AGIS");

	double initialStepSize = 1;
	double alpha;
	double eta = 1.0;
	double tolerance = 0.001;
	int maxIterations = 200;

	// "eps" is a small number to recitify the special case of converging
	// to exactly zero function value
	final double eps = 1.0e-10;
	
	public AGIS (double alph)
	{
		alpha = alph;
	}

	public boolean minimize (Minimizable.ByGISUpdate minable)
	{
		return minimize (minable, maxIterations);
	}
	
	public boolean minimize (Minimizable.ByGISUpdate minable, int numIterations)
	{
		int iterations;
		Matrix params = minable.getNewMatrix();
		Matrix gis = minable.getNewMatrix();
		Matrix temp = minable.getNewMatrix();
		Matrix old_params = minable.getNewMatrix();
		Matrix updates = minable.getNewMatrix();
		minable.getParameters(params);
		minable.getParameters(gis);
		minable.getParameters(temp);
		minable.getParameters(old_params);

		
		for (iterations = 0; iterations < numIterations; iterations++) {

			boolean complete = false;
			
			double old = minable.getCost();

			minable.getGISUpdate(params,updates);

			gis.plusEquals(updates);
			temp.plusEquals(updates);

			temp.plusEquals(params,-1.0);

			params.plusEquals(temp,eta);		
			minable.setParameters(params);

			double next = minable.getCost();

			// Different from normal AGIS, only fall back to GIS updates
			// If log-likelihood gets worse
			// i.e. if lower log-likelihood, always make AGIS update
			if(next < old) {
				complete = true;
				// don't let eta get too large
				if(eta*alpha < 99999999.0)
					eta = eta*alpha;
			}

			// gone too far
			// unlike Roweis, we will back track on eta to find
			// acceptable value, instead of automatically setting it to 1
			while(eta > 1.0 && complete == false) {

				eta = eta/2;

				params.set(old_params);
				
				params.plusEquals(temp,eta);		
				minable.setParameters(params);
				next = minable.getCost();

				if(next < old)
					complete = true;

			}

			// nothing worked - do gis update
			if(complete == false) {
				minable.setParameters(gis);
				eta = 1.0;
				next = minable.getCost();
			}

			if (2.0*Math.abs(next-old) <= tolerance*(Math.abs(next)+Math.abs(old)+eps))
				return true;
			
			if(numIterations > 1) {
				minable.getParameters(params);
				minable.getParameters(old_params);
				minable.getParameters(gis);
				minable.getParameters(temp);
			}
		}
		System.out.println("eta: " + eta);
		return false;
	}
	
}
