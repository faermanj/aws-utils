package com.amazon.faermanj;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient;
import com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionRequest;
import com.amazonaws.services.elasticbeanstalk.model.S3Location;
import com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentRequest;
import com.amazonaws.services.s3.AmazonS3Client;

/**
 * Hello world!
 * 
 */
public class DeployVersion implements Runnable {
	private static final Logger log = Logger.getLogger(DeployVersion.class
			.getName());
	private static final DateFormat df = new SimpleDateFormat(
			"yyyy-MM-dd_HH:mm:ss");

	private BasicAWSCredentials credentials;
	private AWSElasticBeanstalkClient eb;
	private AmazonS3Client s3;

	/* Environment */
	String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
	String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
	String s3Endpoint = "https://s3-sa-east-1.amazonaws.com";
	String ebEndpoint = "https://elasticbeanstalk.sa-east-1.amazonaws.com";

	/* Arguments */
	String application = "kornell-api";
	String environment = application + "-red";
	String bucket = environment;
	String artifact = "/Users/faermanj/Dev/Kornell/kornell-api/target/kornell-api-1.0-SNAPSHOT.war";

	File file;
	String tstamp = df.format(new Date());
	String versionLabel = application + "_" + tstamp;
	private String[] args;
	private CommandLine line;

	public DeployVersion(String[] args) {
		this.args = args;
	}

	public static void main(String[] args) throws Exception {
		new DeployVersion(args).run();
	}

	private static void initLogging() {
		ConsoleHandler ch = new ConsoleHandler();
		log.addHandler(ch);
		ch.setLevel(Level.ALL);
		log.setLevel(Level.ALL);

		// Logger root = Logger.getLogger("");
		// root.setLevel(Level.ALL);
		// root.addHandler(ch);
	}

	public void run() {
		initLogging();
		Options options = new Options()
				.addOption("s3", true, "S3 Endpoint URL")
				.addOption("eb", true, "Elastic Beanstalk endpoint")

				.addOption("a", true, "Access Key Id")
				.addOption("s", true, "Secret Access Key")

				.addOption("app", true, "Elastic Beanstalk application name")
				.addOption("env", true, "Elastic Beanstalk environment name")
				.addOption("b", true, "s3 bucket to hold application version")
				.addOption("war", true, "war file to deploy");
		CommandLineParser parser = new BasicParser();

		try {
			line = parser.parse(options, args);
			initParameters();
			logConfig();
			validateConfig();
			
			initClients();
			createAppVersion();
			updateVersion();
		} catch (IllegalArgumentException e) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("eb-ci", options);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		} catch(IOException e){
			throw new RuntimeException(e);
		}
		log.info("done");

	}

	private void validateConfig() throws IOException{
		file = new File(artifact);
		boolean inexistent = ! file.exists();
		boolean unreadable = ! file.canRead();
		if (inexistent || unreadable) throw new IOException("WAR file does not exist or is not readable.");		
	}

	private void logConfig() {
		log.config("========== eb-ci configuration ==========");
		log.config("Local timestamp: "+tstamp);
		log.config("AWS Access Key Id: "+accessKey);
		log.config("AWS Secret Key: ..."+secretKey.substring(secretKey.length()-6));
		log.config("S3 Endpoint: "+s3Endpoint);
		log.config("S3 Bucket: "+bucket);
		log.config("EB Endpoint: "+ebEndpoint);
		log.config("EB Application: "+application);
		log.config("EB Environment:"+environment);
		log.config("WAR file: "+artifact);
		log.config("========================================");
		
	}

	private void initParameters() {

		if (args.length == 0)
			throw new IllegalArgumentException();
		// "/Users/faermanj/Dev/Kornell/kornell-api/target/kornell-api-1.0-SNAPSHOT.war";

		s3Endpoint = argOrElse("s3", "https://s3-sa-east-1.amazonaws.com");
		ebEndpoint = argOrElse("eb",
				"https://elasticbeanstalk.sa-east-1.amazonaws.com");

		accessKey = argOrElse("a", System.getenv("AWS_ACCESS_KEY_ID"));
		secretKey = argOrElse("s", System.getenv("AWS_SECRET_ACCESS_KEY"));

		application = argOrElse("app", "default-application");
		environment = argOrElse("env", "default-environment");
		bucket = argOrElse("b", application);
		artifact = argOrElse("war", "application.war");

	}

	private String argOrElse(String argument, String defaultValue) {
		return line.hasOption(argument) ? line.getOptionValue(argument)
				: defaultValue;
	}

	private void updateVersion() {
		log.fine("updateEnvironment");
		UpdateEnvironmentRequest updateEnvReq = new UpdateEnvironmentRequest()
				.withEnvironmentName(environment)
				.withVersionLabel(versionLabel);
		eb.updateEnvironment(updateEnvReq);
	}

	private void createAppVersion() {		
		String key = file.getName();
		log.fine("putObject " + key);
		s3.putObject(bucket, key, file);

		log.fine("createApplicationVersion " + versionLabel);
		CreateApplicationVersionRequest createVersionReq = new CreateApplicationVersionRequest();
		createVersionReq.setApplicationName(application);
		createVersionReq.setAutoCreateApplication(true);
		createVersionReq.setDescription(application + " " + tstamp);
		S3Location sourceBundle = new S3Location(bucket, key);
		createVersionReq.setSourceBundle(sourceBundle);
		createVersionReq.setVersionLabel(versionLabel);
		eb.createApplicationVersion(createVersionReq);
	}

	private void initClients() {
		credentials = new BasicAWSCredentials(accessKey, secretKey);
		eb = new AWSElasticBeanstalkClient(credentials);
		eb.setEndpoint(ebEndpoint);
		s3 = new AmazonS3Client(credentials);
		s3.setEndpoint(s3Endpoint);
	}
}
