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
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.Alphabet;
import edu.umass.cs.mallet.base.types.FeatureVector;
import edu.umass.cs.mallet.base.types.Labeling;
import edu.umass.cs.mallet.base.types.LabelVector;
import edu.umass.cs.mallet.base.types.Multinomial;
import edu.umass.cs.mallet.base.types.FeatureSelection;
import edu.umass.cs.mallet.base.pipe.Pipe;

import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Class used to generate a NaiveBayes classifier from a set of training data.
 * In an Bayes classifier,
 *     the p(Classification|Data) = p(Data|Classification)p(Classification)/p(Data)
 * <p>
 *  To compute the likelihood: <br>
 *      p(Data|Classification) = p(d1,d2,..dn | Classification) <br>
 * Naive Bayes makes the assumption  that all of the data are conditionally
 * independent given the Classification: <br>
 *      p(d1,d2,...dn | Classification) = p(d1|Classification)p(d2|Classification)..
 * <p>
 * As with other classifiers in Mallet, NaiveBayes is implemented as two classes:
 * a trainer and a classifier.  The NaiveBayesTrainer produces estimates of the various
 * p(dn|Classifier) and contructs this class with those estimates.
 * <p>
 * A call to train() or incrementalTrain() produces a
 * {@link edu.umass.cs.mallet.base.classify.NaiveBayes} classifier that can
 * can be used to classify instances.  A call to incrementalTrain() does not throw
 * away the internal state of the trainer; subsequent calls to incrementalTrain()
 * train by extending the previous training set.
 * <p>
 * A NaiveBayesTrainer can be persisted using serialization.
 * @see NaiveBayes
 *  @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 *
 */
public class NaiveBayesTrainer extends IncrementalClassifierTrainer implements Boostable, Serializable
{
    // These function as default selections for the kind of Estimator used
	Multinomial.Estimator featureEstimator = new Multinomial.LaplaceEstimator();
	Multinomial.Estimator priorEstimator = new Multinomial.LaplaceEstimator();

    // Added to support incremental training.
    // These are the counts formed after NaiveBayes training.  Note that
    // these are *not* the estimates passed to the NaiveBayes classifier;
    // rather the estimates are formed from these counts.
    // we could break these five fields out into a inner class.
    Multinomial.Estimator[] me;
    Multinomial.Estimator pe;

    // If this style of incremental training is successful, the following members
    // should probably be moved up into ClassifierTrainer
    Pipe instancePipe;        // Needed to construct a new classifier
    Alphabet dataAlphabet;    // Extracted from InstanceList. Must be the same for all calls to incrementalTrain()
    Alphabet targetAlphabet; // Extracted from InstanceList. Must be the same for all calls to incrementalTrain

    /**
     *  Get the MultinomialEstimator instance used to specify the type of estimator
     *  for features.
     *
     * @return  estimator to be cloned on next call to train() or first call
     * to incrementalTrain()
     */
	public Multinomial.Estimator getFeatureMultinomialEstimator ()
	{
		return featureEstimator;
	}

    /**
     * Set the Multinomial Estimator used for features. The MulitnomialEstimator
     * is internally cloned and the clone is used to maintain the counts
     * that will be used to generate probability estimates
     * the next time train() or an initial incrementalTrain() is run.
     * Defaults to a Multinomial.LaplaceEstimator()
     * @param me to be cloned on next call to train() or first call
     * to incrementalTrain()
     */
	public void setFeatureMultinomialEstimator (Multinomial.Estimator me)
	{
        if (instancePipe != null)
            throw new IllegalStateException("Can't set after incrementalTrain() is called");
		featureEstimator = me;
	}

    /**
     *  Get the MultinomialEstimator instance used to specify the type of estimator
     *  for priors.
     *
     * @return  estimator to be cloned on next call to train() or first call
     * to incrementalTrain()
     */
 	public Multinomial.Estimator getPriorMultinomialEstimator ()
	{
		return priorEstimator;
	}
		
