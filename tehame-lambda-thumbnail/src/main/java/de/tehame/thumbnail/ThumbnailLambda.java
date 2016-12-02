package de.tehame.thumbnail;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;

import org.apache.log4j.Logger;

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
 */
public class ThumbnailLambda implements RequestHandler<S3Event, String> {
	
	private static final Logger LOGGER = Logger.getLogger(ThumbnailLambda.class);
	
	/**
	 * Der Name des Buckets mit original Photos.
	 * Die Umgebungsvariable wird in AWS Lambda definiert.
	 */
	private static final String TEHAME_PHOTO_BUCKET = 
			System.getenv("TEHAME_PHOTO_BUCKET");
	
	/**
	 * Der Name des Buckets mit Thumbnails der original Photos.
	 * Die Umgebungsvariable wird in AWS Lambda definiert.
	 */
	private static final String TEHAME_THUMBNAIL_BUCKET = 
			System.getenv("TEHAME_THUMBNAIL_BUCKET");
	
	private static final double MAX_WIDTH = 200;
	private static final double MAX_HEIGHT = 200;
	
	private static final String JPG_TYPE = "jpg";
	private static final String JPG_MIME = "image/jpeg";
	
	/**
	 * @param s3event Das Event, welches durch den S3 Trigger ausgelöst wurde.
	 * @param ctx Die AWS Lambda Laufzeitumgebung.
	 */
	public String handleRequest(final S3Event s3event, final Context ctx) {
		final long start = System.currentTimeMillis();
		
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
	            
	            LOGGER.debug("Source S3 Key: " + srcKey);
	            
	            // Der Original Key wird übernommen.
	            String destKey = srcKey;
	            
	            LOGGER.debug("Destination S3 Key: " + destKey);
	            
	            // Lade nun die konkreten Daten aus S3
	            AmazonS3 s3Client = new AmazonS3Client();
	            S3Object s3Object = s3Client.getObject(new GetObjectRequest(srcBucket, srcKey));
	            InputStream objectData = s3Object.getObjectContent();
	            
	            BufferedImage srcImage = ImageIO.read(objectData);
	            // Aus der Doku:
	            // "Close the input stream in Amazon S3 object as soon as possible"
	            // Siehe http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/AmazonS3Client.html
	            // "Releases any underlying system resources. If the resources are already released then invoking this method has no effect."
	            s3Object.close();
	            
	            // Der Skalierungsfaktor muss bestimmt werden
	            double srcHeight = (double) srcImage.getHeight();
	            double srcWidth = (double) srcImage.getWidth();
	            
	            LOGGER.debug("Breite des Originals: " + srcWidth);
	            LOGGER.debug("Höhe des Originals: " + srcHeight);
	            
	            // Hier liegt der Trick darin, die kleinste notwendige 
	            // Skalierung zu finden.
				double minScalar = this.calcMinScalar(
						srcHeight, MAX_HEIGHT, srcWidth, MAX_WIDTH);

				int destWidth = this.scale(srcWidth, minScalar);
				int destHeight = this.scale(srcHeight, minScalar);
				
	            LOGGER.debug("Breite des Thumbnails: " + destWidth);
	            LOGGER.debug("Höhe des Thumbnails: " + destHeight);
	            
				ResampleOp resizeOp = new ResampleOp(destWidth, destHeight);
	            
	            resizeOp.setFilter(ResampleFilters.getLanczos3Filter());
	            BufferedImage scaledImage = resizeOp.filter(srcImage, null);
	            
	            // Re-encode image to target format
	            ByteArrayOutputStream os = new ByteArrayOutputStream();
	            ImageIO.write(scaledImage, JPG_TYPE, os);
	            InputStream is = new ByteArrayInputStream(os.toByteArray());
	            os.close();
	            
	            // Set Content-Length and Content-Type
	            ObjectMetadata meta = new ObjectMetadata();
	            meta.setContentLength(os.size());
	            meta.setContentType(JPG_MIME);
	            
	            // Uploading to S3 destination bucket
	            LOGGER.debug("Writing to: " + TEHAME_THUMBNAIL_BUCKET + "/" + destKey);
	            s3Client.putObject(TEHAME_THUMBNAIL_BUCKET, destKey, is, meta);
	            is.close();
	            
	            LOGGER.debug("Successfully resized " + srcBucket + "/"
	                    + srcKey + " and uploaded to " + TEHAME_THUMBNAIL_BUCKET + "/" + destKey);
	            
	            LOGGER.debug("Dauer " + (System.currentTimeMillis() - start) + "ms");
	            return "Ok";
			} else {
				LOGGER.error("Falscher Source Bucket");
				LOGGER.debug("Dauer " + (System.currentTimeMillis() - start) + "ms");
				return "Falscher Source Bucket";
			}
			
		} catch (Exception e) {
			// Mache aus der checked Exception eine Runtime Exception, 
			// denn die Methode muss das Interface implementieren.
			LOGGER.debug("Dauer " + (System.currentTimeMillis() - start) + "ms");
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param length Original Länge.
	 * @param scalar Skalar.
	 * @return Skalierte Dimension.
	 */
	protected int scale(double length, double scalar) {
		int scaled = (int) (length * scalar);
		return scaled;
	}

	/**
	 * @param srcHeight Höhe des Original Bildes.
	 * @param srcWidth Breite des Original Bildes.
	 * @param maxWidth Maximal erlaubte Breite.
	 * @param maxHeight Maximal erlaubte Höhe.
	 * 
	 * @return Der Faktor, um den ein Bild skaliert werden muss, 
	 *         damit es die maximale Breite und Höhe erfüllt und so groß wie möglich ist.
	 */
	protected double calcMinScalar(
			double srcHeight, double maxHeight, 
			double srcWidth, double maxWidth) {
		
		double ratioH = maxHeight / srcHeight;
		double ratioW = maxWidth / srcWidth;

		double minRatio = Math.min(ratioH, ratioW);
		
		return minRatio;
	}
}
