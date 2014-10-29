/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */





package edu.umass.cs.mallet.base.classify;

import edu.umass.cs.mallet.base.classify.Classifier;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.InstanceList;
import edu.umass.cs.mallet.base.types.MatrixOps;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.Label;
import edu.umass.cs.mallet.base.types.LabelAlphabet;
import edu.umass.cs.mallet.base.types.FeatureVector;
import edu.umass.cs.mallet.base.types.RankedFeatureVector;
import edu.umass.cs.mallet.base.types.Labeling;
import edu.umass.cs.mallet.base.types.LabelVector;
import edu.umass.cs.mallet.base.types.Vector;
import edu.umass.cs.mallet.base.types.FeatureSelection;
import edu.umass.cs.mallet.base.types.FeatureInducer;
import edu.umass.cs.mallet.base.types.ExpGain;
import edu.umass.cs.mallet.base.types.GradientGain;
import edu.umass.cs.mallet.base.types.InfoGain;
import edu.umass.cs.mallet.base.util.MalletLogger;
import edu.umass.cs.mallet.base.util.Maths;
import edu.umass.cs.mallet.base.maximize.Maximizable;
import edu.umass.cs.mallet.base.maximize.Maximizer;
import edu.umass.cs.mallet.base.maximize.tests.*;
import edu.umass.cs.mallet.base.maximize.LimitedMemoryBFGS;
import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.util.CommandOption;
import edu.umass.cs.mallet.base.util.MalletProgressMessageLogger;

import java.util.logging.*;
import java.util.*;
import java.io.*;

