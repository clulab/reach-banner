/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */
package edu.umass.cs.mallet.base.extract;

import edu.umass.cs.mallet.base.fst.CRF4;
import edu.umass.cs.mallet.base.pipe.Noop;
import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.pipe.SerialPipes;
import edu.umass.cs.mallet.base.pipe.iterator.InstanceListIterator;
import edu.umass.cs.mallet.base.pipe.iterator.PipeInputIterator;
import edu.umass.cs.mallet.base.types.*;

import java.io.*;
import java.util.ArrayList;

/**
 * Created: Oct 12, 2004
 *
 * @author <A HREF="mailto:casutton@cs.umass.edu>casutton@cs.umass.edu</A>
 * @version $Id: CRFExtractor.java,v 1.1 2011/07/29 09:11:45 bleaman Exp $
 */
public class CRFExtractor implements Extractor {

  private CRF4 crf;
  private Pipe tokenizationPipe;
  private Pipe featurePipe;
  private String backgroundTag;
  private TokenizationFilter filter;

  public CRFExtractor (CRF4 crf) {
    this (crf, new Noop ());
  }

  public CRFExtractor (File crfFile) throws IOException {
    this (loadCrf(crfFile), new Noop ());
  }

  public CRFExtractor (CRF4 crf, Pipe tokpipe) {
    this (crf, tokpipe, new BIOTokenizationFilter ());
  }

  public CRFExtractor (CRF4 crf, Pipe tokpipe, TokenizationFilter filter) {
    this (crf, tokpipe, filter, "O");
  }

  public CRFExtractor (CRF4 crf, Pipe tokpipe, TokenizationFilter filter, String backgroundTag) {
    this.crf = crf;
    tokenizationPipe = tokpipe;
    featurePipe = crf.getInputPipe ();
    this.filter = filter;
    this.backgroundTag = backgroundTag;
   }


  private static CRF4 loadCrf (File crfFile) throws IOException
  {
     ObjectInputStream ois = new ObjectInputStream( new FileInputStream( crfFile ) );
    CRF4 crf = null;

    // We shouldn't run into a ClassNotFound exception...
    try {
      crf = (CRF4)ois.readObject();
    } catch (ClassNotFoundException e) {
      System.err.println ("Internal MALLET error: Could not read CRF from file "+crfFile+"\n"+e);
      e.printStackTrace ();
      throw new RuntimeException (e);
    }

    ois.close();
    return crf;
  }



  public Extraction extract (Object o)
  {
    // I don't think there's a polymorphic way to do this. b/c Java sucks. -cas
    if (o instanceof Tokenization) {
      return extract ((Tokenization) o);
    } else  {
      return extract (doTokenize (o));
    }
  }


  private Tokenization doTokenize (Object obj)
  {
    Instance toked = new Instance (obj, null, null, null);
    tokenizationPipe.pipe (toked);
    return (Tokenization) toked.getData ();
  }


  public Extraction extract (Tokenization spans)
  {
    // We assume the input is unpiped.
    Instance carrier = new Instance (spans, null, null, null, featurePipe);
    Sequence output = crf.transduce ((Sequence) carrier.getData ());
    Extraction extraction = new Extraction (this, getTargetAlphabet());
    DocumentExtraction docseq = new DocumentExtraction ("Extraction", getTargetAlphabet(), 
                                                        spans,
                                                        output, null, backgroundTag,
                                                        filter);
    extraction.addDocumentExtraction (docseq);
    return extraction;
  }

  public InstanceList pipeInstances (PipeInputIterator source)
  {
    // I think that pipes should be associated neither with InstanceLists, nor
    //  with Instances. -cas
    InstanceList toked = new InstanceList (tokenizationPipe);
    toked.add (source);
    InstanceList piped = new InstanceList (getFeaturePipe ());
    piped.add (new InstanceListIterator (toked));
    return piped;
  }


  public Extraction extract (PipeInputIterator source)
  {
    Extraction extraction = new Extraction (this, getTargetAlphabet ());
    // Put all the instances through both pipes, then get viterbi path
    InstanceList tokedList = new InstanceList (tokenizationPipe);
    tokedList.add (source);
    InstanceList pipedList = new InstanceList (getFeaturePipe ());
    pipedList.add (new InstanceListIterator (tokedList));

    InstanceList.Iterator it1 = tokedList.iterator ();
    InstanceList.Iterator it2 = pipedList.iterator ();
    while (it1.hasNext()) {
      Instance toked = it1.nextInstance();
      Instance piped = it2.nextInstance ();
      Tokenization tok = (Tokenization) toked.getData();
      String name = piped.getName().toString();
      Sequence input = (Sequence) piped.getData ();
      Sequence target = (Sequence) piped.getTarget ();
      Sequence output = crf.transduce (input);

      DocumentExtraction docseq = new DocumentExtraction (name, getTargetAlphabet (), tok,
                                                          output, target, backgroundTag,
                                                          filter);
      extraction.addDocumentExtraction (docseq);
    }
    return extraction;
  }


  public Pipe getTokenizationPipe ()
  {
    return tokenizationPipe;
  }


  public void setTokenizationPipe (Pipe tokenizationPipe)
  {
    this.tokenizationPipe = tokenizationPipe;
  }


  public Pipe getFeaturePipe ()
  {
    return featurePipe;
  }

  //xxx This method is inherent dangerous!!! Should check that pipe.alphabet equals crf.alphabet
  public void setFeaturePipe (Pipe featurePipe)
  {
    this.featurePipe = featurePipe;
  }

  public Alphabet getInputAlphabet ()
  {
    return crf.getInputAlphabet ();
  }


  public LabelAlphabet getTargetAlphabet ()
  {
    return (LabelAlphabet) crf.getOutputAlphabet ();
  }


  public CRF4 getCrf ()
  {
    return crf;
  }

  /**
   * Transfer some Pipes from the feature pipe to the tokenization pipe.
   *  The feature pipe must be a SerialPipes.  This will destructively modify the CRF object of the extractor.
   *   This is useful if you have a CRF hat has been trained from a single pipe, which you need to split up
   *    int feature and tokenization pipes
   */
  public void slicePipes (int num)
  {
    Pipe fpipe = getFeaturePipe ();
    if (!(fpipe instanceof SerialPipes))
      throw new IllegalArgumentException ("slicePipes: FeaturePipe must be a SerialPipes.");
    SerialPipes sp = (SerialPipes) fpipe;
    ArrayList pipes = new ArrayList ();
    for (int i = 0; i < num; i++) {
      pipes.add (sp.getPipe (0));
      sp.removePipe (0);
    }
    setTokenizationPipe (sp);
  }

  // Java serialization nonsense

  // Serial version 0:  Initial version
  // Serial version 1:  Add featurePipe
  // Serial version 2:  Add filter
  private static final int CURRENT_SERIAL_VERSION = 2;
  private static final long serialVersionUID = 1;

  private void readObject (ObjectInputStream in) throws IOException, ClassNotFoundException
  {
    in.defaultReadObject ();
    int version = in.readInt ();
    if ((version == 0) || (featurePipe == null)) {
      featurePipe = crf.getInputPipe ();
    }
    if (version < 2) {
      filter = new BIOTokenizationFilter ();
    }
  }

  private void writeObject (ObjectOutputStream out) throws IOException
  {
    out.defaultWriteObject ();
    out.writeInt (CURRENT_SERIAL_VERSION);
  }


  public Sequence pipeInput (Object input)
  {
    InstanceList all = new InstanceList (getFeaturePipe ());
    all.add (input, null, null, null);
    return (Sequence) all.getInstance (0).getData();
  }
}