    /**
     * Set the Multinomial Estimator used for priors. The MulitnomialEstimator
     * is internally cloned and the clone is used to maintain the counts
     * that will be used to generate probability estimates
     * the next time train() or an initial incrementalTrain() is run.
     * Defaults to a Multinomial.LaplaceEstimator()
     * @param me to be cloned on next call to train() or first call
     * to incrementalTrain()
     */
    public void setPriorMultinomialEstimator (Multinomial.Estimator me)
	{
        if (instancePipe != null)
            throw new IllegalStateException("Can't set after incrementalTrain() is called");
		priorEstimator = me;
	}

    /**
     * clears the internal state of the trainer.
     * Called automatically at the end of train()
     */
    public void reset()
    {
        instancePipe = null;
        dataAlphabet = null;
        targetAlphabet = null;
        me = null;
        pe = null;
    }

    /**
     * Create a NaiveBayes classifier from a set of training data.
     * The trainer uses counts of each feature in an instance's feature vector
     * to provide an estimate of p(Labeling| feature).  The internal state
     * of the trainer is thrown away ( by a call to reset() ) when train() returns. Each
     * call to train() is completely independent of any other.
     * @param trainingList        The InstanceList to be used to train the classifier.
     * Within each instance the data slot is an instance of FeatureVector and the
     * target slot is an instance of Labeling
     * @param validationList      Currently unused
     * @param testSet             Currently unused
     * @param evaluator           Currently unused
     * @param initialClassifier   Currently unused
     * @return The NaiveBayes classifier as trained on the trainingList
     */
	public Classifier train (InstanceList trainingList,
                             InstanceList validationList,
                             InstanceList testSet,
                             ClassifierEvaluating evaluator,
						     Classifier initialClassifier)
    {
        if (instancePipe !=null)  {
            throw new IllegalStateException("Must call reset() between calls of incrementalTrain() and train()");
        }

        Classifier classifier = incrementalTrain(trainingList, validationList, testSet, evaluator, initialClassifier);

        // wipe trainer state
        reset();

        return classifier;
    }

    /**
     * Create a NaiveBayes classifier from a set of training data and the
     * previous state of the trainer.  Subsequent calls to incrementalTrain()
     * add to the state of the trainer.  An incremental training session
     * should consist only of calls to incrementalTrain() and have no
     * calls to train();     *
     * @param trainingList        The InstanceList to be used to train the classifier.
     * Within each instance the data slot is an instance of FeatureVector and the
     * target slot is an instance of Labeling
     * @param validationList      Currently unused
     * @param testSet             Currently unused
     * @param evaluator           Currently unused
     * @param initialClassifier   Currently unused
     * @return The NaiveBayes classifier as trained on the trainingList and the previous
     * trainingLists passed to incrementalTrain()
     */

