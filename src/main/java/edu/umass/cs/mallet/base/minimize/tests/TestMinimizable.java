/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.minimize.tests;

import edu.umass.cs.mallet.base.minimize.GoldenLineMinimizer;
import edu.umass.cs.mallet.base.minimize.Minimizable;
import edu.umass.cs.mallet.base.minimize.LineMinimizer;
import edu.umass.cs.mallet.base.types.Matrix;
import edu.umass.cs.mallet.base.types.Vector;
import edu.umass.cs.mallet.base.util.MalletLogger;
import junit.framework.*;
import java.util.logging.*;

public class TestMinimizable extends TestCase
{
	private static Logger logger =
	MalletLogger.getLogger(TestMinimizable.class.getName());
	
	public TestMinimizable (String name) {
		super (name);
	}

	public static boolean testGetSetParameters (Minimizable minable)
	{
		System.out.println ("TestMinimizable testGetSetParameters");
		// Set all the parameters to unique values using setParameters()
		Matrix parameters = minable.getNewMatrix ();
		minable.getParameters (parameters);
		for (int i = 0; i < parameters.singleSize(); i++)
			parameters.setSingleValue (i, (double)i);
		minable.setParameters (parameters);

		// Test to make sure those parameters are there
		parameters.setAll (0.0);
		minable.getParameters (parameters);
		for (int i = 0; i < parameters.singleSize(); i++)
			assertTrue (parameters.singleValue (i) == (double)i);

		// Set all the parameters to unique values using setParameter()
		parameters.setAll (0.0);
		minable.setParameters (parameters);
		int[] indices = new int[parameters.getNumDimensions()];
		for (int i = 0; i < parameters.singleSize(); i++) {
			parameters.singleToIndices (i, indices);
			minable.setParameter (indices, (double)i);
		}

		// Test to make sure those parameters are there
		parameters.setAll (0.0);
		minable.getParameters (parameters);
		for (int i = 0; i < parameters.singleSize(); i++) {
			//System.out.println ("Got "+parameters.getSingle(i)+", expecting "+((double)i));
			assertTrue (parameters.singleValue (i) == (double)i);
		}

		// Test to make sure they are also there when we look individually
		for (int i = 0; i < parameters.singleSize(); i++) {
			parameters.singleToIndices (i, indices);
			assertTrue (minable.getParameter (indices) == (double)i);
		}
		
		return true;
	}

	public static double
	testCostAndGradientCurrentParameters (Minimizable.ByGradient minable)
	{
		Matrix parameters = minable.getParameters(minable.getNewMatrix());
		double cost = minable.getCost();
		// the gradient from the minimizable function
		Matrix analyticGradient = minable.getCostGradient(minable.getNewMatrix());
		// the gradient calculate from the slope of the cost
		Matrix empiricalGradient = (Matrix)analyticGradient.cloneMatrix();
		// This setting of epsilon should make the individual elements of
		// the analytical gradient and the empirical gradient equal.  This
		// simplifies the comparison of the individual dimensions of the
		// gradient and thus makes debugging easier.
		double epsilon = 0.1 / analyticGradient.twoNorm();
		double tolerance = epsilon * 5;
		System.out.println ("epsilon = "+epsilon+" tolerance="+tolerance);

		// Check each direction, perturb it, measure new cost,
		// and make sure it agrees with the gradient from minable.getCostGradient()
		for (int i = 0; i < parameters.singleSize(); i++) {
			double param = parameters.singleValue (i);
			parameters.setSingleValue (i, param + epsilon);
			//logger.fine ("Parameters:"); parameters.print();
			minable.setParameters (parameters);
			double epsCost = minable.getCost();
			double slope = (epsCost - cost) / epsilon;
			System.out.println ("cost="+cost+" epsCost="+epsCost+" slope["+i+"] = "+slope+" gradient[]="+analyticGradient.singleValue(i));
			assert (!Double.isNaN (slope));
			logger.fine ("TestMinimizable checking singleIndex "+i+
													": gradient slope = "+analyticGradient.singleValue(i)+
													", cost+epsilon slope = "+slope+
													": slope difference = "+Math.abs(slope - analyticGradient.singleValue(i)));
			// No negative below because the gradient points in the direction
			// of maximizing the function.
			empiricalGradient.setSingleValue (i, slope);
			parameters.setSingleValue (i, param);
		}
		// Normalize the matrices to have the same L2 length
		System.out.println ("empiricalGradient.twoNorm = "+empiricalGradient.twoNorm());
		analyticGradient.timesEquals (1.0/analyticGradient.twoNorm());
		empiricalGradient.timesEquals (1.0/empiricalGradient.twoNorm());
		//logger.info ("AnalyticGradient:"); analyticGradient.print();
		//logger.info ("EmpiricalGradient:"); empiricalGradient.print();
		// Return the angle between the two vectors, in radians
		double angle = Math.acos (analyticGradient.dotProduct (empiricalGradient));
		logger.info ("TestMinimizable angle = "+angle);
		if (Math.abs(angle) > tolerance)
			throw new IllegalStateException ("Gradient/Cost mismatch: angle="+angle);
		if (Double.isNaN (angle))
			throw new IllegalStateException ("Gradient/Cost error: angle is NaN!");
		return angle;
	}

	public static boolean	testCostAndGradient (Minimizable.ByGradient minable)
	{
		Matrix parameters = minable.getNewMatrix();
		parameters.setAll (0.0);
		minable.setParameters (parameters);
		testCostAndGradientCurrentParameters (minable);
		parameters.setAll (0.0);
		Matrix delta = minable.getNewMatrix();
		minable.getCostGradient (delta);
		delta.timesEquals (-0.0001);
		parameters.plusEquals (delta);
		minable.setParameters (parameters);
		testCostAndGradientCurrentParameters (minable);
		return true;
	}
	
	public void testTestCostAndGradient ()
	{
		testCostAndGradient (new Quadratic (10, 2, 3));
	}

	public static Test suite ()
	{
		return new TestSuite (TestMinimizable.class);
	}

	protected void setUp ()
	{
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
