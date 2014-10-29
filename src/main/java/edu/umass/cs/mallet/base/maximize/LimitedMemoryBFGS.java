/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** 
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

/**
 Limited Memory BFGS, as described in Byrd, Nocedal, and Schnabel,
 "Representations of Quasi-Newton Matrices and Their Use in Limited
 Memory Methods"
*/
package edu.umass.cs.mallet.base.maximize;
import edu.umass.cs.mallet.base.maximize.LineMaximizer;
import edu.umass.cs.mallet.base.maximize.Maximizable;
import edu.umass.cs.mallet.base.maximize.BackTrackLineSearch;
import edu.umass.cs.mallet.base.maximize.tests.TestMaximizable;
import edu.umass.cs.mallet.base.types.MatrixOps;
import edu.umass.cs.mallet.base.util.MalletLogger;
import java.util.logging.*;
import java.util.LinkedList;

public class LimitedMemoryBFGS implements Maximizer.ByGradient
{
	
	final int maxIterations = 1000;	
	// xxx need a more principled stopping point
	//final double tolerance = .0001;
    private double tolerance = .0001;
	final double gradientTolerance = .001;
	final double eps = 1.0e-5;

	// The number of corrections used in BFGS update
	// ideally 3 <= m <= 7. Larger m means more cpu time, memory.
	final int m = 4;
	
	private static Logger logger =
		MalletLogger.getLogger("edu.umass.cs.mallet.base.ml.maximize.LimitedMemoryBFGS");

	// Line search function
	LineMaximizer.ByGradient lineMaximizer = new BackTrackLineSearch();

	// State of search
	// g = gradient
	// s = list of m previous "parameters" values
	// y = list of m previous "g" values
	// rho = intermediate calculation
	double [] g, oldg, direction, parameters, oldParameters;
	LinkedList s = new LinkedList();
	LinkedList y = new LinkedList();
	LinkedList rho = new LinkedList();
	double [] alpha;
 	static double step = 1.0;
	int iterations;

  private OptimizerEvaluator eval = null;

  // CPAL - added this
  public void setTolerance(double newtol) {
        this.tolerance = newtol;
  }

  public void setEvaluator (OptimizerEvaluator eval) { this.eval = eval; }

  public boolean maximize (Maximizable.ByGradient maxable)
	{
		return maximize (maxable, Integer.MAX_VALUE);
	}
	
