/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package edu.umass.cs.mallet.base.extract.test;

import junit.framework.*;

import java.io.IOException;
import java.io.File;

import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.pipe.iterator.ArrayIterator;
import edu.umass.cs.mallet.base.fst.tests.TestMEMM;
import edu.umass.cs.mallet.base.fst.tests.TestCRF;
import edu.umass.cs.mallet.base.fst.CRF4;
import edu.umass.cs.mallet.base.types.InstanceList;
import edu.umass.cs.mallet.base.extract.CRFExtractor;
import edu.umass.cs.mallet.base.extract.Extraction;
import edu.umass.cs.mallet.base.extract.DocumentViewer;

/**
 * Created: Mar 30, 2005
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: TestDocumentViewer.java,v 1.1 2011/07/29 09:11:45 bleaman Exp $
 */
public class TestDocumentViewer extends TestCase {

  public TestDocumentViewer (String name)
  {
    super (name);
  }

  public static Test suite ()
  {
    return new TestSuite (TestDocumentViewer.class);
  }

  private String outputDir = "/Scratch/output/extract";

   public void testSpaceViewer () throws IOException
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

     CRFExtractor extor = TestLatticeViewer.hackCrfExtor (crf);
     Extraction extraction = extor.extract (new ArrayIterator (data1));
     DocumentViewer.writeExtraction (new File (outputDir), extraction);
   }


  public static void main (String[] args) throws Throwable
  {
    TestSuite theSuite;
    if (args.length > 0) {
      theSuite = new TestSuite ();
      for (int i = 0; i < args.length; i++) {
        theSuite.addTest (new TestDocumentViewer (args[i]));
      }
    } else {
      theSuite = (TestSuite) suite ();
    }

    junit.textui.TestRunner.run (theSuite);
  }

}
