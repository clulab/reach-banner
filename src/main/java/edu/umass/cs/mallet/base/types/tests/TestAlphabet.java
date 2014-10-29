/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package edu.umass.cs.mallet.base.types.tests;

import junit.framework.*;
import edu.umass.cs.mallet.base.types.Alphabet;

/**
 * Created: Nov 24, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestAlphabet.java,v 1.1 2011/07/29 09:11:46 bleaman Exp $
 */
public class TestAlphabet extends TestCase {

  public TestAlphabet (String name)
  {
    super (name);
  }

  public void testNotFound ()
  {
    Alphabet dict = new Alphabet ();
    dict.lookupIndex ("TEST1");
    dict.lookupIndex ("TEST2");
    dict.lookupIndex ("TEST3");
    assertEquals (-1, dict.lookupIndex ("TEST4", false));
    assertEquals (3, dict.size());
    assertEquals (3, dict.lookupIndex ("TEST4", true));
  }

  public static Test suite ()
  {
    return new TestSuite (TestAlphabet.class);
  }


  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestAlphabet (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
