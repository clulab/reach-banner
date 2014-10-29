/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/**
 @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package edu.umass.cs.mallet.base.fst.tests;

import edu.umass.cs.mallet.base.types.*;
import edu.umass.cs.mallet.base.fst.*;
import edu.umass.cs.mallet.base.minimize.*;
import edu.umass.cs.mallet.base.minimize.tests.*;
import edu.umass.cs.mallet.base.pipe.*;
import edu.umass.cs.mallet.base.pipe.iterator.*;
import edu.umass.cs.mallet.base.pipe.tsf.*;
import edu.umass.cs.mallet.base.minimize.tests.TestMinimizable;
import junit.framework.*;

import java.net.URI;
import java.util.Iterator;
import java.io.*;

public class TestCRF3 extends TestCase {

  public TestCRF3(String name)
  {
    super(name);
  }


  public static final String[] data = new String[]{
    "Free software is a matter of the users' freedom to run, copy, distribute, study, change and improve the software. More precisely, it refers to four kinds of freedom, for the users of the software.",
    "The freedom to run the program, for any purpose.",
    "The freedom to study how the program works, and adapt it to your needs.",
    "The freedom to redistribute copies so you can help your neighbor.",
    "The freedom to improve the program, and release your improvements to the public, so that the whole community benefits.",
    "A program is free software if users have all of these freedoms. Thus, you should be free to redistribute copies, either with or without modifications, either gratis or charging a fee for distribution, to anyone anywhere. Being free to do these things means (among other things) that you do not have to ask or pay for permission.",
    "You should also have the freedom to make modifications and use them privately in your own work or play, without even mentioning that they exist. If you do publish your changes, you should not be required to notify anyone in particular, or in any particular way.",
    "In order for the freedoms to make changes, and to publish improved versions, to be meaningful, you must have access to the source code of the program. Therefore, accessibility of source code is a necessary condition for free software.",
    "Finally, note that criteria such as those stated in this free software definition require careful thought for their interpretation. To decide whether a specific software license qualifies as a free software license, we judge it based on these criteria to determine whether it fits their spirit as well as the precise words. If a license includes unconscionable restrictions, we reject it, even if we did not anticipate the issue in these criteria. Sometimes a license requirement raises an issue that calls for extensive thought, including discussions with a lawyer, before we can decide if the requirement is acceptable. When we reach a conclusion about a new issue, we often update these criteria to make it easier to see why certain licenses do or don't qualify.",
    "In order for these freedoms to be real, they must be irrevocable as long as you do nothing wrong; if the developer of the software has the power to revoke the license, without your doing anything to give cause, the software is not free.",
    "However, certain kinds of rules about the manner of distributing free software are acceptable, when they don't conflict with the central freedoms. For example, copyleft (very simply stated) is the rule that when redistributing the program, you cannot add restrictions to deny other people the central freedoms. This rule does not conflict with the central freedoms; rather it protects them.",
    "Thus, you may have paid money to get copies of free software, or you may have obtained copies at no charge. But regardless of how you got your copies, you always have the freedom to copy and change the software, even to sell copies.",
    "Rules about how to package a modified version are acceptable, if they don't effectively block your freedom to release modified versions. Rules that ``if you make the program available in this way, you must make it available in that way also'' can be acceptable too, on the same condition. (Note that such a rule still leaves you the choice of whether to publish the program or not.) It is also acceptable for the license to require that, if you have distributed a modified version and a previous developer asks for a copy of it, you must send one.",
    "Sometimes government export control regulations and trade sanctions can constrain your freedom to distribute copies of programs internationally. Software developers do not have the power to eliminate or override these restrictions, but what they can and must do is refuse to impose them as conditions of use of the program. In this way, the restrictions will not affect activities and people outside the jurisdictions of these governments.",
    "Finally, note that criteria such as those stated in this free software definition require careful thought for their interpretation. To decide whether a specific software license qualifies as a free software license, we judge it based on these criteria to determine whether it fits their spirit as well as the precise words. If a license includes unconscionable restrictions, we reject it, even if we did not anticipate the issue in these criteria. Sometimes a license requirement raises an issue that calls for extensive thought, including discussions with a lawyer, before we can decide if the requirement is acceptable. When we reach a conclusion about a new issue, we often update these criteria to make it easier to see why certain licenses do or don't qualify.",
    "The GNU Project was launched in 1984 to develop a complete Unix-like operating system which is free software: the GNU system."
  };


  public void testGetSetParameters()
  {
    int inputVocabSize = 100;
    int numStates = 5;
    Alphabet inputAlphabet = new Alphabet();
    for (int i = 0; i < inputVocabSize; i++)
      inputAlphabet.lookupIndex("feature" + i);
    Alphabet outputAlphabet = new Alphabet();
    CRF3 crf = new CRF3(inputAlphabet, outputAlphabet);
    String[] stateNames = new String[numStates];
    for (int i = 0; i < numStates; i++)
      stateNames[i] = "state" + i;
    crf.priorCost(new InstanceList(null));
    crf.addFullyConnectedStates(stateNames);
    CRF3.MinimizableCRF mcrf = crf.getMinimizableCRF(new InstanceList(null));
    TestMinimizable.testGetSetParameters(mcrf);
  }


  // Should print at end:
  // parameters 4 4 3: unconstrainedCost=-2912.0 constrainedCost=-428.0 minCost=35770.0 minGrad=520.0
  public void testCost(int useSave)
  {
    int inputVocabSize = 4;
    int numStates = 5;
    // Create a file to store the CRF
    File f = new File("TestObject.obj");
    File f2 = new File("TestObject2.obj");
    Alphabet inputAlphabet = new Alphabet();
    for (int i = 0; i < inputVocabSize; i++)
      inputAlphabet.lookupIndex("feature" + i);
    Alphabet outputAlphabet = new Alphabet();
    // Store the dictionary
    if (outputAlphabet == null) {
      System.err.println("Output dictionary null.");
    }
    CRF3 crf = new CRF3(inputAlphabet, outputAlphabet);

    String[] stateNames = new String[numStates];
    for (int i = 0; i < numStates; i++)
      stateNames[i] = "state" + i;
    CRF3 saveCRF = crf;
    //inputAlphabet = (Feature.Alphabet) crf.getInputAlphabet();
    FeatureVectorSequence fvs = new FeatureVectorSequence(new FeatureVector[]{
      new FeatureVector((Alphabet) crf.getInputAlphabet(), new int[]{1, 2, 3}, new double[]{1, 1, 1}),
      new FeatureVector((Alphabet) crf.getInputAlphabet(), new int[]{1, 2, 3}, new double[]{1, 1, 1}),
      new FeatureVector((Alphabet) crf.getInputAlphabet(), new int[]{1, 2, 3}, new double[]{1, 1, 1}),
      new FeatureVector((Alphabet) crf.getInputAlphabet(), new int[]{1, 2, 3}, new double[]{1, 1, 1}),
    });
    FeatureSequence ss = new FeatureSequence(crf.getOutputAlphabet(), new int[]{0, 1, 2, 3});
    InstanceList ilist = new InstanceList(null);
    ilist.add(fvs, ss, null, null);

    crf.priorCost(ilist);
    crf.addFullyConnectedStates(stateNames);

    try {
      ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
      oos.writeObject(crf);
      oos.close();
    } catch (IOException e) {
      System.err.println("Exception writing file: " + e);
    }
    System.err.println("Wrote out CRF");
    System.err.println("CRF parameters. hyperbolicPriorSlope: " + crf.getUseHyperbolicPriorSlope() + ". hyperbolicPriorSharpness: " + crf.getUseHyperbolicPriorSharpness() + ". gaussianPriorVariance: " + crf.getGaussianPriorVariance());
    // And read it back in
    crf = null;
    try {
      ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
      crf = (CRF3) ois.readObject();
      ois.close();
    } catch (IOException e) {
      System.err.println("Exception reading file: " + e);
    } catch (ClassNotFoundException cnfe) {
      System.err.println("Cound not find class reading in object: " + cnfe);
    }
    System.err.println("Read in CRF.");
    System.err.println("CRF parameters. hyperbolicPriorSlope: " + crf.getUseHyperbolicPriorSlope() + ". hyperbolicPriorSharpness: " + crf.getUseHyperbolicPriorSharpness() + ". gaussianPriorVariance: " + crf.getGaussianPriorVariance());

    try {
      ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f2));
      oos.writeObject(crf);
      oos.close();
    } catch (IOException e) {
      System.err.println("Exception writing file: " + e);
    }
    System.err.println("Wrote out CRF");
    if (useSave == 1) {
      crf = saveCRF;
    }
    CRF3.MinimizableCRF mcrf = crf.getMinimizableCRF(ilist);
    double unconstrainedCost = crf.forwardBackward(fvs).getCost();
    double constrainedCost = crf.forwardBackward(fvs, ss).getCost();
    double minimizableCost = 0, minimizableGradientNorm = 0;
    Matrix gradient = mcrf.getNewMatrix();
    //System.out.println ("unconstrainedCost="+unconstrainedCost+" constrainedCost="+constrainedCost);
    for (int i = 0; i < numStates; i++)
      for (int j = 0; j < numStates; j++)
        for (int k = 0; k < inputVocabSize; k++) {
          crf.setParameter(i, j, k, (k + i + j) * (k * i + i * j));
          unconstrainedCost = crf.forwardBackward(fvs).getCost();
          constrainedCost = crf.forwardBackward(fvs, ss).getCost();
          minimizableCost = mcrf.getCost();
          minimizableGradientNorm = mcrf.getCostGradient(gradient).oneNorm();
          System.out.println("parameters " + i + " " + j + " " + k
                             + ": unconstrainedCost=" + unconstrainedCost
                             + " constrainedCost=" + constrainedCost
                             + " minCost=" + minimizableCost
                             + " minGrad=" + minimizableGradientNorm);
        }
    assertTrue(Math.abs(minimizableCost - 35770) < 0.001);
    assertTrue(Math.abs(minimizableGradientNorm - 520) < 0.001);
  }


  public void testIncrement()
  {
  }


  public static class TestCRFTokenSequenceRemoveSpaces extends Pipe implements Serializable {

    public TestCRFTokenSequenceRemoveSpaces()
    {
      super(null, Alphabet.class);
    }


    public Instance pipe(Instance carrier)
    {
      TokenSequence ts = (TokenSequence) carrier.getData();
      TokenSequence newTs = new TokenSequence();
      FeatureSequence labelSeq = new FeatureSequence(getTargetAlphabet());
      boolean lastWasSpace = true;
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < ts.size(); i++) {
        Token t = ts.getToken(i);
        if (t.getText().equals(" "))
          lastWasSpace = true;
        else {
          sb.append(t.getText());
          newTs.add(t);
          labelSeq.add(lastWasSpace ? "start" : "notstart");
          lastWasSpace = false;
        }
      }
      if (isTargetProcessing())
        carrier.setTarget(labelSeq);
      carrier.setData(newTs);
      carrier.setSource(sb.toString());
      return carrier;
    }


    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 0;


    private void writeObject(ObjectOutputStream out) throws IOException
    {
      out.writeInt(CURRENT_SERIAL_VERSION);
    }


    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
    {
      int version = in.readInt();
    }

  }

  public class TestCRF2String extends Pipe implements Serializable {

    public TestCRF2String()
    {
      super();
    }


    public Instance pipe(Instance carrier)
    {
      StringBuffer sb = new StringBuffer();
      String source = (String) carrier.getSource();
      Transducer.ViterbiPath vp = (Transducer.ViterbiPath) carrier.getTarget();
      ArraySequence as = (ArraySequence) vp.output();
      //int startLabelIndex = as.getAlphabet().lookupIndex("start");
      for (int i = 0; i < source.length(); i++) {
        System.out.println("target[" + i + "]=" + as.get(i).toString());
        if (as.get(i).toString().equals("start") && i != 0)
          sb.append(' ');
        sb.append(source.charAt(i));
      }
      carrier.setSource(sb.toString());
      System.out.println("carrier.getSource() = " + carrier.getSource());
      return carrier;
    }


    private static final long serialVersionUID = 1;
    private static final int CURRENT_SERIAL_VERSION = 0;


    private void writeObject(ObjectOutputStream out) throws IOException
    {
      out.writeInt(CURRENT_SERIAL_VERSION);
    }


    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
    {
      int version = in.readInt();
    }
  }


  public void doTestSpacePrediction(boolean testCostAndGradient)
  {
    Pipe p = new SerialPipes(new Pipe[]{
      new CharSequence2TokenSequence("."),
      new TokenSequenceLowercase(),
      new TestCRFTokenSequenceRemoveSpaces(),
      new TokenText(),
      new OffsetConjunctions(false,
                             new int[][]{//{0},
                               {1}, {-1, 0}, {0, 1},
                               {-2, -1, 0}, {0, 1, 2}, {-3, -2, -1}, {1, 2, 3},
                               //{-2,-1}, {1,2},
                               //{-3,-2,-1}, {-2,-1,0}, {-1,0,1}, {0,1,2}, {1,2,3}
                             }),
//      new PrintInputAndTarget(),
      new TokenSequence2FeatureVectorSequence()
    });
    Pipe p2 = new TestCRF2String();

    InstanceList instances = new InstanceList(p);
    instances.add(new ArrayIterator(data));
    InstanceList[] lists = instances.split(new double[]{.5, .5});
    CRF3 crf = new CRF3(p, p2);
    crf.priorCost(lists[0]);
    crf.addFullyConnectedStatesForLabels();
    if (testCostAndGradient) {
      Minimizable.ByGradient minable = crf.getMinimizableCRF(lists[0]);
      TestMinimizable.testCostAndGradient(minable);
    } else {
      System.out.println("Training Accuracy before training = " + crf.averageTokenAccuracy(lists[0]));
      System.out.println("Testing  Accuracy before training = " + crf.averageTokenAccuracy(lists[1]));
      System.out.println("Training...");
      crf.train(lists[0]);
      System.out.println("Training Accuracy after training = " + crf.averageTokenAccuracy(lists[0]));
      System.out.println("Testing  Accuracy after training = " + crf.averageTokenAccuracy(lists[1]));
      System.out.println("Training results:");
      /* transduce not implemented -cas
      for (int i = 0; i < lists[0].size(); i++) {
        Instance instance = crf.transduce(lists[0].getInstance(i));
        System.out.println(instance.getSource());
      }
      System.out.println("Testing results:");
      for (int i = 0; i < lists[1].size(); i++) {
        Instance instance = crf.transduce(lists[1].getInstance(i));
        System.out.println(instance.getSource());
      }
      */
    }
  }


  public void doTestSpacePrediction(boolean testCostAndGradient, boolean useSaved)
  {
    Pipe p = new SerialPipes(new Pipe[]{
      new CharSequence2TokenSequence("."),
      new TokenSequenceLowercase(),
      new TestCRFTokenSequenceRemoveSpaces(),
      new TokenText(),
      new OffsetConjunctions(false,
                             new int[][]{//{0}, /*{1},{-1,0},{0,1}, */
                               //{-2,-1}, {-1,0}, {0,1}, {1,2},
                               //{-3,-2,-1}, {-2,-1,0}, {-1,0,1}, {0,1,2}, {1,2,3},
                             }),
//      new PrintInputAndTarget(),
      new TokenSequence2FeatureVectorSequence()
    });

    CRF3 savedCRF;
    File f = new File("TestObject.obj");
    InstanceList instances = new InstanceList(p);
    instances.add(new ArrayIterator(data));
    InstanceList[] lists = instances.split(new double[]{.5, .5});
    CRF3 crf = new CRF3(p.getDataAlphabet(), p.getTargetAlphabet());
    crf.priorCost(lists[0]);
    crf.addFullyConnectedStatesForLabels();
    if (testCostAndGradient) {
      Minimizable.ByGradient minable = crf.getMinimizableCRF(lists[0]);
      TestMinimizable.testCostAndGradient(minable);
    } else {
      System.out.println("Training Accuracy before training = " + crf.averageTokenAccuracy(lists[0]));
      System.out.println("Testing  Accuracy before training = " + crf.averageTokenAccuracy(lists[1]));
      savedCRF = crf;
      System.out.println("Training serialized crf.");
      crf.train(lists[0]);
      double preTrainAcc = crf.averageTokenAccuracy(lists[0]);
      double preTestAcc = crf.averageTokenAccuracy(lists[1]);
      System.out.println("Training Accuracy after training = " + preTrainAcc);
      System.out.println("Testing  Accuracy after training = " + preTestAcc);
      try {
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
        oos.writeObject(crf);
        oos.close();
      } catch (IOException e) {
        System.err.println("Exception writing file: " + e);
      }
      System.err.println("Wrote out CRF");
      System.err.println("CRF parameters. hyperbolicPriorSlope: " + crf.getUseHyperbolicPriorSlope() + ". hyperbolicPriorSharpness: " + crf.getUseHyperbolicPriorSharpness() + ". gaussianPriorVariance: " + crf.getGaussianPriorVariance());
      // And read it back in
      if (useSaved) {
        crf = null;
        try {
          ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
          crf = (CRF3) ois.readObject();
          ois.close();
        } catch (IOException e) {
          System.err.println("Exception reading file: " + e);
        } catch (ClassNotFoundException cnfe) {
          System.err.println("Cound not find class reading in object: " + cnfe);
        }
        System.err.println("Read in CRF.");
        crf = savedCRF;

        double postTrainAcc = crf.averageTokenAccuracy(lists[0]);
        double postTestAcc = crf.averageTokenAccuracy(lists[1]);
        System.out.println("Training Accuracy after saving = " + postTrainAcc);
        System.out.println("Testing  Accuracy after saving = " + postTestAcc);

        assertEquals(postTrainAcc, preTrainAcc, 0.0001);
        assertEquals(postTestAcc, preTestAcc, 0.0001);
      }
    }
  }


  public void testCostGradient()
  {
    doTestSpacePrediction(true);
  }


  public void testTrain()
  {
    doTestSpacePrediction(false);
  }


  public void testSerialization()
  {
    doTestSpacePrediction(false, true);
  }


  public static Test suite()
  {
    return new TestSuite(TestCRF3.class);
  }


  public static void main(String[] args)
  {
    //args = new String[] {"testCostGradient"};
    if (args.length > 0) {
      TestCRF3 t = new TestCRF3("testTrain");
      if (args[0].equals("testTrain"))
        t.testTrain();
      else if (args[0].equals("testSerialization"))
        t.testSerialization();
      else if (args[0].equals("testGetSetParameters"))
        t.testGetSetParameters();
      else if (args[0].equals("testCostGradient"))
        t.testCostGradient();
      else if (args[0].equals("testCost"))
        t.testCost(Integer.parseInt(args[1]));
      else {
        System.err.println("Unrecognized test.");
        System.exit(-1);
      }
    } else {
      junit.textui.TestRunner.run(suite());
    }
  }

}
