/* Copyright (C) 2003 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */

/** Clusters a set of point via k-Means. The instances that are clustered are
    expected to be of the type FeatureVector.

    @author Jerod Weinman <A HREF="mailto:weinman@cs.umass.edu">weinman@cs.umass.edu</A>
*/

package edu.umass.cs.mallet.base.cluster;

import edu.umass.cs.mallet.base.cluster.Clustering;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.InstanceList;
import edu.umass.cs.mallet.base.types.Metric;
import edu.umass.cs.mallet.base.types.SparseVector;
import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.util.VectorStats;

import java.util.logging.*;
import java.lang.reflect.Method;
import java.lang.Class;
import java.lang.Double;
import java.lang.Integer;
import java.lang.IllegalArgumentException;
import java.util.Random;
import java.util.ArrayList;

public class KMeans extends Clusterer {
    
    // Stop after movement of means is less than this
    static double MEANS_TOLERANCE = 1e-2;

    // Maximum number of iterations
    static int MAX_ITER = 100;

    // Minimum fraction of points that move
    static double POINTS_TOLERANCE = .005;

    /** Treat an empty cluster as an error condition. */
    public static final int EMPTY_ERROR = 0;
    /** Drop an empty cluster */
    public static final int EMPTY_DROP = 1;
    /** Place the single instance furthest from the previous cluster mean */
    public static final int EMPTY_SINGLE = 2;
    
    Random randinator;
    Metric metric;
    int numClusters;
    int emptyAction;
    ArrayList clusterMeans;

    private static Logger logger =
	Logger.getLogger("edu.umass.cs.mallet.base.cluster.KMeans");

    /** Construct a KMeans object
     * 
     * @param instancePipe Pipe for the instances being clustered
     * @param numClusters  Number of clusters to use
     * @param metric       Metric object to measure instance distances
     * @param emptyAction  Specify what should happen when an empty cluster 
     *                     occurs
     */
    public KMeans( Pipe instancePipe, int numClusters, Metric metric, 
		   int emptyAction ) {

	super(instancePipe);

	if (emptyAction == EMPTY_SINGLE)
	    throw new UnsupportedOperationException("EMPTY_SINGLE not yet implemented");
	
	this.emptyAction = emptyAction;
	this.metric = metric;
	this.numClusters = numClusters;

	this.clusterMeans = new ArrayList(numClusters);
	this.randinator = new Random();

    }

    /** Construct a KMeans object
     * 
     * @param instancePipe Pipe for the instances being clustered
     * @param numClusters  Number of clusters to use
     * @param metric       Metric object to measure instance distances
     *
     * If an empty cluster occurs, it is considered an error.
     */
    public KMeans( Pipe instancePipe, int numClusters, Metric metric )
    {
	this( instancePipe, numClusters, metric, EMPTY_ERROR );
    }


    /** Cluster instances
     *
     * @param instances List of instances to cluster
     */
    public Clustering cluster( InstanceList instances )
    {

	assert( instances.getPipe() == this.instancePipe );

	// Initialize clusterMeans
	initializeMeansSample(instances);

	int clusterLabels[] = new int[instances.size()];
	ArrayList instanceClusters = new ArrayList(numClusters);
	int instClust;
	double instClustDist, instDist;
	double deltaMeans = Double.MAX_VALUE;
	double deltaPoints = (double)instances.size();
	int iterations = 0;
	SparseVector clusterMean;

	for (int c=0 ; c<numClusters ; c++ ) {
	    instanceClusters.add(c, new InstanceList(instancePipe));
	}

	logger.info("Entering KMeans iteration");
	
	while (deltaMeans > MEANS_TOLERANCE && 
	       iterations < MAX_ITER && 
	       deltaPoints > instances.size()*POINTS_TOLERANCE)
	{

	    iterations++;
	    deltaPoints = 0;

	    // For each instance, measure its distance to the current cluster
	    // means, and subsequently assign it to the closest cluster 
	    // by adding it to an corresponding instance list
	    // The mean of each cluster InstanceList is then updated.
	    for (int n=0 ; n<instances.size() ; n++ ) {

		instClust = 0;
		instClustDist = Double.MAX_VALUE;

		for (int c=0 ; c<numClusters ; c++ ) {
		    
		    instDist = metric.distance( (SparseVector)clusterMeans.get(c), 
						(SparseVector)instances.getInstance(n).getData(instancePipe) );

		    if (instDist < instClustDist)
		    {
			instClust = c;
			instClustDist = instDist;
		    }
		}
		// Add to closest cluster & label it such
		((InstanceList)instanceClusters.get(instClust)).add(instances.getInstance(n));
		
		if (clusterLabels[n]!=instClust)
		{
		    clusterLabels[n] = instClust;
		    deltaPoints++;
		}

	    }

	    deltaMeans = 0;
	    
	    for (int c=0 ; c<numClusters ; c++ ) {

		if (((InstanceList)instanceClusters.get(c)).size()>0) {
		    clusterMean = VectorStats.mean( (InstanceList)instanceClusters.get(c) );
		    
		    deltaMeans += metric.distance( (SparseVector)clusterMeans.get(c), clusterMean );
		    
		    clusterMeans.set( c, clusterMean);

		    instanceClusters.set(c, new InstanceList(instancePipe));

		} else {

		    logger.info("Empty cluster found.");

		    switch (emptyAction) 
		    {
		    case EMPTY_ERROR:
			return null;
		    case EMPTY_DROP:
			logger.fine("Removing cluster "+c);
			clusterMeans.remove(c);
			instanceClusters.remove(c);
			for (int n=0 ; n<instances.size() ; n++ ) {

			    assert ( clusterLabels[n] != c ) : 
				    "Cluster size is "+
				    ((InstanceList)instanceClusters.get(c)).size()+
				    "+ yet clusterLabels[n] is "+
				    clusterLabels[n];

			    if (clusterLabels[n]>c)
				clusterLabels[n]--;
			}

			numClusters--;
			c--; // <-- note this trickiness. bad style? maybe.
			     // it just means now that we've deleted the entry,
			     // we have to repeat the index to get the next entry.
			break;
		    case EMPTY_SINGLE:
			return null;
		    default:
			return null;
		    }
		}

	    }
	    
	    
	    logger.fine("Iter "+iterations+" deltaMeans = "+deltaMeans );
	}

	if (deltaMeans<= MEANS_TOLERANCE)
	    logger.info("KMeans converged with deltaMeans = "+deltaMeans);
	else if ( iterations>=MAX_ITER )
	    logger.info("Maximum number of iterations ("+MAX_ITER+") reached.");
	else if ( deltaPoints <= instances.size()*POINTS_TOLERANCE)
	    logger.info("Minimum number of points (np*"+POINTS_TOLERANCE+"="+
			(int)(instances.size()*POINTS_TOLERANCE)+
			") moved in last iteration. Saying converged.");
	
	
	return new Clustering( instances, numClusters, clusterLabels );
	    
    }

    /** Choose a random instance from an instance list without
     *  replacement as an initial mean for each of the clusters.
     *
     *  @param instances Observation space
     */
    private void initializeMeansSample(InstanceList instances ) {

	ArrayList samplePoints = new ArrayList(numClusters);
	Integer obs;

	while (samplePoints.size()<numClusters)
	{
	    obs = new Integer(randinator.nextInt( instances.size() ));

	    if (!samplePoints.contains(obs))
		samplePoints.add(obs);
	}

	for (int c=0 ; c<numClusters ; c++ )
	    clusterMeans.add( c, 
			      instances.getInstance( ((Integer)samplePoints.get(c)).intValue() ).getData() );


    }	
}
