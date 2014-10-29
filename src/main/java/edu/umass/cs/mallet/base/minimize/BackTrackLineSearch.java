/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */


/** 
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.minimize;

import edu.umass.cs.mallet.base.minimize.LineMinimizer;
import edu.umass.cs.mallet.base.minimize.Minimizable;
import edu.umass.cs.mallet.base.util.MalletLogger;
import edu.umass.cs.mallet.base.types.Matrix;
import java.util.logging.*;

// "Line Searches and Backtracking", p385, "Numeric Recipes in C"

public class BackTrackLineSearch implements LineMinimizer
{
	private static Logger logger = MalletLogger.getLogger(BackTrackLineSearch.class.getName());

	final int maxIterations = 100;
	final double stpmax = 100;
//	final double stpmax = 100;
	final double EPS = 3.0e-8;
	//final double TOLX = EPS;      // xxx check this value
//	final double TOLX = (4*EPS);      // xxx check this value
	final double TOLX = 1.0e-10;      // xxx check this value
	final double ALF = 1e-4;
	
	public double  minimize (Minimizable function, Matrix line, double initialStep)
	{
		Matrix g, x, oldParameters;
		double slope, newSlope, temp, test, alamin, alam, alam2, tmplam;
		double rhs1, rhs2, a, b, disc, oldAlam;
		double f, fold, f2;
		oldParameters = function.getNewMatrix();
		function.getParameters (oldParameters);
		x = (Matrix) oldParameters.cloneMatrix();
		g = function.getNewMatrix();
		alam2 = tmplam = 0.0; 
		f2 = fold = function.getCost();
		logger.fine("Entering BackTrackLnSrch, cost="+fold);
		g = ((Minimizable.ByGradient)function).getCostGradient(g);
		assert (!g.isNaN());
		// xxx This was Culotta's, below is McCallum's double sum = Math.sqrt( ((Matrix2)line).twoNormSquared() );
		double sum = line.twoNorm();
		if(sum > stpmax) {
			logger.fine("attempted step too big. scaling: sum="+sum+", stpmax="+stpmax);
			line.timesEquals(stpmax/sum);
		}
		newSlope = slope = g.dotProduct(line);
		logger.fine("slope="+slope);
		assert(slope <= 0) : "slope:"+slope;
		// find minimum lambda
		test = 0.0;
    int singleSize = oldParameters.singleSize();
    for(int i=0; i<singleSize; i++) {
			temp = Math.abs(line.singleValue(i)) /
						 Math.max(Math.abs(oldParameters.singleValue(i)), 1.0);
			if(temp > test) test = temp;
		}

		alamin = TOLX/test;
		alam  = 1.0;
		oldAlam = 0.0;
		int iteration = 0;
		for(iteration=0; iteration < maxIterations; iteration++) {
			// x = oldParameters + alam*line
			// initially, alam = 1.0, i.e. take full Newton step
			logger.fine("BackTrack loop iteration "+iteration+": current step size="+alam+" old step size="+oldAlam);
			assert(alam != oldAlam) : "alam == oldAlam";
			x.plusEquals(line, alam - oldAlam);
			function.setParameters(x);
			oldAlam = alam;
			f = function.getCost();
			logger.fine("cost="+f);
			/*
			//xxx back track already takes over-stepping into account
			// this code isn't necessary	 
			// f jumped past min...step back and try again
			if(f > f2) {
				while(f > f2) {	
					alam *= .5;
					assert(alam != oldAlam) : "alam == oldAlam";					
					x.plusEquals(line, alam - oldAlam);
					oldAlam = alam;
					function.setParameters(x);
					newSlope =  ((Minimizable.ByGradient)function).getCostGradient(g).dotProduct(line);
					logger.info ("New cost higher than old; backtracking.  alam="+alam+
						     " dotprod(gradient,line)="+
						     newSlope +
						     " line.twoNorm="+line.twoNorm());
					f = function.getCost();
					// xxx Consider some other stopping criterion that would be
					// robust to steep areas of the function
					if(alam < ALF) {
						logger.warning("Could not find lower cost in this direction. Returning previously best value.");
						function.setParameters(oldParameters);
						return 1;
					}
					
					}
				logger.warning("Stepped too far. Found lower cost "+f+" with new alam="+alam+" oldAlam="+oldAlam);
//			xxx 	continue; //don't continue...need to test this step 1st
        assert(f <= fold); // make sure we're decreasing
				logger.fine("tmplam:"+tmplam);
			}
			*/
			if(alam < alamin) { //convergence on delta x
				logger.info("Jump too small. Exiting BackTrackLineSearch. Cost="+f+", step = " + alam);
        // xxx don't need to set parameters .. use current ones
				function.setParameters(oldParameters);
				return 1;
			}
			// sufficient function decrease (Wolf condition)
			else if(f <= fold+ALF*alam*slope) { 
				logger.info("Exiting backtrack: cost="+f+", step="+alam);
				assert(f <= fold); //make sure we're always decreasing
				return 0;
			}
			else { // backtrack
				if(alam == 1.0) // first time through
					tmplam = -slope/(2.0*(f-fold-slope));
				else {
					rhs1 = f-fold-alam*slope;
					rhs2 = f2-fold-alam2*slope;
					assert((alam - alam2) != 0): "FAILURE: dividing by alam-alam2="+(alam-alam2);
					a = (rhs1/(alam*alam)-rhs2/(alam2*alam2))/(alam-alam2);
					b = (-alam2*rhs1/(alam*alam)+alam*rhs2/(alam2*alam2))/(alam-alam2);
					if(a == 0.0) 
						tmplam = -slope/(2.0*b);
					else {
						disc = b*b-3.0*a*slope;
						if(disc < 0.0) {
							//logger.warning("Roundoff problem in BackTrackLineSearch");
							tmplam = .5 * alam;
						}
						else if (b <= 0.0)
							tmplam = (-b+Math.sqrt(disc))/(3.0*a);
						else tmplam = -slope/(b+Math.sqrt(disc));
					}
						if (tmplam > .5*alam)
							tmplam = .5*alam;    // lambda <= .5 lambda_1
				}
			}
			alam2 = alam;
			f2 = f;
			logger.fine("tmplam:"+tmplam);
			alam = Math.max(tmplam, .1*alam);  // lambda >= .1*Lambda_1						
		}
		if(iteration >= maxIterations) 
			throw new IllegalStateException ("Too many iterations.");
		return 0;
	}
}
	