	public boolean maximize (Maximizable.ByGradient maxable, int numIterations)
	{
		
		double initialValue = maxable.getValue();
		logger.fine("Entering L-BFGS.maximize(). Initial Value="+initialValue);		

		
		if(g==null) { //first time through
	    logger.fine("First time through L-BFGS");
	    iterations = 0;
	    s = new LinkedList();
	    y = new LinkedList();
	    rho = new LinkedList();
	    alpha = new double[m];	    
	    for(int i=0; i<m; i++)
				alpha[i] = 0.0;

	    parameters = new double[maxable.getNumParameters()];
	    oldParameters = new double[maxable.getNumParameters()];
	    g = new double[maxable.getNumParameters()];
	    oldg = new double[maxable.getNumParameters()];
	    direction = new double[maxable.getNumParameters()];
	    
	    maxable.getParameters (parameters);
			System.arraycopy (parameters, 0, oldParameters, 0, parameters.length);

	    maxable.getValueGradient (g);
			System.arraycopy (g, 0, oldg, 0, g.length);
			System.arraycopy (g, 0, direction, 0, g.length);
	    
	    if (MatrixOps.absNormalize (direction) == 0) {
				logger.info("L-BFGS initial gradient is zero; saying converged");
				g = null;
				return true;
	    }
			logger.fine ("direction.2norm: " + MatrixOps.twoNorm (direction));
			MatrixOps.timesEquals(direction, MatrixOps.twoNorm(direction));
	    // make initial jump
			logger.fine ("before initial jump: \ndirection.2norm: " +
									 MatrixOps.twoNorm (direction) + " \ngradient.2norm: " +
									 MatrixOps.twoNorm (g) + "\nparameters.2norm: " +
									 MatrixOps.twoNorm(parameters));

			
			//TestMaximizable.testValueAndGradientInDirection (maxable, direction);
			step = lineMaximizer.maximize(maxable, direction, step);
	    if (step == 0.0) {// could not step in this direction.
				                // give up and say converged.
				g = null; // reset search
				throw new IllegalArgumentException("could not step in current direction");
				//return false;
	    }
	    maxable.getParameters (parameters);
			maxable.getValueGradient(g);
			logger.fine ("after initial jump: \ndirection.2norm: " +
									 MatrixOps.twoNorm (direction) + " \ngradient.2norm: "
									 + MatrixOps.twoNorm (g));		
		}
		
		for(int iterationCount = 0; iterationCount < numIterations;
				iterationCount++)	{
			double value = maxable.getValue();
			logger.fine("L-BFGS iteration="+iterationCount
									+", value="+value+" g.twoNorm: "+MatrixOps.twoNorm(g)+
				 " oldg.twoNorm: "+MatrixOps.twoNorm(oldg));
			// get difference between previous 2 gradients and parameters
			double sy = 0.0;
			double yy = 0.0;
			for (int i=0; i < oldParameters.length; i++) {
				// -inf - (-inf) = 0; inf - inf = 0
				if (Double.isInfinite(parameters[i]) &&
						Double.isInfinite(oldParameters[i]) &&
						(parameters[i]*oldParameters[i] > 0))
					oldParameters[i] = 0.0;
				else
					oldParameters[i] = parameters[i] - oldParameters[i];
				if (Double.isInfinite(g[i]) &&
						Double.isInfinite(oldg[i]) &&
						(g[i]*oldg[i] > 0))
					oldg[i] = 0.0;
				else oldg[i] = g[i] - oldg[i];				
				sy += oldParameters[i] * oldg[i]; 	 // si * yi
				yy += oldg[i]*oldg[i];
				direction[i] = g[i];
			}

			if ( sy > 0 )
			    throw new IllegalStateException("sy = "+sy+" > 0" );


			double gamma = sy / yy;	 // scaling factor
			if ( gamma>0 )
			    throw new IllegalStateException("gamma = "+gamma+" > 0" );

			push (rho, 1.0/sy);
			push (s, oldParameters);
			push (y, oldg);
 			// calculate new direction
			assert (s.size() == y.size()) :
												"s.size: " + s.size() + " y.size: " + y.size();
			for(int i = s.size() - 1; i >= 0; i--) {
			 	alpha[i] =  ((Double)rho.get(i)).doubleValue() *
										MatrixOps.dotProduct ( (double[])s.get(i), direction);
			 	MatrixOps.plusEquals (direction, (double[])y.get(i), 
			 												-1.0 * alpha[i]);
			}
	    MatrixOps.timesEquals(direction, gamma);
			for(int i = 0; i < y.size(); i++) {
				double beta = (((Double)rho.get(i)).doubleValue()) *
			 							 MatrixOps.dotProduct((double[])y.get(i), direction);
		 		MatrixOps.plusEquals(direction,(double[])s.get(i),
										 				 alpha[i] - beta);
 	    }

 	    for (int i=0; i < oldg.length; i++) {
			 	oldParameters[i] = parameters[i];
				oldg[i] = g[i];
				direction[i] *= -1.0;
	    }
 			logger.fine ("before linesearch: direction.gradient.dotprod: "+
									 MatrixOps.dotProduct(direction,g)+"\ndirection.2norm: " +
									 MatrixOps.twoNorm (direction) + "\nparameters.2norm: " +
									 MatrixOps.twoNorm(parameters));					
			//TestMaximizable.testValueAndGradientInDirection (maxable, direction);
			step = lineMaximizer.maximize(maxable, direction, step);
			if (step == 0.0) { // could not step in this direction. 
		 		g = null; // reset search
				// xxx Temporary test; passed OK
				TestMaximizable.testValueAndGradientInDirection (maxable, direction);
				throw new IllegalArgumentException("could not step in current direction");
				//	return false;
			}
			maxable.getParameters (parameters);
			maxable.getValueGradient(g);
		 	logger.fine ("after linesearch: direction.2norm: " +
		 							 MatrixOps.twoNorm (direction));					
 	    double newValue = maxable.getValue();

	    // Test for terminations
			if(2.0*Math.abs(newValue-value) <= tolerance*
				 (Math.abs(newValue)+Math.abs(value) + eps)){
				logger.info("Exiting L-BFGS on termination #1:\nvalue difference below tolerance (oldValue: " + value + " newValue: " + newValue);
				return true;
	    }
	    double gg = MatrixOps.twoNorm(g);
	    if(gg < gradientTolerance) {
				logger.fine("Exiting L-BFGS on termination #2: \ngradient="+gg+" < "+gradientTolerance);
				return true;
	    }	    
	    if(gg == 0.0) {
				logger.fine("Exiting L-BFGS on termination #3: \ngradient==0.0");
				return true;
	    }
	    logger.fine("Gradient = "+gg);
	    iterations++;
	    if (iterations > maxIterations) {
				System.err.println("Too many iterations in L-BFGS.java. Continuing with current parameters.");
				return true;
				//throw new IllegalStateException ("Too many iterations.");
	    }

      //end of iteration. call evaluator
      if (eval != null && !eval.evaluate (maxable, iterationCount)) {
        logger.fine ("Exiting L-BFGS on termination #4: evaluator returned false.");
        return false;
      }
    }
		return false;
	}

	/** Resets the previous gradients and values that are used to
	 * approximate the Hessian. NOTE - If the {@link Maximizable} object
	 * is modified externally, this method should be called to avoid
	 * IllegalStateExceptions. */
	public void reset () {
		g = null;
	}

	/**
	 * Pushes a new object onto the queue l
	 * @param l linked list queue of Matrix obj's
	 * @param toadd matrix to push onto queue
	 */
	private void push(LinkedList l, double[] toadd) {
		assert(l.size() <= m);
		if(l.size() == m) {
	    // remove oldest matrix and add newset to end of list.
	    // to make this more efficient, actually overwrite
	    // memory of oldest matrix
	    
	    // this overwrites the oldest matrix
	    double[] last = (double[]) l.get(0);
	    System.arraycopy(toadd, 0, last, 0, toadd.length);
	    Object ptr = last;
	    // this readjusts the pointers in the list
	    for(int i=0; i<l.size()-1; i++) 
				l.set(i, (double[])l.get(i+1));			
	    l.set(m-1, ptr);
		}
		else {
	    double [] newArray = new double[toadd.length];
	    System.arraycopy (toadd, 0, newArray, 0, toadd.length);
			l.addLast(newArray);
		}
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

