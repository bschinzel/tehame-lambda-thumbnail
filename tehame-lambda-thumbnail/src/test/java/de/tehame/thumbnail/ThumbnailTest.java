package de.tehame.thumbnail;
import org.junit.Test;

public class ThumbnailTest {

	@Test
	public void someTest() {
		double MAX_HEIGHT = 200d;
		double MAX_WIDTH = 200d;
		
		int srcHeight = 3435;
		int srcWidth = 4353;
		
		int destHeight = 0;
		int destWidth = 0;
		
        if (srcHeight > srcWidth) {
        	destHeight = new Double(MAX_HEIGHT).intValue();
        	destWidth = new Double(srcWidth * (MAX_HEIGHT / (double) srcHeight)).intValue();
        } else {
        	destWidth = new Double(MAX_WIDTH).intValue();
        	destHeight = new Double(srcHeight * (MAX_WIDTH / (double) srcWidth)).intValue();
        }
        
        System.out.println(destHeight);
        System.out.println(destWidth);
	}
}
