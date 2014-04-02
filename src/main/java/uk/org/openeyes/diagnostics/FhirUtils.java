/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.org.openeyes.diagnostics;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.FilenameUtils;
import org.apache.xmlbeans.XmlException;
import org.hl7.fhir.Attachment;
import org.hl7.fhir.Base64Binary;
import org.hl7.fhir.DiagnosticReport;
import org.hl7.fhir.DiagnosticReportDocument;
import org.hl7.fhir.DiagnosticReportImage;
import org.hl7.fhir.Media;
import org.hl7.fhir.MediaDocument;
import org.hl7.fhir.Observation;
import org.hl7.fhir.OperationOutcomeDocument;
import org.hl7.fhir.Patient;
import org.hl7.fhir.ResourceInline;
import org.hl7.fhir.ResourceReference;
import org.w3.x2005.atom.ContentType;
import org.w3.x2005.atom.EntryType;
import org.w3.x2005.atom.FeedDocument;
import uk.org.openeyes.diagnostics.db.FieldReport;

/**
 * Class for marshaling and un-marshaling FHIR resources, as well as reading
 * resources from remote locations.
 */
public class FhirUtils {

	/**
	 *
	 * @param options
	 */
	public static void printHelp(Options options) {

		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("annotator-app [options], where [options] can be any of:", options);
		System.exit(0);
	}

