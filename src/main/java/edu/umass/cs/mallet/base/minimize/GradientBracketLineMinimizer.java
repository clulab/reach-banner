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

// Brents method using derivative information
// p405, "Numeric Recipes in C"

public class GradientBracketLineMinimizer implements LineMinimizer
{
	private static Logger logger = MalletLogger.getLogger(GradientBracketLineMinimizer.class.getName());
	
	int maxIterations = 50;

	public double minimize (Minimizable function, Matrix line, double initialStep) {
		return minimize ((Minimizable.ByGradient)function, line, initialStep);
	}

	// Return the last step size used.
	// "line" should point in the direction we want to move the parameters to get
	// lower cost; note that this is generally in the opposite direction as the
	// getCostGradient(), since the gradient always points "up-hill".
	public double minimize (Minimizable.ByGradient function, Matrix line, double initialStep)
	{
		assert (initialStep > 0);
		Matrix parameters = function.getNewMatrix ();
		function.getParameters(parameters);
		Matrix gradient = function.getNewMatrix ();
		// a=left, b=center, c=right, t=test
		double ax, bx, cx, tx;							// steps (domain), these are deltas from initial params!
		double ay, by, cy, ty;							// costs (range)
		double ag, bg, cg, tg;							// projected gradients
		double ox;													// the x step of the last function call
		double origY;
		
		tx = ax = bx = cx = ox = 0;
		ty = ay = by = cy = origY = function.getCost();
		assert (ty == ty);
		tg = ag = bg = function.getCostGradient(gradient).dotProduct(line);
		// Make sure search-line points downward
		logger.info ("Initial gradient = "+tg);
		if (ag >= 0) {
			if (logger.isLoggable (Level.FINE)) {
				logger.info ("Search Direction error: Gradient:\n"
										 + function.getCostGradient(gradient).toString());
				logger.info ("Search Direction error: Line:\n"
										 +	line.toString());
			}
			throw new IllegalArgumentException
				("The search direction \"line\" does not point down hill.  "
				 + "gradient.dotProduct(line)="+ag+", but should be negative");
		}

		//System.out.println ("Before gradient cross-over ax="+ax+" bx="+bx+" cx="+cx);
		//System.out.println ("Before gradient cross-over ay="+ay+" by="+by+" cy="+cy);
		
		// Find an cx value where the gradient points the other way.  Then
		// we will know that the (local) zero-gradient minimum falls
		// in between ax and cx.
		assert (by == by);
		int iterations = 0;
		do {
			if (iterations++ > maxIterations)
				throw new IllegalStateException ("Exceeded maximum number allowed iterations searching for gradient cross-over.");
			// If we are still looking to cross the minimum, move ax towards it
			ax = bx; ay = by; ag = bg;
			// Save this (possibly) middle point; it might make an acceptable bx
			bx = tx; by = ty; bg = tg;
			if (tx == 0)
				tx = initialStep;
			else
				tx *= 3;
			logger.info ("Gradient cross-over search, incrementing by "+(tx-ox));
			parameters.plusEquals (line, tx - ox);
			function.setParameters (parameters);
			ty = function.getCost();
			assert (ty == ty);
			tg = function.getCostGradient(gradient).dotProduct(line);
			assert (!gradient.isNaN());
			assert (!line.isNaN());
			logger.info ("Next gradient = "+tg);
			ox = tx;
		} while (tg < 0);
		cx = tx; cy = ty; cg = tg;
		logger.info ("After gradient cross-over ax="+ax+" bx="+bx+" cx="+cx);
		logger.info ("After gradient cross-over ay="+ay+" by="+by+" cy="+cy);
		logger.info ("After gradient cross-over ag="+ag+" bg="+bg+" cg="+cg);

		// We need to find a "by" that is less than both "ay" and "cy"
		assert (!Double.isNaN(by));
		while (by >= ay || by >= cy || bx == ax) {
			// Last condition would happen if we did first while-loop only once
			if (iterations++ > maxIterations)
				throw new IllegalStateException ("Exceeded maximum number allowed iterations searching for bracketed minimum, iteratation count = "+iterations);
			// xxx What should this tolerance be?
			// xxx I'm nervous that this is masking some assert()s below that were previously failing.
			// If they were failing due to round-off error, that's OK, but if not...
			//if ((Math.abs(bg) < 100 || Math.abs(ay-by) < 10 || Math.abs(by-cy) < 10) && bx != ax)
			if ((Math.abs(bg) < 10 || Math.abs(ay-by) < 1 || Math.abs(by-cy) < 1) && bx != ax)
				// Magically, we are done
				break;

			// Instead make a version that finds the interpolating point by
			// fitting a parabola, and then jumps to that minimum.  If the
			// actual y value is within "tolerance" of the parabola fit's
			// guess, then we are done, otherwise, use the parabola's x to
			// split the region, and try again.

			// There might be some cases where this will perform worse than
			// simply bisecting, as we do now, when the function is not at
			// all parabola shaped.

			
			// If the gradients ag and bg point in the same direction, then
			// the value by must be less than ay.  And vice-versa for bg and cg.
			assert (ax==bx || ((ag*bg)>=0 && by<ay) || (((bg*cg)>=0 && by<cy)));
			assert (!Double.isNaN(bg));
			if (bg < 0) {
				// the minimum is at higher x values than bx; drop ax
				assert (by <= ay);
				ax = bx; ay = by; ag = bg;
			} else {
				// the minimum is at lower x values than bx; drop cx
				assert (by <= cy);
				cx = bx; cy = by; cg = bg;
			}
			// Find a new mid-point
			bx = (ax + cx) / 2;
			//logger.info ("Minimum bx search, incrementing by "+(bx-ox));
			parameters.plusEquals (line, bx - ox);
			function.setParameters (parameters);
			by = function.getCost();
			assert (!Double.isNaN(by));
			bg = function.getCostGradient(gradient).dotProduct(line);
			ox = bx;
			logger.info ("  During min bx search ("+iterations+") ax="+ax+" bx="+bx+" cx="+cx);
			logger.info ("  During min bx search ("+iterations+") ay="+ay+" by="+by+" cy="+cy);
			logger.info ("  During min bx search ("+iterations+") ag="+ag+" bg="+bg+" cg="+cg);
		}
		// We now have two points (ax, cx) that straddle the minimum, and a mid-point
		// bx with a value lower than either ay or cy.
		assert (ax == ax); assert (bx == bx); assert (cx == cx);
		assert (ay == ay); assert (by == by); assert (cy == cy);
		tx = ax
				 + (((bx-ax)*(bx-ax)*(cy-ay)
						 -(cx-ax)*(cx-ax)*(by-ay))
						/
						(2.0 * ((bx-ax)*(cy-ay)-(cx-ax)*(by-ay))));
		logger.info ("Ending ax="+ax+" bx="+bx+" cx="+cx+" tx="+tx);
		logger.info ("Ending ay="+ay+" by="+by+" cy="+cy);
		assert (tx == tx) : tx;
		parameters.plusEquals (line, tx - ox);
		function.setParameters (parameters);
		assert (function.getCost() < origY);
		if (logger.isLoggable (Level.INFO)) {
			logger.info ("Ending cost = "+function.getCost());
			logger.info ("Ending gradient = "+
									 function.getCostGradient(gradient).dotProduct(line));
		}
		// As a suggestion for the next initalStep, return the distance
		// from our initialStep to the minimum we found.
		return tx;
	}

}
