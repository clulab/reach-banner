/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package edu.umass.cs.mallet.base.util.tests;

import junit.framework.*;
import edu.umass.cs.mallet.base.util.Maths;
import edu.umass.cs.mallet.base.types.MatrixOps;

/**
 * Created: Oct 31, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestMaths.java,v 1.1 2011/07/29 09:11:47 bleaman Exp $
 */
public class TestMaths extends TestCase {

  public TestMaths (String name)
  {
    super (name);
  }


  public void testLogBinom ()
  {
    assertEquals (-3.207352, Maths.logBinom (25, 50, 0.4), 1e-5);
    assertEquals (-230.2585, Maths.logBinom (0, 100, 0.9), 1e-5);
  }

  public void testPbinom ()
  {
    assertEquals (0.9426562, Maths.pbinom (25, 50, 0.4), 1e-5);
    assertEquals (0.001978561, Maths.pbinom (80, 100, 0.9), 1e-5);
  }

  public void testSumLogProb ()
  {
    double[] vals = { 53.0, 1.56e4, 0.0045, 672.563, 1e-15 };
    double[] logVals = new double [vals.length];
    for (int i = 0; i < vals.length; i++)
      logVals [i] = Math.log (vals[i]);

    double sum = MatrixOps.sum (vals);

    double lsum2 = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < logVals.length; i++) {
      lsum2 = Maths.sumLogProb (lsum2, logVals [i]);
    }
    assertEquals (sum, Math.exp(lsum2), 1e-5);

    double lsum = Maths.sumLogProb (logVals);
    assertEquals (sum, Math.exp (lsum), 1e-5);

  }

  public void testSubtractLogProb ()
  {
    double a = 0.9;
    double b = 0.25;

    assertEquals (Math.log (a - b), Maths.subtractLogProb (Math.log (a), Math.log (b)), 1e-5);

    assertTrue (Double.isNaN (Maths.subtractLogProb (Math.log (b), Math.log (a))));
  }

  public static Test suite ()
  {
    return new TestSuite (TestMaths.class);
  }


  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestMaths (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
