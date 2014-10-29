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
import edu.umass.cs.mallet.base.minimize.BackTrackLineSearch;
import edu.umass.cs.mallet.base.types.Matrix;
import edu.umass.cs.mallet.base.util.MalletLogger;

import java.util.logging.*;
import java.util.Vector;
import java.util.Collection;
import java.util.LinkedList;

public class LimitedMemoryBFGS implements Minimizer.ByGradient
{

	final double initialStepSize = 0.01;
	final int maxIterations = 300;	
// xxx need a more principled stopping point
//	final double tolerance = .001;
//	final double tolerance = .00001;
	final double tolerance = .0001;
	final double gradientTolerance = .001;

	//	final double eps = 1.0e-10;
	final double eps = 1.0e-5;
	// The number of corrections used in BFGS update
	// ideally 3 <= m <= 7. Larger m means more cpu / memory
	final int m = 4;

	private static Logger logger = MalletLogger.getLogger(LimitedMemoryBFGS.class.getName());
	
	/**
	 * Line search function
	 */
	LineMinimizer lineMinimizer = new BackTrackLineSearch();
	// State of search
	Matrix g, oldg, direction, parameters, oldParameters;
	// s is list of previous coefficient values
	// y is list of previous gradient values
	// rho is an intermediate value in calculating new direction
	LinkedList s, y, rho;
	Vector temp;
	double step = 1.0;
	int iterations;

	public double getGradient()
	{
		return g.twoNorm();
	}
	public boolean minimize (Minimizable.ByGradient minable)
	{
		return minimize (minable, Integer.MAX_VALUE);
	}

