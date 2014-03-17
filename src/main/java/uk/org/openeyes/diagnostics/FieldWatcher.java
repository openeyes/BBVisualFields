package uk.org.openeyes.diagnostics;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hl7.fhir.Patient;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IMOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3.x2005.atom.ContentType;
import org.w3.x2005.atom.EntryType;
import org.w3.x2005.atom.FeedDocument;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import sun.misc.BASE64Encoder;
import uk.org.openeyes.diagnostics.db.CommsLog;
import uk.org.openeyes.diagnostics.db.DbUtils;
import uk.org.openeyes.diagnostics.db.Directory;
import uk.org.openeyes.diagnostics.db.FieldError;
import uk.org.openeyes.diagnostics.db.FieldErrorReport;
import uk.org.openeyes.diagnostics.db.FieldReport;
import uk.org.openeyes.diagnostics.db.HibernateUtil;

/**
 * Temporary class to watch for humphrey field images.
 */
public class FieldWatcher implements Runnable {
	
	/** Defaults when no CLI options given for image crop & scale. */
	public static final int[] DEFAULT_IMAGE_OPTIONS
			= {1368, 666, 662, 658, 300, 306};
	/** Directory to watch for incoming files. */
	private File dir;
	/** Directory to move files to. */
	private File errDir;
	/** Successfully processed reports are moved to this directory. */
	private File archiveDir;
	/** Host to send reports to. */
	private String host = "localhost";
	/** Hospital/PID regex; PIDs that fail this pattern will be rejected. */
	private String regex = "^([0-9]{1,9})$";
	/** How long to wait (seconds) between checking for new reports. */
	private int interval = 1;
	/** Port number to send reports on. */
	private int port = 80;
	/** Specify parameters to crop and scale the image. */
	private int[] imageOptions;
	/**
	 * Hibernate persistence session object.
	 */
	private Session session;
	@Autowired(required = true)
	private SessionFactory sessionFactory;

	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	@Autowired(required = true)
	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

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
	 * @param file
	 */
	public void processFile(File file) {

		this.generateDirectories();
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
	 * @param metaData
	 * @param report
	 * @param file
	 * @return
	 */
	private void moveFile(HumphreyFieldMetaData metaData, FieldReport report,
			File file) throws IOException {
		for (Iterator<FieldErrorReport> it = report.getFieldErrorReports().iterator(); it.hasNext();) {
			FieldErrorReport fer = it.next();
			System.out.println(fer.getFieldError().getId() + " " + fer.getFieldError().getDescription());
		}
		// in the case of an invalid file reference, we treat a non-existent image as existing:
		if (!this.errorReportContains(report.getFieldErrorReports(), DbUtils.ERROR_INVALID_FILE_REFERENCE)) {
			// then the image file exists - move this too:
			File imageFile = new File(this.dir, metaData.getFileReference());
			File fileToMove = new File(this.errDir, metaData.getFileReference());
			imageFile.renameTo(fileToMove);
			if (!fileToMove.exists()) {
				throw new IOException("Unable to move " + imageFile.getAbsolutePath());
			}
		} else if (this.errorReportContains(report.getFieldErrorReports(), DbUtils.ERROR_INVALID_FILE_REFERENCE)) {
			// the file might exist but the reference might be duff - check anyway:
			String basename = FilenameUtils.getBaseName(file.getName());
			File imageFile = new File(file.getParentFile(), basename + ".tif");
			if (imageFile.exists()) {
				imageFile.renameTo(new File(this.errDir, imageFile.getName()));
			}
		}
		File fileToMoveTo = new File(this.errDir, file.getName());
		file.renameTo(fileToMoveTo);
		if (!fileToMoveTo.exists()) {
			throw new IOException("Unable to move " + file.getAbsolutePath());
		}
	}

	/**
	 * Check to see if the reports contain the specified error.
	 *
	 * @param reports non-null set of reports.
	 * @param errorCode The error code to check for.
	 * @return true of the reports contain the specified error code; false
	 * otherwise.
	 */
	private boolean errorReportContains(Set<FieldErrorReport> reports, int errorCode) {
		boolean result = false;
		for (Iterator<FieldErrorReport> it = reports.iterator(); it.hasNext();) {
			if (it.next().getFieldError().getId() == errorCode) {
				result = true;
				break;
			}
		}
		return result;
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

		// execute the operation
		File imageConverted = new File(file.getParentFile(), FilenameUtils.getBaseName(file.getName()) + ".gif");
		File imageCropped = new File(file.getParentFile(), FilenameUtils.getBaseName(file.getName()) + "-cropped.gif");
		try {
			ConvertCmd command = new ConvertCmd();
			IMOperation op = new IMOperation();
			op.addImage(file.getAbsolutePath());

			op.format("GIF").addImage(imageConverted.getAbsolutePath());
			command.run(op);
			op.crop(this.getImageOptions()[0], this.getImageOptions()[1],
					this.getImageOptions()[2], this.getImageOptions()[3])
					.thumbnail(this.getImageOptions()[4], this.getImageOptions()[5]);
			op.format("GIF").addImage(imageCropped.getAbsolutePath());
			command.run(op);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		BASE64Encoder encoder = new BASE64Encoder();
		FileInputStream fis = new FileInputStream(imageConverted);
		String encodedData = encoder.encode(IOUtils.toByteArray(fis, fis.available()));

		encoder = new BASE64Encoder();
		fis = new FileInputStream(imageCropped);
		String encodedDataThumb = encoder.encode(IOUtils.toByteArray(fis, fis.available()));

		// TODO poss use jaxb etc.
		String patientMeasurement = "<PatientMeasurement><patient_id value=\""
				+ patientRef + "\"/><measurement_type value=\"MeasurementVisualFieldHumphrey\"/></PatientMeasurement>";

		int code = sender.send(this.getHost(), this.getPort(),
				"PatientMeasurement", patientMeasurement);
		String location = ((HttpTransfer) sender).getLocation();
		// get the resultant ID from the location header:
		Pattern pattern = Pattern.compile("^.*/PatientMeasurement/([0-9]*)/.*$");
		Matcher matcher = pattern.matcher(location);

		// TODO poss use jaxb etc.
		if (code == 201 && matcher.matches()) {
			String reportText = "<MeasurementVisualFieldHumphrey><patient_id value=\"" + patientRef + "\"/>"
					+ "<patient_measurement_id value=\"" + matcher.group(1) + "\"/>"
					+ "<image_scan_data contentType=\"text/html\" value=\"" + encodedData
					+ "\"/>" + "<image_scan_crop_data value=\"" + encodedDataThumb + "\"/>"
					+ "<study_datetime value=\"" + fieldReport.getStudyDate() + " " + fieldReport.getStudyTime() + "\"/>"
					+ "<eye value=\"" + fieldReport.getEye() + "\"/>"
					+ "<file_reference value=\"" + fieldReport.getFileReference() + "\"/>"
					+ "<pattern value=\"" + fieldReport.getTestName() + "\"/>"
					+ "<strategy value=\"" + fieldReport.getTestType() + "\"/>";
			boolean includeSource = true;
			if (includeSource) {
				reportText += "<xml_file_data value=\"" + encoder.encode(IOUtils.toByteArray(new FileInputStream(xmlFile))) + "\"/>";
			}
			reportText += "</MeasurementVisualFieldHumphrey>";
			code = sender.send(this.getHost(), this.getPort(),
					"MeasurementVisualFieldHumphrey", reportText);
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

	/**
	 * Generate a comms report based on the results of sending/transferring a
	 * report.
	 *
	 * @param code result code of transfer; although designed for HTTP status
	 * codes, this could be any value.
	 * @param resourceType arbitrary string determining processed report type.
	 * @param fieldReport the actual report that was sent, linked to the
	 * resulting report.
	 * @param response The result returned from the server.
	 */
	public void generateCommsLog(int code, int resourceType,
			FieldReport fieldReport, String response) {

		session = HibernateUtil.getSessionFactory().getCurrentSession();
		session.getTransaction().begin();

		CommsLog log = new CommsLog();
		log.setResourceType(DbUtils.getResourceType(resourceType));
		log.setReturnCode(code);
		log.setFieldReport(fieldReport);
		log.setResult(response);
		log.setReportTime(Calendar.getInstance().getTime());

		session.save(log);
		session.getTransaction().commit();
	}

	/**
	 * Attempts to extract patient and exam information from the specified file.
	 *
	 * @param file the file to interrogate; must be non-null and point to an
	 * existing study (XML) file.
	 *
	 * @return valid humphrey meta data if the file could be parsed; otherwise,
	 * an empty
	 *
	 * @throws FileNotFoundException if the file does not exist.
	 * @throws ParserConfigurationException if the file could not be parsed.
	 * @throws XPathExpressionException if there are any issues evaluating
	 * paths.
	 */
	protected HumphreyFieldMetaData parseFields(File file) throws FileNotFoundException {
		HumphreyFieldMetaData metaData = new HumphreyFieldMetaData(this.regex);
		try {
			DocumentBuilderFactory builderFactory =
					DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = builderFactory.newDocumentBuilder();
			Document document = builder.parse(file);
			XPath xPath = XPathFactory.newInstance().newXPath();
			// define xpath expressions for data values:
			String root = "/CZM-XML/DataSet/CZM_HFA_EMR_IOD/";
			String patientRoot = root + "Patient_M/";
			String machineRoot = root + "CZM_HFA_Series_M/";
			String imageRoot = root + "ReferencedImage_M/";
			String dateTimeRoot = root + "GeneralStudy_M/";
			String eyeRoot = root + "GeneralSeries_M/";

			String patientId = patientRoot + "patient_id";
			String patientGivenName = patientRoot + "patients_name/given_name";
			String patientFamilyName = patientRoot + "patients_name/family_name";
			String patientDoB = patientRoot + "patients_birth_date";

			String deviceTestName = machineRoot + "test_name";
			String deviceTestStrategy = machineRoot + "test_strategy";

			String deviceTestDate = dateTimeRoot + "study_date";
			String deviceTestTime = dateTimeRoot + "study_time";

			String fileReference = imageRoot + "file_reference";
			String eye = eyeRoot + "laterality";
			// set the given values, if they exist:
			metaData.setGivenName(this.evaluate(document, xPath, patientGivenName));
			metaData.setFamilyName(this.evaluate(document, xPath, patientFamilyName));
			metaData.setDob(this.evaluate(document, xPath, patientDoB));
			metaData.setTestDate(this.evaluate(document, xPath, deviceTestDate));
			metaData.setTestTime(this.evaluate(document, xPath, deviceTestTime));
			metaData.setTestStrategy(this.evaluate(document, xPath, deviceTestStrategy));
			metaData.setTestPattern(this.evaluate(document, xPath, deviceTestName));
			metaData.setPatientId(this.evaluate(document, xPath, patientId));
			metaData.setFileReference(this.evaluate(document, xPath, fileReference));
			metaData.setEye(this.evaluate(document, xPath, eye));
		} catch (SAXException e) {
			// nothing to do
			e.printStackTrace();
		} catch (IOException e) {
			// nothing to do
			e.printStackTrace();
		} catch (ParserConfigurationException pcex) {
			// nothing to do
			pcex.printStackTrace();
		}
		return metaData;
	}

	/**
	 *
	 * Evaluates an xpath expression.
	 *
	 * @param document non-null XML document to test.
	 *
	 * @param xpath xpath object (non-null) to test the pattern.
	 *
	 * @param pattern non-null pattern to test.
	 *
	 * @return the string representing the result of evaluating the given
	 * pattern; null otherwise.
	 */
	private String evaluate(Document document, XPath xpath, String pattern) {
		String metaData = null;
		try {
			metaData = xpath.compile(pattern).evaluate(document);
		} catch (XPathExpressionException e) {
			// nothing to do
			e.printStackTrace();
		}
		return metaData;
	}

	/**
	 * Determine if the given file contains valid XML.
	 *
	 * @param file existing file to test; must contain valid XML.
	 *
	 * @return true if the file could be successfully parsed; false otherwise.
	 */
	protected boolean validate(File file) {
		boolean valid = false;
		InputStream istream = null;
		try {
			DocumentBuilderFactory dBF = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = dBF.newDocumentBuilder();
			istream = new FileInputStream(file);
			Document doc = builder.parse(istream);
			valid = true;
		} catch (NullPointerException e) {
			// nothing to do
			e.printStackTrace();
		} catch (Exception e) {
			// nothing to do
			e.printStackTrace();
		} finally {
			if (istream != null) {
				try {
					istream.close();
				} catch (IOException ioex) {
					// nothing to do
					ioex.printStackTrace();
				}
			}
			if (!valid) {
				// create appropriate report:
				HumphreyFieldMetaData metaData = new HumphreyFieldMetaData(this.regex);
				metaData.addFieldError(DbUtils.ERROR_BADLY_FORMED_XML);
				this.generateReport(file, metaData, false);
			}
			return valid;
		}
	}

	/**
	 * Given data from the exam, persist the information and return the created
	 * report.
	 *
	 * @param file non-null XML data file containing patient data.
	 * @param metaData meta data parsed from the given file.
	 * @param parsed true if the file was successfully parsed; false otherwise.
	 *
	 * @return a report with the meta data attached to it. The report will be
	 * persisted.
	 */
	protected FieldReport generateReport(File file, HumphreyFieldMetaData metaData,
			boolean parsed) {

		session = HibernateUtil.getSessionFactory().getCurrentSession();
		session.getTransaction().begin();

		FieldReport report = new FieldReport();
		report.setParsed(parsed);
		report.setDob(metaData.getDob());
		report.setEye(metaData.getEye());
		report.setFileReference(metaData.getFileReference());
		report.setFileName(file.getName());
		report.setFirstName(metaData.getGivenName());
		report.setLastName(metaData.getFamilyName());
		report.setPatientId(metaData.getPatientId());
		report.setStudyDate(metaData.getTestDate());
		report.setStudyTime(metaData.getTestTime());
		report.setTestName(metaData.getTestPattern());
		report.setTestType(metaData.getTestStrategy());
		report.setReportTime(Calendar.getInstance().getTime());
		report.setDirectory(this.getDirectory(this.dir.getAbsolutePath()));

		session.save(report);

		if (!metaData.getFieldErrors().isEmpty()) {
			// create appropriate error reports against specified report:
			for (Iterator<Integer> it = metaData.getFieldErrors().iterator(); it.hasNext();) {
				Object o = it.next();
				FieldError err = DbUtils.getError((int) o);
				FieldErrorReport errReport = new FieldErrorReport();
				errReport.setFieldError(err);
				errReport.setFieldReport(report);
				session.save(errReport);
			}
		}

		session.refresh(report);
		session.getTransaction().commit();

		return report;
	}

	/**
	 * Pre-generate required directory entries in the database. These
	 * directories are required to associate with reports after files are moved
	 * to the error or archive directories, for example.
	 */
	private void generateDirectories() {
		session = HibernateUtil.getSessionFactory().getCurrentSession();
		session.getTransaction().begin();
		File[] dirs = new File[]{this.dir, this.archiveDir, this.errDir};
		for (File file : dirs) {
			Query query = session.createQuery("from Directory where dir_path = :dir_path ");
			query.setParameter("dir_path", file.getAbsolutePath());
			List list = query.list();
			Directory newDir = new Directory();
			if (list.isEmpty()) {
				newDir.setDirPath(file.getAbsolutePath());
				session.save(newDir);
			}
		}
		session.getTransaction().commit();
	}

	/**
	 * Gets the specified directory, if it exists.
	 *
	 * @param path non-null path giving absolute path of directory.
	 *
	 * @return a valid directory if it exists; null otherwise.
	 */
	private Directory getDirectory(String path) {
		Directory directory = null;
		session = HibernateUtil.getSessionFactory().getCurrentSession();
		Query query = session.createQuery("from Directory where dir_path = :dir_path ");
		query.setParameter("dir_path", path);
		List list = query.list();
		if (!list.isEmpty()) {
			directory = (Directory) list.get(0);
		}
		return directory;
	}

	public File getDir() {
		return dir;
	}

	public void setDir(File dir) {
		this.dir = dir;
	}

	public File getErrDir() {
		return errDir;
	}

	public void setErrDir(File errDir) {
		this.errDir = errDir;
	}

	public File getArchiveDir() {
		return archiveDir;
	}

	public void setArchiveDir(File archiveDir) {
		this.archiveDir = archiveDir;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getRegex() {
		return regex;
	}

	public void setRegex(String regex) {
		this.regex = regex;
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

	/**
	 * 
	 * @return 
	 */
	public int[] getImageOptions() {
		if (this.imageOptions != null) {
			return this.imageOptions;
		} else {
			return this.DEFAULT_IMAGE_OPTIONS;
		}
	}

	/**
	 * 
	 * @param imageOptions 
	 */
	public void setImageOptions(String imageOptions) {
		// let's check all the values are valid:
		String[] options = imageOptions.split(",");
		int[] values = new int[options.length];
		for(int i = 0; i < options.length; i++) {
			try {
				int val = Integer.parseInt(options[i].trim());
				// don't accept negative values:
				if (0 > val) {
					throw new IllegalArgumentException("Invalid image options: "
							+ imageOptions + "; cannot be negative value");
				}
				values[i] = val;
			} catch(NumberFormatException nfex) {
				throw new IllegalArgumentException("Invalid image options: "
						+ imageOptions + "; mustube a positive integer.");
			}
		}
		this.imageOptions = values;
	}
}
