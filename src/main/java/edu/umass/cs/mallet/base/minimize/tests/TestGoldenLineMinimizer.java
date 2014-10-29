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
import edu.umass.cs.mallet.base.types.DenseVector;
import junit.framework.*;


public class TestGoldenLineMinimizer extends TestCase
{
	public TestGoldenLineMinimizer (String name) {
		super (name);
	}

	public void testOne ()
	{
		DenseVector line = new DenseVector (1);
		LineMinimizer searcher = new GoldenLineMinimizer ();

		// The parabola y=x^2 with minimum at x=0
		Quadratic q = new Quadratic (1, 0, 0);

		System.out.println ("start=-10, step=1");
		q.setParameter (-10);
		line.setValue (0, 1.0);
		searcher.minimize (q, line, 1);
		System.out.println ("x="+q.getParameter());
		assertTrue (Math.abs (q.getParameter() - 0.0) < 0.0001);
		
		System.out.println ("start=10, step=1");
		q.setParameter (10);
		line.setValue (0, -1.0);
		searcher.minimize (q, line, 1);
		System.out.println ("x="+q.getParameter());
		assertTrue (Math.abs (q.getParameter() - 0.0) < 0.0001);

		System.out.println ("start=10, step=100");
		q.setParameter (10);
		line.setValue (0, -1.0);
		searcher.minimize (q, line, 100);
		System.out.println ("x="+q.getParameter());
		assertTrue (Math.abs (q.getParameter() - 0.0) < 0.0001);
	}

	public static Test suite ()
	{
		return new TestSuite (TestGoldenLineMinimizer.class);
	}

	protected void setUp ()
	{
	}

	public static void main (String[] args)
	{
		junit.textui.TestRunner.run (suite());
	}
	
}