	public boolean minimize (Minimizable.ByGradient minable, int numIterations)
	{

		double initialCost = minable.getCost();
		logger.fine("Entering L-BFGS.minimize(). Initial Cost="+initialCost);
		
		//first time through
		if(g==null) {
			logger.fine("First time through");
			iterations = 0;
			s = new LinkedList();
			y = new LinkedList();
			rho = new LinkedList();
			temp = new Vector(m);

			oldParameters = minable.getNewMatrix();
			g = minable.getNewMatrix();
			parameters = minable.getNewMatrix();
			minable.getParameters (oldParameters);
			oldg = (Matrix) oldParameters.cloneMatrix();
			minable.getCostGradient(oldg);
			direction = (Matrix) oldg.cloneMatrix(); 
			if (direction.absNormalize() == 0) {
				logger.info("L-BFGS initial gradient is zero; saying converged");
				return true;
			}
			for(int i=0; i<m; i++)
				temp.add(new Double(0.0));
			direction.timesEquals(-1.0 * direction.twoNorm());
//			direction.timesEquals(-1.0);
			// make initial jump
			logger.fine ("enter initial line search");
			int unsuccessfulLineSearch = (int) lineMinimizer.minimize(minable, direction, step);
			logger.fine ("exit initial line search");
			minable.getParameters (parameters);
			//minimize sets new gradient g			
			minable.getCostGradient(g);
			if (unsuccessfulLineSearch == 1) {
				logger.warning ("Line Search exited abnormally. Giving up L-BFGS.");
				g = null;
				return true;
			}
		}

		for(int iterationCount = 0;	iterationCount < numIterations;	iterationCount++)	{
			double cost = minable.getCost();

			logger.fine("In L-BFGS, iteration="+iterationCount
									+", cost="+cost);
			// this returns 0 for component-wise -inf + inf
 			oldParameters.equalsPlus(-1.0, parameters); // parameters - oldParameters
			push(s, oldParameters);
			oldg.equalsPlus(-1.0, g); // g - oldg
			push(y, oldg); 
			// si * yi
			// xxx Q: What will this do when some of the parameters are -Infinity?
			// xxx A: gamma and sy = NaN when a parameter is -Inf
			// xxx made changes in DenseMatrix to enforce Inf - Inf = 0
			double sy = ((Matrix)s.getLast()).dotProduct((Matrix)y.getLast());
			//double sy = pdiff.dotProduct(gdiff);
			// if didn't jump anywhere last time, sy == 0
			// so, make it something small and positive to avoid div by 0
//			if (sy <= 0 )
//				sy =  eps; 
			assert (sy > 0) : "sy: "+sy;
			double gamma = sy /
			    ( (Matrix) y.getLast()).dotProduct((Matrix)y.getLast());
			//double gamma = sy /	gdiff.dotProduct(gdiff);								
			assert (gamma >= 0) : "Gamma:"+gamma;
			push(rho, 1.0/sy);
			direction.set(g);
			//direction.timesEquals(-1.0);
			for(int i = s.size() - 1; i >= 0; i--) {
				double rhoTemp =  ((Double)rho.get(i)).doubleValue();
				double factor = ((Matrix)s.get(i)).dotProduct(direction);
				temp.set(i, new Double(rhoTemp * factor));
				Matrix yTemp = (Matrix)y.get(i);
				direction.plusEquals(yTemp, (-1.0 * rhoTemp * factor));
				//assert (g.dotProduct(direction) > 0) : "alpha i="+i;
			}
			direction.timesEquals(gamma);
			//assert (g.dotProduct(direction) > 0) : "gamma="+gamma;
			for(int i =0; i < s.size(); i++) {
				double tmp = (((Double)rho.get(i)).doubleValue()) *
										 ((Matrix)y.get(i)).dotProduct(direction);
				Matrix sTemp = (Matrix)s.get(i);
				direction.plusEquals(sTemp,((Double)temp.get(i)).doubleValue() - tmp);
				//assert (g.dotProduct(direction) > 0) : "beta i="+i;
			}
			oldg.set(g);
			oldParameters.set(parameters);
			direction.timesEquals(-1);
			logger.info ("enter line search");
			int unsuccessfulLineSearch = (int) lineMinimizer.minimize(minable, direction, step);
			logger.info ("exit line search");
			if (unsuccessfulLineSearch == 1) {
				logger.warning ("Line Search exited abnormally. Giving up L-BFGS.");
				return true;
			}
			//direction.timesEquals(-1);
			minable.getParameters (parameters);
			minable.getCostGradient(g);
			double newCost = minable.getCost();
			// Test for terminations
			if(2.0*Math.abs(newCost-cost) <= tolerance*(Math.abs(newCost)+Math.abs(cost) + eps)){
				logger.info("Exiting L-BFGS on termination #1:\ncost difference below tolerance");
				return true;
			}
//			Matrix xi = minable.getNewMatrix();
//			double xiInfinityNorm = xi.infinityNorm();
//			// xxx AKM: Here "xi" is always a fresh, zero-filled matrix, so the following would have always been true!
//			// Culotta was: if(xiInfinityNorm < tolerance) {
//			if(false && xiInfinityNorm < tolerance) {
//				logger.info("Exiting L-BFGS on termination #2: \ninfinityNorm="+xiInfinityNorm+" < tolerance="+tolerance);
//				return true;
//			}
			double gg = g.twoNorm();
			if(gg < gradientTolerance) {
				logger.info("Exiting L-BFGS on termination #2: \ngradient="+gg+" < "+gradientTolerance);
				return true;
			}
																	 
			if(gg == 0.0) {
				logger.info("Exiting L-BFGS on termination #3: \ngradient==0.0");
				return true;
			}
// xxx seems to give same stopping pt as termination #1
//			if((Math.abs(newCost - cost) / newCost) < tolerance) {
//				logger.info("Exiting L-BFGS on termination #4:\nnormalized cost difference below tolerance");
//				return true;
//			}
			logger.fine("Gradient = "+gg);
			iterations++;
			if (iterations > maxIterations) {
				System.err.println("Too many iterations in L-BFGS.java. Continuing with current parameters.");
				return true;
        //throw new IllegalStateException ("Too many iterations.");
			}
		}
		return false;
	}
	
	/**
	 * Pushes a new object onto the queue l
	 * @param l linked list queue of Matrix obj's
	 * @param toadd matrix to push onto queue
	 */
	private void push(LinkedList l, Matrix toadd) {
		assert(l.size() <= m);
		if(l.size() == m) {
			// remove oldest matrix and add newset to end of list.
			// to make this more efficient, actually overwrite
			// memory of oldest matrix

			// this overwrites the oldest matrix
			Matrix last = (Matrix) l.get(0);
			last.set(toadd);
			Object ptr = last;
			// this readjusts the pointers in the list
			for(int i=0; i<l.size()-1; i++) 
				l.set(i, (Matrix)l.get(i+1));			
			l.set(m-1, ptr);
		}
// xxx this way was highly inefficient
//		if(l.size() == m) { //pop old matrix and add new
//			l.removeFirst(); 
//			l.addLast(toadd.cloneMatrix());
//		}
		else 
			l.addLast(toadd.cloneMatrix());		
		// print out for debug
	}
	
  /**
	 * Pushes a new object onto the queue l
	 * @param l linked list queue of Double obj's
	 * @param toadd double value to push onto queue
	 */
	private void push(LinkedList l, double toadd) {
		assert(l.size() <= m);
		if(l.size() == m) { //pop old double and add new
			l.removeFirst(); 
			l.addLast(new Double(toadd));
		}
		else 
			l.addLast(new Double(toadd));
	}	
}	
	
