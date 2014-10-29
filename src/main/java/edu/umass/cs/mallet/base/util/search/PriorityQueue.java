package edu.umass.cs.mallet.base.util.search;

/**
 * Created by IntelliJ IDEA.
 * User: pereira
 * Date: Jun 18, 2005
 * Time: 7:46:46 PM
 *
 * Interface representing the basic methods for a priority queue.
 */
public interface PriorityQueue {
  /**
   * Insert element <code>e</code> into the queue.
   * @param e the element to insert
   */
  public void insert(QueueElement e);
  /**
   * The current size of the queue.
   * @return current size
   */
  public int size();
  /**
   * Return the top element of the queue.
   * @return top element of the queue
   */
  public QueueElement min();
  /**
   * Remove the top element of the queue.
   * @return the element removed
   */
  public QueueElement extractMin();
  /**
   * Lower the priority of queue element <code>e</code> to <code>priorrity</code>.
   * The element's position in the queue is adjusted as needed.
   * <code>IllegalArgumentException</code>s are thrown if the element is not in the queue or
   * if the new priority value is greater than the old value.
   * @param e the element that has been changed
   * @param priority the new priority
   */
  public void decreaseKey(QueueElement e, double priority);
  /**
   * Does the queue contain an element?
   * @param e the element
   * @return whether the queue contains the element
   */
  public boolean contains(QueueElement e);
}
