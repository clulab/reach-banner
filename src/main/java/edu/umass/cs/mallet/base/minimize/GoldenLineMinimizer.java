/* Copyright (C) 2002 Dept. of Computer Science, Univ. of Massachusetts, Amherst

   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet

   This program toolkit free software; you can redistribute it and/or
   modify it under the terms of the GNU General Public License as
   published by the Free Software Foundation; either version 2 of the
   License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but
   WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  For more
   details see the GNU General Public License and the file README-LEGAL.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
   02111-1307, USA. */


/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.minimize;

import edu.umass.cs.mallet.base.minimize.LineMinimizer;
import edu.umass.cs.mallet.base.minimize.Minimizable;
import edu.umass.cs.mallet.base.types.Matrix;
import edu.umass.cs.mallet.base.util.MalletLogger;
import java.util.logging.*;

// "Routine for Initially Bracketing a Minimum", p401, "Numeric Recipes in C"

// Requires that "initialStep" not over-step the minimum!  Ick.

public class GoldenLineMinimizer implements LineMinimizer 
{
	private static Logger logger = MalletLogger.getLogger(GoldenLineMinimizer.class.getName());

	int maxIterations = 50;

	private static double SIGN (double a, double b)
	{ return ((b) >= 0.0 ? Math.abs(a) : -Math.abs(a)); }

	
	public double minimize (Minimizable function, Matrix line, double initialStep)
	{
		final double GOLD = 1.618034;
		final double GLIMIT = 100;
		final double TINY = 1e-10;
		final double SMALL = 1e-6;
		
		double ulim, u, r, q, fu, dum;
		double ax, bx, cx;
		double fa, fb, fc;
		double ox;													// the "old" x
		double lastStep = Double.MAX_VALUE;
		double initialCost;
		assert (initialStep > 0) : initialStep;
		Matrix parameters = function.getNewMatrix ();
		Matrix oldParameters = function.getNewMatrix ();
		function.getParameters (parameters);
		function.getParameters (oldParameters);
		
	  ax = ox = 0;
		bx = initialStep;
		fa = initialCost = function.getCost();
		logger.info ("Initial cost = "+fa);
		parameters.plusEquals (line, bx - ox);
		function.setParameters (parameters);
		fb = function.getCost();
		ox = bx;
		logger.info ("ax="+ax+" bx="+bx+" fa="+fa+" fb="+fb);

		if (fb > fa){
			// jumped too far
			// loop over "golden" shorter jumps
			// until fb < fa
			logger.info ("Step "+initialStep+" too far...searching for better step.");
			double newStep = initialStep;
      // switch ox back to ax and undo the too-large-step
			ox = ax;
			while(fb > fa){
				parameters.set(oldParameters);
				function.setParameters(parameters);
				newStep /= GOLD;
//				assert(newStep > TINY) : "Can't find downward-step while backtracking. Might be going in wrong direction.";
				if(newStep < SMALL) {
					logger.warning("Can't find downward-step while backtracking. Giving  up and returning initial parameters.");
					function.setParameters(oldParameters);
					return initialStep;
					
				}
				bx = newStep;
				parameters.plusEquals(line, bx - ox);
				function.setParameters(parameters);
				fb = function.getCost();			
			 
			}
			ox = bx;
			logger.info ("New step is "+newStep);
		}
 		// First guess at c
	  cx = bx + GOLD * (bx-ax);
		parameters.plusEquals (line, cx - ox);
		function.setParameters (parameters);
		ox = cx;
		logger.info ("cx="+cx);
		fc = function.getCost();
		logger.info ("fc="+fc);
		// Keep returning here until we bracket
		while (fb > fc) {
			logger.info ("ax="+ax+" bx="+bx+" cx="+cx+"\nfa="+fa+" fb="+fb+" fc="+fc);
			// Compute u by parabolic extrapolation from a, b, c.
			// TINY is used to prevent any possible division by zero
			r = (bx-ax) * (fb-fc);
			q = (bx-cx) * (fb-fa);
			u = bx - (bx-cx)*q-((bx-ax)*r)/(2.0*SIGN(Math.max(Math.abs(q-r),TINY),q-r));
			ulim = bx+GLIMIT*(cx-bx);
			// We won't go farther than this.  Test various possibilities
			if ((bx-u)*(u-cx) > 0.0) {
				// Parabolic u is between b and c: try it.
				parameters.plusEquals (line, u - ox);
				function.setParameters (parameters);
				ox = u; lastStep = Math.abs(u - ox);
				fu = function.getCost();
				if (fu < fc) {
					ax = bx;
					bx = u;
					fa = fb;
					fb = fu;
					break;
				} else if (fu > fb) {
					cx = u;
					fc = fu;
					break;
				}
				u = cx + GOLD * (cx - bx);
				parameters.plusEquals (line, u - ox);
				function.setParameters (parameters);
				ox = u; lastStep = Math.abs(u - ox);
				fu = function.getCost();
			} else if ((cx-u)*(u-ulim) > 0.0) {
				parameters.plusEquals (line, u - ox);
				function.setParameters (parameters);
				ox = u; lastStep = Math.abs(u - ox);
				fu = function.getCost();
				if (fu < fc) {
					bx = cx;
					cx = u;
					u = cx + GOLD * (cx-bx);
					fb = fc;
					fc = fu;
					parameters.plusEquals (line, u - ox);
					function.setParameters (parameters);
					ox = u; lastStep = Math.abs(u - ox);
					fu = function.getCost();
				}
			} else if ((u-ulim)*(ulim-cx) >= 0.0) {
				// Limit parabolic u to maximum allowed value
				u = ulim;
				parameters.plusEquals (line, u - ox);
				function.setParameters (parameters);
				ox = u; lastStep = Math.abs(u - ox);
				fu = function.getCost();
			} else {
 				// Reject parabolic u, use default magnification
				u = cx + GOLD * (cx - bx);
				parameters.plusEquals (line, u - ox);
				function.setParameters (parameters);
				ox = u; lastStep = Math.abs(u - ox);
				fu = function.getCost();
			}
			ax = bx;
			bx = cx;
			cx = u;
			fa = fb;
			fb = fc;
			fc = fu;
		}
		assert (ax != bx && bx != cx);

	  // Now ax,bx,cx (with values fa,fb,fc) bracket the minimum;
		// Make a parabolic interpolation for the minimum
 		double mx = ax
		 						+ (((bx-ax)*(bx-ax)*(fc-fa)
										-(cx-ax)*(cx-ax)*(fb-fa))
									 /
									 (2.0 * ((bx-ax)*(fc-fa)-(cx-ax)*(fb-fa))));
		parameters.plusEquals (line, mx - ox);
		function.setParameters (parameters);
		// xxx We can now delete the "lastStep" variable
		//assert (lastStep != Double.MAX_VALUE);
		double finalCost = function.getCost();
		logger.info ("ax="+ax+" bx="+bx+" cx="+cx+"\nfa="+fa+" fb="+fb+" fc="+fc
								 +" fmx="+finalCost);
		logger.info ("Returning mx="+mx);
		assert(finalCost <= initialCost);

//		double mx = golden(ax, bx, cx, ox, function, line);
		logger.info ("ax="+ax+" bx="+bx+" cx="+cx+"\nfa="+fa+" fb="+fb+" fc="+fc);
		return mx;
	}

