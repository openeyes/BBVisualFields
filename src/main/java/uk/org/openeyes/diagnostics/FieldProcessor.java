package uk.org.openeyes.diagnostics;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.Patient;
import sun.misc.BASE64Encoder;
import uk.org.openeyes.diagnostics.db.DbUtils;
import uk.org.openeyes.diagnostics.db.FieldError;
import uk.org.openeyes.diagnostics.db.FieldErrorReport;
import uk.org.openeyes.diagnostics.db.FieldReport;
import uk.org.openeyes.diagnostics.db.HibernateUtil;

/**
 * Temporary class to watch for humphrey field images.
 */
public class FieldProcessor extends AbstractFieldProcessor implements Runnable {

	/**
	 * Host to send reports to.
	 */
	private String host = "localhost";
	/**
	 * How long to wait (seconds) between checking for new reports.
	 */
	private int interval = 1;
	/**
	 * Port number to send reports on.
	 */
	private int port = 80;
	/**
	 * Which directory to place files that failed to send.
	 */
	private File outgoingDir;

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
	 * @param file
	 * @return
	 */
	@Override
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
			// measurement report text:
			String reportText = null;
			try {
				// get the report's patient id and find out if they exist:
				Patient patient = new FhirUtils().readPatient(this.getHost(), 
						this.getPort(), metaData);
				if (patient == null) { // not found
					this.setUnknownOEPatient(report);
				} else {
					reportText = this.generateMeasurementText(patient.getId(),
							file, imageFile, report);
					this.transferHumphreyVisualField(reportText,report, file);
				}
			} catch(ConnectException cex) {
				cex.printStackTrace();
				if (reportText == null) {
					// mark the patient ID (or lack of) in the report text:
					reportText = this.generateMeasurementText("__NO_PATIENT_ID__",
							file, imageFile, report);
				}
//				this.moveFile(metaData, report, file);
				// mark the message as not having been sent and re-try at a later date:
				// TODO code here
				File measurementFile = new File(this.getOutgoingDir(), 
						FilenameUtils.getBaseName(file.getName()) + ".mes");
				System.out.println(measurementFile.getAbsolutePath());
				measurementFile.createNewFile();
				IOUtils.write(reportText, new FileWriter(measurementFile));
			}

			if (!report.getFieldErrorReports().isEmpty()) {
				this.moveFile(metaData, report, file);
				System.out.println("Error in record; moving " + file.getName()
						+ " to " + this.errDir);
				return;
			}
			System.out.println("records match");
			
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
	 * 
	 * @param report 
	 */
	private void setUnknownOEPatient(FieldReport report) {
		session = HibernateUtil.getSessionFactory().getCurrentSession();
		session.getTransaction().begin();
		// TODO the patient is not found; move to error directory
		FieldError err = DbUtils.getError(DbUtils.ERROR_UNKOWN_OE_PATIENT);
		FieldErrorReport errReport = new FieldErrorReport();
		errReport.setFieldError(err);
		errReport.setFieldReport(report);
		session.save(errReport);

		session.refresh(report);
		session.getTransaction().commit();
	}
	
	/**
	 * 
	 * @param patientRef
	 * @param xmlFile
	 * @param file
	 * @param fieldReport
	 * @return
	 * @throws IOException 
	 */
	private String generateMeasurementText(String patientRef, File xmlFile,
			File file, FieldReport fieldReport) 
		throws IOException {

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

		String reportText = this.getHumphreyMeasurement(xmlFile,
				patientRef, fieldReport,
				encodedData, encodedDataThumb);
		
		imageConverted.delete();
		imageCropped.delete();
		return reportText;
	}

	/**
	 * Transfer the specified report.
	 *
	 * @param patientRef
	 * @param file
	 * @param xmlFile
	 * @param fieldReport
	 */
	private void transferHumphreyVisualField(String reportText, FieldReport fieldReport, File file) throws IOException {
		HttpTransfer sender = new HttpTransfer();
		sender.setHost(this.getHost());
		sender.setPort(this.getPort());
		int code = sender.send("MeasurementVisualFieldHumphrey", reportText);
		if (code > -1) {
			this.generateCommsLog(code,
					DbUtils.FHIR_RESOURCE_TYPE_DIAGNOSTIC_REPORT, fieldReport,
					sender.getResponse());
			if (code == 201) {
				boolean moved = file.renameTo(new File(this.archiveDir, file.getName()));
				if (!moved) {
					System.err.println("Unable to move " + file.getAbsolutePath());
				}
			}
		} else {
			// failed to send - binary exponential backoff algorithm
		}

	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public File getOutgoingDir() {
		return outgoingDir;
	}

	public void setOutgoingDir(String outgoingDir) {
		this.outgoingDir = new File(outgoingDir);
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
