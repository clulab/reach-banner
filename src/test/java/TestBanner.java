
import banner.BannerWrapper;
import banner.tagging.Mention;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Unit test for banner
 * User: mihais
 * Date: 10/29/14
 */
public class TestBanner {
  @Test
  public void testBanner() {
    BannerWrapper banner = new BannerWrapper();

    List<Mention> mentions = banner.tag("Co-immunoprecipitation analysis confirmed that Bis interacted with Bcl-2 in vivo.");
    for(Mention m: mentions) {
      System.out.println("\tMENTION: " + m.getText() + " " + m.getType() + " " + m.getStartChar() + " " + m.getEndChar());
      assertEquals(m.getText(), "Bcl-2");
      assertEquals(m.getStartChar(), 67);
      assertEquals(m.getEndChar(), 72);
    }
  }
}
