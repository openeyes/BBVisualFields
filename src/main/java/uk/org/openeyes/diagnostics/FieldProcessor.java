/**
 * OpenEyes
 *
 * (C) Moorfields Eye Hospital NHS Foundation Trust, 2008-2011
 * (C) OpenEyes Foundation, 2011-2013
 * This file is part of OpenEyes.
 * OpenEyes is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * OpenEyes is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with OpenEyes in a file titled COPYING. If not, see <http://www.gnu.org/licenses/>.
 *
 * @package OpenEyes
 * @link http://www.openeyes.org.uk
 * @author OpenEyes <info@openeyes.org.uk>
 * @copyright Copyright (c) 2008-2011, Moorfields Eye Hospital NHS Foundation Trust
 * @copyright Copyright (c) 2011-2013, OpenEyes Foundation
 * @license http://www.gnu.org/licenses/gpl-3.0.html The GNU General Public License V3.0
 */
/**
 * OpenEyes
 *
 * (C) Moorfields Eye Hospital NHS Foundation Trust, 2008-2011
 * (C) OpenEyes Foundation, 2011-2013
 * This file is part of OpenEyes.
 * OpenEyes is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * OpenEyes is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with OpenEyes in a file titled COPYING. If not, see <http://www.gnu.org/licenses/>.
 *
 * @package OpenEyes
 * @link http://www.openeyes.org.uk
 * @author OpenEyes <info@openeyes.org.uk>
 * @copyright Copyright (c) 2008-2011, Moorfields Eye Hospital NHS Foundation Trust
 * @copyright Copyright (c) 2011-2013, OpenEyes Foundation
 * @license http://www.gnu.org/licenses/gpl-3.0.html The GNU General Public License V3.0
 */
package uk.org.openeyes.diagnostics;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.hibernate.Query;
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
public class FieldProcessor extends AbstractFieldProcessor {

    private final static Logger log = Logger.getLogger(FieldProcessor.class.getName());
    /**
     * Host to send reports to.
     */
    private String host = "localhost";
    /**
     * Username.
     */
    private String authenticationUsername;
    /**
     * Password for authentication.
     */
    private String authenticationPassword;
    /**
     * Port number to send reports on.
     */
    private int port = 80;
    /**
     * Which directory to place files that failed to send.
     */
    private File outgoingDir;
    /**
     * Time, in minutes for when to send old unsent files.
     */
    private static final int[] BACKOFF_TIMES = {5, 10, 20, 60, 120, 240, 480, 960, 86400};

    /**
     * Main thread for checking files.
     */
    public void run() {
        while (true) {
            try {
                this.checkDir();
                // check which files need to be sent (again):
                this.checkOutgoing();
                Thread.sleep(this.getInterval() * 1000);
            } catch (InterruptedException iex) {
                iex.printStackTrace();
            }
        }
    }

    /**
     * Checks the outgoing directory and see if there are any files that
     * need re-sending.
     */
    private void checkOutgoing() {
        // get file list -  all XML files
        File[] files = this.outgoingDir.listFiles(new FileFilter() {

            public boolean accept(File pathname) {
                return pathname.getName().toLowerCase().endsWith(".xml");
            }
        });
        for (File file : files) {
            this.checkFile(file);
        }
    }