// Does not currently handle instances that are labeled with distributions
// instead of a single label.
/**
 * The trainer for a Maximum Entropy classifier.
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

public class MaxEntTrainer extends ClassifierTrainer implements Boostable, Serializable //implements CommandOption.ListProviding
{
	private static Logger logger = MalletLogger.getLogger(MaxEntTrainer.class.getName());
	private static Logger progressLogger = MalletProgressMessageLogger.getLogger(MaxEntTrainer.class.getName()+"-pl");

	int numGetValueCalls = 0;
	int numGetValueGradientCalls = 0;
	int numIterations = 10;

  public static final String EXP_GAIN = "exp";
  public static final String GRADIENT_GAIN = "grad";
  public static final String INFORMATION_GAIN = "info";

	// xxx Why does TestMaximizable fail when this variance is very small?
	static final double DEFAULT_GAUSSIAN_PRIOR_VARIANCE = 1;
	static final double DEFAULT_HYPERBOLIC_PRIOR_SLOPE = 0.2;
	static final double DEFAULT_HYPERBOLIC_PRIOR_SHARPNESS = 10.0;
	static final Class DEFAULT_MAXIMIZER_CLASS = LimitedMemoryBFGS.class;

    // CPAL
    boolean usingMultiConditionalTraining = false;
    boolean usingHyperbolicPrior = false;
	double gaussianPriorVariance = DEFAULT_GAUSSIAN_PRIOR_VARIANCE;
	double hyperbolicPriorSlope = DEFAULT_HYPERBOLIC_PRIOR_SLOPE;
	double hyperbolicPriorSharpness = DEFAULT_HYPERBOLIC_PRIOR_SHARPNESS;
	Class maximizerClass = DEFAULT_MAXIMIZER_CLASS;

    // CPAL
    static CommandOption.Boolean usingMultiConditionalTrainingOption =
    new CommandOption.Boolean (MaxEntTrainer.class, "useMCTraining", "true|false", false, false,
                                                        "Use MultiConditional Training", null);
    static CommandOption.Boolean usingHyperbolicPriorOption =
	new CommandOption.Boolean (MaxEntTrainer.class, "useHyperbolicPrior", "true|false", false, false,
														 "Use hyperbolic (close to L1 penalty) prior over parameters", null);
	static CommandOption.Double gaussianPriorVarianceOption =
	new CommandOption.Double (MaxEntTrainer.class, "gaussianPriorVariance", "FLOAT", true, 10.0,
														"Variance of the gaussian prior over parameters", null);
	static CommandOption.Double hyperbolicPriorSlopeOption =
	new CommandOption.Double (MaxEntTrainer.class, "hyperbolicPriorSlope", "FLOAT", true, 0.2,
														"Slope of the (L1 penalty) hyperbolic prior over parameters", null);
	static CommandOption.Double hyperbolicPriorSharpnessOption =
	new CommandOption.Double (MaxEntTrainer.class, "hyperbolicPriorSharpness", "FLOAT", true, 10.0,
														"Sharpness of the (L1 penalty) hyperbolic prior over parameters", null);

	static final CommandOption.List commandOptions =
	new CommandOption.List (
		"Maximum Entropy Classifier",
		new CommandOption[] {
			usingHyperbolicPriorOption,
			gaussianPriorVarianceOption,
			hyperbolicPriorSlopeOption,
			hyperbolicPriorSharpnessOption,
            usingMultiConditionalTrainingOption,   // CPAL
        });

	public static CommandOption.List getCommandOptionList ()
	{
		return commandOptions;
	}

	/*
	public MaxEntTrainer(Maximizer.ByGradient maximizer)
	{
		this.maximizerByGradient = maximizer;
		this.usingHyperbolicPrior = false;
	}
	*/

	public MaxEntTrainer (CommandOption.List col)
	{
		this.usingHyperbolicPrior = usingHyperbolicPriorOption.value;
		this.gaussianPriorVariance = gaussianPriorVarianceOption.value;
		this.hyperbolicPriorSlope = hyperbolicPriorSlopeOption.value;
		this.hyperbolicPriorSharpness = hyperbolicPriorSharpnessOption.value;
        this.usingMultiConditionalTraining = usingMultiConditionalTrainingOption.value;
    }

	public MaxEntTrainer ()
	{
		this (false);
	}

	public MaxEntTrainer (boolean useHyperbolicPrior)
	{
		this.usingHyperbolicPrior = useHyperbolicPrior;
	}

    /** Constructs a trainer with a parameter to avoid overtraining.  1.0 is
     * usually a reasonable default value. */
	public MaxEntTrainer (double gaussianPriorVariance)
	{
		this.usingHyperbolicPrior = false;
		this.gaussianPriorVariance = gaussianPriorVariance;
	}

    // CPAL - added this to do MultiConditionalTraining
    public MaxEntTrainer (double gaussianPriorVariance, boolean useMultiConditionalTraining )
    {
        this.usingHyperbolicPrior = false;
        this.usingMultiConditionalTraining = useMultiConditionalTraining;
        this.gaussianPriorVariance = gaussianPriorVariance;
    }

    public MaxEntTrainer (double hyperbolicPriorSlope,
												double hyperbolicPriorSharpness)
	{
		this.usingHyperbolicPrior = true;
		this.hyperbolicPriorSlope = hyperbolicPriorSlope;
		this.hyperbolicPriorSharpness = hyperbolicPriorSharpness;
	}

	public Maximizable.ByGradient getMaximizableTrainer (InstanceList ilist)
	{
		if (ilist == null)
			return new MaximizableTrainer ();
		return new MaximizableTrainer (ilist, null);
	}

  /**
   * Specifies the maximum number of iterations to run during a single call
   * to <code>train</code> or <code>trainWithFeatureInduction</code>.  Not
   * currently functional.
   * @return This trainer
   */
  // XXX Since we maximize before using numIterations, this doesn't work.
  // Is that a bug?  If so, should the default numIterations be higher?
	public MaxEntTrainer setNumIterations (int i)
	{
		numIterations = i;
		return this;
	}

	public MaxEntTrainer setUseHyperbolicPrior (boolean useHyperbolicPrior)
	{
		this.usingHyperbolicPrior = useHyperbolicPrior;
		return this;
	}

    /**
     * Sets a parameter to prevent overtraining.  A smaller variance for the prior
     * means that feature weights are expected to hover closer to 0, so extra
     * evidence is required to set a higher weight.
     * @return This trainer
     */
	public MaxEntTrainer setGaussianPriorVariance (double gaussianPriorVariance)
	{
		this.usingHyperbolicPrior = false;
		this.gaussianPriorVariance = gaussianPriorVariance;
		return this;
	}

	public MaxEntTrainer setHyperbolicPriorSlope(double hyperbolicPriorSlope)
	{
		this.usingHyperbolicPrior = true;
		this.hyperbolicPriorSlope = hyperbolicPriorSlope;

		return this;
	}

	public MaxEntTrainer setHyperbolicPriorSharpness (double hyperbolicPriorSharpness)
	{
		this.usingHyperbolicPrior = true;
		this.hyperbolicPriorSharpness = hyperbolicPriorSharpness;

		return this;
	}


	public Classifier train (InstanceList trainingSet,
													 InstanceList validationSet,
													 InstanceList testSet,
													 ClassifierEvaluating evaluator,
													 Classifier initialClassifier)
	{
		logger.fine ("trainingSet.size() = "+trainingSet.size());
		MaximizableTrainer mt = new MaximizableTrainer (trainingSet, (MaxEnt)initialClassifier);
		Maximizer.ByGradient maximizer = new LimitedMemoryBFGS();
		maximizer.maximize (mt); // XXX given the loop below, this seems wrong.

		logger.info("MaxEnt ngetValueCalls:"+getValueCalls()+"\nMaxEnt ngetValueGradientCalls:"+getValueGradientCalls());
//		boolean converged;
//
//	 	for (int i = 0; i < numIterations; i++) {
//			converged = maximizer.maximize (mt, 1);
//			if (converged)
//			 	break;
//			else if (evaluator != null)
//			 	if (!evaluator.evaluate (mt.getClassifier(), converged, i, mt.getValue(),
//				 												 trainingSet, validationSet, testSet))
//				 	break;
//		}
//		TestMaximizable.testValueAndGradient (mt);
		progressLogger.info("\n"); //  progess messages are on one line; move on.
		return mt.getClassifier ();
	}

  /**
   * <p>Trains a maximum entropy model using feature selection and feature induction
   * (adding conjunctions of features as new features).</p>
   *
   * @param trainingData A list of <code>Instance</code>s whose <code>data</code>
   * fields are binary, augmentable <code>FeatureVector</code>s.
   * and whose <code>target</code> fields are <code>Label</code>s.
   * @param validationData [not currently used] As <code>trainingData</code>,
   * or <code>null</code>.
   * @param testingData As <code>trainingData</code>, or <code>null</code>.
   * @param evaluator The evaluator to track training progress and decide whether
   * to continue, or <code>null</code>.
   * @param totalIterations The maximum total number of training iterations,
   * including those taken during feature induction.
   * @param numIterationsBetweenFeatureInductions How many iterations to train
   * between one round of feature induction and the next; this should usually
   * be fairly small, like 5 or 10, to avoid overfitting with current features.
   * @param numFeatureInductions How many rounds of feature induction to run
   * before beginning normal training.
   * @param numFeaturesPerFeatureInduction The maximum number of features to
   * choose during each round of featureInduction.
   *
   * @return The trained <code>MaxEnt</code> classifier
   */
  // added - cjmaloof@linc.cis.upenn.edu
  public Classifier trainWithFeatureInduction (InstanceList trainingData,
                                               InstanceList validationData,
                                               InstanceList testingData,
                                               ClassifierEvaluating evaluator,

                                               int totalIterations,
                                               int numIterationsBetweenFeatureInductions,
                                               int numFeatureInductions,
                                               int numFeaturesPerFeatureInduction) {

    return trainWithFeatureInduction (trainingData,
                                      validationData,
                                      testingData,
                                      evaluator,
                                      null,
                                      totalIterations,
                                      numIterationsBetweenFeatureInductions,
                                      numFeatureInductions,
                                      numFeaturesPerFeatureInduction,
                                      EXP_GAIN);
  }

  /**
   * <p>Like the other version of <code>trainWithFeatureInduction</code>, but
   * allows some default options to be changed.</p>
   *
   * @param maxent An initial partially-trained classifier (default <code>null</code>).
   * This classifier may be modified during training.
   * @param gainName The estimate of gain (log-likelihood increase) we want our chosen
   * features to maximize.
   * Should be one of <code>MaxEntTrainer.EXP_GAIN</code>,
   * <code>MaxEntTrainer.GRADIENT_GAIN</code>, or
   * <code>MaxEntTrainer.INFORMATION_GAIN</code> (default <code>EXP_GAIN</code>).
   *
   * @return The trained <code>MaxEnt</code> classifier
   */
  public Classifier trainWithFeatureInduction (InstanceList trainingData,
                                               InstanceList validationData,
                                               InstanceList testingData,
                                               ClassifierEvaluating evaluator,
                                               MaxEnt maxent,

                                               int totalIterations,
                                               int numIterationsBetweenFeatureInductions,
                                               int numFeatureInductions,
                                               int numFeaturesPerFeatureInduction,
                                               String gainName) {

    // XXX This ought to be a parameter, except that setting it to true can
    // crash training ("Jump too small").
    boolean saveParametersDuringFI = false;

    Alphabet inputAlphabet = trainingData.getDataAlphabet();
    Alphabet outputAlphabet = trainingData.getTargetAlphabet();

    if (maxent == null)
      maxent = new MaxEnt(trainingData.getPipe(),
                          new double[(1+inputAlphabet.size()) * outputAlphabet.size()]);

		int trainingIteration = 0;
		int numLabels = outputAlphabet.size();

    // Initialize feature selection
		FeatureSelection globalFS = trainingData.getFeatureSelection();
		if (globalFS == null) {
			// Mask out all features; some will be added later by FeatureInducer.induceFeaturesFor(.)
			globalFS = new FeatureSelection (trainingData.getDataAlphabet());
			trainingData.setFeatureSelection (globalFS);
		}
		if (validationData != null) validationData.setFeatureSelection (globalFS);
		if (testingData != null) testingData.setFeatureSelection (globalFS);
    maxent = new MaxEnt(maxent.getInstancePipe(), maxent.getParameters(), globalFS);

    // Run feature induction
    for (int featureInductionIteration = 0;
         featureInductionIteration < numFeatureInductions;
         featureInductionIteration++) {

      // Print out some feature information
			logger.info ("Feature induction iteration "+featureInductionIteration);

			// Train the model a little bit.  We don't care whether it converges; we
      // execute all feature induction iterations no matter what.
			if (featureInductionIteration != 0) {
				// Don't train until we have added some features
        setNumIterations(numIterationsBetweenFeatureInductions);
				maxent = (MaxEnt)this.train (trainingData, validationData, testingData, evaluator,
                                     maxent);
      }
			trainingIteration += numIterationsBetweenFeatureInductions;

			logger.info ("Starting feature induction with "+(1+inputAlphabet.size())+
                   " features over "+numLabels+" labels.");

			// Create the list of error tokens
			InstanceList errorInstances = new InstanceList (trainingData.getDataAlphabet(),
                                                      trainingData.getTargetAlphabet());

			// This errorInstances.featureSelection will get examined by FeatureInducer,
			// so it can know how to add "new" singleton features
			errorInstances.setFeatureSelection (globalFS);
			List errorLabelVectors = new ArrayList();    // these are length-1 vectors
      for (int i = 0; i < trainingData.size(); i++) {
				Instance instance = trainingData.getInstance(i);
				FeatureVector inputVector = (FeatureVector) instance.getData();
				Label trueLabel = (Label) instance.getTarget();

        // Having trained using just the current features, see how we classify
        // the training data now.
        Classification classification = maxent.classify(instance);
        if (!classification.bestLabelIsCorrect()) {
          errorInstances.add(inputVector, trueLabel, null, null);
          errorLabelVectors.add(classification.getLabelVector());
        }
      }
      logger.info ("Error instance list size = "+errorInstances.size());
      int s = errorLabelVectors.size();

      LabelVector[] lvs = new LabelVector[s];
      for (int i = 0; i < s; i++) {
        lvs[i] = (LabelVector)errorLabelVectors.get(i);
      }

      RankedFeatureVector.Factory gainFactory = null;
      if (gainName.equals (EXP_GAIN))
        gainFactory = new ExpGain.Factory (lvs, gaussianPriorVariance);
      else if (gainName.equals(GRADIENT_GAIN))
        gainFactory =	new GradientGain.Factory (lvs);
      else if (gainName.equals(INFORMATION_GAIN))
        gainFactory =	new InfoGain.Factory ();
      else
        throw new IllegalArgumentException("Unsupported gain name: "+gainName);

      FeatureInducer klfi =
        new FeatureInducer (gainFactory,
                            errorInstances,
                            numFeaturesPerFeatureInduction,
                            2*numFeaturesPerFeatureInduction,
                            2*numFeaturesPerFeatureInduction);

      // Note that this adds features globally, but not on a per-transition basis
      klfi.induceFeaturesFor (trainingData, false, false);
      if (testingData != null) klfi.induceFeaturesFor (testingData, false, false);
      logger.info ("MaxEnt FeatureSelection now includes "+globalFS.cardinality()+" features");
      klfi = null;

      double[] newParameters = new double[(1+inputAlphabet.size()) * outputAlphabet.size()];

      // XXX (Executing this block often causes an error during training; I don't know why.)
      if (saveParametersDuringFI) {
        // Keep current parameter values
        // XXX This relies on the implementation detail that the most recent features
        // added to an Alphabet get the highest indices.

        // Count parameters per output label
        int oldParamCount = maxent.parameters.length / outputAlphabet.size();
        int newParamCount = 1+inputAlphabet.size();
        // Copy params into the proper locations
        for (int i=0; i<outputAlphabet.size(); i++) {
          System.arraycopy(maxent.parameters, i*oldParamCount,
                           newParameters, i*newParamCount,
                           oldParamCount);
        }
        for (int i=0; i<oldParamCount; i++)
          if (maxent.parameters[i] != newParameters[i]) {
            System.out.println(maxent.parameters[i]+" "+newParameters[i]);
            System.exit(0);
          }
      }

      maxent.parameters = newParameters;
      maxent.defaultFeatureIndex = inputAlphabet.size();
    }

    // Finished feature induction
    logger.info("Ended with "+globalFS.cardinality()+" features.");
    setNumIterations(totalIterations - trainingIteration);
    return this.train (trainingData, validationData, testingData,
                       evaluator, maxent);
  }


    // XXX Should these really be public?  Why?
    /** Counts how many times this trainer has computed the gradient of the
     * log probability of training labels. */
	public int getValueGradientCalls() {return numGetValueGradientCalls;}
    /** Counts how many times this trainer has computed the
     * log probability of training labels. */
	public int getValueCalls() {return numGetValueCalls;}
