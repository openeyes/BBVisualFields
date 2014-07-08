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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import sun.misc.BASE64Encoder;
import uk.org.openeyes.diagnostics.db.FieldReport;

/**
 *
 * @author rich
 */
public class LegacyFieldProcessor extends AbstractFieldProcessor {

    private final static Logger log = Logger.getLogger(LegacyFieldProcessor.class.getName());
    /** Directory to transfer intermediate files to for
     * processing by OpenEyes. */
    private File legacyDir;

    public File getLegacyDir() {
        return legacyDir;
    }

    public void setLegacyDir(File legacyDir) {
        this.legacyDir = legacyDir;
    }
    
    /**
     *
     */
    public void run() {
        while (true) {
            try {
                this.checkDir();
                Thread.sleep(this.getInterval() * 1000);
            } catch (InterruptedException iex) {
                iex.printStackTrace();
            }
        }
    }

    /**
     * 
     * @param file
     * @return 
     */
    @Override
    public void processFile(File file) {
        this.generateDirectories(new File[]{this.dir, this.archiveDir,
                    this.errDir, this.legacyDir});

        log.fine("Processing " + file.getName());
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
                log.fine("records do NOT match; moving to " + this.errDir);
                return;
            }

            log.fine("records match");
            this.transferLegacyHumphreyVisualField(file, imageFile, report);
            File moveToFile = new File(this.archiveDir, imageFile.getName());
//            imageFile.renameTo(new File(this.hashDir(moveToFile.getParentFile(), metaData), moveToFile.getName()));
	    moveFile(imageFile, new File(this.hashDir(moveToFile.getParentFile(), metaData), moveToFile.getName()));
            // don't use boolean result, not always consistent, just check if new file exists:
            if (!moveToFile.exists()) {
                log.warning("Unable to move " + imageFile.getAbsolutePath());
                // TODO clean up - mark file as ignored?
            }
            moveToFile = new File(this.archiveDir, file.getName());
//            file.renameTo(new File(this.hashDir(moveToFile.getParentFile(), metaData), moveToFile.getName()));
	    moveFile(file, new File(this.hashDir(moveToFile.getParentFile(), metaData), moveToFile.getName()));
            // don't use boolean result, not always consistent, just check if new file exists:
            if (!moveToFile.exists()) {
                log.warning("Unable to move " + file.getAbsolutePath());
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
    private void transferLegacyHumphreyVisualField(File xmlFile, File file,
            FieldReport fieldReport) throws IOException {

        // execute the operation
        File imageConverted = new File(file.getParentFile(),
                FilenameUtils.getBaseName(file.getName()) + ".gif");
        File imageCropped = new File(file.getParentFile(),
                FilenameUtils.getBaseName(file.getName()) + "-cropped.gif");
        transformImages(file, imageConverted, imageCropped);
        BASE64Encoder encoder = new BASE64Encoder();
        FileInputStream fis = new FileInputStream(imageConverted);
        String encodedData = encoder.encode(IOUtils.toByteArray(fis,
                fis.available()));

        fis = new FileInputStream(imageCropped);
        String encodedDataThumb = encoder.encode(IOUtils.toByteArray(fis,
                fis.available()));

        String reportText = this.getHumphreyMeasurement(xmlFile,
                "__OE_PATIENT_ID_" + String.format("%07d", new Integer(fieldReport.getPatientId())) + "__",
                fieldReport, encodedData, encodedDataThumb);

        File f2 = new File(this.getLegacyDir(),
                FilenameUtils.getBaseName(file.getName()) + ".fmes");
        f2.createNewFile();
        FileUtils.write(f2, reportText);
        imageConverted.delete();
        imageCropped.delete();

    }
}
