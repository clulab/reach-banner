package edu.umass.cs.mallet.base.util.tests;

import junit.framework.*;
import edu.umass.cs.mallet.base.util.search.*;

/**
 * Created by IntelliJ IDEA.
 * User: pereira
 * Date: Jun 18, 2005
 * Time: 11:19:36 PM
 * Test priority queues and their implementation.
 */
public class TestPriorityQueue extends TestCase {
  private static final int N = 100;
  private static class Item implements QueueElement {
    private int position;
    private double priority;
  private Item(double p) {
    priority = p;
  }
  public double getPriority() { return priority; };
  public void setPriority(double p) { priority = p; }
  public int getPosition() { return position; }
  public void setPosition(int p) { position = p; }
  }
  public TestPriorityQueue(String name) {
    super(name);
  }
  public void testAscending() {
    PriorityQueue q = new MinHeap(N);
    double p[] = new double[N];
    for (int i = 0; i < N; i++) {
      p[i] = i;
      Item e = new Item(i);
      q.insert(e);
    }
    int j = 0;
    double pr = Double.NEGATIVE_INFINITY;
    assertTrue("ascending size", q.size() == N);
    while (q.size() > 0) {
      assertTrue("ascending extract", j < N);
      QueueElement e = q.extractMin();
      assertTrue("ascending order", e.getPriority() > pr);
      assertEquals("ascending priority", e.getPriority(), p[j++], 1e-5);
      pr = e.getPriority();
    }
  }
  public void testDescending() {
    PriorityQueue q = new MinHeap(N);
    double p[] = new double[N];
    for (int i = 0; i < N; i++) {
      p[i] = i;
      Item e = new Item(N-i-1);
      q.insert(e);
    }
    int j = 0;
    double pr = Double.NEGATIVE_INFINITY;
    assertTrue("descending size", q.size() == N);
    while (q.size() > 0) {
      assertTrue("descending extract", j < N);
      QueueElement e = q.extractMin();
      assertTrue("descending order", e.getPriority() > pr);
      assertEquals("descending priority", e.getPriority(), p[j++], 1e-5);
      pr = e.getPriority();
    }
  }
  public static Test suite() {
    return new TestSuite(TestPriorityQueue.class);
  }
  public static void main(String[] args) {
    junit.textui.TestRunner.run(suite());
  }
}