    /**
     * Check specified file to see if it needs sending again.
     * 
     * @param f the non-null file to send. 
     */
    private void checkFile(File f) {

        session = HibernateUtil.getSessionFactory().getCurrentSession();

        session.getTransaction().begin();
        Query query = session.createQuery("from FieldReport where file_name=:file_name");
        query.setParameter("file_name", f.getName());
        List list = query.list();
        FieldReport report = null;
        if (!list.isEmpty()) {
            report = (FieldReport) list.get(0);
        }
        if (report != null) {
            long time = ((long) ((Calendar.getInstance().getTimeInMillis() - f.lastModified()) / 1000));
            query = session.createQuery(
                    "from FieldErrorReport err where field_report_id= :report_id");
            query.setParameter("report_id", report.getId());
            int results = query.list().size();
            if (results > 0) {
                int timeDiff = 0;
                if (results < FieldProcessor.BACKOFF_TIMES.length) {
                    timeDiff = FieldProcessor.BACKOFF_TIMES[results - 1];
                } else {
                    timeDiff = FieldProcessor.BACKOFF_TIMES[FieldProcessor.BACKOFF_TIMES.length - 1];
                }
                if (time > timeDiff) {
                    // move the file back to import directory for re-processing:
                    File imageFile = new File(this.outgoingDir, report.getFileReference());
                    File xmlFile = new File(this.outgoingDir, report.getFileName());
                    HumphreyFieldMetaData metaData = new HumphreyFieldMetaData(this.regex);
                    // only need certain patient-identifiable fields:
                    metaData.setDob(report.getDob());
                    metaData.setFamilyName(report.getLastName());
                    metaData.setGivenName(report.getFirstName());
                    metaData.setFileReference(report.getFileReference());
                    metaData.setPatientId(report.getPatientId());
                    try {
                        this.send(metaData, xmlFile, imageFile, report);
                    } catch (IOException ioex) {
                        log.warning("IO Error processing "
                                + f.getName() + ": " + ioex.getMessage());
                    }
                }
            }

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
     * Processes the file and attempts to move it to a correct location.
     * 
     * @param file a non-null already existing file.
     */
    @Override
    public void processFile(File file) {

        this.generateDirectories(new File[]{this.dir, this.archiveDir,
                    this.errDir});
        log.log(Level.FINE, "Processing {0}", file.getName());
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
            this.send(metaData, file, imageFile, report);

            if (!report.getFieldErrorReports().isEmpty()) {
                // if it is an unknown patient, try a resend in case the patient
                // has been picked up by the PAS at this point:
                boolean outgoing = false;
                for (Iterator<FieldErrorReport> it = report.getFieldErrorReports().iterator(); it.hasNext();) {
                    if (it.next().getFieldError().getId() == DbUtils.ERROR_UNKOWN_OE_PATIENT) {
                        outgoing = true;
                    }
                }
                if (outgoing) {

                    File dest = new File(this.getOutgoingDir(), file.getName());
                    File imgDest = new File(this.getOutgoingDir(), report.getFileReference());
                    file.renameTo(dest);
                    imageFile.renameTo(imgDest);
                    log.log(Level.WARNING, "Failed to send; moving {0} to {1}", new Object[]{file.getName(), this.outgoingDir});
                    return;
                } else {
                    // it's a general error, so just move it to the error dir:
                    this.moveFile(metaData, report, file);
                    log.log(Level.WARNING, "Error in record; moving {0} to {1}", new Object[]{file.getName(), this.errDir});
                    return;
                }
            }

            // move the image file to the archive directory:
            File moveToFile = new File(this.archiveDir, imageFile.getName());
            imageFile.renameTo(moveToFile);
            // don't use boolean result, not always consistent, just check if new file exists:
            if (!moveToFile.exists()) {
                log.log(Level.WARNING, "Unable to move {0}", imageFile.getAbsolutePath());
                // TODO clean up - mark file as ignored?
            }
            // now move the XML:
            moveToFile = new File(this.archiveDir, file.getName());
            if (file.renameTo(moveToFile)) {
                log.info("Moved " + file.getName() + " to " + moveToFile.getParentFile().getAbsolutePath());
            }
            
            // don't use boolean result, not always consistent, just check if new file exists:
            if (!moveToFile.exists()) {
                log.log(Level.WARNING, "Unable to move {0}", file.getAbsolutePath());
                // TODO clean up - mark file as ignored?
            }
        } catch (FileNotFoundException fnfex) {
            fnfex.printStackTrace();
        } catch (IOException fnfex) {
            fnfex.printStackTrace();
        }
    }

    /**
     * Attempts to send the details of the scan to the OE server. This involves
     * contacting the server to see if the patient exists, and if they do,
     * sending it on.
     * 
     * @param metaData meta data containing patient information.
     * @param file the XML file being processed.
     * @param imageFile the non-null image file.
     * @param report non-null field report.
     * @throws IOException 
     */
    private void send(HumphreyFieldMetaData metaData, File file, File imageFile, FieldReport report) throws IOException {
        String reportText = null;
        try {
            // get the report's patient id and find out if they exist:
            Patient patient = new FhirUtils().readPatient(this.getHost(),
                    this.getPort(), metaData, this.getAuthenticationUsername(),
                    this.getAuthenticationPassword());
            if (patient == null) { // not found
                this.setUnknownOEPatient(report);
            } else {
                reportText = this.generateMeasurementText(patient.getId(),
                        file, imageFile, report);
                this.transferHumphreyVisualField(reportText, report, file);
            }
        } catch (IllegalArgumentException | ConnectException iaex) {
            // the illegal argument exception occurs when (for example) a patient hos num
            // contains no leading zeros, but should - e.g. 0123456 vs. 123456
            // in the report, pid is 123456 but /should/ be 0123456
            // This results in an illegal argument exception and thinks the patient can't be found
            this.setUnknownOEPatient(report);
            if (reportText == null) {
                // mark the patient ID (or lack of) in the report text:
                reportText = this.generateMeasurementText("__OE_PATIENT_ID_"
                        + report.getPatientId() + "__",
                        file, imageFile, report);
            }
            // mark the message as not having been sent and re-try at a later date:
            File measurementFile = new File(this.getOutgoingDir(),
                    FilenameUtils.getBaseName(file.getName()) + ".fmes");
            measurementFile.createNewFile();
            IOUtils.write(reportText, new FileWriter(measurementFile));
        }
    }

    /**
     * Mark a patient as unknown. This happens when a lookup is performed
     * for a patient, but they are not yet present in the PAS.
     * 
     * @param report the non-null report to check to see if the patient
     * exists.
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
        session.update(report);
        session.getTransaction().commit();
    }

    /**
     * Create the main measurement text that will be understood by the server.
     * 
     * @param patientRef the patient's reference/hos num.
     * @param xmlFile non-null existent file containing humphrey XML.
     * @param file the non-null image file.
     * @param fieldReport field report containing 
     * 
     * @return String measurement text.
     * 
     * @throws IOException if the 
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

        String reportText = null;

        reportText = this.getHumphreyMeasurement(xmlFile,
                String.format("%07d", new Integer(patientRef)), fieldReport,
                encodedData, encodedDataThumb);

        imageConverted.delete();
        imageCropped.delete();
        return reportText;
    }

    /**
     * Transfer the specified report to the server.
     *
     * @param reportText actual report text to send to the server.
     * @param fieldReport non-null report containing field exam information.
     * @param file non-null existent file containing CZM XML.
     */
    private void transferHumphreyVisualField(String reportText, FieldReport fieldReport, File file) throws IOException {
        HttpTransfer sender = new HttpTransfer();
        sender.setHost(this.getHost());
        sender.setPort(this.getPort());
        int code = sender.send("MeasurementVisualFieldHumphrey", reportText,
                this.authenticationUsername, this.authenticationPassword);
        if (code > -1) {
            this.generateCommsLog(code,
                    DbUtils.FHIR_RESOURCE_TYPE_DIAGNOSTIC_REPORT, fieldReport,
                    sender.getResponse());
            if (code == 200) {
                boolean moved = file.renameTo(new File(this.archiveDir, file.getName()));
                if (!moved) {
                    log.log(Level.WARNING, "Unable to move {0}", file.getAbsolutePath());
                }
            }
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

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getAuthenticationUsername() {
        return authenticationUsername;
    }

    public void setAuthenticationUsername(String authenticationUsername) {
        this.authenticationUsername = authenticationUsername;
    }

    public String getAuthenticationPassword() {
        return authenticationPassword;
    }

    public void setAuthenticationPassword(String authenticationPassword) {
        this.authenticationPassword = authenticationPassword;
    }
}
