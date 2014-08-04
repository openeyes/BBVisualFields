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

import java.util.HashSet;
import java.util.Set;
import uk.org.openeyes.diagnostics.db.DbUtils;

/**
 *
 * @author rich
 */
public class HumphreyFieldMetaData {

  private String familyName;
  private String givenName;
  private String dob;
  private String testPattern;
  private String testStrategy;
  private String testDate;
  private String testTime;
  private String patientId;
  private String fileReference;
  private String eye;
  
  private Set<Integer> errors;

  public HumphreyFieldMetaData() {
    errors = new HashSet<Integer>();
  }
  
  public void addFieldError(int error_type) {
    this.errors.add(error_type);
  }
   
  public Set<Integer> getFieldErrors() {
    return this.errors;
  }

  public String getPatientId() {
    return patientId;
  }

  /**
   *
   * @param patientId
   */
  public void setPatientId(String patientId) {
    this.patientId = patientId;
  }

  /**
   *
   * @return
   */
  public String getFamilyName() {
    return familyName;
  }

  /**
   *
   * @param familyName
   */
  public void setFamilyName(String familyName) {
    this.familyName = familyName;
    if (!isSet(familyName)) {
      this.addFieldError(DbUtils.ERROR_MISSING_NAME);
    }
  }

  public String getGivenName() {
    return givenName;
  }

  public void setGivenName(String givenName) {
    this.givenName = givenName;
  }
  
  public String getDob() {
    return dob;
  }

  public void setDob(String dob) {
    this.dob = dob;
    if (!isSet(dob)) {
      this.addFieldError(DbUtils.ERROR_MISSING_DOB);
    }
  }

  public String getTestPattern() {
    return testPattern;
  }

  public void setTestPattern(String testName) {
    this.testPattern = testName;
    if (!isSet(testName)) {
      this.addFieldError(DbUtils.ERROR_MISSING_TEST_NAME);
    }
  }

  public String getTestStrategy() {
    return testStrategy;
  }

  public void setTestStrategy(String testStrategy) {
    this.testStrategy = testStrategy;
    if (!isSet(testStrategy)) {
      this.addFieldError(DbUtils.ERROR_MISSING_TEST_STRATEGY);
    }
  }

  public String getTestDate() {
    return testDate;
  }

  public void setTestDate(String testDate) {
    this.testDate = testDate;
    if (!isSet(testDate)) {
      this.addFieldError(DbUtils.ERROR_MISSING_DATE);
    }
  }

  public String getTestTime() {
    return testTime;
  }

  public void setTestTime(String testTime) {
    this.testTime = testTime;
    if (!isSet(testTime)) {
      this.addFieldError(DbUtils.ERROR_MISSING_TIME);
    }
  }

  public String getFileReference() {
    return fileReference;
  }

  public void setFileReference(String fileReference) {
    this.fileReference = fileReference;
    if (!isSet(fileReference)) {
      this.addFieldError(DbUtils.ERROR_INVALID_FILE_REFERENCE);
    }
  }

  private boolean isSet(String data) {
    return (data != null && !"".equals(data));
  }

  public String getEye() {
    return eye;
  }

  public void setEye(String eye) {
    this.eye = eye;
    if (!isSet(eye)) {
      this.addFieldError(DbUtils.ERROR_MISSING_LATERALITY);
    }
  }
  
}