    public Classifier incrementalTrain (InstanceList trainingList,
                                        InstanceList validationList,
                                        InstanceList testSet,
                                        ClassifierEvaluating evaluator,
                                        Classifier initialClassifier)
	{
		// FeatureSelection selectedFeatures = trainingList.getFeatureSelection();
		//if (selectedFeatures != null)
			// xxx Attend to FeatureSelection!!!
		//	throw new UnsupportedOperationException ("FeatureSelection not yet implemented.");


        if (instancePipe == null){
            // first call to incremntalTrain() in this instance of NaiveBayesTrainer.
            // Save arguments in members
		    instancePipe = trainingList.getPipe ();
            dataAlphabet = trainingList.getDataAlphabet();
            targetAlphabet = trainingList.getTargetAlphabet();

            int numLabels = targetAlphabet.size();

            //aah todo: this is wrong. should get type from featureEstimator instance.
            me = new Multinomial.LaplaceEstimator[numLabels];

            for (int i = 0; i < numLabels; i++) {
                Multinomial.Estimator mest = (Multinomial.Estimator)featureEstimator.clone ();
                mest.setAlphabet (dataAlphabet);
                me[i] = mest;
            }

            pe = (Multinomial.Estimator) priorEstimator.clone ();

        }else{
            // >1st call.  Train starting with counts accumlated from previous train() calls.
            // check that alphabets and pipe are the same
            // Should this be done with exceptions instead of asserts?  Java recommended
            // style would be to use exceptions.  However, other Mallet code uses assert
            // to check arguments, so..
            if (instancePipe != trainingList.getPipe())
                throw new IllegalArgumentException(
                      "Instance pipe differs from that used in previous call to incrementalTrain()");
            if (dataAlphabet != trainingList.getDataAlphabet())
                 throw new IllegalArgumentException(
                         "Data Alphabet differs from that used on previous call to incrementalTrain()");
            if (targetAlphabet != trainingList.getTargetAlphabet())
                throw new IllegalArgumentException(
                     "Target Alphabet differs from that used on previous call to incrementalTrain()");

            if (targetAlphabet.size() > me.length){
                // target alphabet grew. increase size of our multinomial array

                int targetAlphabetSize = targetAlphabet.size();

                // copy over old values
                Multinomial.Estimator[] newMe = new Multinomial.Estimator[targetAlphabetSize];
                System.arraycopy (me, 0, newMe, 0, me.length);

                // initialize new expanded space
                for (int i= me.length; i<targetAlphabetSize; i++){
                    Multinomial.Estimator mest = (Multinomial.Estimator)featureEstimator.clone ();
                    mest.setAlphabet (dataAlphabet);
                    newMe[i] = mest;
                }

                me = newMe;

            }
        }

        int numLabels = targetAlphabet.size();

		InstanceList.Iterator iter = trainingList.iterator();
		while (iter.hasNext()) {
			double instanceWeight = iter.getInstanceWeight();
			Instance inst = iter.nextInstance();
			Labeling labeling = inst.getLabeling ();
			FeatureVector fv = (FeatureVector) inst.getData (instancePipe);
			for (int lpos = 0; lpos < labeling.numLocations(); lpos++) {
				int li = labeling.indexAtLocation (lpos);
				double labelWeight = labeling.valueAtLocation (lpos);
				if (labelWeight == 0) continue;
				//System.out.println ("NaiveBayesTrainer me.increment "+ labelWeight * instanceWeight);
				me[li].increment (fv, labelWeight * instanceWeight);
				// This relies on labelWeight summing to 1 over all labels
				pe.increment (li, labelWeight * instanceWeight);
			}
		}
		Multinomial[] m = new Multinomial[numLabels];
		for (int li = 0; li < numLabels; li++) {
			//me[li].print (); // debugging
			m[li] = me[li].estimate();
		}

        // note that state is saved in member variables that will be added
        // to on next call to incrementalTrain()

		return new NaiveBayes (instancePipe, pe.estimate(), m);
	}

	public String toString()
	{
		return "NaiveBayesTrainer";
	}


  // Serialization
  // serialVersionUID is overriden to prevent innocuous changes in this
  // class from making the serialization mechanism think the external
  // format has changed.

  private static final long serialVersionUID = 1;
  private static final int CURRENT_SERIAL_VERSION = 1;

  private void writeObject(ObjectOutputStream out) throws IOException
  {
      out.writeInt(CURRENT_SERIAL_VERSION);

      //default selections for the kind of Estimator used
      out.writeObject(featureEstimator);
      out.writeObject(priorEstimator);

      // These are the counts formed after NaiveBayes training.
      out.writeObject(me);
      out.writeObject(pe);

      // pipe and alphabets
      out.writeObject(instancePipe);
      out.writeObject(dataAlphabet);
      out.writeObject(targetAlphabet);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    int version = in.readInt();
    if (version != CURRENT_SERIAL_VERSION)
      throw new ClassNotFoundException("Mismatched NaiveBayesTrainer versions: wanted " +
                                       CURRENT_SERIAL_VERSION + ", got " +
                                       version);

      //default selections for the kind of Estimator used
      featureEstimator = (Multinomial.Estimator) in.readObject();
      priorEstimator = (Multinomial.Estimator) in.readObject();

      // These are the counts formed after NaiveBayes training.
      me = (Multinomial.Estimator []) in.readObject();
      pe = (Multinomial.Estimator) in.readObject();

      // pipe and alphabets
      instancePipe = (Pipe) in.readObject();
      dataAlphabet = (Alphabet) in.readObject();
      targetAlphabet = (Alphabet) in.readObject();
  }
}
