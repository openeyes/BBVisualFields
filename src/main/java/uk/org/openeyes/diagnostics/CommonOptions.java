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

/**
 *
 * @author rich
 */
public class CommonOptions {

    /**
     * 
     * @return 
     */
    public static Options getCommonOptions() {
        Options options = new Options();
        Option optionFile = new Option("f", "file", true,
                "Specify XML file to send, then quit (mutually exclusive with -i)");
        Option optionHost = new Option("s", "host", true,
                "Specify server to send messages to.");
        Option optionInterval = new Option("i", "interval", true,
                "Time in seconds to sleep between checking. Must be > 0");
        Option optionPort = new Option("p", "port", true,
                "Port to connect to on server.");
        Option optionRegex = new Option("r", "regex", true,
                "Regular expression to determine valid patient identifiers."
                + " Defaults to " + AbstractFieldProcessor.DEFAULT_REGEX);
        Option optionGlobalSearchPath = new Option("g", "global-search-path", true,
                "Specify search path for applications (like ImageMagick, for example).");
        Option optionInDir = new Option("d", "dir", true,
                "Directory to watch for new files.");
        Option optionDupDir = new Option("u", "duplicates", true,
                "Duplicate files (successfully transferred) are moved to this directory.");
        Option optionErrDir = new Option("e", "errDir", true,
                "Dirctory to move files that that failed validation.");
        Option optionArchiveDir = new Option("a", "archive-dir", true,
                "Directory to move sucessfully transferred files to.");
        Option optionImageOpts = new Option("o", "image-options", true,
                "Specify location and segment of humphrey test to extract, along with"
                + " scaling parameters. Format: x,y,w,h,x1,y1 where x,y is the"
                + " the location to cut image with wxh size, scaled to x1,y1."
                + " Scaling parameters (x1,y1) are optional and can be omitted.");
        Option optionSource = new Option("x", "xml-source", false,
                "Include XML source file information with captured data. Default"
                + " is false.");
        Option optionHelp = new Option("h", "help", false,
                "Print this help then quit.");
        options.addOption(optionErrDir);
        options.addOption(optionFile);
        options.addOption(optionInDir);
        options.addOption(optionArchiveDir);
        options.addOption(optionGlobalSearchPath);
        options.addOption(optionHost);
        options.addOption(optionInterval);
        options.addOption(optionImageOpts);
        options.addOption(optionPort);
        options.addOption(optionDupDir);
        options.addOption(optionSource);
        options.addOption(optionHelp);
        options.addOption(optionRegex);
        return options;
    }

    /**
     * 
     * @param args 
     */
    public static void parseCommonOptions(AbstractFieldProcessor watcher, Options options, String[] args) {
        try {
            CommandLineParser parser = new PosixParser();
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption("help") || cmd.hasOption('h')) {
                FhirUtils.printHelp(options);
            }
            if (cmd.hasOption("g") || cmd.hasOption("global-search-path")) {
                watcher.setGlobalSearchPath(cmd.getOptionValue("global-search-path"));
            }
            if (cmd.hasOption("d") || cmd.hasOption("dir")) {
                watcher.setDir(new File(cmd.getOptionValue("dir")));
                if (!watcher.getDir().exists()) {
                    System.err.println(watcher.getDir().getAbsolutePath()
                            + " does not exist. Specify a valid watch directory.");
                    System.exit(1);
                }
            }
            if (cmd.hasOption("a") || cmd.hasOption("archive-dir")) {
                watcher.setArchiveDir(new File(cmd.getOptionValue("archive-dir")));
                if (!watcher.getArchiveDir().exists()) {
                    System.err.println(watcher.getArchiveDir().getAbsolutePath()
                            + " does not exist. Specify a valid directory.");
                    System.exit(1);
                }
            }
            if (cmd.hasOption("r") || cmd.hasOption("regex")) {
                watcher.setRegex(cmd.getOptionValue("regex"));
            }
            
//	  TODO - duplicates are an issue that needs to be dealt with
//      if (cmd.hasOption("u") || cmd.hasOption("duplicates")) {
//        watcher.setDuplicateDir(new File(cmd.getOptionValue("duplicates")));
//        if (!watcher.getDuplicateDir().exists()) {
//          System.err.println(watcher.getDuplicateDir() + " does not exist. "
//                  + "Specify a valid directory.");
//          System.exit(1);
//        }
//      }
            if (cmd.hasOption("e") || cmd.hasOption("errDir")) {
                watcher.setErrDir(new File(cmd.getOptionValue("errDir")));
                if (!watcher.getErrDir().exists()) {
                    System.err.println(watcher.getErrDir() + " does not exist. "
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
            } else {

                    // post checks
                    if (null != watcher) {
                            Thread t = new Thread(watcher);
                            t.start();
                    }
            }
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {

        if (args.length == 0) {
            System.out.println("Try -h");
            System.exit(1);
        }
        FieldProcessor watcher = new FieldProcessor();

        Options options = CommonOptions.getCommonOptions();
        CommonOptions.parseCommonOptions(watcher, options, args);
    }
}
