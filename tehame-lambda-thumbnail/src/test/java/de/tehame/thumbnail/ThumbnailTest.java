package de.tehame.thumbnail;
import org.junit.Assert;
import org.junit.Test;

public class ThumbnailTest {

	@Test
	public void skaliere() {
		ThumbnailLambda t = new ThumbnailLambda();
		
		double srcHeight = 1080;
		double srcWidth = 1920;
		
		double scalar = t.calcMinScalar(srcHeight, 200, srcWidth, 200);
		
		// Bild ist breiter als hoch, deswegen wird 200 erwartet
		Assert.assertEquals(200d, t.scale(srcWidth, scalar), 0d);
		
		// 200 / 1920 = 0,104166666666666...
		// 1080 * 0,1041666666666667 = 112,5
		Assert.assertEquals(112d, t.scale(srcHeight, scalar), 0d);
	}
}
