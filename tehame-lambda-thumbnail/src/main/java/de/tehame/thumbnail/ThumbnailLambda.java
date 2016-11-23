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
	
	private static final int MAX_WIDTH = 200;
	private static final int MAX_HEIGHT = 200;
	
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
	            
	            LOGGER.debug("Source S3 Key: " + srcKey);
	            
	            // Der neue Key des Thumbnails bekommt das Suffix '-thumbnail'.
	            String destKey = srcKey + THUMBNAIL_SUFFIX;
	            
	            LOGGER.debug("Destination S3 Key: " + destKey);
	            
	            // Lade nun die konkreten Daten aus S3
	            AmazonS3 s3Client = new AmazonS3Client();
	            S3Object s3Object = s3Client.getObject(new GetObjectRequest(srcBucket, srcKey));
	            InputStream objectData = s3Object.getObjectContent();
	            
	            BufferedImage srcImage = ImageIO.read(objectData);
	            
	            // Der Skalierungsfaktor muss bestimmt werden
	            int srcHeight = srcImage.getHeight();
	            int srcWidth = srcImage.getWidth();
	            
	            LOGGER.debug("Breite des Originals: " + srcWidth);
	            LOGGER.debug("Höhe des Originals: " + srcHeight);
	            
	            int destHeight = -1;
	            int destWidth = -1;
	            
	            // Berechne die Länge für die kürzere Seite
	            if (srcHeight < srcWidth) {
	            	destHeight = MAX_HEIGHT;
	            	destWidth = srcWidth * (MAX_HEIGHT / destHeight);
	            } else {
	            	destWidth = MAX_WIDTH;
	            	destHeight = srcHeight * (MAX_WIDTH / destWidth);
	            }
	            
	            LOGGER.debug("Breite des Thumbnails: " + destWidth);
	            LOGGER.debug("Höhe des Thumbnails: " + destHeight);
	            
	            ResampleOp resizeOp = new ResampleOp(destWidth, destHeight);
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
	            LOGGER.debug("Writing to: " + TEHAME_THUMBNAIL_BUCKET + "/" + destKey);
	            s3Client.putObject(TEHAME_THUMBNAIL_BUCKET, destKey, is, meta);
	            LOGGER.debug("Successfully resized " + srcBucket + "/"
	                    + srcKey + " and uploaded to " + TEHAME_THUMBNAIL_BUCKET + "/" + destKey);
	            return "Ok";
			} else {
				LOGGER.error("Falscher Source Bucket");
				return "Falscher Source Bucket";
			}
			
		} catch (Exception e) {
			// Mache aus der checked Exception eine Runtime Exception, 
			// denn die Methode muss das Interface implementieren.
			throw new RuntimeException(e);
		}
	}
}
