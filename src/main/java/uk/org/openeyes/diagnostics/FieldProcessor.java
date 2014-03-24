package uk.org.openeyes.diagnostics;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.Patient;
import org.w3.x2005.atom.ContentType;
import org.w3.x2005.atom.EntryType;
import org.w3.x2005.atom.FeedDocument;
import sun.misc.BASE64Encoder;
import uk.org.openeyes.diagnostics.db.DbUtils;
import uk.org.openeyes.diagnostics.db.FieldReport;

/**
 * Temporary class to watch for humphrey field images.
 */
public class FieldProcessor extends AbstractFieldProcessor implements Runnable {
	
	/** Host to send reports to. */
	private String host = "localhost";
	/** How long to wait (seconds) between checking for new reports. */
	private int interval = 1;
	/** Port number to send reports on. */
	private int port = 80;
	
	/**
	 *
	 */
	public void run() {
		while (true) {
			try {
				this.checkDir();
				Thread.sleep(this.interval * 1000);
			} catch (InterruptedException iex) {
				iex.printStackTrace();
			}
		}
	}

	/**
	 *
	 */
	private void checkDir() {
		// get file list -  all XML files
		File[] files = this.dir.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(".xml");
			}
		});
		for (File file : files) {
			this.processFile(file);
		}
	}

	/**
	 *
	 * @param patient
	 * @return
	 */
	private String getPatientId(Patient patient) {
		String id = null;
		try {
			id = patient.getIdentifierArray(0).getValue().getValue();
		} catch (NullPointerException npex) {
		}
		return id;
	}

	/**
	 *
	 * @param patient
	 * @return
	 */
	private String getPatientFamilyName(Patient patient) {
		String name = null;
		try {
			name = patient.getNameArray(0).getFamilyArray(0).getValue();
		} catch (NullPointerException npex) {
		}
		return name;
	}

	/**
	 *
	 * @param patient
	 * @return
	 */
	private String getPatientDoB(Patient patient) {
		String dob = null;
		try {
			dob = patient.getBirthDate().getValue().toString();
		} catch (NullPointerException npex) {
		}
		return dob;
	}

	/**
	 *
	 * @param patient
	 * @return
	 */
	private String getPatientGender(Patient patient) {
		String gender = null;
		try {
			gender = patient.getGender().getCodingArray(0).getCode().getValue();
		} catch (NullPointerException npex) {
		}
		return gender;
	}

	/**
	 *
	 * @param pid
	 * @return
	 */
	private Patient getPatient(HumphreyFieldMetaData metaData) {
		Patient p = null;
		FhirUtils fhirUtils = new FhirUtils();
		HttpTransfer sender = fhirUtils.readPatient(this.getHost(), this.getPort(), metaData);
		try {
			FeedDocument doc = FeedDocument.Factory.parse(sender.getResponse());
			EntryType entry = doc.getFeed().getEntryArray(0);
			ContentType content = entry.getContentArray(0);
			p = content.getPatient();
			// reference to patient, last part is ID:
			p.setId(FilenameUtils.getBaseName(entry.getIdArray(0).getStringValue()));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return p;
	}
	
	/**
	 * 
	 * @param file
	 * @return 
	 */
	public void processFile(File file) {

		this.generateDirectories(new File[]{this.dir, this.archiveDir,
			this.errDir});
		System.out.println("Processing " + file.getName());
		try {
			// parse XML file:
			if (!this.validate(file)) {
				file.renameTo(new File(this.errDir, file.getName()));
				// if the file is invalid, that means we can't get the file name -
				// though it might still exist. Check anyway:
				String basename = FilenameUtils.getBaseName(file.getName());
				File imageFile = new File(file.getParentFile(), basename + ".tif");
				if (imageFile.exists()) {
					imageFile.renameTo(new File(this.errDir, imageFile.getName()));
				}
				return;
			}
			HumphreyFieldMetaData metaData = this.parseFields(file);
			// find out what the values in the report are:
			FieldReport report = this.generateReport(file, metaData, true);

			/*
			 // TODO: these are reliant on the PAS being in place
			 String familyName = this.getPatientFamilyName(patient);
			 String id = this.getPatientId(patient);
			 String birthDate = this.getPatientDoB(patient);
			 if (birthDate != null && !birthDate.equals(metaData.getDob())) {
			 report.setFieldError(DbUtils.getError(DbUtils.ERROR_NO_DOB_MATCH));
			 continue;
			 }
			 if (familyName != null && !familyName.equals(metaData.getFamilyName())) {
			 report.setFieldError(DbUtils.getError(DbUtils.ERROR_NO_SURNAME_MATCH));
			 continue;
			 }*/
			// we expect to see the named file reference in the same directory
			// as the XML file:

			File imageFile = new File(this.dir, metaData.getFileReference());

			if (imageFile.isDirectory() || !imageFile.exists()) {
				this.moveFile(metaData, report, file);
				return;
			}

			if (!report.getFieldErrorReports().isEmpty()) {
				this.moveFile(metaData, report, file);
				System.out.println("records do NOT match; moving to " + this.errDir);
				return;
			}

			// get the report's patient id and find out if they exist:
			Patient patient = this.getPatient(metaData);
			if (patient == null) {
				// TODO
				System.out.println("Could not find patient " + metaData.getPatientId());
				return;
			}
			System.out.println("records match");
			this.transferHumphreyVisualField(Integer.parseInt(patient.getId()), file, imageFile, report);
			File moveToFile = new File(this.archiveDir, imageFile.getName());
			imageFile.renameTo(moveToFile);
			// don't use boolean result, not always consistent, just check if new file exists:
			if (!moveToFile.exists()) {
				System.out.println("Unable to move " + imageFile.getAbsolutePath());
				// TODO clean up - mark file as ignored?
			}
			moveToFile = new File(this.archiveDir, file.getName());
			file.renameTo(moveToFile);
			// don't use boolean result, not always consistent, just check if new file exists:
			if (!moveToFile.exists()) {
				System.out.println("Unable to move " + file.getAbsolutePath());
				// TODO clean up - mark file as ignored?
			}
		} catch (FileNotFoundException fnfex) {
			fnfex.printStackTrace();
		} catch (IOException fnfex) {
			fnfex.printStackTrace();
		}
	}

	/**
	 * Transfer the specified report.
	 *
	 * @param patientRef
	 * @param file
	 * @param xmlFile
	 * @param fieldReport
	 */
	private void transferHumphreyVisualField(int patientRef, File xmlFile, File file,
			FieldReport fieldReport) throws IOException {
		HttpTransfer sender = new HttpTransfer();
		sender.setHost(this.getHost());
		sender.setPort(this.getPort());

		// execute the operation
		File imageConverted = new File(file.getParentFile(), FilenameUtils.getBaseName(file.getName()) + ".gif");
		File imageCropped = new File(file.getParentFile(), FilenameUtils.getBaseName(file.getName()) + "-cropped.gif");
		this.transformImages(file, imageConverted, imageCropped);
		BASE64Encoder encoder = new BASE64Encoder();
		FileInputStream fis = new FileInputStream(imageConverted);
		String encodedData = encoder.encode(IOUtils.toByteArray(fis, fis.available()));

		encoder = new BASE64Encoder();
		fis = new FileInputStream(imageCropped);
		String encodedDataThumb = encoder.encode(IOUtils.toByteArray(fis, fis.available()));

		// TODO poss use jaxb etc.
		String patientMeasurement = this.getPatientMeasurement(Integer.toString(patientRef));

		int code = sender.send("PatientMeasurement", patientMeasurement);
		String location = ((HttpTransfer) sender).getLocation();
		// get the resultant ID from the location header:
		Pattern pattern = Pattern.compile("^.*/PatientMeasurement/([0-9]*)/.*$");
		Matcher matcher = pattern.matcher(location);

		System.out.println("Location for measurement: " + location);
		// TODO poss use jaxb etc.
		if (code == 201 && matcher.matches()) {
			String reportText = this.getHumphreyMeasurement(xmlFile,
					Integer.toString(patientRef), matcher.group(1), fieldReport,
					encodedData, encodedDataThumb);
			code = sender.send("MeasurementVisualFieldHumphrey", reportText);
			this.generateCommsLog(code,
					DbUtils.FHIR_RESOURCE_TYPE_DIAGNOSTIC_REPORT, fieldReport,
					sender.getResponse());

			if (code == 201) {
				boolean moved = file.renameTo(new File(this.archiveDir, file.getName()));
				if (!moved) {
					System.err.println("Unable to move " + file.getAbsolutePath());
				}
			} else {
				System.out.println("ERROR: " + code + ", " + sender.getResponse());
				// TODO cleanup - what was the error?
			}
		} else {
			// deal with the error
		}
		imageConverted.delete();
		imageCropped.delete();

	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getInterval() {
		return interval;
	}

	public void setInterval(int interval) {
		this.interval = interval;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}
	
}
