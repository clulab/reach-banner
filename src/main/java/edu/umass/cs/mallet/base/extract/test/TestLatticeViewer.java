/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package edu.umass.cs.mallet.base.extract.test;

import edu.umass.cs.mallet.base.extract.CRFExtractor;
import edu.umass.cs.mallet.base.extract.Extraction;
import edu.umass.cs.mallet.base.extract.LatticeViewer;
import edu.umass.cs.mallet.base.fst.CRF4;
import edu.umass.cs.mallet.base.fst.tests.TestMEMM;
import edu.umass.cs.mallet.base.fst.TokenAccuracyEvaluator;
import edu.umass.cs.mallet.base.fst.MEMM;
import edu.umass.cs.mallet.base.fst.tests.TestCRF;
import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.pipe.SerialPipes;
import edu.umass.cs.mallet.base.pipe.iterator.ArrayIterator;
import edu.umass.cs.mallet.base.types.InstanceList;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.*;

/**
 * Created: Oct 31, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestLatticeViewer.java,v 1.1 2011/07/29 09:11:45 bleaman Exp $
 */
public class TestLatticeViewer extends TestCase {

  public TestLatticeViewer (String name)
  {
    super (name);
  }

  private static File htmlFile = new File ("/Scratch/output/errors.html");
  private static File latticeFile = new File ("/Scratch/output/lattice.html");
  private static File htmlDir = new File ("/Scratch/output/html/");

  public void testSpaceViewer () throws FileNotFoundException
  {
    Pipe pipe = TestMEMM.makeSpacePredictionPipe ();
    String[] data0 = { TestCRF.data[0] };
    String[] data1 = { TestCRF.data[1] };

    InstanceList training = new InstanceList (pipe);
    training.add (new ArrayIterator (data0));
    InstanceList testing = new InstanceList (pipe);
    testing.add (new ArrayIterator (data1));

    CRF4 crf = new CRF4 (pipe, null);
    crf.addFullyConnectedStatesForLabels ();
    crf.train (training, null, null, null);

    CRFExtractor extor = hackCrfExtor (crf);
    Extraction extration = extor.extract (new ArrayIterator (data1));

    PrintStream out = new PrintStream (new FileOutputStream (htmlFile));
    LatticeViewer.extraction2html (extration, extor, out);
    out.close();

    out = new PrintStream (new FileOutputStream (latticeFile));
    LatticeViewer.extraction2html (extration, extor, out, true);
    out.close();


  }


  static CRFExtractor hackCrfExtor (CRF4 crf)
  {
    Pipe[] newPipes = new Pipe [3];

    SerialPipes pipes = (SerialPipes) crf.getInputPipe ();
    for (int i = 0; i < 3; i++) {
      Pipe p0 = pipes.getPipe (0);
      pipes.removePipe (0);
      p0.setParent (null);
      newPipes[i] = p0;
    }

    Pipe tokPipe = new SerialPipes (newPipes);

    CRFExtractor extor = new CRFExtractor (crf, tokPipe);
    return extor;
  }


  public void testDualSpaceViewer () throws IOException
  {
    Pipe pipe = TestMEMM.makeSpacePredictionPipe ();
    String[] data0 = { TestCRF.data[0] };
    String[] data1 = TestCRF.data;

    InstanceList training = new InstanceList (pipe);
    training.add (new ArrayIterator (data0));
    InstanceList testing = new InstanceList (pipe);
    testing.add (new ArrayIterator (data1));

    CRF4 crf = new CRF4 (pipe, null);
    crf.addFullyConnectedStatesForLabels ();
    crf.train (training, null, testing, new TokenAccuracyEvaluator (), 5);

    CRFExtractor extor = hackCrfExtor (crf);
    Extraction e1 = extor.extract (new ArrayIterator (data1));

    Pipe pipe2 = TestMEMM.makeSpacePredictionPipe ();
    InstanceList training2 = new InstanceList (pipe2);
    training2.add (new ArrayIterator (data0));
    InstanceList testing2 = new InstanceList (pipe2);
    testing2.add (new ArrayIterator (data1));

    CRF4 memm = new MEMM (pipe2, null);
    memm.addFullyConnectedStatesForLabels ();
    memm.train (training2, null, testing2, new TokenAccuracyEvaluator (), 5);

    CRFExtractor extor2 = hackCrfExtor (memm);
    Extraction e2 = extor2.extract (new ArrayIterator (data1));

    LatticeViewer.viewDualResults (htmlDir, e1, extor, e2, extor2);

  }

  public static Test suite ()
  {
    return new TestSuite (TestLatticeViewer.class);
  }


  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestLatticeViewer (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
