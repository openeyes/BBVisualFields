CREATE TABLE IF NOT EXISTS directory (
  id INT NOT NULL AUTO_INCREMENT,
  dir_path VARCHAR(256) NOT NULL,
  PRIMARY KEY (id)
);
CREATE TABLE IF NOT EXISTS resource_type (
  id INT NOT NULL AUTO_INCREMENT,
  resource_name VARCHAR(256) NOT NULL,
  PRIMARY KEY (id)
);
CREATE TABLE IF NOT EXISTS field_report (
  id INT NOT NULL AUTO_INCREMENT,
  report_time DATETIME NOT NULL,
  parsed BOOLEAN DEFAULT NULL,
  file_name VARCHAR(256) DEFAULT NULL,
  file_reference VARCHAR(256) DEFAULT NULL,
  study_date VARCHAR(256) NULL,
  study_time VARCHAR(256) DEFAULT NULL,
  test_type VARCHAR(256) NULL,
  test_name VARCHAR(256) DEFAULT NULL,
  patient_id VARCHAR(128) DEFAULT NULL,
  first_name VARCHAR(256) DEFAULT NULL,
  last_name VARCHAR(256) DEFAULT NULL,
  dob VARCHAR(256) DEFAULT NULL,
  gender VARCHAR(10) DEFAULT NULL,
  eye VARCHAR(10) DEFAULT NULL,
  directory_id INT,
  PRIMARY KEY (id),
  FOREIGN KEY (directory_id) REFERENCES directory(id)
);
CREATE TABLE IF NOT EXISTS field_error (
  id INT NOT NULL AUTO_INCREMENT,
  description VARCHAR(256) DEFAULT NULL,
  PRIMARY KEY (id)
);
CREATE TABLE IF NOT EXISTS field_error_report (
  id INT NOT NULL AUTO_INCREMENT,
  field_error_id INT NOT NULL,
  field_report_id INT NOT NULL,
  FOREIGN KEY (field_error_id) REFERENCES field_error(id),
  FOREIGN KEY (field_report_id) REFERENCES field_report(id),
  PRIMARY KEY (id)
);
CREATE TABLE IF NOT EXISTS comms_log (
  id INT NOT NULL AUTO_INCREMENT,
  report_time DATETIME NOT NULL,
  result TEXT DEFAULT NULL,
  return_code INT NOT NULL,
  resource_type_id INT NOT NULL,
  field_report_id INT NOT NULL,
  PRIMARY KEY (id),
  FOREIGN KEY (resource_type_id) REFERENCES resource_type(id),
  FOREIGN KEY (field_report_id) REFERENCES field_report(id)
);
CREATE TABLE IF NOT EXISTS duplicate (
  id INT NOT NULL AUTO_INCREMENT,
  file_name VARCHAR(256) DEFAULT NULL,
  sys_time LONG NOT NULL,
  directory_id INT,
  PRIMARY KEY (id),
  FOREIGN KEY (directory_id) REFERENCES directory(id)
);
insert into field_error (description) values ("Patient not found in PAS");
insert into field_error (description) values ("Badly formed XML");
insert into field_error (description) values ("Invalid patient identifier");
insert into field_error (description) values ("Missing field - name");
insert into field_error (description) values ("Missing field - DoB");
insert into field_error (description) values ("Missing field - patient idenfitier");
insert into field_error (description) values ("Missing field - study time");
insert into field_error (description) values ("Missing field - study date");
insert into field_error (description) values ("Missing field - eye");
insert into field_error (description) values ("Missing field - test name");
insert into field_error (description) values ("Missing field - test strategy");
insert into field_error (description) values ("Missing image - file reference is invalid");
insert into field_error (description) values ("Unknown patient in OpenEyes");
insert into field_error (description) values ("Badly formed patient identifier");
insert into field_error (description) values ("Sunames do not match");
insert into field_error (description) values ("DoB does not match");
insert into resource_type (resource_name) values ("media");
insert into resource_type (resource_name) values ("diagnosticReport");