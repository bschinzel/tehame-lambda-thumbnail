package de.tehame.thumbnail;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.event.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.mortennobel.imagescaling.ResampleFilters;
import com.mortennobel.imagescaling.ResampleOp;

/**
 * AWS Lambda Function Handler: de.tehame.thumbnail.ThumbnailLambda::handleRequest
 * 
 * Dieses AWS Lambda basiert auf dem Beispiel aus folgendem Tutorial:
 * http://docs.aws.amazon.com/lambda/latest/dg/with-s3-example-deployment-pkg.html#with-s3-example-deployment-pkg-java 
 */
public class ThumbnailLambda implements RequestHandler<S3Event, String> {
	
	/**
	 * Der Name des Buckets mit original Photos.
	 */
	private static final String TEHAME_PHOTO_BUCKET = "tehame";
	
	/**
	 * Der Name des Buckets mit Thumbnails der original Photos.
	 */
	private static final String TEHAME_THUMBNAIL_BUCKET = "tehame-thumbnails";
	
	/**
	 * Thumbnails im S3 bekommen ein eigenes Suffix im Key.
	 */
	private static final String THUMBNAIL_SUFFIX = "-thumbnail";
	
	private static final float MAX_WIDTH = 200;
	private static final float MAX_HEIGHT = 200;
	
	private static final String JPG_TYPE = "jpg";
	private static final String JPG_MIME = "image/jpeg";
	
	/**
	 * @param s3event Das Event, welches durch den S3 Trigger ausgelöst wurde.
	 * @param ctx Die AWS Lambda Laufzeitumgebung.
	 */
	public String handleRequest(final S3Event s3event, final Context ctx) {
		
		try {
			
			// Eine S3EventNotification besteht aus mehreren Records.
			// Ein Record beinhaltet awsRegion, eventName, eventSource, 
			// eventTime, eventVersion, S3Entity ...
			// Da es hier um PUT Events im S3 geht gibt es wohl nur ein Record.
			S3EventNotificationRecord record = s3event.getRecords().get(0);
			
			// Der Name des Buckets
			String srcBucket = record.getS3().getBucket().getName();

			// Nur Photos aus diesem Bucket sollen in Thumbnails umgewandelt werden
			// Normalerweise muss das nicht geprüft werden, 
			// weil der Trigger Bucket-spezifisch ist.
			if (srcBucket.equals(TEHAME_PHOTO_BUCKET)) {
				
				// Der Key eines S3 Objektes kann Leerzeichen und Unicode
				// Non-ASCII Zeichen enthalten. Pluszeichen werden in Leerzeichen
				// umgewandelt (warum?).
	            String srcKey = record.getS3().getObject().getKey().replace('+', ' ');
	            srcKey = URLDecoder.decode(srcKey, "UTF-8");
	            
	            // Der neue Key des Thumbnails bekommt das Suffix '-thumbnail'.
	            String destKey = srcKey + THUMBNAIL_SUFFIX;
	            
	            // Lade nun die konkreten Daten aus S3
	            AmazonS3 s3Client = new AmazonS3Client();
	            S3Object s3Object = s3Client.getObject(new GetObjectRequest(srcBucket, srcKey));
	            InputStream objectData = s3Object.getObjectContent();
	            
	            BufferedImage srcImage = ImageIO.read(objectData);
	            
	            // Der Skalierungsfaktor muss bestimmt werden
	            int srcHeight = srcImage.getHeight();
	            int srcWidth = srcImage.getWidth();
	            
	            int dWidth = (int) MAX_WIDTH / srcWidth;
	            int dHeight = (int) MAX_HEIGHT / srcHeight;
	            
	            int scale = Math.min(dWidth, dHeight);
	            
	            ResampleOp resizeOp = new ResampleOp(scale, scale);
	            resizeOp.setFilter(ResampleFilters.getLanczos3Filter());
	            BufferedImage scaledImage = resizeOp.filter(srcImage, null);
	            
	            // Re-encode image to target format
	            ByteArrayOutputStream os = new ByteArrayOutputStream();
	            ImageIO.write(scaledImage, JPG_TYPE, os);
	            InputStream is = new ByteArrayInputStream(os.toByteArray());
	            
	            // Set Content-Length and Content-Type
	            ObjectMetadata meta = new ObjectMetadata();
	            meta.setContentLength(os.size());
	            meta.setContentType(JPG_MIME);
	            
	            // Uploading to S3 destination bucket
	            System.out.println("Writing to: " + TEHAME_THUMBNAIL_BUCKET + "/" + destKey);
	            s3Client.putObject(TEHAME_THUMBNAIL_BUCKET, destKey, is, meta);
	            System.out.println("Successfully resized " + srcBucket + "/"
	                    + srcKey + " and uploaded to " + TEHAME_THUMBNAIL_BUCKET + "/" + destKey);
	            return "Ok";
			}
			
		} catch (IOException e) {
			// Mache aus der checked Exception eine Runtime Exception, 
			// denn die Methode muss das Interface implementieren.
			throw new RuntimeException(e);
		}
		
		return null;
	}

}