	/**
	 * Find minimum using golden section search.
	 * See Numerical recipes in C, p. 401
	 * @param ax ax
	 * @param bx bx
	 * @param cx cx
	 * @param ox old x
	 * @param function function we're minimizing
	 * @param line direction
	 * @return double mx, the parameter giving the minimum cost
	 */
	// xxx Time-Sucker!!
	private double golden(double ax, double bx, double cx, double ox,
												Minimizable function, Matrix line)
	{
		final double tol = .1;
		// Golden ratios
		final double R = 0.61803399;
		final double C = 1.0-R; 
			
		double f1,f2;
		double x0,x1,x2,x3;
		Matrix parameters = function.getNewMatrix ();
		
		x0 = ax;
		x3 = cx;
		if(Math.abs(cx-bx) > Math.abs(bx-ax)) {
			x1 = bx;
			x2 = bx + C * (cx - bx);
		}
		else {
			x2 = bx;
			x1 = bx - C * (bx - ax);
		}
		
		parameters.plusEquals (line, x1 - ox);
		function.setParameters (parameters);
		f1 = function.getCost();
		ox = x1;

		parameters.plusEquals (line, x2 - ox);
		function.setParameters (parameters);
		f2 = function.getCost();
		ox = x2;

		while (Math.abs(x3-x0) > (tol * (Math.abs(x1) + Math.abs(x2)))) {
			//logger.info ("x0="+x0+" x1="+x1+" x2="+x2+" x3="+x3);
			if (f2 < f1) {
				x0 = x1;
				x1 = x2;
				x2 = R * x1 + C * x3;
				f1 = f2;
				parameters.plusEquals (line, x2 - ox);
				function.setParameters (parameters);
				f2 = function.getCost();
				ox = x2;				
			}
			else {
				x3 = x2;
				x2 = x1;
				x1 = R * x2 + C * x0;
				f2 = f1;
				parameters.plusEquals (line, x1 - ox);
				function.setParameters (parameters);
				f1 = function.getCost();
				ox = x1;
			}
		}

		if (f1 < f2) {
			double min;
			parameters.plusEquals (line, x1 - ox);
			function.setParameters (parameters);
			min = function.getCost();
			ox = x1;
			logger.info ("golden found min "+min+" at point "+x1);
			return x1;
}
		else {
			double min;
			parameters.plusEquals (line, x2 - ox);
			function.setParameters (parameters);
			min = function.getCost();
			ox = x2;
			logger.info ("golden found min "+min+" at point "+x2);
			return x2;
		}
	}


}
