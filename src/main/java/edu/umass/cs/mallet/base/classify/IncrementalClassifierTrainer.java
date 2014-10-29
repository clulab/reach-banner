package edu.umass.cs.mallet.base.classify;

import edu.umass.cs.mallet.base.types.InstanceList;


/**
 * Adds the notion of incremental training to a ClassifierTrainer, through the
 * availability of incrementalTrain() methods, which
 * parallel the train() methods.
 * <p>
 * A train method on an incrmental trainer behaves exactly as the train
 * method on a non incremental trainer.  Train() is stateless; all calls
 * to train() are independent of each other.
 * For incremental training, the user should call only the incrementalTrain()
 * methods, which maintain state between calls.
 *
 *
 */
public abstract class IncrementalClassifierTrainer extends ClassifierTrainer {
    /** Return a new classifier tuned from an instanceList
            @param trainingSet examples used to set parameters.
    */
    public Classifier incrementalTrain (InstanceList trainingSet)
    {
        return this.incrementalTrain (trainingSet, null);
    }

    /** Return a new classifier tuned using  two arguments.
            @param trainingSet examples used to set parameters.
            @param validationSet examples used to tune meta-parameters.  May be null.
    */
    public Classifier incrementalTrain (InstanceList trainingSet,
                                        InstanceList validationSet)
    {
        return this.incrementalTrain (trainingSet, validationSet, null);
    }


    /** Return a new classifier tuned using  three arguments.
            @param trainingSet examples used to set parameters.
            @param validationSet examples used to tune meta-parameters.  May be null.
            @param testSet examples not examined at all for training, but passed on to diagnostic routines.  May be null.
    */
    public Classifier incrementalTrain (InstanceList trainingSet,
                                        InstanceList validationSet,
                                        InstanceList testSet)
    {
        return this.incrementalTrain (trainingSet, validationSet, testSet, null, null);
    }

    /** Return a new classifier tuned using  four arguments.
            @param trainingSet examples used to set parameters.
            @param validationSet examples used to tune meta-parameters.  May be null.
            @param testSet examples not examined at all for training, but passed on to diagnostic routines.  May be null.
            @param evaluator May be null
    */

    public Classifier incrementalTrain (InstanceList trainingSet,
                                        InstanceList validationSet,
                                        InstanceList testSet,
                                        ClassifierEvaluating evaluator)
    {
        return this.incrementalTrain (trainingSet, validationSet, testSet, evaluator, null);
    }



    /** Return a new classifier tuned using the five arguments.
            @param trainingSet examples used to set parameters.
            @param validationSet examples used to tune meta-parameters.  May be null.
            @param testSet examples not examined at all for training, but passed on to diagnostic routines.  May be null.
            @param evaluator May be null
            @param initialClassifier training process may start from here.  The parameters of the initialClassifier are not modified.  May be null.
    */
    public abstract Classifier incrementalTrain (InstanceList trainingSet,
                                                 InstanceList validationSet,
                                                 InstanceList testSet,
                                                 ClassifierEvaluating evaluator,
                                                 Classifier initialClassifier);


    /**
     *  Throw away the internal state of the trainer as set by incrementalTrain().
     *  Incremental trainers must be explicitly reset between a call
     *  of incrementalTrain() and a call to train().
     */
    public abstract void reset();

}
