/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.org.openeyes.diagnostics;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uk.org.openeyes.diagnostics.db.DbUtils;
import uk.org.openeyes.diagnostics.db.FieldError;

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
  
  private String regex;
  
  private Set<Integer> errors;

  /**
   *
   * @param regex
   */
  public HumphreyFieldMetaData(String regex) {
    this.regex = regex;
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
    if (!isSet(patientId)) {
      this.addFieldError(DbUtils.ERROR_MISSING_PID);
    } else {
      if (!this.checkIdentifier(this.regex)) {
        this.addFieldError(DbUtils.ERROR_BADLY_FORMED_PID);
      }
    }
  }

  /**
   * Is the specified patient ID valid compared to the given regex?
   * 
   * @param regex the non-null expression to test the PID against.
   * 
   * @return true if the PID is a valid regex; false otherwise.
   */
  private boolean checkIdentifier(String regex) {
    boolean valid = false;
    if (this.patientId != null) {
      Pattern p = Pattern.compile(regex);
      Matcher matcher = p.matcher(this.patientId);
      valid = matcher.matches();
    }
    return valid;
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
