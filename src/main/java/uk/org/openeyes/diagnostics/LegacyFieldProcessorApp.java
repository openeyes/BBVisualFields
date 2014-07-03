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
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;


public class LegacyFieldProcessorApp {

  public static void main(String[] args) {

    if (args.length == 0) {
      System.out.println("Error: no options specified. Try -h");
      System.exit(1);
    }
    Options options = CommonOptions.getCommonOptions();
    Option optionFile = new Option("f", "file", true,
            "Specify XML file to send, then quit (mutually exclusive with -i)");
    Option optionInDir = new Option("d", "dir", true,
            "Directory to watch for new files.");
    Option optionRegex = new Option("r", "regex", true,
            "Regular expression to determine valid patient identifiers."
			+ " Defaults to " + AbstractFieldProcessor.DEFAULT_REGEX);
    Option optionGlobalSearchPath = new Option("g", "global-search-path", true,
            "Specify search path for applications (like ImageMagick, for example).");
    Option optionErrDir = new Option("e", "errDir", true,
            "Dirctory to move files that that failed validation.");
    Option optionArchiveDir = new Option("a", "archive-dir", true,
            "Directory to move sucessfully transferred files to.");
    Option optionLegacy = new Option("l", "legacy-dir", true,
            "Do not transfer files via the API; Rather, package them as OE-"
			+ "compatible resources and write them to file in the specified"
			+ " directory, the purpose being to run a separate script to" 
			+ " transfer them.");
    Option optionImageOpts = new Option("o", "image-options", true,
            "Specify location and segment of humphrey test to extract, along with"
			+ " scaling parameters. Format: x,y,w,h,x1,y1 where x,y is the"
			+ " the location to cut image with wxh size, scaled to x1,y1.");
    Option optionSource = new Option("x", "xml-source", false,
            "Include XML source file information with captured data. Default"
			+ " is false.");
    Option optionHelp = new Option("h", "help", false,
            "Print this help then quit.");
    options.addOption(optionErrDir);
    options.addOption(optionFile);
    options.addOption(optionInDir);
    options.addOption(optionArchiveDir);
    options.addOption(optionImageOpts);
    options.addOption(optionGlobalSearchPath);
    options.addOption(optionLegacy);
    options.addOption(optionSource);
    options.addOption(optionHelp);
    options.addOption(optionRegex);
    CommandLineParser parser = new PosixParser();
    LegacyFieldProcessor watcher = new LegacyFieldProcessor();
    CommonOptions.parseCommonOptions(watcher, options, args);
    try {
      CommandLine cmd = parser.parse(options, args);
      if (cmd.hasOption("help") || cmd.hasOption('h')) {
        FhirUtils.printHelp(options);
      }
      if (cmd.hasOption("g") || cmd.hasOption("global-search-path")) {
        watcher.setGlobalSearchPath(cmd.getOptionValue("global-search-path"));
      }
      if (cmd.hasOption("e") || cmd.hasOption("errDir")) {
        watcher.setErrDir(new File(cmd.getOptionValue("errDir")));
        if (!watcher.getErrDir().exists()) {
          System.err.println(watcher.getErrDir() + " does not exist. "
                  + "Specify a valid directory.");
          System.exit(1);
        }
      }
      if (cmd.hasOption("l") || cmd.hasOption("legacy")) {
        watcher.setLegacyDir(new File(cmd.getOptionValue("legacy-dir")));
        if (!watcher.getLegacyDir().exists()) {
          System.err.println(watcher.getLegacyDir() + " does not exist. "
                  + "Specify a valid directory.");
          System.exit(1);
        }
      }
      if (cmd.hasOption("a") || cmd.hasOption("archive")) {
        watcher.setArchiveDir(new File(cmd.getOptionValue("archive-dir")));
        if (!watcher.getArchiveDir().exists()) {
          System.err.println(watcher.getArchiveDir() + " does not exist. "
                  + "Specify a valid directory.");
          System.exit(1);
        }
      }
      if (cmd.hasOption("o") || cmd.hasOption("image-options")) {
        watcher.setImageOptions(cmd.getOptionValue("image-options"));
      }
      if (cmd.hasOption("x") || cmd.hasOption("xml-source")) {
        watcher.setIncludeSource(true);
      }
      if (cmd.hasOption("f") || cmd.hasOption("file")) {
        String file = cmd.getOptionValue("file");
		watcher.processFile(new File(file));
      }
    } catch (Exception ex) {
		ex.printStackTrace();
      System.err.println("Error: " + ex.getMessage());
    }
  }
}
