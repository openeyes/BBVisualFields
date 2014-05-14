/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.org.openeyes.diagnostics;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import org.im4java.core.ConvertCmd;
import org.im4java.core.IMOperation;
import org.im4java.process.ProcessStarter;
import org.springframework.beans.factory.annotation.Autowired;
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
 *
 * @author rich
 */
public abstract class AbstractFieldProcessor implements Runnable {

    private final static Logger log = Logger.getLogger(AbstractFieldProcessor.class.getName());
    /** Default hos num regex. */
    public static final String DEFAULT_REGEX = "^([0-9]{1,9})$";
    /** Directory to watch for incoming files. */
    protected File dir;
    /** Directory to move files to. */
    protected File errDir;
    /** Successfully processed reports are moved to this directory. */
    protected File archiveDir;
    /** Hospital/PID regex; PIDs that fail this pattern will be rejected. */
    protected String regex = AbstractFieldProcessor.DEFAULT_REGEX;
    /** Some OSs require the path setting for ImageMagick. */
    protected String globalSearchPath = null;
    /** Defaults when no CLI options given for image crop & scale. */
    public static final int[] DEFAULT_IMAGE_OPTIONS = {1368, 666, 662, 658, 300, 306};
    /** Specify parameters to crop and scale the image. */
    protected int[] imageOptions;
    /** Include the XML source in the table data? Default to false. */
    protected boolean includeSource = false;
    /** How long to wait (seconds) between checking for new reports. */
    private int interval = 1;
    /**
     * Hibernate persistence session object.
     */
    protected Session session;
    private SessionFactory sessionFactory;

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    @Autowired(required = true)
    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * Process the given XML file and determine it's contents.
     * 
     * The proecss should check for validity of the file.
     * 
     * @param f
     * @return 
     */
    public abstract void processFile(File f);
    
    /**
     * 
     */
    protected void checkDir() {
        // get file list -  all XML files
        File[] files = this.dir.listFiles(new FileFilter() {

            public boolean accept(File pathname) {
                return pathname.getName().toLowerCase().endsWith(".xml");
            }
        });
        for (File file : files) {System.out.println("processing " + file.getName());
            this.processFile(file);
        }
    }

