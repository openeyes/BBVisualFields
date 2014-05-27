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
package uk.org.openeyes.diagnostics.db;

import java.util.List;
import org.hibernate.Query;
import org.hibernate.Session;
import uk.org.openeyes.diagnostics.db.ResourceType;

/**
 *
 * @author rich
 */
public class DbUtils {
  
  public static final int ERROR_PATIENT_NOT_IN_PAS = 1;
  public static final int ERROR_BADLY_FORMED_XML = 2;
  public static final int ERROR_INVALID_PID = 3;
  public static final int ERROR_MISSING_NAME = 4;
  public static final int ERROR_MISSING_DOB = 5;
  public static final int ERROR_MISSING_PID = 6;
  public static final int ERROR_MISSING_TIME = 7;
  public static final int ERROR_MISSING_DATE = 8;
  public static final int ERROR_MISSING_LATERALITY = 9;
  public static final int ERROR_MISSING_TEST_NAME = 10;
  public static final int ERROR_MISSING_TEST_STRATEGY = 11;
  public static final int ERROR_INVALID_FILE_REFERENCE = 12;
  public static final int ERROR_UNKOWN_OE_PATIENT = 13;
  public static final int ERROR_BADLY_FORMED_PID = 14;
  public static final int ERROR_NO_SURNAME_MATCH = 15;
  public static final int ERROR_NO_DOB_MATCH = 16;
  
  public static final int FHIR_RESOURCE_TYPE_MEDIA = 1;
  public static final int FHIR_RESOURCE_TYPE_DIAGNOSTIC_REPORT = 2;
  
  /**
   *
   * @param code
   * @return
   */
  public static final FieldError getError(int code) {
    FieldError err = null;
    Session session = HibernateUtil.getSessionFactory().getCurrentSession();
    session.getTransaction().begin();
    Query query = session.createQuery("from FieldError where id = :id ");
    query.setParameter("id", code);
    List list = query.list();
    if (!list.isEmpty()) {
      err = (FieldError) list.get(0);
    }
    return err;
  }
  
  /**
   *
   * @param code
   * @return
   */
  public static ResourceType getResourceType(int code) {
    ResourceType resourceType = null;
    Session session = HibernateUtil.getSessionFactory().getCurrentSession();
    session.getTransaction().begin();
    Query query = session.createQuery("from ResourceType where id = :id ");
    query.setParameter("id", code);
    List list = query.list();
    if (!list.isEmpty()) {
      resourceType = (ResourceType) list.get(0);
    }
    return resourceType;
  }
}