	/**
	 *
	 * @param args
	 */
	public static void main(String[] args) {

		if (args.length == 0) {
			System.out.println("Try -h");
			System.exit(1);
		}
		String command = null;
		if (args[0].charAt(0) != '-') {
			command = args[0];
			args = Arrays.copyOfRange(args, 1, args.length);
		}
		Options options = new Options();
		Option optionHost = new Option("s", "host", true,
				"Specify port number.");
		Option optionPort = new Option("p", "port", true,
				"Specify port number.");
		Option optionType = new Option("t", "type", true,
				"Specify resource type.");
		Option optionRef = new Option("r", "reference", true,
				"Supply media reference to diagnostic report.");
		Option optionMedia = new Option("m", "media", true,
				"Specify images to send. Quote and separate using ';'");
		Option optionAttachments = new Option("a", "attachments", true,
				"Specify attachments to send. Quote and separate using ';'");
		Option optionHelp = new Option("h", "help", false,
				"Print this help then quit.");
		Option optionAutoCreate = new Option("c", "auto-create", false,
				"Create patients if they don't exist (on the server)");
		options.addOption(optionType);
		options.addOption(optionMedia);
		options.addOption(optionAttachments);
		options.addOption(optionRef);
		options.addOption(optionHelp);
		options.addOption(optionPort);
		options.addOption(optionHost);
		options.addOption(optionAutoCreate);
		CommandLineParser parser = new PosixParser();
		try {
			String host = "localhost";
			int port = 80;
			String type = null;
			String media = null;
			String attachments = null;
			String reference = null;
			boolean pas = false;
			boolean autoCreate = false;
			CommandLine cmd = parser.parse(options, args);
			if (cmd.hasOption("help") || cmd.hasOption('h')) {
				FhirUtils.printHelp(options);
			}
			if (cmd.hasOption("t") || cmd.hasOption("type")) {
				type = cmd.getOptionValue("type");
			} else {
				System.out.println("You must specify a type (-t)");
				System.exit(1);
			}
			if (cmd.hasOption("m") || cmd.hasOption("media")) {
				media = cmd.getOptionValue("media");
			}
			if (cmd.hasOption("a") || cmd.hasOption("attachments")) {
				attachments = cmd.getOptionValue("attachments");
			}
			if (cmd.hasOption("r") || cmd.hasOption("reference")) {
				reference = cmd.getOptionValue("reference");
			}
			if (cmd.hasOption("p") || cmd.hasOption("pas")) {
				pas = true;
			}
			if (cmd.hasOption("c") || cmd.hasOption("auto-create")) {
				autoCreate = true;
			}
			File[] mediaFiles = null;
			if (media != null) {
				String[] fileStr = media.split(";");
				mediaFiles = new File[fileStr.length];
				for (int i = 0; i < mediaFiles.length; i++) {
					mediaFiles[i] = new File(fileStr[i].trim());
					if (!mediaFiles[i].exists()) {
						throw new FileNotFoundException();
					}
				}
			}
			File[] attachmentFiles = null;
			if (attachments != null) {
				String[] fileStr = attachments.split(";");
				attachmentFiles = new File[fileStr.length];
				for (int i = 0; i < attachmentFiles.length; i++) {
					attachmentFiles[i] = new File(fileStr[i].trim());
					if (!attachmentFiles[i].exists()) {
						throw new FileNotFoundException();
					}
				}
			}
			if (pas) {
				// check the pas
				System.out.println("PAS implementation not yet provided.");
				System.exit(1);
			}
			if (autoCreate) {
				// TODO
			}
			if ("create".equals(command)) {
				if (type.toLowerCase().equals("diagnosticreport")) {
					FhirUtils bb = new FhirUtils();
					DiagnosticReport report = bb.createDiagnosticReport(1, reference, attachmentFiles);
					DiagnosticReportDocument diagDoc = DiagnosticReportDocument.Factory.newInstance();
					diagDoc.setDiagnosticReport(report);
					bb.send(host, port, type, diagDoc.xmlText());
				} else if (type.toLowerCase().equals("media")) {
					if (mediaFiles.length < 0) {
						System.out.println("Supply a file to transfer.");
						System.exit(1);
					}
					FhirUtils bb = new FhirUtils();
					Media m = bb.createMedia(mediaFiles[0]);
					MediaDocument mediaDoc = MediaDocument.Factory.newInstance();
					mediaDoc.setMedia(m);
					bb.send(host, port, type, mediaDoc.xmlText());
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 *
	 * @param type
	 * @param data
	 */
	public static boolean send(String host, int port, String type, String data) {

		boolean sent = false;
		String ref = null;
		HttpTransfer sender = new HttpTransfer();
		sender.setHost(host);
		sender.setPort(port);
		int code = -1;
		try {
			code = sender.send(type, data);
			sent = true;
		} catch (ConnectException ex) {
			// nothing to do
		}
		if (code == 201) {
			String response = sender.getResponse();
			try {
				OperationOutcomeDocument opdoc = OperationOutcomeDocument.Factory.parse(response);
				ref = opdoc.getOperationOutcome().getIssueArray()[0].getDetails().getValue();

			} catch (XmlException xmlex) {
				xmlex.printStackTrace();
			}
		}
		return sent;
	}

	/**
	 *
	 * @param reference
	 * @param attachments
	 * @return
	 * @throws IOException
	 */
	public DiagnosticReport createDiagnosticReport(int patientRef, String reference, File[] attachments)
			throws IOException {
		DiagnosticReport report = DiagnosticReport.Factory.newInstance();

		ResourceReference subject = report.addNewSubject();
		subject.addNewReference().setValue("patient/pat-" + patientRef);
		DiagnosticReportImage image = report.addNewImage();
		ResourceReference imageRef = image.addNewLink();
		org.hl7.fhir.String ref = imageRef.addNewReference();
		ref.setValue(reference);
		for (int i = 0; i < attachments.length; i++) {
			ResourceReference resref = report.addNewResult();
			resref.addNewReference().setValue("#r" + (i + 1)); // TODO
			ResourceInline inline = report.addNewContained();
			Observation o = inline.addNewObservation();
			o.setId("r" + (i + 1));

			Attachment att = o.addNewValueAttachment();
			Base64Binary binary = att.addNewData();
			Path path = FileSystems.getDefault().getPath(attachments[i].getParentFile().getAbsolutePath(), attachments[i].getName());
			att.addNewContentType().setValue(Files.probeContentType(path));
			att.addNewTitle().setValue(attachments[i].getName());
			binary.setValue(this.base64encode(attachments[i]));
			att.setData(binary);

		}
		DiagnosticReportDocument doc = DiagnosticReportDocument.Factory.newInstance();
		doc.setDiagnosticReport(report);
		return report;
	}

	/**
	 * @param type
	 * @param media
	 * @param attachments
	 */
	public Media createMedia(File media)
			throws IOException {
		Media m = Media.Factory.newInstance();
		Attachment att = m.addNewContent();
		Base64Binary data = att.addNewData();

		Path path = FileSystems.getDefault().getPath(media.getParentFile().getAbsolutePath(), media.getName());
		att.addNewContentType().setValue(Files.probeContentType(path));
		att.addNewTitle().setValue(media.getName());
		data.setValue(this.base64encode(media));
		att.setData(data);
		m.addNewSubject().addNewReference().setValue("media/med-1");
		MediaDocument doc = MediaDocument.Factory.newInstance();
		doc.setMedia(m);
		return m;
	}

	/**
	 *
	 * @param metaData
	 * @return
	 */
	public Patient readPatient(String host, int port,
			HumphreyFieldMetaData metaData) throws ConnectException {
		Patient p = null;
		HttpTransfer sender = new HttpTransfer();
		String requestParams = "identifier=" + metaData.getPatientId()
				+ "&last_name=" + metaData.getFamilyName()
				+ "&first_name=" + metaData.getGivenName();
		sender.setHost(host);
		sender.setPort(port);
		int result = -1;
		try {
			result = sender.read("Patient", "pat", requestParams);
			if (result == 201) {
				FeedDocument doc = FeedDocument.Factory.parse(sender.getResponse());
				EntryType entry = doc.getFeed().getEntryArray(0);
				ContentType content = entry.getContentArray(0);
				p = content.getPatient();
				// reference to patient, last part is ID:
				p.setId(FilenameUtils.getBaseName(entry.getIdArray(0).getStringValue()));
			}
		} catch (ConnectException ex) {
			System.out.println("### CAUGHT EXCEPTION ");
			throw ex;
		} catch (XmlException ex) {
			ex.printStackTrace();
		}
		return p;
	}

	/**
	 *
	 * @param f
	 * @return
	 */
	private byte[] base64encode(File f) throws IOException {
		Path p = Paths.get(f.getAbsolutePath());
		return Files.readAllBytes(p);
	}
}