    /**
     * 
     * @param original
     * @param image1
     * @param image2 
     */
    protected void transformImages(File original, File image1, File image2) {

        try {
            if (null != this.globalSearchPath) {
                ProcessStarter.setGlobalSearchPath(this.globalSearchPath);
            }
            ConvertCmd command = new ConvertCmd();
            IMOperation op = new IMOperation();
            op.addImage(original.getAbsolutePath());
            op.format("GIF").addImage(image1.getAbsolutePath());
            command.run(op);
            op = new IMOperation();
            op.addImage(image1.getAbsolutePath());
            op.crop(this.getImageOptions()[2], this.getImageOptions()[3],
                    this.getImageOptions()[0], this.getImageOptions()[1]).thumbnail(this.getImageOptions()[4], this.getImageOptions()[5]);
            op.format("GIF").addImage(image2.getAbsolutePath());
            command.run(op);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 
     * @param patientRef
     * @param fieldReport
     * @param encodedData
     * @param encodedDataThumb
     * @return 
     */
    public String getHumphreyMeasurement(File xmlFile, String patientRef,
            FieldReport fieldReport, String encodedData,
            String encodedDataThumb) throws IOException {

        BASE64Encoder encoder = new BASE64Encoder();
        String reportText = "<MeasurementVisualFieldHumphrey><patient_id value=\"" + patientRef + "\"/>"
                + "<study_datetime value=\"" + fieldReport.getStudyDate() + " " + fieldReport.getStudyTime() + "\"/>"
                + "<eye value=\"" + fieldReport.getEye() + "\"/>"
                + "<file_reference value=\"" + fieldReport.getFileReference() + "\"/>"
                + "<pattern value=\"" + fieldReport.getTestName() + "\"/>"
                + "<strategy value=\"" + fieldReport.getTestType() + "\"/>"
                + "<image_scan_data contentType=\"text/html\" value=\"" + encodedData
                + "\"/>" + "<image_scan_crop_data value=\"" + encodedDataThumb + "\"/>";
        if (this.isIncludeSource()) {
            reportText += "<xml_file_data value=\"" + encoder.encode(IOUtils.toByteArray(new FileInputStream(xmlFile))) + "\"/>";
        }
        reportText += "</MeasurementVisualFieldHumphrey>";
        return reportText;
    }

    /**
     * 
     * @return 
     */
    public int[] getImageOptions() {
        if (this.imageOptions != null) {
            return this.imageOptions;
        } else {
            return FieldProcessor.DEFAULT_IMAGE_OPTIONS;
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
        for (int i = 0; i < options.length; i++) {
            try {
                int val = Integer.parseInt(options[i].trim());
                // don't accept negative values:
                if (0 > val) {
                    throw new IllegalArgumentException("Invalid image options: "
                            + imageOptions + "; cannot be negative value");
                }
                values[i] = val;
            } catch (NumberFormatException nfex) {
                throw new IllegalArgumentException("Invalid image options: "
                        + imageOptions + "; mustube a positive integer.");
            }
        }
        this.imageOptions = values;
    }

    /**
     *
     * @param metaData
     * @param report
     * @param file
     * @return
     */
    protected void moveFile(HumphreyFieldMetaData metaData, FieldReport report,
            File file) throws IOException {
        session = HibernateUtil.getSessionFactory().getCurrentSession();
        session.getTransaction().begin();
        session.update(report);
        for (Iterator<FieldErrorReport> it = report.getFieldErrorReports().iterator(); it.hasNext();) {
            session.update(it.next());
        }
        if (!report.getFieldErrorReports().isEmpty()) {
            log.log(Level.FINE, "Errors detected in {0}:", file.getName());
        }
        for (Iterator<FieldErrorReport> it = report.getFieldErrorReports().iterator(); it.hasNext();) {
            FieldErrorReport fer = it.next();
            log.log(Level.FINE, "\t{0} {1}", new Object[]{fer.getFieldError().getId(), fer.getFieldError().getDescription()});
        }

        session.getTransaction().commit();

        // in the case of an invalid file reference, we treat a non-existent image as existing:
        if (!this.errorReportContains(report.getFieldErrorReports(), DbUtils.ERROR_INVALID_FILE_REFERENCE)) {
            // then the image file exists - move this too:
            File imageFile = new File(this.dir, metaData.getFileReference());
            File fileToMove = new File(this.errDir, metaData.getFileReference());

            Path source = imageFile.toPath();
            try {
                Files.move(source, source.resolveSibling(fileToMove.getAbsolutePath()));
            } catch (IOException e) {
                e.printStackTrace();
                throw new IOException("Unable to rename " + imageFile.getAbsolutePath()
                        + " to " + fileToMove.getAbsolutePath());
            }
//			if (!imageFile.renameTo(fileToMove)) {
//                            throw new IOException("Unable to rename " + imageFile.getAbsolutePath()
//                                    + " to " + fileToMove.getAbsolutePath());
//                        }
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

    public boolean isIncludeSource() {
        return includeSource;
    }

    public void setIncludeSource(boolean includeSource) {
        this.includeSource = includeSource;
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

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public String getRegex() {
        return regex;
    }

    public String getGlobalSearchPath() {
        return globalSearchPath;
    }

    public void setGlobalSearchPath(String globalSearchPath) {
        this.globalSearchPath = globalSearchPath;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
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
            if ("CZM-XML".equals(document.getDocumentElement().getNodeName())) {
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
            } else if ("HFA_EXPORT".equals(document.getDocumentElement().getNodeName())) {
                String root = "/HFA_EXPORT/";

                String patientRoot = root + "PATIENT/";

                String studyRoot = patientRoot + "STUDY/";
                String visitDate = this.evaluate(document, xPath, studyRoot + "VISIT_DATE");
                String seriesRoot = studyRoot + "SERIES/";
                String fieldExam = seriesRoot + "FIELD_EXAM/";
                String examTime = this.evaluate(document, xPath, fieldExam + "EXAM_TIME");
                String staticTest = fieldExam + "STATIC_TEST/";
                // laterality not stored in this version of the file; obtain it via file name
                String eye = "L";
                // OS/OD == oculus sinister/dexter = left/right
                if (file.getName().contains("_OD_")) {
                    eye = "R";
                }
                String fileReference = fieldExam + "/SINGLE_EXAM_IMAGE/IMAGE_FILE_NAME";

                metaData.setPatientId(this.evaluate(document, xPath, patientRoot + "PATIENT_ID"));
                metaData.setDob(this.evaluate(document, xPath, patientRoot + "BIRTH_DATE"));
                metaData.setGivenName(this.evaluate(document, xPath, patientRoot + "GIVEN_NAME"));
                metaData.setFamilyName(this.evaluate(document, xPath, patientRoot + "LAST_NAME"));
                metaData.setEye(eye);
                metaData.setTestPattern(this.getPattern(this.evaluate(document, xPath, staticTest + "TEST_PATTERN")));
                metaData.setTestStrategy(this.getStrategy(this.evaluate(document, xPath, staticTest + "TEST_STRATEGY")));
                metaData.setTestDate(visitDate);
                metaData.setTestTime(examTime);
                metaData.setFileReference(this.evaluate(document, xPath, fileReference));
            }
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
     *	+--------------+
     *	| name         |
     *	+--------------+
     *	| 10-2         |
     *	| S S-24-2 Thr |
     *	| 30-2 Thu     |
     *	| Macula       |
     *	| 60-4         |
     *	| Nasal Step   |
     *	+--------------+
     * 
     * @param pattern
     * @return 
     */
    private String getPattern(String pattern) {
        String val = null;
        int result = Integer.parseInt(pattern);
        switch (result) {
            case 2:
                val = "30-2 Thu";
                break;
            case 10:
                val = "10-2";
                break;
            case 25:
                val = "S S-24-2 Thr";
                break;
            case 30:
                val = "60-4";
                break;
        }
        return val;
    }

    /**
     * 
     *	+----------------+
     *	| name           |
     *	+----------------+
     *	| SITA-Standard  |
     *	| SITA-Fast      |
     *	| Full-Threshold |
     *	+----------------+
     * @param pattern
     * @return 
     */
    private String getStrategy(String strategy) {
        String val = null;
        int result = Integer.parseInt(strategy);
        switch (result) {
            case 4:
                val = "SITA-Standard";
                break;
            case 6:
                val = "SITA-Fast";
                break;
            case 11:
                val = "SITA-Standard";
                break;
        }
        return val;
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
    protected void generateDirectories(File[] dirs) {
        session = HibernateUtil.getSessionFactory().getCurrentSession();
        session.getTransaction().begin();
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
}