//	public int getIterations() {return maximizerByGradient.getIterations();}

	public String toString()
	{
		return "MaxEntTrainer"
		//	+ "("+maximizerClass.getName()+") "
		    + ",numIterations=" + numIterations
			+ (usingHyperbolicPrior
				 ? (",hyperbolicPriorSlope="+hyperbolicPriorSlope+
						",hyperbolicPriorSharpness="+hyperbolicPriorSharpness)
				 : (",gaussianPriorVariance="+gaussianPriorVariance));
	}



  // A private inner class that wraps up a MaxEnt classifier and its training data.
	// The result is a maximize.Maximizable function.
	private class MaximizableTrainer implements Maximizable.ByGradient
	{
		double[] parameters, constraints, cachedGradient;
		MaxEnt theClassifier;
		InstanceList trainingList;
		// The expectations are (temporarily) stored in the cachedGradient
		double cachedValue;
		boolean cachedValueStale;
		boolean cachedGradientStale;
		int numLabels;
		int numFeatures;
		int defaultFeatureIndex;						// just for clarity
		FeatureSelection featureSelection;
		FeatureSelection[] perLabelFeatureSelection;

		public MaximizableTrainer (){}

		public MaximizableTrainer (InstanceList ilist, MaxEnt initialClassifier)
		{
			this.trainingList = ilist;
			Alphabet fd = ilist.getDataAlphabet();
			LabelAlphabet ld = (LabelAlphabet) ilist.getTargetAlphabet();
			// Don't fd.stopGrowth, because someone might want to do feature induction
			ld.stopGrowth();
			// Add one feature for the "default feature".
			this.numLabels = ld.size();
			this.numFeatures = fd.size() + 1;
			this.defaultFeatureIndex = numFeatures-1;
			this.parameters = new double [numLabels * numFeatures];
			this.constraints = new double [numLabels * numFeatures];
			this.cachedGradient = new double [numLabels * numFeatures];
			Arrays.fill (parameters, 0.0);
			Arrays.fill (constraints, 0.0);
			Arrays.fill (cachedGradient, 0.0);
			this.featureSelection = ilist.getFeatureSelection();
			this.perLabelFeatureSelection = ilist.getPerLabelFeatureSelection();
			// Add the default feature index to the selection
			if (featureSelection != null)
				featureSelection.add (defaultFeatureIndex);
			if (perLabelFeatureSelection != null)
				for (int i = 0; i < perLabelFeatureSelection.length; i++)
					perLabelFeatureSelection[i].add (defaultFeatureIndex);
			// xxx Later change this to allow both to be set, but select which one to use by a boolean flag?
			assert (featureSelection == null || perLabelFeatureSelection == null);
			if (initialClassifier != null) {

        this.theClassifier = initialClassifier;
        this.parameters = theClassifier.parameters;
        this.featureSelection = theClassifier.featureSelection;
        this.perLabelFeatureSelection = theClassifier.perClassFeatureSelection;
        this.defaultFeatureIndex = theClassifier.defaultFeatureIndex;
				assert (initialClassifier.getInstancePipe() == ilist.getPipe());
			}
			else if (this.theClassifier == null) {
				this.theClassifier = new MaxEnt (ilist.getPipe(), parameters, featureSelection, perLabelFeatureSelection);
			}
			cachedValueStale = true;
			cachedGradientStale = true;

			// Initialize the constraints
			InstanceList.Iterator iter = trainingList.iterator ();
			logger.fine("Number of instances in training list = " + trainingList.size());
			while (iter.hasNext()) {
				double instanceWeight = iter.getInstanceWeight();
				Instance inst = iter.nextInstance();
				Labeling labeling = inst.getLabeling ();
				//logger.fine ("Instance "+ii+" labeling="+labeling);
				FeatureVector fv = (FeatureVector) inst.getData ();
				Alphabet fdict = fv.getAlphabet();
				assert (fv.getAlphabet() == fd);
				int li = labeling.getBestIndex();
				MatrixOps.rowPlusEquals (constraints, numFeatures, li, fv, instanceWeight);
				// For the default feature, whose weight is 1.0
				assert(!Double.isNaN(instanceWeight)) : "instanceWeight is NaN";
				assert(!Double.isNaN(li)) : "bestIndex is NaN";
				boolean hasNaN = false;
				for(int i = 0; i < fv.numLocations(); i++) {
					if(Double.isNaN(fv.valueAtLocation(i))) {
						logger.info("NaN for feature " + fdict.lookupObject(fv.indexAtLocation(i)).toString());
						hasNaN = true;
					}
				}
				if(hasNaN)
					logger.info("NaN in instance: " + inst.getName());

				constraints[li*numFeatures + defaultFeatureIndex] += 1.0 * instanceWeight;
			}
			//TestMaximizable.testValueAndGradientCurrentParameters (this);
		}

		public MaxEnt getClassifier () { return theClassifier; }

		public double getParameter (int index) {
			return parameters[index];
		}

		public void setParameter (int index, double v) {
			cachedValueStale = true;
			cachedGradientStale = true;
			parameters[index] = v;
		}

		public int getNumParameters() {
			return parameters.length;
		}

		public void getParameters (double[] buff) {
			if (buff == null || buff.length != parameters.length)
				buff = new double [parameters.length];
			System.arraycopy (parameters, 0, buff, 0, parameters.length);
		}

		public void setParameters (double [] buff) {
			assert (buff != null);
			cachedValueStale = true;
			cachedGradientStale = true;
			if (buff.length != parameters.length)
				parameters = new double[buff.length];
			System.arraycopy (buff, 0, parameters, 0, buff.length);
		}


		// log probability of the training labels
		public double getValue ()
		{
			if (cachedValueStale) {
				numGetValueCalls++;
				cachedValue = 0;
				// We'll store the expectation values in "cachedGradient" for now
				cachedGradientStale = true;
				MatrixOps.setAll (cachedGradient, 0.0);
				// Incorporate likelihood of data
				double[] scores = new double[trainingList.getTargetAlphabet().size()];
				double value = 0.0;
                //System.out.println("I Now "+inputAlphabet.size()+" regular features.");
				InstanceList.Iterator iter = trainingList.iterator();
				int ii=0;
				while (iter.hasNext()) {
					ii++;
					double instanceWeight = iter.getInstanceWeight();
					Instance instance = iter.nextInstance();
					Labeling labeling = instance.getLabeling ();
                    //System.out.println("L Now "+inputAlphabet.size()+" regular features.");

					this.theClassifier.getClassificationScores (instance, scores);
					FeatureVector fv = (FeatureVector) instance.getData ();
					int li = labeling.getBestIndex();
					value = - (instanceWeight * Math.log (scores[li]));
					if(Double.isNaN(value)) {
						logger.fine ("MaxEntTrainer: Instance " + instance.getName() +
												 "has NaN value. log(scores)= " + Math.log(scores[li]) +
												 " scores = " + scores[li] +
												 " has instance weight = " + instanceWeight);

					}
					if (Double.isInfinite(value)) {
						logger.warning ("Instance "+instance.getSource() + " has infinite value; skipping value and gradient");
						cachedValue -= value;
						cachedValueStale = false;
						return -value;
//						continue;
					}
					cachedValue += value;
                    // CPAL - this is a loop over classes and their scores
                    //      - we compute the gradient by taking the dot product of the feature value
                    //        and the probability of the class
                    for (int si = 0; si < scores.length; si++) {
						if (scores[si] == 0) continue;
						assert (!Double.isInfinite(scores[si]));
                        // CPAL - accumulating the current classifiers expectation of the feature
                        // vector counts for this class label
                        // Current classifier has expectation over class label, not over feature vector
                        MatrixOps.rowPlusEquals (cachedGradient, numFeatures,
						si, fv, -instanceWeight * scores[si]);
						cachedGradient[numFeatures*si + defaultFeatureIndex] += (-instanceWeight * scores[si]);
					}

                    // CPAL - if we wish to do multiconditional training we need another term for this accumulated
                    //        expectation
                    if (usingMultiConditionalTraining) {
                        // need something analogous to this
                        // this.theClassifier.getClassificationScores (instance, scores);
                        // this.theClassifier.getFeatureDistributions (instance,
                        // Note: li is the "label" for this instance

                        for (int fi = 0; fi < numFeatures; fi++) {

                            //if(parameters[numFeatures*li + fi] != 0) {
                            // MatrixOps.rowPlusEquals(cachedGradient, numFeatures,li,fv,))
                                cachedGradient[numFeatures*li + fi] += (-instanceWeight * Math.exp(parameters[numFeatures*li + fi]));
                            //    }
                        }

                    }
                }
					//logger.info ("-Expectations:"); cachedGradient.print();
				// Incorporate prior on parameters
				if (usingHyperbolicPrior) {
					for (int li = 0; li < numLabels; li++)
						for (int fi = 0; fi < numFeatures; fi++)
							cachedValue += (hyperbolicPriorSlope / hyperbolicPriorSharpness
														 * Math.log (Maths.cosh (hyperbolicPriorSharpness * parameters[li *numFeatures + fi])));
				} else {
					for (int li = 0; li < numLabels; li++)
						for (int fi = 0; fi < numFeatures; fi++) {
							double param = parameters[li*numFeatures + fi];
							cachedValue += param * param / (2 * gaussianPriorVariance);
						}
				}
				cachedValue *= -1.0; // MAXIMIZE, NOT MINIMIZE
				cachedValueStale = false;
				progressLogger.info ("Value (loglikelihood) = "+cachedValue);
			}
			return cachedValue;
		}

 // CPAL first get value, then gradient

        public void getValueGradient (double [] buffer)
		{
			// Gradient is (constraint - expectation - parameters/gaussianPriorVariance)
			if (cachedGradientStale) {
				numGetValueGradientCalls++;
				if (cachedValueStale)
					// This will fill in the cachedGradient with the "-expectation"
					getValue ();
                // cachedGradient contains the negative expectations
                // expectations are model expectations and constraints are
                // empirical expectations
                MatrixOps.plusEquals (cachedGradient, constraints);
                // CPAL - we need a second copy of the constraints
                if (usingMultiConditionalTraining){
                    MatrixOps.plusEquals(cachedGradient, constraints);
                }
                // Incorporate prior on parameters
				if (usingHyperbolicPrior) {
					throw new UnsupportedOperationException ("Hyperbolic prior not yet implemented.");
				}
				else {
					MatrixOps.plusEquals (cachedGradient, parameters,
																-1.0 / gaussianPriorVariance);
				}

				// A parameter may be set to -infinity by an external user.
				// We set gradient to 0 because the parameter's value can
				// never change anyway and it will mess up future calculations
				// on the matrix, such as norm().
				MatrixOps.substitute (cachedGradient, Double.NEGATIVE_INFINITY, 0.0);
				// Set to zero all the gradient dimensions that are not among the selected features
				if (perLabelFeatureSelection == null) {
					for (int labelIndex = 0; labelIndex < numLabels; labelIndex++)
						MatrixOps.rowSetAll (cachedGradient, numFeatures,
																 labelIndex, 0.0, featureSelection, false);
				} else {
					for (int labelIndex = 0; labelIndex < numLabels; labelIndex++)
						MatrixOps.rowSetAll (cachedGradient, numFeatures,
																 labelIndex, 0.0,
																 perLabelFeatureSelection[labelIndex], false);
				}
				cachedGradientStale = false;
			}
			assert (buffer != null && buffer.length == parameters.length);
			System.arraycopy (cachedGradient, 0, buffer, 0, cachedGradient.length);
		}
	}

}
